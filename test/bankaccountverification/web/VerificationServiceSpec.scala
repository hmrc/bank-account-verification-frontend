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

import bankaccountverification.{Address, BusinessAccountDetails, JourneyRepository, PersonalAccountDetails,
  PersonalSession, Session}
import bankaccountverification.connector.{BankAccountReputationConnector, BarsAddress, BarsBusinessAssessResponse,
  BarsPersonalAssessResponse, BarsValidationRequest, BarsValidationResponse}
import bankaccountverification.connector.ReputationResponseEnum._
import bankaccountverification.web.business.BusinessVerificationRequest
import bankaccountverification.web.personal.PersonalVerificationRequest
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

  "Assessing personal bank account details provided by the user" when {
    val mockConnector = mock[BankAccountReputationConnector]
    val mockRepository = mock[JourneyRepository]
    val service = new VerificationService(mockConnector, mockRepository)

    val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")

    val assessResult =
      Success(BarsPersonalAssessResponse(Yes, Yes, Yes, Yes, Indeterminate, Indeterminate, Some(No), None))

    when(mockConnector.assessPersonal(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful
    (assessResult))

    "a valid address is provided" should {
      val inputAddress = Address(List("line1", "line2"), Some("town"), Some("postcode"))
      val expectedBarsAddress = BarsAddress(List("line1", "line2"), Some("town"), Some("postcode"))

      "strip the dashes from the sort code and pass address as is" in {
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address where the address lines are all empty is provided" should {
      val inputAddress = Address(List("", ""), Some("town"), Some("postcode-b"))
      val expectedBarsAddress = BarsAddress(List(" "), Some("town"), Some("postcode-b"))

      "modify the address to one with a single non-empty line" in {
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with no lines is provided" should {
      val inputAddress = Address(List.empty, Some("town"), Some("postcode-c"))
      val expectedBarsAddress = BarsAddress(List(" "), Some("town"), Some("postcode-c"))

      "modify the address to one with a single non-empty line" in {
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with too many lines (> 4) is provided" should {
      val inputAddress = Address(List("line1", "line2", "line3", "line4", "line5"), Some("town"), Some("postcode-d"))
      val expectedBarsAddress = BarsAddress(List("line1", "line2", "line3", "line4"), Some("town"), Some("postcode-d"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with mixture of blank and non-blank lines is provided" should {
      val inputAddress = Address(List("line1", "", "line3", ""), Some("town"), Some("postcode-e"))
      val expectedBarsAddress = BarsAddress(List("line1", "line3"), Some("town"), Some("postcode-e"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with too many and mixture of blank and non-blank lines is provided" should {
      val inputAddress = Address(List("", "line2", "", "line4", "", "line6"), Some("town"), Some("postcode-f"))
      val expectedBarsAddress = BarsAddress(List("line2", "line4", "line6"), Some("town"), Some("postcode-f"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address having lines with a total length > 140 characters is provided" should {
      val inputAddress = Address(List(
        "11111222223333344444555556666677777888889999900000",
        "11111222223333344444555556666677777888889999900000",
        "11111222223333344444555556666677777888889999900000"), Some("town"), Some("postcode-h"))
      val expectedBarsAddress = new BarsAddress(List(
        "11111222223333344444555556666677777888889999900000",
        "11111222223333344444555556666677777888889999900000",
        "1111122222333334444455555666667777788888"), Some("town"), Some("postcode-h"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"),
          meq("203040"),
          meq("12345678"),
          meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address having lines with a total length > 140 chars and having empty line is provided" should {
      val inputAddress = Address(List(
        "1111122222333334444455555666667777788888999990000011111222223333344444555556666677777888889999900000", "",
        "11111222223333344444555556666677777888889999900000", "",
        "11111222223333344444555556666677777888889999900000"), Some("town"), Some("postcode-h"))
      val expectedBarsAddress = new BarsAddress(List(
        "1111122222333334444455555666667777788888999990000011111222223333344444555556666677777888889999900000",
        "1111122222333334444455555666667777788888"), Some("town"), Some("postcode-h"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"),
          meq("203040"),
          meq("12345678"),
          meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with a town that is too short is provided" should {
      val inputAddress = Address(List("line1", "line2"), Some(""), Some("postcode-g"))
      val expectedBarsAddress = BarsAddress(List("line1", "line2"), None, Some("postcode-g"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with a town that is too long is provided" should {
      val inputAddress = Address(List("line1", "line2"), Some("111112222233333444445555566666777778"), Some
      ("postcode-i"))
      val expectedBarsAddress = BarsAddress(List("line1", "line2"), Some("11111222223333344444555556666677777"), Some
      ("postcode-i"))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessPersonal(userInput, Some(inputAddress)))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "no address is provided" should {
      val inputAddress = None
      val expectedBarsAddress = BarsAddress.emptyAddress

      "modify the address to one with a single non-empty line" in {
        await(service.assessPersonal(userInput, None))

        verify(mockConnector).assessPersonal(meq("Bob"), meq("203040"), meq("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }
  }

  "processing the personal assess response" when {
    val journeyId = BSONObjectID.generate()

    val mockConnector = mock[BankAccountReputationConnector]
    val mockRepository = mock[JourneyRepository]
    val service = new VerificationService(mockConnector, mockRepository)

    val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")
    val form = PersonalVerificationRequest.form.fillAndValidate(userInput)

    "the details provided pass the remote bars checks" should {
      val assessResult =
        Success(BarsPersonalAssessResponse(Yes, Yes, Yes, No, Indeterminate, Indeterminate, Some(No), Some
        ("sort-code-bank-name-personal")))

      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val res = await(service.processPersonalAssessResponse(journeyId, assessResult, form))

      "persist the details to mongo" in {
        val expectedAccountDetails = PersonalAccountDetails(
          Some("Bob"),
          Some("20-30-40"),
          Some("12345678"),
          None,
          Some(Yes),
          Some(Yes),
          Some(Yes),
          Some(No),
          Some(Indeterminate),
          Some(Indeterminate),
          Some(No),
          Some("sort-code-bank-name-personal")
        )

        verify(mockRepository).updatePersonalAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        res.hasErrors shouldEqual false
      }
    }

    "the remote bars check fails with an Internal Server Error" should {
      val mockConnector = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service = new VerificationService(mockConnector, mockRepository)

      val userInput = PersonalVerificationRequest("Bob", "20-30-40", "12345678")
      val form = PersonalVerificationRequest.form.fillAndValidate(userInput)

      val assessResult = Failure(new HttpException("FIRE IN SERVER ROOM", 500))
      when(mockRepository.updatePersonalAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val res = await(service.processPersonalAssessResponse(journeyId, assessResult, form))

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
          Some(Error),
          Some(Error),
          None
        )
        verify(mockRepository).updatePersonalAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        res.hasErrors shouldEqual false
      }
    }
  }

  "Assessing business bank account details provided by the user" when {
    val mockConnector = mock[BankAccountReputationConnector]
    val mockRepository = mock[JourneyRepository]
    val service = new VerificationService(mockConnector, mockRepository)

    val userInput = BusinessVerificationRequest("Bob Company", Some("SC1234567"), "20-30-40", "12345678", None)

    val assessResult = Future.successful(
      Success(BarsBusinessAssessResponse(Yes, Yes, None, Yes, Yes, Indeterminate, Indeterminate, Some(No))))

    when(mockConnector.assessBusiness(any(), any(), any(), any(), any())(any(), any())).thenReturn(assessResult)

    "a valid address is provided" should {
      val inputAddress = Some(Address(List("line1", "line2"), Some("town"), Some("postcode")))
      val expectedBarsAddress = Some(BarsAddress(List("line1", "line2"), Some("town"), Some("postcode")))

      "strip the dashes from the sort code and pass address as is" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address where the address lines are all empty is provided" should {
      val inputAddress = Some(Address(List("", ""), Some("town"), Some("postcode-b")))
      val expectedBarsAddress = Some(BarsAddress(List(" "), Some("town"), Some("postcode-b")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with no lines is provided" should {
      val inputAddress = Some(Address(List.empty, Some("town"), Some("postcode-c")))
      val expectedBarsAddress = Some(BarsAddress(List(" "), Some("town"), Some("postcode-c")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with too many lines (> 4) is provided" should {
      val inputAddress = Some(Address(List("line1", "line2", "line3", "line4", "line5"), Some("town"), Some
      ("postcode-d")))
      val expectedBarsAddress = Some(BarsAddress(List("line1", "line2", "line3", "line4"), Some("town"), Some
      ("postcode-d")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with mixture of blank and non-blank lines is provided" should {
      val inputAddress = Some(Address(List("line1", "", "line3", ""), Some("town"), Some("postcode-e")))
      val expectedBarsAddress = Some(BarsAddress(List("line1", "line3"), Some("town"), Some("postcode-e")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with too many and mixture of blank and non-blank lines is provided" should {
      val inputAddress = Some(Address(List("", "line2", "", "line4", "", "line6"), Some("town"), Some("postcode-f")))
      val expectedBarsAddress = Some(BarsAddress(List("line2", "line4", "line6"), Some("town"), Some("postcode-f")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address having lines with a total length > 140 characters is provided" should {
      val inputAddress = Some(Address(List(
        "11111222223333344444555556666677777888889999900000",
        "11111222223333344444555556666677777888889999900000",
        "11111222223333344444555556666677777888889999900000"), Some("town"), Some("postcode-h")))
      val expectedBarsAddress = Some(new BarsAddress(List(
        "11111222223333344444555556666677777888889999900000",
        "11111222223333344444555556666677777888889999900000",
        "1111122222333334444455555666667777788888"), Some("town"), Some("postcode-h")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"),
          meq(Some("SC1234567")),
          meq("203040"),
          meq("12345678"),
          meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address having lines with a total length > 140 chars and having empty line is provided" should {
      val inputAddress = Some(Address(List(
        "1111122222333334444455555666667777788888999990000011111222223333344444555556666677777888889999900000", "",
        "11111222223333344444555556666677777888889999900000", "",
        "11111222223333344444555556666677777888889999900000"), Some("town"), Some("postcode-h")))
      val expectedBarsAddress = Some(new BarsAddress(List(
        "1111122222333334444455555666667777788888999990000011111222223333344444555556666677777888889999900000",
        "1111122222333334444455555666667777788888"), Some("town"), Some("postcode-h")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"),
          meq(Some("SC1234567")),
          meq("203040"),
          meq("12345678"),
          meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with a town that is too short is provided" should {
      val inputAddress = Some(Address(List("line1", "line2"), Some(""), Some("postcode-g")))
      val expectedBarsAddress = Some(BarsAddress(List("line1", "line2"), None, Some("postcode-g")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "an address with a town that is too long is provided" should {
      val inputAddress = Some(Address(List("line1", "line2"), Some("111112222233333444445555566666777778"), Some
      ("postcode-i")))
      val expectedBarsAddress = Some(BarsAddress(List("line1", "line2"), Some("11111222223333344444555556666677777"),
        Some("postcode-i")))

      "modify the address to one with a single non-empty line" in {
        clearInvocations(mockConnector)
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }

    "no address is provided" should {
      val inputAddress = None
      val expectedBarsAddress = None

      "modify the address to one with a single non-empty line" in {
        await(service.assessBusiness(userInput, inputAddress))

        verify(mockConnector).assessBusiness(meq("Bob Company"), meq(Some("SC1234567")), meq("203040"), meq
        ("12345678"), meq(expectedBarsAddress))(any(), any())
      }
    }
  }

  "processing the business assess response" when {
    val journeyId = BSONObjectID.generate()

    val mockConnector = mock[BankAccountReputationConnector]
    val mockRepository = mock[JourneyRepository]
    val service = new VerificationService(mockConnector, mockRepository)

    val userInput = BusinessVerificationRequest("Bob Company", Some("SC1234567"), "20-30-40", "12345678", None)
    val form = BusinessVerificationRequest.form.fillAndValidate(userInput)

    "the details provided pass the remote bars checks" should {
      val assessResult = Success(BarsBusinessAssessResponse(Yes, Yes, Some("sort-code-bank-name-business"), Yes, Yes,
        Indeterminate, Indeterminate, Some(No)))

      clearInvocations(mockRepository)
      when(mockRepository.updateBusinessAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = await(service.processBusinessAssessResponse(journeyId, assessResult, form))

      "persist the details to mongo" in {
        val expectedAccountDetails = BusinessAccountDetails(
          Some("Bob Company"),
          Some("SC1234567"),
          Some("20-30-40"),
          Some("12345678"),
          None,
          Some(Yes),
          Some(No),
          Some(Yes),
          Some(Yes),
          Some(Indeterminate),
          Some(Indeterminate),
          Some("sort-code-bank-name-business")
        )
        verify(mockRepository).updateBusinessAccountDetails(meq(journeyId), meq(expectedAccountDetails))(any(), any())
      }

      "return a valid form" in {
        updatedForm.hasErrors shouldEqual false
      }
    }

    "the remote bars check fails with an Internal Server Error" should {
      val mockConnector = mock[BankAccountReputationConnector]
      val mockRepository = mock[JourneyRepository]
      val service = new VerificationService(mockConnector, mockRepository)

      val userInput = BusinessVerificationRequest("Bob Company", Some("SC1231234"), "20-30-40", "12345678", None)
      val form = BusinessVerificationRequest.form.fillAndValidate(userInput)

      val assessResult = Failure(new HttpException("FIRE IN SERVER ROOM", 500))
      when(mockRepository.updateBusinessAccountDetails(any(), any())(any(), any())).thenReturn(Future.successful(true))

      val updatedForm = service.processBusinessAssessResponse(journeyId, assessResult, form)

      "persist the details to mongo" in {
        val expectedAccountDetails = BusinessAccountDetails(
          Some("Bob Company"),
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
}
