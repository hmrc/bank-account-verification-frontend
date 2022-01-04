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

package bankaccountverification

import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class JourneyRepositoryITSpec extends AnyWordSpec with Matchers with GuiceOneServerPerSuite with MockitoSugar {
  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder()
      .build()
  }

  "Creating personal account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "Should create the journey and session including the prepopulated data" in {
      val journeyId = await(repository.create(Some("1234"), "serviceIdentifier", "continueUrl", None, None,
        Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("HP1 1HP"))),
        Some(PrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))),
        Some(BACSRequirements(true, true)), Some(TimeoutConfig("url", 100, None)), Some("/signout")))

      val journey = await(repository.findById(journeyId))
      val timeoutConfig = journey.flatMap(j => j.timeoutConfig)
      timeoutConfig shouldBe Some(TimeoutConfig("url", 100, None))

      val personalSession = journey.flatMap(j => j.data.personal)
      personalSession shouldBe Some(PersonalAccountDetails(Some("Bob"), Some("123456"), Some("12345678"), Some("A123")))
    }
  }

  "Creating business account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "Should create the session including the prepopulated data" in {
      val journeyId = await(repository.create(Some("1234"), "serviceIdentifier", "continueUrl", None, None,
        Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("HP1 1HP"))),
        Some(PrepopulatedData(Business, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))),
        Some(BACSRequirements(true, true)), Some(TimeoutConfig("url", 100, None)), Some("/signout")))

      val journey = await(repository.findById(journeyId))
      val timeoutConfig = journey.flatMap(j => j.timeoutConfig)
      timeoutConfig shouldBe Some(TimeoutConfig("url", 100, None))

      val businessSession = journey.flatMap(j => j.data.business)
      businessSession shouldBe Some(BusinessAccountDetails(Some("Bob"), Some("123456"), Some("12345678"), Some("A123")))
    }
  }

  "Updating personal account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle unsetting the roll number" in {
      val journeyId = ObjectId.get()

      val personalSession = PersonalAccountDetails(Some("accountName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Personal), address = None, personal = Some(personalSession))
      val journey = Journey(journeyId, Some("1234"), LocalDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", session, None, None, None)
      await(repository.insertRaw(journey))

      val accountDetails = PersonalAccountDetails(Some("updated accountName"), Some("updated sortCode"), Some("updated accountNumber"), None)
      await(repository.updatePersonalAccountDetails(journeyId, accountDetails))

      val updatedPersonalData = await(repository.findById(journeyId)).flatMap(j => j.data.personal)
      updatedPersonalData.get.rollNumber shouldBe None
    }
  }

  "Updating business account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle unsetting the roll number" in {
      val journeyId = ObjectId.get()

      val businessSession = BusinessAccountDetails(Some("companyName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Business), address = None, personal = None, business = Some(businessSession))
      val journey = Journey(journeyId, Some("1234"), LocalDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", session, None, None, None)
      await(repository.insertRaw(journey))

      val accountDetails = BusinessAccountDetails(Some("updated companyName"), Some("updated sortCode"), Some("updated accountNumber"), None)
      await(repository.updateBusinessAccountDetails(journeyId, accountDetails))

      val updatedBusinessData = await(repository.findById(journeyId)).flatMap(j => j.data.business)
      updatedBusinessData.get.rollNumber shouldBe None
    }

  }

  "Updating account type" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle changing the account type" in {
      val journeyId = ObjectId.get()

      val businessSession = BusinessAccountDetails(Some("companyName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Business), address = None, personal = None, business = Some(businessSession))
      val journey = Journey(journeyId, Some("1234"), LocalDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", session, None, None, None)
      await(repository.insertRaw(journey))

      await(repository.updateAccountType(journeyId, Personal))

      val updatedSession = await(repository.findById(journeyId)).map(j => j.data).get
      updatedSession.accountType shouldBe Some(Personal)
      updatedSession.business shouldBe Some(businessSession)
      updatedSession.personal shouldBe None
    }
  }

  "Can store a typical journey" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle changing the account type" in {
      val journeyId = ObjectId.get()

      val messages = Json.parse(
        """{
          | "en":{
          |   "service.name":"Your service name here",
          |   "footer.accessibility.url":"http://localhost:9929/bank-account-verification-example-frontend/accessibility",
          |   "phaseBanner.tag":"BETA"
          | },
          | "cy":{
          |   "service.name":"W Your service name here",
          |   "footer.accessibility.url":"http://localhost:9929/bank-account-verification-example-frontend/accessibility",
          |   "phaseBanner.tag":"BETA"
          | }
          |}""".stripMargin).as[JsObject]

      val businessSession = BusinessAccountDetails(Some("companyName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Business), address = None, personal = None, business = Some(businessSession))
      val journey = Journey(journeyId, Some("1234"), LocalDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", session, Some(messages), None, None)
      await(repository.insertRaw(journey))

      await(repository.updateAccountType(journeyId, Personal))

      val updatedSession = await(repository.findById(journeyId)).get
      updatedSession.id shouldBe journeyId
      (updatedSession.messages.get \ "en" \ "service.name").as[String] shouldBe  "Your service name here"
    }
  }
}