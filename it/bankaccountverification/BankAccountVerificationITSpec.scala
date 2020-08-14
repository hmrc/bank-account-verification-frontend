package bankaccountverification

import bankaccountverification.api.InitRequest
import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BankAccountReputationConnector, BarsValidationResponse}
import bankaccountverification.web.VerificationRequest
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.any
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito._
import org.scalatestplus.mockito._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test._

import scala.concurrent.Future
import scala.util.Success

class BankAccountVerificationITSpec() extends AnyWordSpec with GuiceOneServerPerSuite with MockitoSugar {
  private val mockBankAccountReputationConnector = mock[BankAccountReputationConnector]
  override lazy val app = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder()
      .overrides(bind[BankAccountReputationConnector].toInstance(mockBankAccountReputationConnector))
      .build()
  }

  private def getCCParams(cc: AnyRef) =
    cc.getClass.getDeclaredFields.foldLeft(Map.empty[String, Seq[String]]) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> Seq(f.get(cc).toString))
    }

  "BankAccountVerification" in {
    when(mockBankAccountReputationConnector.validateBankDetails(any())(any(), any())).thenReturn(
      Future.successful(Success(BarsValidationResponse(Yes, No, None)))
    )

    val wsClient = app.injector.instanceOf[WSClient]
    val baseUrl  = s"http://localhost:$port"

    val initUrl = s"$baseUrl/api/init"

    val initRequest = InitRequest("continueUrl")
    val initResponse =
      await(wsClient.url(initUrl).post[JsValue](Json.toJson(initRequest)))

    initResponse.status shouldBe 200
    val journeyId = initResponse.json.as[String]

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

    import Journey._
    val completeUrl = s"$baseUrl/api/complete/$journeyId"
    val completeResponse =
      await(wsClient.url(completeUrl).get())
    completeResponse.status shouldBe 200
    val sessionDataMaybe = Json.fromJson[Session](completeResponse.json)

    sessionDataMaybe shouldBe JsSuccess[Session](
      Session(
        Some("some-account-name"),
        Some("12-12-12"),
        Some("12349876"),
        accountNumberWithSortCodeIsValid = Some(Yes)
      )
    )
  }
}
