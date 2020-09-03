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

package bankaccountverification.web

import bankaccountverification.{BusinessAccountDetails, JourneyRepository, PersonalAccountDetails, PersonalSession, Session}
import bankaccountverification.connector.{BankAccountReputationConnector, BarsBusinessAssessResponse, BarsPersonalAssessResponse, BarsValidationRequest, BarsValidationResponse}
import bankaccountverification.connector.ReputationResponseEnum._
import bankaccountverification.web.AccountTypeRequestEnum.Personal
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
  implicit val hc      = HeaderCarrier()

  "Assessing personal bank account details provided by the user" should {
    val mockConnector  = mock[BankAccountReputationConnector]
    val mockRepository = mock[JourneyRepository]
    val service        = new VerificationService(mockConnector, mockRepository)

    val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")

    val assessResult =
      Success(BarsPersonalAssessResponse(Yes, Yes, Yes, Yes, Indeterminate, Indeterminate, Some(No)))

    when(mockConnector.assessPersonal(any(), any(), any())(any(), any())).thenReturn(Future.successful(assessResult))

    val personalVerificationRequest = PersonalVerificationRequest("Bob", "20-30-40", "12345678", None)
    val res                         = await(service.assessPersonal(userInput))

    "strip the dashes from the sort code" in {
      verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"))(any(), any())
    }
  }

  "processing the assess response" when {
    val journeyId = BSONObjectID.generate()

    val mockConnector  = mock[BankAccountReputationConnector]
    val mockRepository = mock[JourneyRepository]
    val service        = new VerificationService(mockConnector, mockRepository)

    val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")
    val form      = PersonalVerificationRequest.form.fillAndValidate(userInput)

    "the details provided pass the remote bars checks" should {
      val assessResult =
        Success(BarsPersonalAssessResponse(Yes, Yes, Yes, Yes, Indeterminate, Indeterminate, Some(No)))

      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val res = await(service.processAssessResponse(journeyId, assessResult, form))

      "persist the details to mongo" in {
        val expectedAccountDetails = PersonalAccountDetails(
          Some("Bob"),
          Some("20-30-40"),
          Some("12345678"),
          None,
          Some(Yes),
          Some(Yes),
          Some(Yes),
          Some(Indeterminate),
          Some(Indeterminate),
          Some(No)
        )

        verify(mockRepository).updatePersonalAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        res.hasErrors shouldEqual false
      }
    }

    "the remote bars check fails with an Internal Server Error" should {
      val mockConnector  = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service        = new VerificationService(mockConnector, mockRepository)

      val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")
      val form      = PersonalVerificationRequest.form.fillAndValidate(userInput)

      val assessResult = Failure(new HttpException("FIRE IN SERVER ROOM", 500))
      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val res = await(service.processAssessResponse(journeyId, assessResult, form))

      "persist the details to mongo" in {
        val expectedAccountDetails = PersonalAccountDetails(
          Some("Bob"),
          Some("20-30-40"),
          Some("12345678"),
          None,
          Some(Error),
          Some(Error),
          Some(Error),
          Some(Error),
          Some(Error),
          Some(Error)
        )
        verify(mockRepository).updatePersonalAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        res.hasErrors shouldEqual false
      }
    }
  }

  "Assessing business bank account details provided by the user" when {
    val journeyId = BSONObjectID.generate()

    "the details provided pass the remote bars checks" should {
      val mockConnector  = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service        = new VerificationService(mockConnector, mockRepository)

      val userInput = BusinessVerificationRequest("Bob", Some("SC1234567"), "20-30-40", "12345678", None)
      val form      = BusinessVerificationRequest.form.fillAndValidate(userInput)

      val assessResult = Future.successful(
        Success(BarsBusinessAssessResponse(Yes, Yes, None, Yes, Yes, Indeterminate, Indeterminate, Some(No)))
      )
      when(mockConnector.assessBusiness(any(), any(), any(), any())(any(), any())).thenReturn(assessResult)
      when(mockRepository.updateBusinessAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.assessBusiness(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector)
          .assessBusiness(meq("Bob"), meq(Some("SC1234567")), meq("203040"), meq("12345678"))(any(), any())
      }

      "persist the details to mongo" in {
        val expectedAccountDetails = BusinessAccountDetails(
          Some("Bob"),
          Some("SC1234567"),
          Some("20-30-40"),
          Some("12345678"),
          None,
          Some(Yes),
          Some(No),
          Some(Yes),
          Some(Yes),
          Some(Indeterminate),
          Some(Indeterminate)
        )
        verify(mockRepository).updateBusinessAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        await(updatedForm).hasErrors shouldEqual false
      }
    }

    "the remote bars check fails with an Internal Server Error" should {
      val mockConnector  = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service        = new VerificationService(mockConnector, mockRepository)

      val userInput = BusinessVerificationRequest("Bob", Some("SC1231234"), "20-30-40", "12345678", None)
      val form      = BusinessVerificationRequest.form.fillAndValidate(userInput)

      val assessResult = Future.successful(Failure(new HttpException("FIRE IN SERVER ROOM", 500)))
      when(mockConnector.assessBusiness(any(), any(), any(), any())(any(), any())).thenReturn(assessResult)
      when(mockRepository.updateBusinessAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.assessBusiness(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector)
          .assessBusiness(meq("Bob"), meq(Some("SC1231234")), meq("203040"), meq("12345678"))(any(), any())
      }

      "persist the details to mongo" in {
        val expectedAccountDetails = BusinessAccountDetails(
          Some("Bob"),
          Some("SC1231234"),
          Some("20-30-40"),
          Some("12345678"),
          None,
          Some(Error),
          None,
          Some(Error),
          Some(Error),
          Some(Error),
          Some(Error)
        )
        verify(mockRepository).updateBusinessAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        await(updatedForm).hasErrors shouldEqual false
      }
    }
  }

  "Verifying personal bank account details provided by the user" when {
    val journeyId = BSONObjectID.generate()

    "the remote bars check fails" should {
      val mockConnector  = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service        = new VerificationService(mockConnector, mockRepository)

      val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")
      val form      = PersonalVerificationRequest.form.fillAndValidate(userInput)

      val validationResult = Future.successful(Failure(new HttpException("FIRE IN SERVER ROOM", 500)))
      when(mockConnector.validateBankDetails(any())(any(), any())).thenReturn(validationResult)
      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.verify(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector).validateBankDetails(meq(BarsValidationRequest("203040", "12345678")))(any(), any())
      }

      "persist the details to mongo" in {
        val expectedAccountDetails =
          PersonalAccountDetails(Some("Bob"), Some("20-30-40"), Some("12345678"), None, Some(Error))
        verify(mockRepository).updatePersonalAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        await(updatedForm).hasErrors shouldEqual false
      }
    }

    "the details provided pass the remote bars checks" should {
      val mockConnector  = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service        = new VerificationService(mockConnector, mockRepository)

      val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")
      val form      = PersonalVerificationRequest.form.fillAndValidate(userInput)

      val validationResult = Future.successful(Success(BarsValidationResponse(Yes, No, None)))
      when(mockConnector.validateBankDetails(any())(any(), any())).thenReturn(validationResult)
      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.verify(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector).validateBankDetails(meq(BarsValidationRequest("203040", "12345678")))(any(), any())
      }

      "persist the details to mongo" in {
        val expectedAccountDetails =
          PersonalAccountDetails(Some("Bob"), Some("20-30-40"), Some("12345678"), None, Some(Yes))
        verify(mockRepository).updatePersonalAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        await(updatedForm).hasErrors shouldEqual false
      }
    }

    "the details provided do not pass the remote bars checks" should {
      val mockConnector  = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service        = new VerificationService(mockConnector, mockRepository)

      val userInput = PersonalVerificationRequest("Bob", "20-30-40", "00000000")
      val form      = PersonalVerificationRequest.form.fillAndValidate(userInput)

      val validationResult = Future.successful(Success(BarsValidationResponse(No, No, None)))
      when(mockConnector.validateBankDetails(any())(any(), any())).thenReturn(validationResult)
      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.verify(journeyId, form)

      "strip the dashes from the sort code" in {
        verify(mockConnector).validateBankDetails(meq(BarsValidationRequest("203040", "00000000")))(any(), any())
      }

      "not persist the details to mongo" in {
        verify(mockRepository, never()).updatePersonalAccountDetails(meq(journeyId), any())(any(), any())
      }

      "return a form with errors" in {
        await(updatedForm).hasErrors shouldEqual true
      }
    }

  }

}
