/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.data.Mapping
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

import scala.util.{Failure, Success, Try}

object Forms {

  import Implicits._

  def accountNameMapping: Mapping[String] = text.verifying(nameConstraint("accountName"))

  def companyNameMapping: Mapping[String] = text.verifying(nameConstraint("companyName"))

  def accountNumberMapping: Mapping[String] = text.verifying(accountNumberConstraint())

  def sortCodeMapping: Mapping[String] = text.verifying(sortcodeConstraint())

  def rollNumberMapping: Mapping[String] = text.verifying(rollNumberConstraint())

  private def nameConstraint(nameTag: String): Constraint[String] =
    Constraint[String](Some(s"constraints.$nameTag"), Seq.empty) { (input: String) =>
      val trimmedInput = input.stripLeadingSpaces().stripTrailingSpaces()
      if (trimmedInput.isEmpty) Invalid(ValidationError(s"error.$nameTag.required"))
      else if(trimmedInput.toAscii.length != trimmedInput.length)
        Invalid(ValidationError(s"error.$nameTag.asciiOnly"))
      else if (trimmedInput.length > 70) Invalid(ValidationError(s"error.$nameTag.maxLength"))
      else Valid
    }

  private def accountNumberConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.accountNumber"), Seq.empty) { input =>
      if (input.isEmpty) Invalid(ValidationError("error.accountNumber.required"))
      else {
        val strippedInput = input.stripSpacesAndDashes()
        val errors =
          Seq(
            if (strippedInput.length < 6) Some("error.accountNumber.minLength") else None,
            if (strippedInput.length > 8) Some("error.accountNumber.maxLength") else None,
            """[0-9]+""".r.unapplySeq(strippedInput).map(_ => None).getOrElse(Some("error.accountNumber.digitsOnly"))
          ).flatten

        errors match {
          case Seq() => Valid
          case errs => Invalid(ValidationError(errs))
        }
      }
    }

  private def sortcodeConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.sortcode.format"), Seq.empty) { input =>
      if (input.isEmpty) Invalid(ValidationError("error.sortcode.required"))
      else {
        val strippedInput = input.stripSpacesAndDashes()
        val errors =
          Seq(
            if (strippedInput.length != 6) Some("error.sortcode.invalidLengthError") else None,
            if (sortcodeHasInvalidChars(strippedInput)) Some("error.sortcode.invalidCharsError") else None
          ).flatten

        errors match {
          case Seq() => Valid
          case errs => Invalid(ValidationError(errs))
        }
      }
    }

  private def rollNumberConstraint(): Constraint[String] = {
    Constraint[String](Some("constraint.rollNumber.format"), Seq.empty) { (input: String) =>
      if (input.isEmpty) Invalid(ValidationError("error.rollNumber.minLength"))
      else {
        val strippedInput = input.stripSpaces()
        val errors = Seq(
          if (strippedInput.length > 18) Some("error.rollNumber.maxLength") else None,
          if (!"""[A-Z0-9/.\-]+""".r.pattern.matcher(strippedInput).matches()) Some("error.rollNumber.format") else None
        ).flatten

        errors match {
          case Seq() => Valid
          case errs => Invalid(ValidationError(errs))
        }
      }
    }
  }

  private def sortcodeHasInvalidChars(sortcode: String): Boolean =
    Try(sortcode.toInt) match {
      case Success(sc) => false
      case Failure(_) => true
    }
}
