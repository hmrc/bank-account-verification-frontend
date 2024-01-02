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

import org.apache.pekko.stream.Materializer
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Partial, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.{TimeoutConfig, _}
import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.{HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalDateTime}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.language.postfixOps

class ApiV3ControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with OptionValues {
  implicit private val timeout: FiniteDuration = 1 second

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration)
  private val appConfig = new AppConfig(configuration, serviceConfig, env)
  private lazy val sessionStore = mock[JourneyRepository]
  private lazy val mockAuthConnector = mock[AuthConnector]

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
        .configure("microservice.services.access-control.allow-list.1" -> "test-user-agent")
        .overrides(bind[ServicesConfig].toInstance(serviceConfig))
        .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
        .overrides(bind[JourneyRepository].toInstance(sessionStore))
        .overrides(bind[AppConfig].toInstance(appConfig))
        .build()
  }


  private val controller = app.injector.instanceOf[ApiV3Controller]

  implicit val mat = app.injector.instanceOf[Materializer]

  "POST /init" should {
    import InitRequest._

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
            meq(Some("/too-many-requests"))
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json = Json.toJson(InitRequest("serviceIdentifier", "continueUrl",
          address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
          timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None)), signOutUrl = Some("/sign-out"),
          maxCallConfig = Some(InitRequestMaxCallConfig(count = 5, redirectUrl = "/too-many-requests"))))

        val fakeRequest = FakeRequest("POST", "/api/v3/init")
            .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
            .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)
        val initResponse = contentAsJson(result).as[InitResponse]

        status(result) shouldBe Status.OK
        Try(new ObjectId(initResponse.journeyId)).toOption shouldBe Some(newJourneyId)

        initResponse.startUrl shouldBe s"/bank-account-verification/start/${initResponse.journeyId}"
        initResponse.completeUrl shouldBe s"/api/v3/complete/${initResponse.journeyId}"
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
            meq(None)
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json = Json.toJson(InitRequest("serviceIdentifier", "continueUrl",
          address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
          prepopulatedData = Some(InitRequestPrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))),
          timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None))
        ))

        val fakeRequest = FakeRequest("POST", "/api/v3/init")
            .withHeaders(HeaderNames.USER_AGENT -> "test-user-agent")
            .withJsonBody(json)

        val result = controller.init().apply(fakeRequest)
        val initResponse = contentAsJson(result).as[InitResponse]

        status(result) shouldBe Status.OK
        Try(new ObjectId(initResponse.journeyId)).toOption shouldBe Some(newJourneyId)

        initResponse.startUrl shouldBe s"/bank-account-verification/start/${initResponse.journeyId}"
        initResponse.completeUrl shouldBe s"/api/v3/complete/${initResponse.journeyId}"
        initResponse.detailsUrl shouldBe Some(s"/bank-account-verification/verify/personal/${initResponse.journeyId}")
      }
    }

    "return 400" when {
      "A continueUrl is not provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
            .thenReturn(Future.successful("1234"))

        val json = Json.parse("{}")
        val fakeRequest = FakeRequest("POST", "/api/v3/init")
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

        val fakeRequest = FakeRequest("POST", "/api/v3/init")
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
            signOutUrl = Some("www.google.com")))

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
            timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None))))
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
            timeoutConfig = Some(InitRequestTimeoutConfig("url", 100, None))))
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

        val fakeRequest = FakeRequest("POST", "/api/v3/init")
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

        val fakeRequest = FakeRequest("POST", "/api/v3/init")
            .withHeaders(HeaderNames.USER_AGENT -> "not-registered-user-agent")
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
              PersonalAccountDetails(Some("Bob"), Some("203040"), Some("12345678"), Some("roll1"), None, Some(Yes), Some(Yes), Some(Partial), Some(No), Some("sort-code-bank-name-personal"), iban = Some("some-iban"), matchedAccountName = Some("Robert"))),
            None
          ),
          timeoutConfig = None)

        when(sessionStore.findById(meq(journeyId))(any())).thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/v3/complete/${journeyId.toHexString}")
        val completeResult = controller.complete(journeyId.toHexString).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String] shouldBe "personal"
        (json \ "personal" \ "accountName").as[String] shouldBe "Bob"
        (json \ "personal" \ "sortCode").as[String] shouldBe "203040"
        (json \ "personal" \ "accountNumber").as[String] shouldBe "12345678"
        (json \ "personal" \ "accountNumberIsWellFormatted").as[String] shouldBe "yes"
        (json \ "personal" \ "accountExists").as[String] shouldBe "yes"
        (json \ "personal" \ "rollNumber").as[String] shouldBe "roll1"
        (json \ "personal" \ "nameMatches").as[String] shouldBe "partial"
        (json \ "personal" \ "matchedAccountName").as[String] shouldBe "Robert"
        (json \ "personal" \ "nonStandardAccountDetailsRequiredForBacs").as[String] shouldBe "no"
        (json \ "personal" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-personal"
        (json \ "personal" \ "iban").asOpt[String] shouldBe Some("some-iban")
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
              BusinessAccountDetails(Some("Bob Ltd"), Some("203040"), Some("12345678"), Some("roll1"), None, Some(Yes), Some(No), None, Some(Partial), Some(Yes), Some("sort-code-bank-name-business"), iban = Some("some-iban"), matchedAccountName = Some("Robert Ltd")))),
          timeoutConfig = None)

        when(sessionStore.findById(meq(journeyId))(any()))
            .thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/v3/complete/${journeyId.toHexString}")
        val completeResult = controller.complete(journeyId.toHexString).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String] shouldBe "business"
        (json \ "business" \ "companyName").as[String] shouldBe "Bob Ltd"
        (json \ "business" \ "sortCode").as[String] shouldBe "203040"
        (json \ "business" \ "accountNumber").as[String] shouldBe "12345678"
        (json \ "business" \ "accountNumberIsWellFormatted").as[String] shouldBe "yes"
        (json \ "business" \ "accountExists").as[String] shouldBe "no"
        (json \ "business" \ "rollNumber").as[String] shouldBe "roll1"
        (json \ "business" \ "nameMatches").as[String] shouldBe "partial"
        (json \ "business" \ "matchedAccountName").as[String] shouldBe "Robert Ltd"
        (json \ "business" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-business"
        (json \ "business" \ "iban").asOpt[String] shouldBe Some("some-iban")
      }
    }

    "return NotFound" when {
      "a non-existent journeyId is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
            .thenReturn(Future.successful("1234"))

        val nonExistentJourneyId = ObjectId.get()
        when(sessionStore.findById(meq(nonExistentJourneyId))(any())).thenReturn(Future.successful(None))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/v3/complete/$nonExistentJourneyId")
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
              BusinessAccountDetails(Some("Bob Ltd"), Some("203040"), Some("12345678"), Some("roll1"), None, Some(Yes), Some(No), Some(Indeterminate), None, None, Some("sort-code-bank-name-business"), iban = Some("some-iban"), matchedAccountName = None))),
          timeoutConfig = None)

        when(sessionStore.findById(meq(journeyId))(any()))
            .thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/v3/complete/$journeyId")
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
        val fakeCompleteRequest = FakeRequest("GET", s"/api/v3/complete/$nonExistentJourneyId")
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

        val fakeCompleteRequest = FakeRequest("GET", s"/api/v3/complete/$nonExistentJourneyId")
        val completeResult = controller.complete(nonExistentJourneyId.toHexString).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.UNAUTHORIZED
      }
    }
  }
}
