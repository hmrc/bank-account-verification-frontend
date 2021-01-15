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

package bankaccountverification.web
import play.api.data.Forms.text
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid, ValidationError}

import scala.util.{Failure, Success, Try}

object Forms {
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
