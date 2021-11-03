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

import bankaccountverification.BACSRequirements
import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BarsBusinessAssessBadRequestResponse, BarsBusinessAssessResponse, BarsBusinessAssessSuccessResponse}
import bankaccountverification.web.Forms._
import bankaccountverification.web.Implicits.SanitizedString
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json

case class BusinessVerificationRequest(companyName: String, sortCode: String, accountNumber: String,
                                       rollNumber: Option[String])

object BusinessVerificationRequest {
  def apply(companyName: String, sortCode: String, accountNumber: String,
            rollNumber: Option[String] = None): BusinessVerificationRequest = {
    val cleanSortCode = sortCode.stripSpacesAndDashes()
    val cleanAccountNumber = accountNumber.stripSpacesAndDashes().leftPadToLength(8, '0')
    val cleanRollNumber = rollNumber.map(_.stripSpaces())

    new BusinessVerificationRequest(companyName, cleanSortCode, cleanAccountNumber, cleanRollNumber)
  }

  object formats {
    implicit val bankAccountDetailsReads = Json.reads[BusinessVerificationRequest]
    implicit val bankAccountDetailsWrites = Json.writes[BusinessVerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[BusinessVerificationRequest]) {
    def validateUsingBarsBusinessAssessResponse(response: BarsBusinessAssessResponse,
                                                directDebitConstraints: BACSRequirements)
    : Form[BusinessVerificationRequest] =
      response match {
        case badRequest: BarsBusinessAssessBadRequestResponse =>
          form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
        case success: BarsBusinessAssessSuccessResponse =>
          import success._
          if (accountNumberIsWellFormatted == No) {
            form.fill(form.get).withError("accountNumber", "error.accountNumber.modCheckFailed")
          } else if (sortCodeIsPresentOnEISCD != Yes) {
            form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
          } else if (sortCodeSupportsDirectDebit != Yes && directDebitConstraints.directDebitRequired) {
            form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
          } else if (sortCodeSupportsDirectCredit != Yes && directDebitConstraints.directCreditRequired) {
            form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
          } else if (accountExists == No) {
            form.fill(form.get).withError("accountNumber", "error.accountNumber.doesNotExist")
          } else if (nonStandardAccountDetailsRequiredForBacs.getOrElse(No) == Yes && form.get.rollNumber.isEmpty) {
            form.fill(form.get).withError("rollNumber", "error.rollNumber.required")
          } else {
            form
          }
      }
  }

  val form: Form[BusinessVerificationRequest] =
    Form(
      mapping(
        "companyName" -> companyNameMapping,
        "sortCode" -> sortCodeMapping,
        "accountNumber" -> accountNumberMapping,
        "rollNumber" -> optional(rollNumberMapping)
      )(BusinessVerificationRequest.apply)(BusinessVerificationRequest.unapply)
    )
}
