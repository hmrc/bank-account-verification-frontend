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

package web

import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.libs.json.{Format, Json}

import scala.util.{Failure, Success, Try}

case class BankAccountDetails(name: String, sortCode: String, accountNumber: String)
object BankAccountDetails {
  object formats {
    implicit val bankAccountDetailsReads   = Json.reads[BankAccountDetails]
    implicit val bankAccountDetailsWrites  = Json.writes[BankAccountDetails]
    implicit val bankAccountDetailsFormats = Format(bankAccountDetailsReads, bankAccountDetailsWrites)
  }

  def bankAccountDetailsForm(): Form[BankAccountDetails] =
    Form(
      mapping("name" -> text, "sortCode" -> text.verifying(sortcodeConstraint), "accountNumber" -> text)(
        BankAccountDetails.apply
      )(BankAccountDetails.unapply)
    )

  def sortcodeConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.sortcode"), Seq.empty) { input =>
      val strippedInput = input.replaceAll("""[ \-]""", "")

      val errors =
        if (strippedInput.isEmpty) Seq("sortcode.emptyError")
        else
          Seq(
            if (strippedInput.length != 6) Some("sortcode.invalidLengthError") else None,
            if (sortcodeHasInvalidChars(strippedInput)) Some("sortcode.invalidCharsError") else None
          ).flatten

      errors match {
        case Seq() => Valid
        case errs  => Invalid(ValidationError(errs))
      }
    }

  private def sortcodeHasInvalidChars(sortcode: String): Boolean =
    Try(sortcode.toInt) match {
      case Success(sc) => false
      case Failure(_)  => true
    }

}
