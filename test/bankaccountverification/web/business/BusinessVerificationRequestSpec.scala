/*
 * Copyright 2021 HM Revenue & Customs
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

package bankaccountverification.web.business

import bankaccountverification.Journey.DirectDebitConstraints
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.connector.{BarsBusinessAssessBadRequestResponse, BarsBusinessAssessErrorResponse, BarsBusinessAssessResponse, BarsBusinessAssessSuccessResponse, ReputationResponseEnum}
import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.FormError
import play.api.i18n.MessagesApi

class BusinessVerificationRequestSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  override lazy val app = {
    SharedMetricRegistries.clear()
    fakeApplication()
  }

  "BankAccountDetails form" should {
    val messagesApi       = app.injector.instanceOf[MessagesApi]
    implicit val messages = messagesApi.preferred(Seq())

    "validate sortcode successfully" when {
      "sortcode is hyphenated" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "10-10-10", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.sortCode shouldBe "101010"
      }
      "sortcode is hyphenated with spaces" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "10 10 10", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.sortCode shouldBe "101010"
      }
      "sortcode contains just 6 digits" in {
        val bankAccountDetails     = BusinessVerificationRequest("Joe Blogs", "101010", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.sortCode shouldBe "101010"
      }
      "sortcode contains just 6 digits and spaces and dashes" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", " 10-10 10   ", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.sortCode shouldBe "101010"
      }
    }

    "flag company name validation errors" when {
      "company name is empty" in {
        val bankAccountDetails     = BusinessVerificationRequest("", "123456", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "companyName")
        error shouldNot be(None)
        error.get.message shouldBe "error.companyName.required"
      }

      "company name is too long" in {
        val tooLongName = "ASDFGHJKLPASDFGHJKLPASDFGHJKLPASDFGHJKLPASDFGHJKLPASDFGHJKLPASDFGHJKLPA"
        val bankAccountDetails     = BusinessVerificationRequest(tooLongName, "123456", "12345678")
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "companyName")
        error shouldNot be(None)
        error.get.message shouldBe "error.companyName.maxLength"
      }
    }

    "validate account numbers" when {
      "account number contains spaces" in {
        val bankAccountDetails     = BusinessVerificationRequest("Some Company", "123456", " 1 2 3 4 5 6 7 8 ")
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.accountNumber shouldBe "12345678"
      }
      "account number contains dashes" in {
        val bankAccountDetails     = BusinessVerificationRequest("Some Company", "123456", "-1-2-3-4-5-6-7-8-")
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.accountNumber shouldBe "12345678"
      }
      "account number contains dashes and spaces" in {
        val bankAccountDetails     = BusinessVerificationRequest("Some Company", "123456", "-1 -2 -3 -4- 5- 6- 7 8 -")
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.accountNumber shouldBe "12345678"
      }
    }

    "flag account number validation errors" when {
      "account number is empty" in {
        val bankAccountDetails     = Map("companyName" -> "Joe Blogs", "sortCode" -> "123456",
          "accountNumber" -> "")
        val bankAccountDetailsForm = BusinessVerificationRequest.form.bind(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.required"
      }

      "account number is less than 6 digits" in {
        val bankAccountDetails     = Map("companyName" -> "Joe Blogs", "sortCode" -> "123456", "accountNumber" -> "12345")
        val bankAccountDetailsForm = BusinessVerificationRequest.form.bind(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.minLength"
      }

      "account number is more than 8 digits" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "123456", "123456789", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.maxLength"
      }

      "account number is not numeric" in {
        val bankAccountDetails     = BusinessVerificationRequest("Joe Blogs", "123456", "123FOO78", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.digitsOnly"
      }

      "account number is too long and not numeric" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "123456", "123FOOO78", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.digitsOnly"
      }
    }

    "flag sortcode validation errors" when {
      "sortcode is empty" in {
        val bankAccountDetails     = BusinessVerificationRequest("Joe Blogs", "", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.required"
      }

      "sortcode is less than 6 digits" in {
        val bankAccountDetails     = BusinessVerificationRequest("Joe Blogs", "1010", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.invalidLengthError"
      }

      "sortcode is longer than 6 digits" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "1010101", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.invalidLengthError"
      }

      "sortcode contains invalid characters" in {
        val bankAccountDetails     = BusinessVerificationRequest("Joe Blogs", "SRTCDE", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.invalidCharsError"
      }

      "sortcode contains too many invalid characters" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "SORTCODE", "12345678", None)
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.messages should contain theSameElementsAs Seq(
          "error.sortcode.invalidLengthError",
          "error.sortcode.invalidCharsError"
        )
      }
    }

    "validate roll numbers" when {
      "roll number contains spaces" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Some Company", "100010", "12345678", Some(" 12 34 56 78 90 12 34 56 78 "))
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)

        bankAccountDetailsForm.hasErrors shouldBe false
        bankAccountDetailsForm.get.rollNumber shouldBe Some("123456789012345678")
      }
    }

    "flag roll number validation errors" when {
      "roll number contains more than 18 characters" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "1010", "12345678", Some("1234567890123456789"))
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "rollNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.rollNumber.maxLength"
      }

      "roll number contains invalid characters" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "1010", "12345678", Some("1234*$£@!"))
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "rollNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.rollNumber.format"
      }

      "roll number is too long and contains invalid characters" in {
        val bankAccountDetails =
          BusinessVerificationRequest("Joe Blogs", "1010", "12345678", Some("1234*$£@!%1234*$£@!%"))
        val bankAccountDetailsForm = BusinessVerificationRequest.form.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "rollNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.rollNumber.format"
      }
    }
  }

  "Validation using the bars business assess response" when {
    val request = BusinessVerificationRequest("Some Company", "10-10-10", "12345678", None)
    val form    = BusinessVerificationRequest.form.fillAndValidate(request)

    "the response indicates the sort code and account number combination is not valid" should {
      val response = BarsBusinessAssessSuccessResponse(
        No,
        Indeterminate,
        None,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        No, No,
        Some(No)
      )
      val updatedForm = form.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag an error against the account number" in {
        updatedForm.error("accountNumber") shouldBe Some(
          FormError("accountNumber", "error.accountNumber.modCheckFailed")
        )
      }
    }

    "the response indicates the account does not exist" should {
      val response = BarsBusinessAssessSuccessResponse(
        Yes,
        Yes,
        None,
        No,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Yes, Yes,
        Some(No)
      )

      val updatedForm = form.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag an error against the account number" in {
        updatedForm.error("accountNumber") shouldBe Some(
          FormError("accountNumber", "error.accountNumber.doesNotExist")
        )
      }
    }

    "the response indicates that a roll number is required but none was provided" should {
      val response = BarsBusinessAssessSuccessResponse(
        Yes,
        Yes,
        None,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Yes, Yes,
        Some(Yes)
      )
      val updatedForm = form.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag an error against the roll number field" in {
        updatedForm.error("rollNumber") shouldBe Some(FormError("rollNumber", "error.rollNumber.required"))
      }
    }

    "the response indicates that a roll number is required and a valid roll number was provided" should {
      val requestWithRollNumber =
        BusinessVerificationRequest("Joe Blogs", "10-10-10", "12345678", Some("ROLL1"))
      val formWithRollNumber = BusinessVerificationRequest.form.fillAndValidate(requestWithRollNumber)

      val response = BarsBusinessAssessSuccessResponse(
        Yes,
        Yes,
        None,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Yes, Yes,
        Some(Yes)
      )
      val updatedForm = formWithRollNumber.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag no errors" in {
        updatedForm.hasErrors shouldBe false
      }
    }

    "the response indicates the sort code provided is not in EISCD" should {
      val requestWithNonEISCDSortCode =
        BusinessVerificationRequest("Joe Blogs", "19-19-19", "12345678", None)
      val formWithRollNumber = BusinessVerificationRequest.form.fillAndValidate(requestWithNonEISCDSortCode)

      val response = BarsBusinessAssessSuccessResponse(
        Yes,
        No,
        None,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        No, No,
        None
      )
      val updatedForm = formWithRollNumber.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag errors" in {
        updatedForm.hasErrors shouldBe true
        updatedForm.error("sortCode") shouldBe defined
      }
    }

    "the response indicates the sort code provided exists in EISCD but does not support direct debit payments" should {
      val requestWithNonEISCDSortCode =
        BusinessVerificationRequest("Joe Blogs", "19-19-19", "12345678", None)
      val formWithRollNumber = BusinessVerificationRequest.form.fillAndValidate(requestWithNonEISCDSortCode)

      val response = BarsBusinessAssessSuccessResponse(
        Yes,
        Yes,
        None,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        No, Yes,
        None
      )
      val updatedForm = formWithRollNumber.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag errors" in {
        updatedForm.hasErrors shouldBe true
        updatedForm.error("sortCode") shouldBe defined
      }
    }

    "the response indicates the sort code provided exists in EISCD but does not support direct credit payments" should {
      val requestWithNonEISCDSortCode =
        BusinessVerificationRequest("Joe Blogs", "19-19-19", "12345678", None)
      val formWithRollNumber = BusinessVerificationRequest.form.fillAndValidate(requestWithNonEISCDSortCode)

      val response = BarsBusinessAssessSuccessResponse(
        Yes,
        Yes,
        None,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Indeterminate,
        Yes, No,
        None
      )
      val updatedForm = formWithRollNumber.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag errors" in {
        updatedForm.hasErrors shouldBe true
        updatedForm.error("sortCode") shouldBe defined
      }
    }

    "the response indicates an error occurred" should {
      val response = BarsBusinessAssessSuccessResponse(
        ReputationResponseEnum.Error,
        ReputationResponseEnum.Yes,
        None,
        ReputationResponseEnum.Error,
        ReputationResponseEnum.Error,
        ReputationResponseEnum.Error,
        ReputationResponseEnum.Error,
        Yes, Yes,
        Some(ReputationResponseEnum.Error)
      )

      val updatedForm = form.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag no errors so that the journey is not impacted" in {
        updatedForm.hasErrors shouldBe false
      }
    }

    "the response indicates that the sort code provided was on the deny list" should {
      val response = BarsBusinessAssessBadRequestResponse("SORT_CODE_ON_DENY_LIST", "083200: sort code is in deny list")
      val updatedForm = form.validateUsingBarsBusinessAssessResponse(response, DirectDebitConstraints(directDebitRequired = true, directCreditRequired = true))

      "flag an error against the sort code field" in {
        updatedForm.error("sortCode") shouldBe Some(FormError("sortCode", "error.sortCode.denyListed"))
      }
    }

  }
}
