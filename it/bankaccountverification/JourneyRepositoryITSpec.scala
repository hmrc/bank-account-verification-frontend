package bankaccountverification

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

  "Updating personal account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle unsetting the roll number" in {
      val journeyId = BSONObjectID.generate()

      val personalSession = PersonalSession(Some("accountName"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Personal), address = None, personal = Some(personalSession))
      val journey = Journey(journeyId, ZonedDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", None, None, data = Some(session))
      await(repository.insert(journey))

      val accountDetails = PersonalAccountDetails(Some("updated accountName"), Some("updated sortCode"), Some("updated accountNumber"), None)
      await(repository.updatePersonalAccountDetails(journeyId, accountDetails))

      val updatedPersonalData = await(repository.findById(journeyId)).flatMap(j => j.data).flatMap(d => d.personal)
      updatedPersonalData.get.rollNumber shouldBe None
    }
  }

  "Updating business account details" should {
    val repository = app.injector.instanceOf[JourneyRepository]

    "handle unsetting the roll number and company registration number" in {
      val journeyId = BSONObjectID.generate()

      val businessSession = BusinessSession(Some("companyName"), Some("companyRegistrationNumber"), Some("sortCode"), Some("accountNumber"), Some("rollNumber"))
      val session = Session(accountType = Some(Business), address = None, personal = None, business = Some(businessSession))
      val journey = Journey(journeyId, ZonedDateTime.now.plusHours(1), "serviceIdentifier", "continueUrl", None, None, data = Some(session))
      await(repository.insert(journey))

      val accountDetails = BusinessAccountDetails(Some("updated companyName"), None, Some("updated sortCode"), Some("updated accountNumber"), None)
      await(repository.updateBusinessAccountDetails(journeyId, accountDetails))

      val updatedBusinessData = await(repository.findById(journeyId)).flatMap(j => j.data).flatMap(d => d.business)
      updatedBusinessData.get.companyRegistrationNumber shouldBe None
      updatedBusinessData.get.rollNumber shouldBe None
    }

  }
}