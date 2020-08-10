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

import bankaccountverification.connector.BarsValidationResponse
import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

case class VerificationRequest(
  accountName: String,
  sortCode: String,
  accountNumber: String,
  rollNumber: Option[String] = None
)

object VerificationRequest {
  object formats {
    implicit val bankAccountDetailsReads  = Json.reads[VerificationRequest]
    implicit val bankAccountDetailsWrites = Json.writes[VerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[VerificationRequest]) {

    def validateUsingBarsResponse(response: BarsValidationResponse): Form[VerificationRequest] =
      if (response.accountNumberWithSortCodeIsValid == No)
        form
          .fill(form.get)
          .withError("sortCode", "error.sortcode.eiscdInvalid")
          .withError("accountNumber", "error.accountNumber.eiscdInvalid")
      else if (response.nonStandardAccountDetailsRequiredForBacs == Yes)
        form
          .fill(form.get)
          .withError("rollNumber", "error.rollNumber.required")
      else form

  }

  val form: Form[VerificationRequest] =
    Form(
      mapping(
        "accountName"   -> accountNameMapping,
        "sortCode"      -> sortCodeMapping,
        "accountNumber" -> accountNumberMapping,
        "rollNumber"    -> optional(rollNumberMapping)
      )(VerificationRequest.apply)(VerificationRequest.unapply)
    )

  def accountNameMapping = text.verifying(Constraints.nonEmpty(errorMessage = "error.accountName.required"))

  def accountNumberMapping =
    text.verifying(
      Constraints.nonEmpty("error.accountNumber.required"),
      Constraints.pattern("[0-9]+".r, "constraint.accountNumber.digitsOnly", "error.accountNumber.digitsOnly"),
      Constraints.minLength(6, "error.accountNumber.minLength"),
      Constraints.maxLength(8, "error.accountNumber.maxLength")
    )

  def sortCodeMapping =
    text.verifying(
      Constraints.nonEmpty(errorMessage = "error.sortcode.required"),
      sortcodeConstraint()
    )

  def rollNumberMapping =
    text.verifying(
      Constraints.pattern("""[A-Z0-9/.\-]+""".r, "constraint.rollNumber.format", "error.rollNumber.format"),
      Constraints.minLength(1, "error.rollNumber.minLength"),
      Constraints.maxLength(18, "error.rollNumber.maxLength")
    )

  def sortcodeConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.sortcode.format"), Seq.empty) { input =>
      val strippedInput = stripSortCode(input)
      val errors =
        Seq(
          if (strippedInput.length != 6) Some("error.sortcode.invalidLengthError") else None,
          if (sortcodeHasInvalidChars(strippedInput)) Some("error.sortcode.invalidCharsError") else None
        ).flatten

      errors match {
        case Seq() => Valid
        case errs  => Invalid(ValidationError(errs))
      }
    }

  def stripSortCode(sortCode: String) = sortCode.replaceAll("""[ \-]""", "")

  private def sortcodeHasInvalidChars(sortcode: String): Boolean =
    Try(sortcode.toInt) match {
      case Success(sc) => false
      case Failure(_)  => true
    }

}
