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

import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi

class BankAccountDetailsSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  override lazy val app = {
    SharedMetricRegistries.clear()
    fakeApplication()
  }

  "BankAccountDetails form" should {
    val messagesApi       = app.injector.instanceOf[MessagesApi]
    implicit val messages = messagesApi.preferred(Seq())

    "validate sortcode successfully" when {
      "sortcode is hyphenated" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "10-10-10", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe false
      }
      "sortcode is hyphenated with spaces" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "10 10 10", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe false
      }
      "sortcode contains just 6 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "101010", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe false
      }
      "sortcode contains just 6 digits and leading & trailing spaces" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", " 10-10 10   ", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe false
      }
    }

    "flag account name validation errors" when {
      "account name is empty" in {
        val bankAccountDetails     = BankAccountDetails("", "123456", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountName")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountName.required"
      }
    }

    "flag account number validation errors" when {
      "account number is empty" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "123456", "")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.required"
      }

      "account number is less than 6 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "123456", "12345")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.minLength"
      }

      "account number is more than 8 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "123456", "123456789")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.maxLength"
      }

      "account number is not numeric" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "123456", "123FOO78")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.digitsOnly"
      }

      "account number is too long and not numeric" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "123456", "123FOOO78")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "accountNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.accountNumber.digitsOnly"
      }
    }

    "flag sortcode validation errors" when {
      "sortcode is empty" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.required"
      }

      "sortcode is less than 6 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.invalidLengthError"
      }

      "sortcode is longer than 6 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010101", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.invalidLengthError"
      }

      "sortcode contains invalid characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "SRTCDE", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "error.sortcode.invalidCharsError"
      }

      "sortcode contains too many invalid characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "SORTCODE", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.messages should contain theSameElementsAs Seq(
          "error.sortcode.invalidLengthError",
          "error.sortcode.invalidCharsError"
        )
      }
    }

    "flag roll number validation errors" when {
      "roll number contains more than 18 characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010", "12345678", Some("1234567890123456789"))
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "rollNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.rollNumber.maxLength"
      }

      "roll number contains invalid characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010", "12345678", Some("1234*$£@!"))
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "rollNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.rollNumber.format"
      }

      "roll number is too long and contains invalid characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010", "12345678", Some("1234*$£@!%1234*$£@!%"))
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "rollNumber")
        error shouldNot be(None)
        error.get.message shouldBe "error.rollNumber.format"
      }
    }
  }
}
