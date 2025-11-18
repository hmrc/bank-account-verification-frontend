/*
 * Copyright 2023 HM Revenue & Customs
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

package bankaccountverification.api

import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Partial, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification._
import com.codahale.metrics.SharedMetricRegistries
import org.apache.pekko.stream.Materializer
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}

import java.time.Instant
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

class ApiControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with OptionValues {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  private lazy val sessionStore = mock[JourneyRepository]
  private lazy val mockAuthConnector = mock[AuthConnector]
  private lazy val controller = app.injector.instanceOf[ApiController]

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .configure("microservice.services.access-control.allow-list.1" -> "test-user-agent")
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .overrides(bind[JourneyRepository].toInstance(sessionStore))
      .build()
  }

  "POST /init" should {

    val newJourneyId = ObjectId.get()

    "return 200" when {
      "A continueUrl is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        when(
          sessionStore.create(
            meq(Some("1234")),
            meq("serviceIdentifier"),
            meq("continueUrl"),
            meq(None),
            meq(None),
            meq(Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode")))),
            meq(None),
            meq(Some(BACSRequirements.defaultBACSRequirements)),
            meq(Some(TimeoutConfig("url", 100, None))),
            meq(Some("/sign-out")),
            meq(Some(5)),
            meq(Some("/too-many-requests")),
            meq(Some(false))
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json = Json.toJson(
          InitRequest("serviceIdentifier", "continueUrl",
            address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)), signOutUrl = Some("/sign-out"),
            maxCallConfig = Some(InitRequestMaxCallConfig(count = 5, redirectUrl = "/too-many-requests")),
            useNewGovUkServiceNavigation = Some(false)))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)
        val initResponse = contentAsJson(result).as[InitResponse]

        status(result) shouldBe Status.OK
        Try(new ObjectId(initResponse.journeyId)).toOption shouldBe Some(newJourneyId)

        initResponse.startUrl shouldBe
          bankaccountverification.web.routes.AccountTypeController.getAccountType(initResponse.journeyId).url

        initResponse.completeUrl shouldBe
          bankaccountverification.api.routes.ApiController.complete(initResponse.journeyId).url

        initResponse.detailsUrl shouldBe None
      }

      "prepopulated data is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        when(
          sessionStore.create(
            meq(Some("1234")),
            meq("serviceIdentifier"),
            meq("continueUrl"),
            meq(None),
            meq(None),
            meq(Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode")))),
            meq(Some(PrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123")))),
            meq(Some(BACSRequirements.defaultBACSRequirements)),
            meq(Some(TimeoutConfig("url", 100, None))),
            meq(None),
            meq(None),
            meq(None),
            meq(Some(false))
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json = Json.toJson(InitRequest("serviceIdentifier", "continueUrl",
          address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
          prepopulatedData = Some(InitRequestPrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))),
          timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)),
          useNewGovUkServiceNavigation = Some(false)
        ))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)
        val initResponse = contentAsJson(result).as[InitResponse]

        status(result) shouldBe Status.OK
        Try(new ObjectId(initResponse.journeyId)).toOption shouldBe Some(newJourneyId)

        initResponse.startUrl shouldBe
          bankaccountverification.web.routes.AccountTypeController.getAccountType(initResponse.journeyId).url

        initResponse.completeUrl shouldBe
          bankaccountverification.api.routes.ApiController.complete(initResponse.journeyId).url

        initResponse.detailsUrl shouldBe
          Some(bankaccountverification.web.personal.routes.PersonalVerificationController.getAccountDetails(initResponse.journeyId).url)
      }
    }

    "return 400" when {
      "A continueUrl is not provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val json = Json.parse("{}")
        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }

      "Prepopulated data is present but account type is not provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val json = Json.parse(
          """{
            |    "serviceIdentifier":"serviceIdentifier",
            |    "continueUrl":"continueUrl",
            |    "address":{"lines":["Line 1","Line 2"],"town":"Town","postcode":"Postcode"},
            |    "prepopulatedData": {
            |        "name": "Bob"
            |    }
            |}""".stripMargin)

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }

      "A signout url is provided that violates the security policy" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val json = Json.toJson(
          InitRequest("serviceIdentifier", "continueUrl",
            address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)),
            signOutUrl = Some("www.google.com"),
            useNewGovUkServiceNavigation = Some(false)))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }

      "A maxCallConfig is provided with a count but without a redirectUrl" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val json = Json.toJson(
          InitRequest("serviceIdentifier", "continueUrl",
            address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)),
            useNewGovUkServiceNavigation = Some(false)))
          .as[JsObject] + ("maxCallConfig" -> JsObject(Seq("count" -> Json.toJson(5))))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }

      "A maxCallConfig is provided with a redirectUrl but without a count" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val json = Json.toJson(
          InitRequest("serviceIdentifier", "continueUrl",
            address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)),
            useNewGovUkServiceNavigation = Some(false)))
          .as[JsObject] + ("maxCallConfig" -> JsObject(Seq("redirectUrl" -> Json.toJson("/too-many-requests"))))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "return 401" when {
      "The user is not logged in" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
          .withJsonBody(Json.parse("{}"))
        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "return 403" when {
      "The calling service has not been registered" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        val fakeRequest = FakeRequest("POST", "/api/init")
          .withHeaders(HeaderNames.USER_AGENT -> "non-registered-user-agent")
          .withJsonBody(Json.parse("{}"))
        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.FORBIDDEN
      }
    }
  }

  "GET /complete" should {
    "return 200" when {
      "a valid journeyId is provided with a personal response" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val journeyId = ObjectId.get()
        val returnData = Journey(
          journeyId,
          Some("1234"),
          Instant.now,
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(
              PersonalAccountDetails(Some("Bob"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), None, Some(Yes), Some(Partial), Some(No), Some("sort-code-bank-name-personal"), iban = None, matchedAccountName = Some("account-name"))),
            None
          ),
          timeoutConfig = None,
          useNewGovUkServiceNavigation = Some(false))

        when(sessionStore.findById(meq(journeyId))(any())).thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/${journeyId.toHexString}")
        val completeResult = controller.complete(journeyId.toHexString).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String] shouldBe "personal"
        (json \ "personal" \ "accountName").as[String] shouldBe "Bob"
        (json \ "personal" \ "sortCode").as[String] shouldBe "203040"
        (json \ "personal" \ "accountNumber").as[String] shouldBe "12345678"
        (json \ "personal" \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (json \ "personal" \ "accountExists").as[String] shouldBe "yes"
        (json \ "personal" \ "rollNumber").as[String] shouldBe "roll1"
        (json \ "personal" \ "nameMatches").as[String] shouldBe "yes"
        (json \ "personal" \ "addressMatches").as[String] shouldBe "indeterminate"
        (json \ "personal" \ "nonConsented").as[String] shouldBe "indeterminate"
        (json \ "personal" \ "subjectHasDeceased").as[String] shouldBe "indeterminate"
        (json \ "personal" \ "nonStandardAccountDetailsRequiredForBacs").as[String] shouldBe "no"
        (json \ "personal" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-personal"
        (json \ "personal" \ "iban").asOpt[String] shouldBe None
      }

      "a valid journeyId is provided with a business response" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val journeyId = ObjectId.get()
        val returnData = Journey(
          journeyId,
          Some("1234"),
          Instant.now,
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Business),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            None,
            Some(
              BusinessAccountDetails(Some("Bob Ltd"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), None, Some(No), Some(Partial), None, None, Some("sort-code-bank-name-business"), iban = Some("some-iban"), matchedAccountName = Some("account-name")))),
          timeoutConfig = None,
          useNewGovUkServiceNavigation = Some(false))

        when(sessionStore.findById(meq(journeyId))(any()))
          .thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/${journeyId.toHexString}")
        val completeResult = controller.complete(journeyId.toHexString).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String] shouldBe "business"
        (json \ "business" \ "companyName").as[String] shouldBe "Bob Ltd"
        (json \ "business" \ "sortCode").as[String] shouldBe "203040"
        (json \ "business" \ "accountNumber").as[String] shouldBe "12345678"
        (json \ "business" \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (json \ "business" \ "accountExists").as[String] shouldBe "no"
        (json \ "business" \ "rollNumber").as[String] shouldBe "roll1"
        (json \ "business" \ "companyNameMatches").as[String] shouldBe "yes"
        (json \ "business" \ "companyPostCodeMatches").as[String] shouldBe "indeterminate"
        (json \ "business" \ "companyRegistrationNumberMatches").as[String] shouldBe "indeterminate"
        (json \ "business" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-business"
        (json \ "business" \ "iban").asOpt[String] shouldBe None
      }
    }

    "return NotFound" when {
      "a non-existent journeyId is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val nonExistentJourneyId = ObjectId.get()
        when(sessionStore.findById(meq(nonExistentJourneyId))(any())).thenReturn(Future.successful(None))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
        val completeResult = controller.complete(nonExistentJourneyId.toHexString).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.NOT_FOUND
      }

      "a journey is found but it belongs to another user" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("9876"))

        val journeyId = ObjectId.get()
        val returnData = Journey(
          journeyId,
          Some("1234"),
          Instant.now,
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Business),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            None,
            Some(
              BusinessAccountDetails(Some("Bob Ltd"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), None, Some(No), Some(Indeterminate), None, None, Some("sort-code-bank-name-business"), iban = None, matchedAccountName = None))),
          timeoutConfig = None,
          useNewGovUkServiceNavigation = Some(false))

        when(sessionStore.findById(meq(journeyId))(any()))
          .thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$journeyId")
        val completeResult = controller.complete(journeyId.toHexString).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.NOT_FOUND
      }
    }

    "return BadRequest" when {
      "an invalid journeyId is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val nonExistentJourneyId = "invalid-journey-id"
        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
        val completeResult = controller.complete(nonExistentJourneyId).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.BAD_REQUEST
      }
    }

    "return 401" when {
      "The user is not logged in" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        val nonExistentJourneyId = ObjectId.get()
        when(sessionStore.findById(meq(nonExistentJourneyId))(any())).thenReturn(Future.successful(None))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
        val completeResult = controller.complete(nonExistentJourneyId.toHexString).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.UNAUTHORIZED
      }
    }
  }
}
