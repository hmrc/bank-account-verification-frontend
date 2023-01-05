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

package bankaccountverification.web.personal

import akka.stream.Materializer
import bankaccountverification._
import bankaccountverification.connector.BarsPersonalAssessSuccessResponse
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.Personal
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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.http.HttpException

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PersonalVerificationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
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
      .overrides(bind[VerificationService].toInstance(mockService))
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .overrides(bind[ExecutionContext].toInstance(implicitly[ExecutionContext]))
      .build()
  }

  private val injector = app.injector
  private val controller = injector.instanceOf[PersonalVerificationController]
  implicit val mat: Materializer = injector.instanceOf[Materializer]

  "GET /verify/personal" when {
    "the user is not logged in" should {
      val id = ObjectId.get()

      "return 401" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/verify/personal/${id.toHexString}")
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

        val fakeRequest = FakeRequest("GET", s"/verify/personal/${id.toHexString}")
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
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session()))))

        val fakeRequest = FakeRequest("GET", s"/verify/personal/${id.toHexString}")
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

        val fakeRequest = FakeRequest("GET", s"/verify/personal/${id.toHexString}")
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
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(PersonalAccountDetails(accountName = Some("account_name"), sortCode = Some("112233"), accountNumber = Some("12092398"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/verify/personal/${id.toHexString}")
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
            Some(Personal),
            None,
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), rollNumber = Some("ROLL.NUMBER"), iban = None, matchedAccountName = None)))))))
        val fakeRequest = FakeRequest("GET", s"/verify/personal/${id.toHexString}")
        val result = controller.getAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.OK
        val viewHtml = Jsoup.parse(contentAsString(result))
        viewHtml.getElementById("accountName").`val`() shouldBe "some account name"
        viewHtml.getElementById("accountNumber").`val`() shouldBe "12345678"
        viewHtml.getElementById("sortCode").`val`() shouldBe "112233"
        viewHtml.getElementById("rollNumber").`val`() shouldBe "ROLL.NUMBER"
      }
    }
  }

  "POST /verify/personal" when {
    val address = Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))

    "the user is not logged in" should {
      val id = ObjectId.get()

      "return 401" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("POST", s"/verify/${id.toHexString}")
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

        val fakeRequest = FakeRequest("POST", s"/verify/${id.toHexString}")
        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey but for a different user" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("", "", "")

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("9876"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              Some(Personal),
              Some(address),
              Some(bankaccountverification.PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = None, matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("POST", s"/verify/${id.toHexString}")
          .withBody(data)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("", "", "")

      "return 400" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              Some(Personal),
              Some(address),
              Some(bankaccountverification.PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = None, matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("POST", s"/verify/${id.toHexString}")
          .withBody(data)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey is valid, a valid form is posted and the bars checks fail" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("Bob", "123456", "12345678")

      val form = PersonalVerificationRequest.form.fillAndValidate(data)

      "Redirect to the confirm view" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(
              Some(Personal),
              Some(address),
              Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = None, matchedAccountName = None)))))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), any(), meq(serviceIdentifier))(any(), any()))
          .thenReturn(Future.successful(Failure(new HttpException("SERVER ON FIRE", 500))))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(form))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq : _*)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/personal/${id.toHexString}")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks indicate an issue" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("Bob", "123456", "12345678")

      val formWithErrors = PersonalVerificationRequest.form.fillAndValidate(data).withError("Error", "a.specific.error")

      val barsPersonalAssessResponse =
        BarsPersonalAssessSuccessResponse(Yes, No, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

      "Render the view and display the errors" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = None, matchedAccountName = None)))))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), any(), meq(serviceIdentifier))(any(), any()))
          .thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(formWithErrors))

        val fakeRequest = FakeRequest("POST", s"/verify/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) should include("a.specific.error")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks pass and the account exists" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("Bob", "123456", "12345678")

      val form = PersonalVerificationRequest.form.fillAndValidate(data)
      val barsPersonalAssessResponse = BarsPersonalAssessSuccessResponse(Yes, Yes, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

      "Redirect to the continueUrl" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))))
          )))

        reset(mockService)
        when(mockService.assessPersonal(any(), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        val fakeRequest = FakeRequest("POST", s"/verify/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"$continueUrl/${id.toHexString}")
      }
    }

    "the journey is valid, a valid form is posted and the bars checks pass but the account existence is indeterminate" should {
      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("Bobby", "123456", "12345678")

      val form = PersonalVerificationRequest.form.fillAndValidate(data)
      val barsPersonalAssessResponse = BarsPersonalAssessSuccessResponse(Yes, Indeterminate, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

      "Redirect to the confirm view" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))))
          )))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/personal/${id.toHexString}")
      }
    }

    "the maximum number of backend calls has been configured on the init call" should {

      val id = ObjectId.get()
      val expiry = LocalDateTime.now.plusMinutes(60)
      val data = PersonalVerificationRequest("Bob", "123456", "12345678")

      val form = PersonalVerificationRequest.form.fillAndValidate(data)

      "Render the view and display the errors while the max call count is not met and the BARS checks indicate an issue" in {
        val barsPersonalAssessResponse =
          BarsPersonalAssessSuccessResponse(Yes, Indeterminate, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form.withError("Error", "a.specific.error")))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)
          .withSession(Journey.callCountSessionKey -> "0")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) should include("a.specific.error")
      }

      "Redirect to the maxCallCountRedirectUrl when the max call count is met and the BARS checks indicate an issue" in {
        val barsPersonalAssessResponse =
          BarsPersonalAssessSuccessResponse(Yes, Indeterminate, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form.withError("Error", "a.specific.error")))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)
          .withSession(Journey.callCountSessionKey -> "1")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/too-many-requests/${id.toHexString}")
      }

      "Redirect to the confirm view while the max call count is not met and an indeterminate response is received" in {
        val barsPersonalAssessResponse =
          BarsPersonalAssessSuccessResponse(Yes, Indeterminate, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)
          .withSession(Journey.callCountSessionKey -> "0")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/confirm/personal/${id.toHexString}")
      }

      "Redirect to the maxCallCountRedirectUrl when the max call count is met and an indeterminate response is received" in {
        val barsPersonalAssessResponse =
          BarsPersonalAssessSuccessResponse(Yes, Indeterminate, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)
          .withSession(Journey.callCountSessionKey -> "1")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/too-many-requests/${id.toHexString}")
      }

      "Redirect to the continueUrl when the max call count is met but a yes response is received" in {
        val barsPersonalAssessResponse =
          BarsPersonalAssessSuccessResponse(Yes, Yes, Indeterminate, Indeterminate, No, No, Some(No), None, None, None)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(address),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None))),
            maxCallCount = Some(2), maxCallCountRedirectUrl = Some("/too-many-requests")
          ))))

        reset(mockService)
        when(mockService.assessPersonal(meq(data), meq(Some(address)), meq(serviceIdentifier))(any(), any())).thenReturn(Future.successful(Success(barsPersonalAssessResponse)))
        when(mockService.processPersonalAssessResponse(meq(id), any(), any(), any())(any(), any())).thenReturn(Future.successful(form))

        val fakeRequest = FakeRequest("POST", s"/verify/personal/${id.toHexString}")
          .withFormUrlEncodedBody(PersonalVerificationRequest.form.fill(data).data.toSeq: _*)
          .withSession(Journey.callCountSessionKey -> "1")

        val result = controller.postAccountDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"$continueUrl/${id.toHexString}")
      }
    }
  }

  "GET /confirm/personal" when {
    "the user is not logged in" should {
      val id = ObjectId.get()

      "return 401" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest("GET", s"/confirm/personal/${id.toHexString}")
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

        val fakeRequest = FakeRequest("GET", s"/confirm/personal/${id.toHexString}")
        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey but for a different user" should {
      "return a 404" in {
        val id = ObjectId.get()
        val expiry = LocalDateTime.now.plusMinutes(60)

        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("9876"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(Some(Personal))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/personal/${id.toHexString}")
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

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(Some(Personal))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/personal/${id.toHexString}")

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

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/personal/${id.toHexString}")

        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentAsString(result) should include("some account name")
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
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(PersonalAccountDetails(accountName = Some("some account name"), sortCode = Some("112233"), accountNumber = Some("12345678"), sortCodeBankName = Some("sort-code-bank-name-personal"), iban = Some("some-iban"), matchedAccountName = None)))))))

        val fakeRequest = FakeRequest("GET", s"/confirm/personal/${id.toHexString}")

        val result = controller.getConfirmDetails(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentAsString(result) should include("some account name")
        contentAsString(result) should include("112233")
        contentAsString(result) should include("12345678")
        contentAsString(result) should include("with sort-code-bank-name-personal")
      }
    }
  }
}
