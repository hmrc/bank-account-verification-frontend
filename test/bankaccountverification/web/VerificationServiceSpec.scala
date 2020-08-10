package bankaccountverification.web

import bankaccountverification.{SessionData, SessionDataRepository}
import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BankAccountReputationConnector, BarsValidationRequest, BarsValidationResponse}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}

class VerificationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {
  implicit val timeout = 1 second
  implicit val hc = HeaderCarrier()

  "Verifying bank account details provided by the user" when {
    val journeyId = BSONObjectID.generate()

    "the remote bars check fails" should {
      val mockConnector = mock[BankAccountReputationConnector]
      val mockRepository = mock[SessionDataRepository]
      val service = new VerificationService(mockConnector, mockRepository)

      val userInput = VerificationRequest("Bob", "20-30-40", "12345678")
      val form = VerificationRequest.form.fillAndValidate(userInput)

      val validationResult = Future.successful(Failure(new HttpException("FIRE IN SERVER ROOM", 500)))
      when(mockConnector.validateBankDetails(any())(any(), any())).thenReturn(validationResult)
      when(mockRepository.findAndUpdateById(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.verify(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector).validateBankDetails(meq(BarsValidationRequest("203040", "12345678")))(any(), any())
      }

      "persist the details to mongo" in {
        val expectedSessionData  = SessionData(Some("Bob"), Some("20-30-40"), Some("12345678"), None, None)
        verify(mockRepository).findAndUpdateById(meq(journeyId), meq(expectedSessionData))(any(), any())
      }

      "return a valid form" in {
        await(updatedForm).hasErrors shouldEqual false
      }
    }

    "the details provided pass the remote bars checks" should {
      val mockConnector = mock[BankAccountReputationConnector]
      val mockRepository = mock[SessionDataRepository]
      val service = new VerificationService(mockConnector, mockRepository)

      val userInput = VerificationRequest("Bob", "20-30-40", "12345678")
      val form = VerificationRequest.form.fillAndValidate(userInput)

      val validationResult = Future.successful(Success(BarsValidationResponse(Yes, No, None)))
      when(mockConnector.validateBankDetails(any())(any(), any())).thenReturn(validationResult)
      when(mockRepository.findAndUpdateById(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.verify(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector).validateBankDetails(meq(BarsValidationRequest("203040", "12345678")))(any(), any())
      }

      "persist the details to mongo" in {
        val expectedSessionData  = SessionData(Some("Bob"), Some("20-30-40"), Some("12345678"), None, Some(Yes))
        verify(mockRepository).findAndUpdateById(meq(journeyId), meq(expectedSessionData))(any(), any())
      }

      "return a valid form" in {
        await(updatedForm).hasErrors shouldEqual false
      }
    }

    "the details provided do not pass the remote bars checks" should {
      val mockConnector = mock[BankAccountReputationConnector]
      val mockRepository = mock[SessionDataRepository]
      val service = new VerificationService(mockConnector, mockRepository)

      val userInput = VerificationRequest("Bob", "20-30-40", "00000000")
      val form = VerificationRequest.form.fillAndValidate(userInput)

      val validationResult = Future.successful(Success(BarsValidationResponse(No, No, None)))
      when(mockConnector.validateBankDetails(any())(any(), any())).thenReturn(validationResult)
      when(mockRepository.findAndUpdateById(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.verify(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector).validateBankDetails(meq(BarsValidationRequest("203040", "00000000")))(any(), any())
      }

      "not persist the details to mongo" in {
        verify(mockRepository, never()).findAndUpdateById(meq(journeyId), any())(any(), any())
      }

      "return a form with errors" in {
        await(updatedForm).hasErrors shouldEqual true
      }
    }

  }

}
