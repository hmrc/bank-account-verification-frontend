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

package bankaccountverification.web.personal

import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BarsPersonalAssessBadRequestResponse, BarsPersonalAssessResponse, BarsPersonalAssessSuccessResponse, ReputationResponseEnum}
import bankaccountverification.web.Forms._
import bankaccountverification.web.Implicits.SanitizedString
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.libs.json.Json

case class PersonalVerificationRequest private (accountName: String, sortCode: String, accountNumber: String,
                                       rollNumber: Option[String] = None)
object PersonalVerificationRequest {
  def apply(accountName: String, sortCode: String, accountNumber: String,
            rollNumber: Option[String] = None): PersonalVerificationRequest = {
    val cleanSortCode = sortCode.stripSpacesAndDashes()
    val cleanAccountNumber = accountNumber.stripSpacesAndDashes().leftPadToLength(8, '0')
    val cleanRollNumber = rollNumber.map(_.stripSpaces())

    new PersonalVerificationRequest(accountName, cleanSortCode, cleanAccountNumber, cleanRollNumber)
  }

  object formats {
    implicit val bankAccountDetailsReads = Json.reads[PersonalVerificationRequest]
    implicit val bankAccountDetailsWrites = Json.writes[PersonalVerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[PersonalVerificationRequest]) {
    def validateUsingBarsPersonalAssessResponse(response: BarsPersonalAssessResponse): Form[PersonalVerificationRequest] =
      response match {
        case badRequest: BarsPersonalAssessBadRequestResponse =>
          form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
        case success: BarsPersonalAssessSuccessResponse =>
          import success._

          if (accountNumberWithSortCodeIsValid == No)
            form.fill(form.get).withError("accountNumber", "error.accountNumber.modCheckFailed")
          else if (accountExists == No)
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
      )(PersonalVerificationRequest.apply)(PersonalVerificationRequest.unapply))
}