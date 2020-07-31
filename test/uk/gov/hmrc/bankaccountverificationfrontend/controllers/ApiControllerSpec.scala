package uk.gov.hmrc.bankaccountverificationfrontend.controllers

import java.util.UUID

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bankaccountverificationfrontend.config.AppConfig
import uk.gov.hmrc.bankaccountverificationfrontend.store.MongoSessionRepo
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.duration._

class ApiControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with OptionValues {
  implicit val timeout = 1 second

  private val env           = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration)
  private val appConfig     = new AppConfig(configuration, serviceConfig)
  private val sessionStore  = fakeApplication().injector.instanceOf[MongoSessionRepo]

  private val controller =
    new ApiController(appConfig, stubMessagesControllerComponents(), sessionStore)

  "GET /start" should {
    "return 200" in {
      val fakeRequest = FakeRequest("GET", "/api/init").withMethod("POST")
      val result      = controller.init().apply(fakeRequest)
      status(result) shouldBe Status.OK
      val journeyIdMaybe = contentAsString(result)
      journeyIdMaybe                          should not be ""
      Option(UUID.fromString(journeyIdMaybe)) should not be None
    }
  }
}
