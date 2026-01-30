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

package bankaccountverification.web.personal

import bankaccountverification.BACSRequirements
import bankaccountverification.connector.ReputationResponseEnum.{No, Partial, Yes}
import bankaccountverification.connector.{BarsPersonalAssessBadRequestResponse, BarsPersonalAssessResponse, BarsPersonalAssessSuccessResponse}
import bankaccountverification.web.Forms._
import bankaccountverification.web.Implicits.SanitizedString
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Json, Reads, Writes}

case class PersonalVerificationRequest private(accountName: String,
                                               sortCode: String,
                                               accountNumber: String,
                                               rollNumber: Option[String] = None)

object PersonalVerificationRequest {
  def apply(accountName: String, sortCode: String, accountNumber: String,
            rollNumber: Option[String] = None): PersonalVerificationRequest = {
    val cleanSortCode = sortCode.stripSpacesAndDashes()
    val cleanAccountNumber = {
      val nsAccountNumber = accountNumber.stripSpacesAndDashes()
      if (nsAccountNumber.isEmpty) "" else nsAccountNumber.leftPadToLength(8, '0')
    }
    val cleanRollNumber = rollNumber.map(_.stripSpaces())

    new PersonalVerificationRequest(accountName, cleanSortCode, cleanAccountNumber, cleanRollNumber)
  }

  def convertNameToASCII(verificationRequest: PersonalVerificationRequest): PersonalVerificationRequest = {
    verificationRequest.copy(accountName = verificationRequest.accountName.toAscii)
  }

  object formats {
    implicit val bankAccountDetailsReads: Reads[PersonalVerificationRequest] = Json.reads[PersonalVerificationRequest]
    implicit val bankAccountDetailsWrites: Writes[PersonalVerificationRequest] = Json.writes[PersonalVerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[PersonalVerificationRequest]) {
    def validateUsingBarsPersonalAssessResponse(response: BarsPersonalAssessResponse,
                                                directDebitConstraints: BACSRequirements): Form[PersonalVerificationRequest] =
      response match {
        case badRequest: BarsPersonalAssessBadRequestResponse if badRequest.code == "SORT_CODE_ON_DENY_LIST" =>
          form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
        case _: BarsPersonalAssessBadRequestResponse                                                =>
          form.fill(form.get).withError("", "error.summaryText")
        case success: BarsPersonalAssessSuccessResponse                                                      =>
          import success._

          if (accountNumberIsWellFormatted == No)
            form.fill(form.get).withError("accountNumber", "error.accountNumber.modCheckFailed")
          else if (sortCodeIsPresentOnEISCD != Yes) {
            form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
          } else if (sortCodeSupportsDirectDebit != Yes && directDebitConstraints.directDebitRequired) {
            form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
          } else if (sortCodeSupportsDirectCredit != Yes && directDebitConstraints.directCreditRequired) {
            form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
          } else if (accountExists == No && (nameMatches == Yes || nameMatches == Partial)) {
            form.fill(form.get).withError("accountNumber", "error.accountNumber.wrongBankAccountType")
          } else if (accountExists == No)
            form.fill(form.get).withError("accountNumber", "error.accountNumber.doesNotExist")
          else if (nonStandardAccountDetailsRequiredForBacs.getOrElse(No) == Yes && form.get.rollNumber.isEmpty)
            form.fill(form.get).withError("rollNumber", "error.rollNumber.required")
          else form
      }
  }

  val form: Form[PersonalVerificationRequest] =
    Form(
      mapping(
        "accountName" -> accountNameMapping,
        "sortCode" -> sortCodeMapping,
        "accountNumber" -> accountNumberMapping,
        "rollNumber" -> optional(rollNumberMapping)
      )(PersonalVerificationRequest.apply)(a => Some(a.accountName, a.sortCode, a.accountNumber, a.rollNumber)))
}
