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

package bankaccountverification.web.business

import akka.stream.Materializer
import bankaccountverification._
import bankaccountverification.connector.BarsBusinessAssessSuccessResponse
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.Business
import bankaccountverification.web.VerificationService
import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.http.HttpException

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class BusinessVerificationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
  implicit val timeout: FiniteDuration = 1 second

  private val mockRepository = mock[JourneyRepository]
  private val mockService = mock[VerificationService]
  private val mockAuthConnector = mock[AuthConnector]
  val serviceIdentifier = "example-service"
  val continueUrl = "https://continue.url"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[JourneyRepository].toInstance(mockRepository))
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .overrides(bind[VerificationService].toInstance(mockService))
      .build()
  }

  private val injector = app.injector
  private val controller = injector.instanceOf[BusinessVerificationController]
  implicit val mat: Materializer = injector.instanceOf[Materializer]

  "GET /verify/business" when {
    "the user is not logged in" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      "return 401" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)

        val fakeRequest = FakeRequest("GET", s"/verify/business/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "there is no valid journey" should {
      val id = ObjectId.get()

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/verify/business/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey but for a different user" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("9876"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              Some(Business),
              Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
              None,
              Some(BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/verify/business/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey but account type has not yet been selected" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      "redirect to the account type screen" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session()))))

        val fakeRequest = FakeRequest("GET", s"/verify/business/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/start/${id.toHexString}")
      }
    }

    "there is a valid journey" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      "return 200" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              Some(Business),
              Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
              None,
              Some(BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/verify/business/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }

    "pre-populated data has been provided" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      "the account details view should be pre-filled with this data" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Business),
            None,
            None,
            Some(BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), rollNumber = Some("ROLL.NUMBER"), iban = None, matchedAccountName = None)))))))
        val fakeRequest = FakeRequest("GET", s"/verify/business/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.OK
        val viewHtml = Jsoup.parse(contentAsString(result))
        viewHtml.getElementById("companyName").`val`() shouldBe "some company name"
        viewHtml.getElementById("accountNumber").`val`() shouldBe "12345678"
        viewHtml.getElementById("sortCode").`val`() shouldBe "112233"
        viewHtml.getElementById("rollNumber").`val`() shouldBe "ROLL.NUMBER"
      }
    }
  }

  "POST /verify/business" when {
    "the user is not logged in" should {
      val id = ObjectId.get()

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)

        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}")
        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "there is no valid journey" should {
      val id = ObjectId.get()

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}")
        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but for a different user" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      val data = BusinessVerificationRequest("", "", "", None)

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("9876"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              accountType = Some(Business),
              business = Some(BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}")
          .withBody(data)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)

      val data = BusinessVerificationRequest("", "", "", None)

      "return 400" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              accountType = Some(Business),
              business = Some(BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = None, matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}")
          .withBody(data)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey is valid, a valid form is posted and the bars call fails" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = BusinessVerificationRequest("some company name", "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      "Redirect to the confirm view" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        reset(mockService)
        when(mockService.assessBusiness(any(), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Failure(new HttpException("SERVER ON FIRE", 500))))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/business/${id.toHexString}")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks indicate an issue" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = BusinessVerificationRequest("some company name", "123456", "12345678", None)

      val formWithErrors = BusinessVerificationRequest.form
        .fillAndValidate(data)
        .withError("Error", "a.specific.error")

      val barsBusinessAssessResponse =
        BarsBusinessAssessSuccessResponse(Yes, No, None, Indeterminate, Indeterminate, No, No, Some(No), None, None)

      "Render the view and display the errors" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        reset(mockService)
        when(mockService.assessBusiness(any(), any(), meq(serviceIdentifier))(any(), any()))
          .thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(formWithErrors))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) should include("a.specific.error")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks pass and the account existence is indeterminate" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = BusinessVerificationRequest("some company name 2", "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      val barsBusinessAssessResponse =
        BarsBusinessAssessSuccessResponse(Yes, No, None, Indeterminate, Indeterminate, No, No, Some(No), None, None)

      "Redirect to the confirm view" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(companyName = Some("some company name 2"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/business/${id.toHexString}")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks pass and the account exists" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = BusinessVerificationRequest("some company name", "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      val barsBusinessAssessResponse =
        BarsBusinessAssessSuccessResponse(Yes, No, None, Yes, Indeterminate, No, No, Some(No), None, None)

      "Redirect to the continueUrl" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(companyName = Some("some company name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"$continueUrl/${id.toHexString}")
      }
    }

    "the maximum number of backend calls has been configured on the init call" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = BusinessVerificationRequest("some company name 2", "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      "Render the view and display the errors while the max call count is not met and the BARS checks indicate an issue" in {
        val barsBusinessAssessResponse =
          BarsBusinessAssessSuccessResponse(Yes, No, None, Indeterminate, Indeterminate, No, No, Some(No), None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(
              companyName = Some("some company name 2"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form.withError("Error", "a.specific.error")))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data)).withSession(Journey.callCountSessionKey -> "0")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) should include("a.specific.error")
      }

      "Redirect to the maxCallCountRedirectUrl when the max call count is met and the BARS checks indicate an issue" in {
        val barsBusinessAssessResponse =
          BarsBusinessAssessSuccessResponse(Yes, No, None, Indeterminate, Indeterminate, No, No, Some(No), None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(
              companyName = Some("some company name 2"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form.withError("Error", "a.specific.error")))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data)).withSession(Journey.callCountSessionKey -> "1")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/too-many-requests/${id.toHexString}")
      }

      "Redirect to the confirm view while the max call count is not met and an indeterminate response is received" in {
        val barsBusinessAssessResponse =
          BarsBusinessAssessSuccessResponse(Yes, No, None, Indeterminate, Indeterminate, No, No, Some(No), None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(
              companyName = Some("some company name 2"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data)).withSession(Journey.callCountSessionKey -> "0")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/business/${id.toHexString}")
      }

      "Redirect to the maxCallCountRedirectUrl when the max call count is met and an indeterminate response is received" in {
        val barsBusinessAssessResponse =
          BarsBusinessAssessSuccessResponse(Yes, No, None, Indeterminate, Indeterminate, No, No, Some(No), None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(
              companyName = Some("some company name 2"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data)).withSession(Journey.callCountSessionKey -> "1")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/too-many-requests/${id.toHexString}")
      }

      "Redirect to the continueUrl when the max call count is met but a yes response is received" in {
        val barsBusinessAssessResponse =
          BarsBusinessAssessSuccessResponse(Yes, No, None, Yes, Indeterminate, No, No, Some(No), None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessAccountDetails(
              companyName = Some("some company name 2"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any(), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.toHexString}").withJsonBody(Json.toJson(data)).withSession(Journey.callCountSessionKey -> "1")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"$continueUrl/${id.toHexString}")
      }
    }
  }

  "GET /confirm/business" when {
    "the user is not logged in" should {
      val id = ObjectId.get()

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.toHexString}")
        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "there is no valid journey" should {
      val id = ObjectId.get()

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.toHexString}")
        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey but account details have not yet been entered" should {
      "return a 404" in {
        val id = ObjectId.get()
        val expiry = LocalDateTime.now.plusMinutes(60)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(Some(Business))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.toHexString}")

        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey" should {
      "confirmation view is rendered correctly without a bank name" in {
        val id = ObjectId.get()
        val expiry = LocalDateTime.now.plusMinutes(60)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              Some(Business),
              Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
              None,
              Some(BusinessAccountDetails(Some("some company name"), Some("SC123456"), Some("112233"), Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.toHexString}")

        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentAsString(result) should include("some company name")
        contentAsString(result) should include("SC123456")
        contentAsString(result) should include("112233")
        contentAsString(result) should include("12345678")
        contentAsString(result) shouldNot include("$bankName$")
      }

      "confirmation view is rendered correctly with a bank name" in {
        val id = ObjectId.get()
        val expiry = LocalDateTime.now.plusMinutes(60)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Business),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            None,
            Some(BusinessAccountDetails(Some("some company name"), Some("SC123456"), Some("112233"), Some("12345678"), sortCodeBankName = Some("sort-code-bank-name-business"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.toHexString}")

        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentAsString(result) should include("some company name")
        contentAsString(result) should include("SC123456")
        contentAsString(result) should include("112233")
        contentAsString(result) should include("12345678")
        contentAsString(result) should include("with sort-code-bank-name-business")
      }
    }
  }
}
