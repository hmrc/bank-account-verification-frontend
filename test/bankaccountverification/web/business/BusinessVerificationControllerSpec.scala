/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{ZoneOffset, ZonedDateTime}

import akka.stream.Materializer
import bankaccountverification._
import bankaccountverification.connector.BarsBusinessAssessResponse
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.Business
import bankaccountverification.web.{AccountTypeController, AccountTypeRequest, AccountTypeRequestEnum, VerificationService}
import com.codahale.metrics.SharedMetricRegistries
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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.http.HttpException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class BusinessVerificationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
  implicit val timeout = 1 second

  val mockRepository = mock[JourneyRepository]
  val mockService = mock[VerificationService]
  val serviceIdentifier = "example-service"
  val continueUrl = "https://continue.url"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[JourneyRepository].toInstance(mockRepository))
      .overrides(bind[VerificationService].toInstance(mockService))
      .build()
  }

  private val injector = app.injector
  private val accountTypeController = injector.instanceOf[AccountTypeController]
  private val controller = injector.instanceOf[BusinessVerificationController]
  implicit val mat: Materializer = injector.instanceOf[Materializer]

  "GET /start" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()

      "return 404" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "return 200" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(accountType = Some(Business), business = Some(BusinessSession(companyName = Some("company_name"), companyRegistrationNumber = Some("SC1231234"), sortCode = Some("11-22-33"),
              accountNumber = Some("12092398")))))))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result = controller.getAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }
  }

  "POST /start" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()
      val data = AccountTypeRequest(AccountTypeRequestEnum.Business)

      "return 404" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("POST", s"/start/${id.stringify}").withBody(data)
        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data = AccountTypeRequest(AccountTypeRequestEnum.Business)

      "return 400" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessSession(companyName = Some("some company name"), companyRegistrationNumber = Some("SC1231234"), sortCode = Some("112233"),
              accountNumber = Some("12345678")))))))))

        val fakeRequest = FakeRequest("POST", s"/start/${id.stringify}")
          .withBody(data)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey and form are valid" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "Redirect to the getAccountDetails endpoint" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None))))

        reset(mockService)
        when(mockService.setAccountType(meq(id), meq(AccountTypeRequestEnum.Business))(any(), any()))
          .thenReturn(Future.successful(true))

        val fakeRequest =
          FakeRequest("POST", s"/verify/business/${id.stringify}")
            .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Business.toString)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/verify/business/${id.stringify}")
      }
    }
  }

  "GET /verify" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()

      "return 404" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/verify/${id.stringify}")
        val result = controller.getAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "return 200" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(
              Some(Business),
              Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
              None,
              Some(BusinessSession(
                companyName = Some("some company name"), companyRegistrationNumber = Some("SC1231234"),
                sortCode = Some("112233"), accountNumber = Some("12345678")))))))))

        val fakeRequest = FakeRequest("GET", s"/verify/${id.stringify}")
        val result = controller.getAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }
  }

  "POST /verify" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()

      "return 404" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}")
        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      val data = BusinessVerificationRequest("", None, "", "", None)

      "return 400" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(
              accountType = Some(Business),
              business = Some(BusinessSession(
                companyName = Some("some company name"), companyRegistrationNumber = Some("SC1231234"), sortCode = Some("112233"),
                accountNumber = Some("12345678")))))))))

        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}")
          .withBody(data)

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey is valid, a valid form is posted and the bars call fails" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data = BusinessVerificationRequest("some company name", Some("SC1231234"), "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      "Redirect to the confirm view" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessSession(companyName = Some("some company name"), None, sortCode = Some("112233"),
              accountNumber = Some("12345678")))))))))

        reset(mockService)
        when(mockService.assessBusiness(any(), any())(any(), any())).thenReturn(Future.successful(Failure(new HttpException("SERVER ON FIRE", 500))))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any())(any(), any()))
          .thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/business/${id.stringify}")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks indicate an issue" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data = BusinessVerificationRequest("some company name", Some("SC1231234"), "123456", "12345678", None)

      val formWithErrors = BusinessVerificationRequest.form
        .fillAndValidate(data)
        .withError("Error", "a.specific.error")

      val barsBusinessAssessResponse =
        BarsBusinessAssessResponse(Yes, No, None, Indeterminate, Indeterminate, Indeterminate, Indeterminate, Some(No))

      "Render the view and display the errors" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessSession(companyName = Some("some company name"), None, sortCode = Some("112233"),
              accountNumber = Some("12345678")))))))))

        reset(mockService)
        when(mockService.assessBusiness(any(), any())(any(), any()))
          .thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any())(any(), any()))
          .thenReturn(Future.successful(formWithErrors))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) should include("a.specific.error")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks pass and the account existence is indeterminate" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data = BusinessVerificationRequest("some company name 2", Some("SC1231234"), "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      val barsBusinessAssessResponse =
        BarsBusinessAssessResponse(Yes, No, None, Indeterminate, Indeterminate, Indeterminate, Indeterminate, Some(No))

      "Redirect to the confirm view" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessSession(companyName = Some("some company name 2"), companyRegistrationNumber = Some("SC1231234"), sortCode = Some("112233"),
              accountNumber = Some("12345678")))))))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any())(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/business/${id.stringify}")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks pass and the account exists" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data = BusinessVerificationRequest("some company name", Some("SC1231234"), "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      val barsBusinessAssessResponse =
        BarsBusinessAssessResponse(Yes, No, None, Yes, Indeterminate, Indeterminate, Indeterminate, Some(No))

      "Redirect to the continueUrl" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None, Some(
            Session(accountType = Some(Business), business = Some(bankaccountverification.BusinessSession(companyName = Some("some company name"), companyRegistrationNumber = Some("SC1231234"), sortCode = Some("112233"),
              accountNumber = Some("12345678")))))))))

        reset(mockService)
        when(mockService.assessBusiness(meq(data), any())(any(), any())).thenReturn(Future.successful(Success(barsBusinessAssessResponse)))
        when(mockService.processBusinessAssessResponse(meq(id), any(), any())(any(), any())).thenReturn(Future.successful(form))

        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"$continueUrl/${id.stringify}")
      }
    }
  }

  "GET /confirm" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()

      "return 404" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.stringify}")
        val result = controller.getConfirmDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey" should {
      "confirmation view is rendered correctly without a bank name" in {
        val id = BSONObjectID.generate()
        val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None,
            Some(Session(
              Some(Business),
              Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
              None,
              Some(BusinessSession(
                Some("some company name"), Some("SC123456"), Some("112233"), Some("12345678")))))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.stringify}")

        val result = controller.getConfirmDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentAsString(result) should include("some company name")
        contentAsString(result) should include("SC123456")
        contentAsString(result) should include("112233")
        contentAsString(result) should include("12345678")
        contentAsString(result) should include("We have not been able to check the account details. Make sure the details you entered are correct.")
      }

      "confirmation view is rendered correctly with a bank name" in {
        val id = BSONObjectID.generate()
        val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None,
            Some(Session(
              Some(Business),
              Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
              None,
              Some(BusinessSession(
                Some("some company name"), Some("SC123456"), Some("112233"), Some("12345678"), sortCodeBankName = Some("sort-code-bank-name-business")))))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/business/${id.stringify}")

        val result = controller.getConfirmDetails(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentAsString(result) should include("some company name")
        contentAsString(result) should include("SC123456")
        contentAsString(result) should include("112233")
        contentAsString(result) should include("12345678")
        contentAsString(result) should include("We have not been able to check the account details with sort-code-bank-name-business. Make sure the details you entered are correct.")
      }
    }
  }
}
