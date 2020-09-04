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
import bankaccountverification.web.AccountTypeRequestEnum.Business
import bankaccountverification.web.{AccountTypeController, AccountTypeRequest, AccountTypeRequestEnum, VerificationService}
import bankaccountverification.{BusinessSession, Journey, JourneyRepository, Session}
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BusinessVerificationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
  implicit val timeout = 1 second

  val mockRepository    = mock[JourneyRepository]
  val mockService       = mock[VerificationService]
  val serviceIdentifier = "example-service"
  val continueUrl       = "https://continue.url"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[JourneyRepository].toInstance(mockRepository))
      .overrides(bind[VerificationService].toInstance(mockService))
      .build()
  }

  private val injector              = app.injector
  private val accountTypeController = injector.instanceOf[AccountTypeController]
  private val controller            = injector.instanceOf[BusinessVerificationController]
  implicit val mat: Materializer    = injector.instanceOf[Materializer]

  "GET /start" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()
      when(mockRepository.findById(id)).thenReturn(Future.successful(None))

      "return 404" in {
        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result      = controller.getAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "there is a valid journey" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      when(mockRepository.findById(id))
        .thenReturn(
          Future.successful(
            Some(
              Journey(
                id,
                expiry,
                serviceIdentifier,
                continueUrl,
                None,
                None,
                Some(
                  Session(
                    accountType = Some(Business),
                    business = Some(
                      BusinessSession(
                        companyName = Some("company_name"),
                        companyRegistrationNumber = Some("SC1231234"),
                        sortCode = Some("11-22-33"),
                        accountNumber = Some("12092398")
                      )
                    )
                  )
                )
              )
            )
          )
        )

      "return 200" in {
        val fakeRequest = FakeRequest("GET", s"/start/${id.stringify}").withMethod("GET")
        val result      = controller.getAccountDetails(id.stringify).apply(fakeRequest)
        status(result)           shouldBe Status.OK
        redirectLocation(result) shouldBe None
      }
    }
  }

  "POST /start" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()
      when(mockRepository.findById(id)).thenReturn(Future.successful(None))

      "return 404" in {
        val fakeRequest = FakeRequest("POST", s"/start/${id.stringify}")
        val result      = controller.getAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      when(mockRepository.findById(id))
        .thenReturn(
          Future.successful(
            Some(
              Journey(
                id,
                expiry,
                serviceIdentifier,
                continueUrl,
                None,
                None,
                Some(
                  Session(
                    accountType = Some(Business),
                    business = Some(
                      bankaccountverification.BusinessSession(
                        companyName = Some("some company name"),
                        companyRegistrationNumber = Some("SC1231234"),
                        sortCode = Some("112233"),
                        accountNumber = Some("12345678")
                      )
                    )
                  )
                )
              )
            )
          )
        )
      val data = AccountTypeRequest(AccountTypeRequestEnum.Business)

      "return 400" in {
        val fakeRequest = FakeRequest("POST", s"/start/${id.stringify}")
          .withBody(data)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey and form are valid" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

      when(mockRepository.findById(id))
        .thenReturn(
          Future.successful(
            Some(Journey(id, expiry, serviceIdentifier, continueUrl, None, None))
          )
        )
      when(mockService.setAccountType(meq(id), meq(AccountTypeRequestEnum.Business))(any(), any()))
        .thenReturn(Future.successful(true))

      "Redirect to the getAccountDetails endpoint" in {
        val fakeRequest =
          FakeRequest("POST", s"/verify/business/${id.stringify}")
            .withFormUrlEncodedBody("accountType" -> AccountTypeRequestEnum.Business.toString)

        val result = accountTypeController.postAccountType(id.stringify).apply(fakeRequest)

        status(result)           shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bank-account-verification/verify/business/${id.stringify}")
      }
    }
  }

  "POST /verify" when {
    "there is no valid journey" should {
      val id = BSONObjectID.generate()
      when(mockRepository.findById(id)).thenReturn(Future.successful(None))

      "return 404" in {
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}")
        val result      = controller.postAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "the journey is valid but there are form errors" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      when(mockRepository.findById(id))
        .thenReturn(
          Future.successful(
            Some(
              Journey(
                id,
                expiry,
                serviceIdentifier,
                continueUrl,
                None,
                None,
                Some(
                  Session(
                    accountType = Some(Business),
                    business = Some(
                      bankaccountverification.BusinessSession(
                        companyName = Some("some company name"),
                        companyRegistrationNumber = Some("SC1231234"),
                        sortCode = Some("112233"),
                        accountNumber = Some("12345678")
                      )
                    )
                  )
                )
              )
            )
          )
        )
      val data = BusinessVerificationRequest("", None, "", "", None)

      "return 400" in {
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}")
          .withBody(data)

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "the journey is valid, a valid form is posted and the bars checks indicate an issue" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data   = BusinessVerificationRequest("Bob", Some("SC1231234"), "123456", "12345678", None)

      val formWithErrors = BusinessVerificationRequest.form
        .fillAndValidate(data)
        .withError("Error", "a.specific.error")

      when(mockRepository.findById(id))
        .thenReturn(
          Future.successful(
            Some(
              Journey(
                id,
                expiry,
                serviceIdentifier,
                continueUrl,
                None,
                None,
                Some(
                  Session(
                    accountType = Some(Business),
                    business = Some(
                      bankaccountverification.BusinessSession(
                        companyName = Some("some company name"),
                        None,
                        sortCode = Some("112233"),
                        accountNumber = Some("12345678")
                      )
                    )
                  )
                )
              )
            )
          )
        )

      when(mockService.assessBusiness(meq(id), any())(any(), any()))
        .thenReturn(Future.successful(formWithErrors))

      "Render the view and display the errors" in {
        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)

        status(result)        shouldBe Status.BAD_REQUEST
        contentAsString(result) should include("a.specific.error")
      }
    }

    "the journey is valid, a valid form is posted but the bars checks pass" should {
      val id     = BSONObjectID.generate()
      val expiry = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)
      val data   = BusinessVerificationRequest("Bob", Some("SC1231234"), "123456", "12345678", None)

      val form = BusinessVerificationRequest.form.fillAndValidate(data)

      when(mockRepository.findById(id))
        .thenReturn(
          Future.successful(
            Some(
              Journey(
                id,
                expiry,
                serviceIdentifier,
                continueUrl,
                None,
                None,
                Some(
                  Session(
                    accountType = Some(Business),
                    business = Some(
                      bankaccountverification.BusinessSession(
                        companyName = Some("some company name"),
                        companyRegistrationNumber = Some("SC1231234"),
                        sortCode = Some("112233"),
                        accountNumber = Some("12345678")
                      )
                    )
                  )
                )
              )
            )
          )
        )
      when(mockService.assessBusiness(meq(id), any())(any(), any())).thenReturn(Future.successful(form))

      "Redirect to the continueUrl" in {
        import BusinessVerificationRequest.formats.bankAccountDetailsWrites
        val fakeRequest = FakeRequest("POST", s"/verify/business/${id.stringify}").withJsonBody(Json.toJson(data))

        val result = controller.postAccountDetails(id.stringify).apply(fakeRequest)

        status(result)           shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"$continueUrl/${id.stringify}")
      }
    }
  }
}
