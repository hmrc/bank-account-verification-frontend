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

package bankaccountverification.api

import java.time.ZonedDateTime

import akka.stream.Materializer
import bankaccountverification.connector.ReputationResponseEnum.Yes
import bankaccountverification.web.AccountTypeRequest.personalAccountType
import bankaccountverification.{AppConfig, Journey, JourneyRepository, Session}
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.Future
import scala.concurrent.duration._

class ApiControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with OptionValues {
  implicit private val timeout: FiniteDuration = 1 second

  private val env           = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration)
  private val appConfig     = new AppConfig(configuration, serviceConfig)

  override lazy val app = {
    SharedMetricRegistries.clear()
    fakeApplication()
  }

  private lazy val sessionStore = mock[JourneyRepository]

  private val controller =
    new ApiController(appConfig, stubMessagesControllerComponents(), sessionStore)

  implicit val mat = app.injector.instanceOf[Materializer]

  "POST /init" should {
    import InitRequest._

    val newJourneyId = BSONObjectID.generate()

    "return 200" when {
      "A continueUrl is provided" in {
        when(
          sessionStore.create(
            meq("serviceIdentifier"),
            meq("continueUrl"),
            meq(None),
            meq(None)
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json        = Json.toJson(InitRequest("serviceIdentifier", "continueUrl"))
        val fakeRequest = FakeRequest("POST", "/api/init").withJsonBody(json)

        val result         = controller.init().apply(fakeRequest)
        val journeyIdMaybe = contentAsJson(result).as[String]

        status(result)                              shouldBe Status.OK
        journeyIdMaybe                                should not be ""
        BSONObjectID.parse(journeyIdMaybe).toOption shouldBe Some(newJourneyId)
      }
    }

    "return 400" when {
      "A continueUrl is not provided" in {
        val json        = Json.parse("{}")
        val fakeRequest = FakeRequest("POST", "/api/init").withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }
    }
  }

  "GET /complete" should {
    "return 200" when {
      "a valid journeyId is provided" in {
        val journeyId = BSONObjectID.generate()
        val returnData = Journey(
          journeyId,
          ZonedDateTime.now(),
          "serviceIdentifier",
          "continueUrl",
          None,
          None,
          Some(
            Session(Some("Bob"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), Some(personalAccountType))
          )
        )

        when(sessionStore.findById(meq(journeyId), any())(any())).thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/${journeyId.stringify}")
        val completeResult      = controller.complete(journeyId.stringify).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String]                      shouldBe "personal"
        (json \ "accountName").as[String]                      shouldBe "Bob"
        (json \ "sortCode").as[String]                         shouldBe "203040"
        (json \ "accountNumber").as[String]                    shouldBe "12345678"
        (json \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (json \ "rollNumber").as[String]                       shouldBe "roll1"
      }
    }
  }

  "return NotFound" when {
    "a non-existent journeyId is provided" in {
      val nonExistentJourneyId = BSONObjectID.generate()
      when(sessionStore.findById(meq(nonExistentJourneyId), any())(any())).thenReturn(Future.successful(None))

      val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
      val completeResult      = controller.complete(nonExistentJourneyId.stringify).apply(fakeCompleteRequest)
      status(completeResult) shouldBe Status.NOT_FOUND
    }
  }

  "return BadRequest" when {
    "an invalid journeyId is provided" in {
      val nonExistentJourneyId = "invalid-journey-id"
      val fakeCompleteRequest  = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
      val completeResult       = controller.complete(nonExistentJourneyId).apply(fakeCompleteRequest)
      status(completeResult) shouldBe Status.BAD_REQUEST
    }
  }
}
