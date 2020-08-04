package web

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.{Form, FormError}
import play.api.i18n.{Messages, MessagesApi}

class BankAccountDetailsSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {
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

    "flag sortcode validation errors" when {
      "sortcode is empty" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "sortcode.emptyError"
      }

      "sortcode is less than 6 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "sortcode.invalidLengthError"
      }

      "sortcode is longer than 6 digits" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "1010101", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "sortcode.invalidLengthError"
      }

      "sortcode contains invalid characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "SRTCDE", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.message shouldBe "sortcode.invalidCharsError"
      }

      "sortcode contains too many invalid characters" in {
        val bankAccountDetails     = BankAccountDetails("Joe Blogs", "SORTCODE", "12345678")
        val bankAccountDetailsForm = BankAccountDetails.bankAccountDetailsForm.fillAndValidate(bankAccountDetails)
        bankAccountDetailsForm.hasErrors shouldBe true

        val error = bankAccountDetailsForm.errors.find(e => e.key == "sortCode")
        error shouldNot be(None)
        error.get.messages should contain theSameElementsAs Seq("sortcode.invalidLengthError", "sortcode.invalidCharsError")

      }

    }
  }
}
