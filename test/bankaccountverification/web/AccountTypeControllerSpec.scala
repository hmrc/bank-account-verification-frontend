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

package bankaccountverification.web

import java.time.{ZoneOffset, ZonedDateTime}

import akka.stream.Materializer
import bankaccountverification._
import bankaccountverification.web.AccountTypeRequestEnum.Personal
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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class AccountTypeControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
  implicit val timeout: FiniteDuration = 1 second

  private val mockService = mock[VerificationService]
  private val mockRepository = mock[JourneyRepository]
  private val serviceIdentifier = "example-service"
  private val continueUrl = "https://continue.url"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[JourneyRepository].toInstance(mockRepository))
      .overrides(bind[VerificationService].toInstance(mockService))
      .build()
  }

  private val injector = app.injector
  private val accountTypeController = injector.instanceOf[AccountTypeController]
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
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, Session()))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }

    "account type is pre-populated" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "render account type view with the prepopulated account type" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, Session(Some(Personal))))))

        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result = accountTypeController.getAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsString(result) should include regex """value="personal"\p{Space}+checked"""
      }
    }
  }

  "POST /start" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()

      "return 404" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(Future.successful(None))
        val fakeRequest = FakeRequest("POST", s"/start/${id.stringify}")
          .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Personal.toString)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "return 400" in {
        reset(mockRepository)
        when(mockRepository.findById(id))
          .thenReturn(Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, Session()))))

        val fakeRequest = FakeRequest("POST", s"/start/${id.stringify}")
          .withFormUrlEncodedBody("accountType" -> "")

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey and form are valid, personal account type is selected" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "Redirect to the getAccountDetails endpoint" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, Session()))))

        reset(mockService)
        when(mockService.setAccountType(meq(id), meq(AccountTypeRequestEnum.Personal))(any(), any()))
          .thenReturn(Future.successful(true))

        val fakeRequest =
          FakeRequest("POST", s"/start/${id.stringify}")
            .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Personal.toString)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/verify/personal/${id.stringify}")
      }
    }

    "the journey and form are valid, business account type is selected" should {
      val id = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      "Redirect to the getAccountDetails endpoint" in {
        reset(mockRepository)
        when(mockRepository.findById(id)).thenReturn(
          Future.successful(Some(Journey(id, expiry, serviceIdentifier, continueUrl, Session()))))

        reset(mockService)
        when(mockService.setAccountType(meq(id), meq(AccountTypeRequestEnum.Business))(any(), any()))
          .thenReturn(Future.successful(true))

        val fakeRequest =
          FakeRequest("POST", s"/start/${id.stringify}")
            .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Business.toString)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/verify/business/${id.stringify}")
      }
    }
  }
}
