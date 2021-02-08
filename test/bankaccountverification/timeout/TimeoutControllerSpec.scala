/*
 * Copyright 2021 HM Revenue & Customs
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

package bankaccountverification.timeout

import akka.stream.Materializer
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.web.{ActionWithCustomisationsProvider, TimeoutController, VerificationService}
import bankaccountverification.{TimeoutConfig, _}
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import java.time.ZonedDateTime
import scala.concurrent.Future
import scala.concurrent.duration._

class TimeoutControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with
  OptionValues {
  implicit private val timeout: FiniteDuration = 1 second

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration)
  private val appConfig = new AppConfig(configuration, serviceConfig)

  private val mockJourneyRepository = mock[JourneyRepository]
  private val mockAuthConnector = mock[AuthConnector]

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .overrides(bind[JourneyRepository].toInstance(mockJourneyRepository))
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .build()
  }

  val actionWithCustomisationsProvider = app.injector.instanceOf[ActionWithCustomisationsProvider]

  private val controller =
    new TimeoutController(appConfig, stubMessagesControllerComponents(), mockJourneyRepository, mockAuthConnector,
      actionWithCustomisationsProvider)

  implicit val mat = app.injector.instanceOf[Materializer]

  "GET /renewSession" should {
    val newJourneyId = BSONObjectID.generate()

    "return 200" when {
      "when authorised and a valid journey id is provided" in {
        val journeyId = BSONObjectID.generate()
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val returnData = Journey(
          journeyId,
          Some("1234"),
          ZonedDateTime.now(),
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(
              PersonalSession(Some("Bob"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), Some(Yes),
                Some(Indeterminate), Some(No), Some(Indeterminate), Some(Indeterminate), Some(No), Some
                ("sort-code-bank-name-personal"))),
            None
          ),
          timeoutConfig = None)

        when(mockJourneyRepository.findById(meq(journeyId), any())(any())).thenReturn(Future.successful(Some
        (returnData)))
        when(mockJourneyRepository.renewExpiryDate(meq(journeyId))(any())).thenReturn(Future.successful(true))

        val fakeRequest = FakeRequest("GET", s"/bank-account-verification/renewSession?journeyId=${
          journeyId
            .stringify
        }")

        val result = controller.renewSession(journeyId.stringify).apply(fakeRequest)

        status(result) shouldBe Status.OK
        contentType(result) shouldBe Some("image/jpeg")
      }
    }

    "return 400" when {
      "Authorised and an invalid journey id is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val fakeRequest = FakeRequest("GET", "/bank-account-verification/renewSession?journeyId=23")

        val result = controller.renewSession("23").apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "return 401" when {
      "Unauthorised" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(new Exception("unauthorised")))

        val fakeRequest = FakeRequest("GET", "/bank-account-verification/renewSession?journeyId=23")

        val result = controller.renewSession("23").apply(fakeRequest)

        status(result) shouldBe Status.UNAUTHORIZED
      }
    }
  }

  "GET /destroySession" should {
    "return 200" when {
      "when authorised and a valid journey id is provided" in {
        val journeyId = BSONObjectID.generate()
        val timeoutUrl = "/some-timeout-url"
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val returnData = Journey(
          journeyId,
          Some("1234"),
          ZonedDateTime.now(),
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(
              PersonalSession(Some("Bob"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), Some(Yes),
                Some(Indeterminate), Some(No), Some(Indeterminate), Some(Indeterminate), Some(No), Some
                ("sort-code-bank-name-personal"))),
            None
          ),
          timeoutConfig = None)

        when(mockJourneyRepository.findById(meq(journeyId), any())(any())).thenReturn(Future.successful(Some
        (returnData)))

        when(mockJourneyRepository.renewExpiryDate(meq(journeyId))(any())).thenReturn(Future.successful(true))

        val fakeRequest =
          FakeRequest("GET", s"/bank-account-verification/destroySession?journeyId=&${journeyId.stringify}timeoutUrl=${timeoutUrl}")

        val result = controller.timeoutSession(journeyId.stringify, RedirectUrl(timeoutUrl)).apply(fakeRequest)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/some-timeout-url")
      }
    }

    "return 400" when {
      "Authorised and an invalid journey id is provided" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.successful("1234"))

        val fakeRequest = FakeRequest("GET", s"/bank-account-verification/destroySession?journeyId=23&timeoutUrl=/timeout-url")

        val result = controller.timeoutSession("23", RedirectUrl("/timeout-url")).apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "return 401" when {
      "Unauthorised" in {
        reset(mockAuthConnector)
        when(mockAuthConnector.authorise(meq(EmptyPredicate), meq(AuthProviderId.retrieval))(any(), any()))
          .thenReturn(Future.failed(new Exception("unauthorised")))

        val fakeRequest = FakeRequest("GET", "/bank-account-verification/destroySession?journeyId=23&timeoutUrl=/timeout-url")

        val result = controller.timeoutSession("23", RedirectUrl("/timeout-url")).apply(fakeRequest)

        status(result) shouldBe Status.UNAUTHORIZED
      }
    }
  }
}
