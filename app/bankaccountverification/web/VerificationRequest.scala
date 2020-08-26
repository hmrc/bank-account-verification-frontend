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

import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BarsPersonalAssessResponse, BarsValidationResponse, ReputationResponseEnum}
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

    def validateUsingBarsValidateResponse(response: BarsValidationResponse): Form[VerificationRequest] =
      validate(response.accountNumberWithSortCodeIsValid, response.nonStandardAccountDetailsRequiredForBacs)

    def validateUsingBarsPersonalAssessResponse(response: BarsPersonalAssessResponse): Form[VerificationRequest] =
      validate(
        response.accountNumberWithSortCodeIsValid,
        response.nonStandardAccountDetailsRequiredForBacs.getOrElse(No)
      )

    private def validate(
      accountNumberWithSortCodeIsValid: ReputationResponseEnum,
      nonStandardAccountDetailsRequiredForBacs: ReputationResponseEnum
    ): Form[VerificationRequest] =
      if (accountNumberWithSortCodeIsValid == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.modCheckFailed")
      else if (nonStandardAccountDetailsRequiredForBacs == Yes && form.get.rollNumber.isEmpty)
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

  def accountNumberMapping = text.verifying(accountNumberConstraint())

  def sortCodeMapping = text.verifying(sortcodeConstraint())

  def rollNumberMapping =
    text.verifying(
      Constraints.pattern("""[A-Z0-9/.\-]+""".r, "constraint.rollNumber.format", "error.rollNumber.format"),
      Constraints.minLength(1, "error.rollNumber.minLength"),
      Constraints.maxLength(18, "error.rollNumber.maxLength")
    )

  def accountNumberConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.accountNumber"), Seq.empty) { input =>
      if (input.isEmpty) Invalid(ValidationError("error.accountNumber.required"))
      else {
        val strippedInput = stripSortCode(input)
        val errors =
          Seq(
            if (strippedInput.length < 6) Some("error.accountNumber.minLength") else None,
            if (strippedInput.length > 8) Some("error.accountNumber.maxLength") else None,
            """[0-9]+""".r.unapplySeq(strippedInput).map(_ => None).getOrElse(Some("error.accountNumber.digitsOnly"))
          ).flatten

        errors match {
          case Seq() => Valid
          case errs  => Invalid(ValidationError(errs))
        }
      }
    }

  def sortcodeConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.sortcode.format"), Seq.empty) { input =>
      if (input.isEmpty) Invalid(ValidationError("error.sortcode.required"))
      else {
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
    }

  def stripSortCode(sortCode: String) = sortCode.replaceAll("""[ \-]""", "")

  private def sortcodeHasInvalidChars(sortcode: String): Boolean =
    Try(sortcode.toInt) match {
      case Success(sc) => false
      case Failure(_)  => true
    }
}
