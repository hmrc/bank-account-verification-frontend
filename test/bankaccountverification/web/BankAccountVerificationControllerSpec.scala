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
import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BankAccountReputationConnector, BankAccountReputationValidationResponse}
import bankaccountverification.{MongoSessionData, SessionData, SessionDataRepository}
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentCaptor
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class BankAccountVerificationControllerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with GuiceOneAppPerSuite {
  implicit val timeout = 1 second

  val mockRepository   = mock[SessionDataRepository]
  val mockBARConnector = mock[BankAccountReputationConnector]
  val continueUrl      = "https://continue.url"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[SessionDataRepository].toInstance(mockRepository))
      .overrides(bind[BankAccountReputationConnector].toInstance(mockBARConnector))
      .configure("consumers.mtd.continueUrl" -> continueUrl)
      .build()
  }

  private val injector           = app.injector
  private val controller         = injector.instanceOf[BankAccountVerificationController]
  implicit val mat: Materializer = injector.instanceOf[Materializer]

  "GET /start" when {
    "There is a valid journey" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      when(mockRepository.findById(id)).thenReturn(Future.successful(Some(MongoSessionData(id, expiry))))

      "return 200" in {
        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result      = controller.start(id.stringify).apply(fakeRequest)
        status(result)           shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }

    "There is no valid journey" should {
      val id = BSONObjectID.generate()
      when(mockRepository.findById(id)).thenReturn(Future.successful(None))

      "return 404" in {
        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result      = controller.start(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }
  }

  "POST /verify" when {
    "There is no valid journey" should {
      val id = BSONObjectID.generate()
      when(mockRepository.findById(id)).thenReturn(Future.successful(None))

      "return 404" in {
        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result      = controller.start(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "There are form errors" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      when(mockRepository.findById(id)).thenReturn(Future.successful(Some(MongoSessionData(id, expiry))))
      val data = VerificationRequest("", "", "")

      "return 400" in {
        val fakeRequest = FakeRequest("POST", s"/verify/${id.stringify}")
          .withBody(data)

        val result = controller.verifyDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "A valid form is posted" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data   = VerificationRequest("Bob", "123456", "12345678")

      when(mockRepository.findById(id)).thenReturn(Future.successful(Some(MongoSessionData(id, expiry))))
      when(mockRepository.findAndUpdateById(meq(id), any())(any(), any())).thenReturn(Future.successful(true))
      when(mockBARConnector.validateBankDetails(any())(any(), any(), any())).thenReturn(
        Future.successful(
          Success(
            BankAccountReputationValidationResponse(Yes, No, None)
          )
        )
      )

      "Persist the data to mongo and redirect to the continueUrl" in {
        import VerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/${id.stringify}")
          .withJsonBody(Json.toJson(data))

        val result = controller.verifyDetails(id.stringify).apply(fakeRequest)

        val expectedSessionData =
          SessionData(Some("Bob"), Some("123456"), Some("12345678"), accountNumberWithSortCodeIsValid = Some(Yes))
        verify(mockRepository).findAndUpdateById(meq(id), meq(expectedSessionData))(any(), any())

        status(result)           shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(continueUrl)
      }
    }
  }
}
