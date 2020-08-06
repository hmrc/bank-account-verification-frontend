package bankaccountverification

import bankaccountverification.api.ApiController
import bankaccountverification.web.{BankAccountVerificationController, VerificationRequest}
import com.codahale.metrics.SharedMetricRegistries
import org.scalatest._
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play._
import org.scalatestplus.play.guice.{GuiceOneAppPerSuite, GuiceOneServerPerSuite}
import play.api.Application
import play.api.http.MimeTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsSuccess, Json}
import play.api.libs.ws.WSClient
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test._

class BankAccountVerificationITSpec() extends AnyWordSpec with GuiceOneServerPerSuite {
  override lazy val app = {
    SharedMetricRegistries.clear()
    fakeApplication()
  }

  private def getCCParams(cc: AnyRef) =
    cc.getClass.getDeclaredFields.foldLeft(Map.empty[String, Seq[String]]) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> Seq(f.get(cc).toString))
    }

  "BankAccountVerification" in {

    val wsClient = app.injector.instanceOf[WSClient]
    val baseUrl  = s"http://localhost:$port"

    val initUrl = s"$baseUrl/api/init"

    val initResponse =
      await(wsClient.url(initUrl).post(""))

    initResponse.status shouldBe 200
    val journeyId = initResponse.body

    val bankAccountDetails = VerificationRequest("some-account-name", "12-12-12", "12349876")
    val formData           = getCCParams(bankAccountDetails) ++ Map("rollNumber" -> Seq())
    val verifyUrl          = s"$baseUrl/bank-account-verification/verify/$journeyId"

    val request = FakeRequest().withCSRFToken
    val verifyResponse =
      await(
        wsClient
          .url(verifyUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(request.headers.toSimpleMap.toSeq: _*)
          .post(formData)
      )

    import MongoSessionData._
    val completeUrl = s"$baseUrl/api/complete/$journeyId"
    val completeResponse =
      await(wsClient.url(completeUrl).get())
    completeResponse.status shouldBe 200
    val sessionDataMaybe = Json.fromJson[SessionData](completeResponse.json)

    sessionDataMaybe shouldBe JsSuccess[SessionData](
      SessionData(Some("some-account-name"), Some("12-12-12"), Some("12349876"))
    )
  }
}
