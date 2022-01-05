/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bankaccountverification

import bankaccountverification.api._
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.connector.{BankAccountReputationConnector, BarsBusinessAssessSuccessResponse, BarsPersonalAssessSuccessResponse}
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.web.business.BusinessVerificationRequest
import bankaccountverification.web.personal.PersonalVerificationRequest
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate

import scala.concurrent.Future
import scala.util.Success

class BankAccountVerificationV2ITSpec() extends AnyWordSpec with GuiceOneServerPerSuite with MockitoSugar {
  private val mockBankAccountReputationConnector = mock[BankAccountReputationConnector]
  private val mockAuthConnector = mock[AuthConnector]

  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder()
        .configure("microservice.services.access-control.allow-list.1" -> "test-user-agent")
        .overrides(bind[BankAccountReputationConnector].toInstance(mockBankAccountReputationConnector))
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .build()
  }

  private def getCCParams(cc: AnyRef) =
    cc.getClass.getDeclaredFields
      .foldLeft(Seq.empty[(String, Seq[String])]) { (a, f) =>
        f.setAccessible(true)
        a ++ (if (f.getType == classOf[Option[String]])
          Seq(f.get(cc).asInstanceOf[Option[String]].map(x => f.getName -> Seq(x))).flatten
        else Seq(f.getName -> Seq(f.get(cc).toString)))
      }
      .toMap

  "PersonalBankAccountVerification" in {
    when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
      .thenReturn(Future.successful("1234"))

    when(mockBankAccountReputationConnector.assessPersonal(any(), any(), any(), any(), any())(any(), any())).thenReturn(
      Future.successful(
        Success(BarsPersonalAssessSuccessResponse(Yes, Yes, Indeterminate, Yes, Yes, Yes, Some(No), Some("sort-code-bank-name-personal"), Some("iban")))))

    val wsClient = app.injector.instanceOf[WSClient]
    val baseUrl = s"http://localhost:$port"

    val initUrl = s"$baseUrl/api/v2/init"

    val initRequest = InitRequest("serviceIdentifier", "continueUrl",
      address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
      timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)))

    val initResponse = await(wsClient.url(initUrl).withHttpHeaders(HeaderNames.USER_AGENT -> "test-user-agent").post[JsValue](Json.toJson(initRequest)))

    initResponse.status shouldBe 200
    val response = initResponse.json.as[InitResponse]

    val atformData = Map("accountType" -> "personal")
    val setAccountTypeUrl = s"$baseUrl${response.startUrl}"
    val atrequest = FakeRequest().withCSRFToken
    val setAccountTypeResponse =
      await(
        wsClient
          .url(setAccountTypeUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(atrequest.headers.toSimpleMap.toSeq: _*)
          .post(atformData)
      )

    val bankAccountDetails = PersonalVerificationRequest("some-account-name", "12-12-12", "12349876")
    val formData = getCCParams(bankAccountDetails) ++ Map("rollNumber" -> Seq())
    val verifyUrl = s"$baseUrl/bank-account-verification/verify/personal/${response.journeyId}"

    val request = FakeRequest().withCSRFToken
    val verifyResponse =
      await(
        wsClient
          .url(verifyUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(request.headers.toSimpleMap.toSeq: _*)
          .post(formData))

    val completeUrl = s"$baseUrl${response.completeUrl}"
    val completeResponse = await(wsClient.url(completeUrl).get())
    completeResponse.status shouldBe 200

    import bankaccountverification.connector.ReputationResponseEnum._
    val sessionDataMaybe = Json.fromJson[CompleteV2Response](completeResponse.json)

    sessionDataMaybe shouldBe JsSuccess[CompleteV2Response](
      CompleteV2Response(
        accountType = Personal,
        personal = Some(
          PersonalCompleteV2Response("some-account-name", "121212", "12349876", Yes, None, accountExists = Some(Yes), nameMatches = Some(Indeterminate), nonStandardAccountDetailsRequiredForBacs = Some(No), sortCodeBankName = Some("sort-code-bank-name-personal"), sortCodeSupportsDirectDebit = Some(Yes), sortCodeSupportsDirectCredit = Some(Yes), iban = Some("iban"))),
        business = None
      )
    )
  }

  "BusinessBankAccountVerification" in {
    when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
      .thenReturn(Future.successful("1234"))

    when(mockBankAccountReputationConnector.assessBusiness(any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(
      Future.successful(Success(BarsBusinessAssessSuccessResponse(Yes, Yes, Some("sort-code-bank-name-business"), Indeterminate, Indeterminate, Yes, No, Some(No), None))))

    val wsClient = app.injector.instanceOf[WSClient]
    val baseUrl = s"http://localhost:$port"

    val initUrl = s"$baseUrl/api/v2/init"

    val initRequest = InitRequest(
      serviceIdentifier = "serviceIdentifier",
      continueUrl = "continueUrl",
      address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
      bacsRequirements = Some(InitBACSRequirements(directDebitRequired = true, directCreditRequired = false)),
      timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)))

    val initResponse =
      await(wsClient.url(initUrl).withHttpHeaders(HeaderNames.USER_AGENT -> "test-user-agent").post[JsValue](Json.toJson(initRequest)))

    initResponse.status shouldBe 200
    val response = initResponse.json.as[InitResponse]

    val atformData = Map("accountType" -> "business")
    val setAccountTypeUrl = s"$baseUrl${response.startUrl}"
    val atrequest = FakeRequest().withCSRFToken
    val setAccountTypeResponse =
      await(
        wsClient
          .url(setAccountTypeUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(atrequest.headers.toSimpleMap.toSeq: _*)
          .post(atformData))

    val bankAccountDetails =
      BusinessVerificationRequest("some-company-name", "12-12-12", "12349876", None)
    val formData = getCCParams(bankAccountDetails) ++ Map("rollNumber" -> Seq())
    val verifyUrl = s"$baseUrl/bank-account-verification/verify/business/${response.journeyId}"

    val request = FakeRequest().withCSRFToken
    val verifyResponse =
      await(
        wsClient
          .url(verifyUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(request.headers.toSimpleMap.toSeq: _*)
          .post(formData))

    val completeUrl = s"$baseUrl${response.completeUrl}"
    val completeResponse =
      await(wsClient.url(completeUrl).get())
    completeResponse.status shouldBe 200
    import bankaccountverification.connector.ReputationResponseEnum._
    val sessionDataMaybe = Json.fromJson[CompleteV2Response](completeResponse.json)

    sessionDataMaybe shouldBe JsSuccess[CompleteV2Response](
      CompleteV2Response(
        accountType = Business,
        business = Some(
          BusinessCompleteV2Response("some-company-name", "121212", "12349876", rollNumber = None, accountNumberIsWellFormatted = Yes, accountExists = Some(Indeterminate), nameMatches = Some(Indeterminate), nonStandardAccountDetailsRequiredForBacs = Some(No), sortCodeBankName = Some("sort-code-bank-name-business"), Some(Yes), Some(No), None)
        ),
        personal = None
      )
    )
  }

  "BankAccountVerification with prepopulated account type, skipping account type screen" in {
    when(mockBankAccountReputationConnector.assessPersonal(any(), any(), any(), any(), any())(any(), any())).thenReturn(
      Future.successful(
        Success(BarsPersonalAssessSuccessResponse(Yes, Yes, Indeterminate, Yes, Yes, Yes, Some(No), Some("sort-code-bank-name-personal"), Some("iban")))))

    val wsClient = app.injector.instanceOf[WSClient]
    val baseUrl = s"http://localhost:$port"

    val initUrl = s"$baseUrl/api/v2/init"

    val initRequest = InitRequest("serviceIdentifier", "continueUrl",
      address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
      prepopulatedData = Some(InitRequestPrepopulatedData(Personal)),
      timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)))

    val initResponse = await(wsClient.url(initUrl).withHttpHeaders(HeaderNames.USER_AGENT -> "test-user-agent").post[JsValue](Json.toJson(initRequest)))

    initResponse.status shouldBe 200
    val response = initResponse.json.as[InitResponse]

    val bankAccountDetails = PersonalVerificationRequest("some-account-name", "12-12-12", "12349876")
    val formData = getCCParams(bankAccountDetails) ++ Map("rollNumber" -> Seq())
    val verifyUrl = s"$baseUrl${response.detailsUrl.get}"

    val request = FakeRequest().withCSRFToken
    val verifyResponse =
      await(
        wsClient
          .url(verifyUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(request.headers.toSimpleMap.toSeq: _*)
          .post(formData)
      )

    val completeUrl = s"$baseUrl${response.completeUrl}"
    val completeResponse = await(wsClient.url(completeUrl).get())
    completeResponse.status shouldBe 200

    import bankaccountverification.connector.ReputationResponseEnum._
    val sessionDataMaybe = Json.fromJson[CompleteV2Response](completeResponse.json)

    sessionDataMaybe shouldBe JsSuccess[CompleteV2Response](
      CompleteV2Response(
        accountType = Personal,
        personal = Some(
          PersonalCompleteV2Response("some-account-name", "121212", "12349876", Yes, None, accountExists = Some(Yes), nameMatches = Some(Indeterminate), nonStandardAccountDetailsRequiredForBacs = Some(No), sortCodeBankName = Some("sort-code-bank-name-personal"), sortCodeSupportsDirectDebit = Some(Yes), sortCodeSupportsDirectCredit = Some(Yes), Some("iban"))),
        business = None
      )
    )
  }
}
