package bankaccountverification

import bankaccountverification.DirectDebitRequirements
import bankaccountverification.api.InitDirectDebitRequirements

import java.time.ZonedDateTime
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.bson.BSONObjectID
import play.api.test.Helpers.{await, defaultAwaitTimeout}

import scala.concurrent.ExecutionContext.Implicits.global

class JourneyRepositoryITSpec extends AnyWordSpec with Matchers with GuiceOneServerPerSuite with MockitoSugar {
  override lazy val app = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder()
      .build()
  }

  "Creating personal account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "Should create the journey and session including the prepopulated data" in {
      val journeyId = await(repository.create(Some("1234"), "serviceIndentifier", "continueUrl", None, None,
        Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("HP1 1HP"))),
        Some(PrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))),
        Some(DirectDebitRequirements(true, true)), Some(TimeoutConfig("url", 100, None))))

      val journey = await(repository.findById(journeyId))
      val timeoutConfig = journey.flatMap(j => j.timeoutConfig)
      timeoutConfig shouldBe Some(TimeoutConfig("url", 100, None))

      val personalSession = journey.flatMap(j => j.data.personal)
      personalSession shouldBe Some(PersonalSession(Some("Bob"), Some("123456"), Some("12345678"), Some("A123")))
    }
  }

  "Creating business account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "Should create the session including the prepopulated data" in {
      val journeyId = await(repository.create(Some("1234"), "serviceIndentifier", "continueUrl", None, None,
        Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("HP1 1HP"))),
        Some(PrepopulatedData(Business, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))),
        Some(DirectDebitRequirements(true, true)), Some(TimeoutConfig("url", 100, None))))

      val journey = await(repository.findById(journeyId))
      val timeoutConfig = journey.flatMap(j => j.timeoutConfig)
      timeoutConfig shouldBe Some(TimeoutConfig("url", 100, None))

      val businessSession = journey.flatMap(j => j.data.business)
      businessSession shouldBe Some(BusinessSession(Some("Bob"), Some("123456"), Some("12345678"), Some("A123")))
    }
  }

  "Updating personal account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle unsetting the roll number" in {
      val journeyId = BSONObjectID.generate()

      val personalSession = PersonalSession(Some("accountName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Personal), address = None, personal = Some(personalSession))
      val journey = Journey(journeyId, Some("1234"), ZonedDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", session, None, None, None)
      await(repository.insert(journey))

      val accountDetails = PersonalAccountDetails(Some("updated accountName"), Some("updated sortCode"), Some("updated accountNumber"), None)
      await(repository.updatePersonalAccountDetails(journeyId, accountDetails))

      val updatedPersonalData = await(repository.findById(journeyId)).flatMap(j => j.data.personal)
      updatedPersonalData.get.rollNumber shouldBe None
    }
  }

  "Updating business account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle unsetting the roll number" in {
      val journeyId = BSONObjectID.generate()

      val businessSession = BusinessSession(Some("companyName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Business), address = None, personal = None, business = Some(businessSession))
      val journey = Journey(journeyId, Some("1234"), ZonedDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", session, None, None, None)
      await(repository.insert(journey))

      val accountDetails = BusinessAccountDetails(Some("updated companyName"), Some("updated sortCode"), Some("updated accountNumber"), None)
      await(repository.updateBusinessAccountDetails(journeyId, accountDetails))

      val updatedBusinessData = await(repository.findById(journeyId)).flatMap(j => j.data.business)
      updatedBusinessData.get.rollNumber shouldBe None
    }

  }
}