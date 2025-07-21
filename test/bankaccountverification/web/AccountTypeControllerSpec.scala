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

package bankaccountverification.web

import org.apache.pekko.stream.Materializer
import bankaccountverification._
import bankaccountverification.web.AccountTypeRequestEnum.Personal
import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
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

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class AccountTypeControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
  implicit val timeout: FiniteDuration = 1 second

  private val mockService = mock[VerificationService]
  private val mockRepository = mock[JourneyRepository]
  private val mockAuthConnector = mock[AuthConnector]
  private val serviceIdentifier = "example-service"

  private val continueUrl = "https://continue.url"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[JourneyRepository].toInstance(mockRepository))
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .overrides(bind[VerificationService].toInstance(mockService))
      .overrides(bind[ExecutionContext].toInstance(implicitly[ExecutionContext]))
      .build()
  }

  private val injector = app.injector
  private val accountTypeController = injector.instanceOf[AccountTypeController]
  implicit val mat: Materializer = injector.instanceOf[Materializer]

  "GET /start" when {

    "the user is not logged in" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "return 401" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(), timeoutConfig = None))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.toHexString}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.toHexString).apply(fakeRequest)
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

        val fakeRequest = FakeRequest("GET", s"/start/${id.toHexString}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey but it does not match this user" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
            .thenReturn(Future.successful("9876"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(), timeoutConfig = None))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.toHexString}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey and the user is logged in" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "return 200" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(), timeoutConfig = None))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.toHexString}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }

    "account type is pre-populated" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "render account type view with the prepopulated account type" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(Some(Personal)), timeoutConfig = None))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.toHexString}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsString(result) should include regex """value="personal"\p{Space}+checked"""
      }
    }
  }

  "POST /start" when {
    "the user is not logged in" should {
      val id = ObjectId.get()

      "return 401" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(AuthorisationException.fromString("MissingBearerToken")))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))
        val fakeRequest = FakeRequest("POST", s"/start/${id.toHexString}")
          .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Personal.toString)

        val result = accountTypeController.postAccountType(id.toHexString).apply(fakeRequest)
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
        val fakeRequest = FakeRequest("POST", s"/start/${id.toHexString}")
          .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Personal.toString)

        val result = accountTypeController.postAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but does not match the current user" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "return 404" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("9876"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(), timeoutConfig = None))))

        val fakeRequest = FakeRequest("POST", s"/start/${id.toHexString}")
          .withFormUrlEncodedBody("accountType" -> "")

        val result = accountTypeController.postAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "return 400" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(), timeoutConfig = None))))

        val fakeRequest = FakeRequest("POST", s"/start/${id.toHexString}")
          .withFormUrlEncodedBody("accountType" -> "")

        val result = accountTypeController.postAccountType(id.toHexString).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey and form are valid, personal account type is selected" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "Redirect to the getAccountDetails endpoint" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl, Session(),
            timeoutConfig = None))))

        reset(mockService)
        when(mockService.setAccountType(meq(id), meq(AccountTypeRequestEnum.Personal))(any(), any()))
          .thenReturn(Future.successful(true))

        val fakeRequest =
          FakeRequest("POST", s"/start/${id.toHexString}")
            .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Personal.toString)

        val result = accountTypeController.postAccountType(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe
          Some(bankaccountverification.web.personal.routes.PersonalVerificationController.getAccountDetails(id.toHexString).url)
      }
    }

    "the journey and form are valid, business account type is selected" should {
      val id = ObjectId.get()
      val expiry = Instant.now.plus(60, ChronoUnit.MINUTES)

      "Redirect to the getAccountDetails endpoint" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, Some("1234"), expiry, serviceIdentifier, continueUrl,
            Session(), timeoutConfig = None))))

        reset(mockService)
        when(mockService.setAccountType(meq(id), meq(AccountTypeRequestEnum.Business))(any(), any()))
          .thenReturn(Future.successful(true))

        val fakeRequest =
          FakeRequest("POST", s"/start/${id.toHexString}")
            .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Business.toString)

        val result = accountTypeController.postAccountType(id.toHexString).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe
          Some(bankaccountverification.web.business.routes.BusinessVerificationController.getAccountDetails(id.toHexString).url)
      }
    }
  }
}
