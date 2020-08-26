package bankaccountverification

import bankaccountverification.api.{CompleteResponse, InitRequest}
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.connector.{BankAccountReputationConnector, BarsPersonalAssessResponse, BarsValidationResponse}
import bankaccountverification.web.{AccountTypeRequest, VerificationRequest}
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.any
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito._
import org.scalatestplus.mockito._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test._

import scala.concurrent.Future
import scala.util.Success

class BankAccountVerificationITSpec() extends AnyWordSpec with GuiceOneServerPerSuite with MockitoSugar {
  private val mockBankAccountReputationConnector = mock[BankAccountReputationConnector]
  override lazy val app = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder()
      .overrides(bind[BankAccountReputationConnector].toInstance(mockBankAccountReputationConnector))
      .build()
  }

  private def getCCParams(cc: AnyRef) =
    cc.getClass.getDeclaredFields.foldLeft(Map.empty[String, Seq[String]]) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> Seq(f.get(cc).toString))
    }

  "BankAccountVerification" in {
    when(mockBankAccountReputationConnector.assessPersonal(any(), any(), any())(any(), any())).thenReturn(
      Future.successful(
        Success(
          BarsPersonalAssessResponse(Yes, Yes, Indeterminate, Indeterminate, Indeterminate, Indeterminate, Some(No))
        )
      )
    )

    val wsClient = app.injector.instanceOf[WSClient]
    val baseUrl  = s"http://localhost:$port"

    val initUrl = s"$baseUrl/api/init"

    val initRequest = InitRequest("serviceIdentifier", "continueUrl")
    val initResponse =
      await(wsClient.url(initUrl).post[JsValue](Json.toJson(initRequest)))

    initResponse.status shouldBe 200
    val journeyId = initResponse.json.as[String]

    val atformData        = Map("accountType" -> "personal")
    val setAccountTypeUrl = s"$baseUrl/bank-account-verification/start/$journeyId"
    val atrequest         = FakeRequest().withCSRFToken
    val setAccountTypeResponse =
      await(
        wsClient
          .url(setAccountTypeUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(atrequest.headers.toSimpleMap.toSeq: _*)
          .post(atformData)
      )

    val bankAccountDetails = VerificationRequest("some-account-name", "12-12-12", "12349876")
    val formData           = getCCParams(bankAccountDetails) ++ Map("rollNumber" -> Seq())
    val verifyUrl          = s"$baseUrl/bank-account-verification/verify/$journeyId"

    val request = FakeRequest().withCSRFToken
    val verifyResponse =
      await(
        wsClient
          .url(verifyUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(request.headers.toSimpleMap.toSeq: _*)
          .post(formData)
      )

    import Journey._
    val completeUrl = s"$baseUrl/api/complete/$journeyId"
    val completeResponse =
      await(wsClient.url(completeUrl).get())
    completeResponse.status shouldBe 200
    import bankaccountverification.connector.ReputationResponseEnum._
    val sessionDataMaybe = Json.fromJson[CompleteResponse](completeResponse.json)

    sessionDataMaybe shouldBe JsSuccess[CompleteResponse](
      CompleteResponse(
        "personal",
        "some-account-name",
        "12-12-12",
        "12349876",
        accountNumberWithSortCodeIsValid = Yes,
        None,
        accountExists = Some(Yes),
        nameMatches = Some(Indeterminate),
        nonConsented = Some(Indeterminate),
        subjectHasDeceased = Some(Indeterminate),
        nonStandardAccountDetailsRequiredForBacs = Some(No)
      )
    )
  }
}
