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
import play.api.i18n.Messages
import play.api.libs.json.{Format, Json}

import scala.util.{Failure, Success, Try}

case class BankAccountDetails(name: String, sortCode: String, accountNumber: String)
object BankAccountDetails {
  object formats {
    implicit val bankAccountDetailsReads   = Json.reads[BankAccountDetails]
    implicit val bankAccountDetailsWrites  = Json.writes[BankAccountDetails]
    implicit val bankAccountDetailsFormats = Format(bankAccountDetailsReads, bankAccountDetailsWrites)
  }

  def bankAccountDetailsForm(implicit messages: Messages): Form[BankAccountDetails] =
    Form(
      mapping("name" -> text, "sortCode" -> text.verifying(sortcodeConstraint), "accountNumber" -> text)(
        BankAccountDetails.apply
      )(BankAccountDetails.unapply)
    )

  def sortcodeConstraint(implicit messages: Messages): Constraint[String] =
    Constraint[String](Some("constraints.sortcode"), Seq.empty)(input =>
      input.replaceAll("""[ \-]""", "") match {
        case empty if empty.isEmpty =>
          Invalid(Seq(ValidationError(messages("sortcode.emptyError"))))
        case chars if chars.length != 6 =>
          Invalid(Seq(ValidationError(messages("sortcode.invalidLengthError"))))
        case chars if sortcodeHasInvalidChars(chars) =>
          Invalid(Seq(ValidationError(messages("sortcode.invalidCharsError"))))
        case _ =>
          Valid
      }
    )

  private def sortcodeHasInvalidChars(sortcode: String): Boolean =
    Try(sortcode.toInt) match {
      case Success(sc) => false
      case Failure(_)  => true
    }

}
