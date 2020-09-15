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

package bankaccountverification

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

import java.time.ZonedDateTime

import bankaccountverification.api.{BusinessCompleteResponse, CompleteResponse, CompleteResponseAddress}
import bankaccountverification.connector.{BarsBusinessAssessResponse, BarsPersonalAssessResponse, ReputationResponseEnum}
import bankaccountverification.web.AccountTypeRequestEnum
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.web.business.BusinessVerificationRequest
import bankaccountverification.web.personal.PersonalVerificationRequest
import play.api.libs.json.{JsValue, Json}

case class Address(lines: List[String], town: Option[String], postcode: Option[String])

case class Session(accountType: Option[AccountTypeRequestEnum] = None, address: Option[Address] = None,
                   personal: Option[PersonalSession] = None, business: Option[BusinessSession] = None)

object Session {
  def toCompleteResponseJson(session: Session): Option[JsValue] =
    session.accountType match {
      case Some(AccountTypeRequestEnum.Personal) => PersonalSession.toCompleteResponse(session).map(Json.toJson(_))
      case Some(AccountTypeRequestEnum.Business) => BusinessSession.toCompleteResponse(session).map(Json.toJson(_))
    }
}

case class PersonalSession(accountName: Option[String],
                           sortCode: Option[String],
                           accountNumber: Option[String],
                           rollNumber: Option[String] = None,
                           accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None,
                           accountExists: Option[ReputationResponseEnum] = None,
                           nameMatches: Option[ReputationResponseEnum] = None,
                           addressMatches: Option[ReputationResponseEnum] = None,
                           nonConsented: Option[ReputationResponseEnum] = None,
                           subjectHasDeceased: Option[ReputationResponseEnum] = None,
                           nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
                           sortCodeBankName: Option[String] = None
                          )

object PersonalSession {
  def toCompleteResponse(session: Session): Option[CompleteResponse] =
    session match {
      case Session(
      _,
      address,
      Some(PersonalSession(
      Some(accountName),
      Some(sortCode),
      Some(accountNumber),
      rollNumber,
      Some(accountNumberWithSortCodeIsValid),
      accountExists,
      nameMatches,
      addressMatches,
      nonConsented,
      subjectHasDeceased,
      nonStandardAccountDetailsRequiredForBacs,
      sortCodeBankName)),
      _
      ) =>
        Some(
          CompleteResponse(
            Personal,
            Some(
              api.PersonalCompleteResponse(
                address.map(a => CompleteResponseAddress(a.lines, a.town, a.postcode)),
                accountName, sortCode, accountNumber, accountNumberWithSortCodeIsValid, rollNumber, accountExists,
                nameMatches, addressMatches, nonConsented, subjectHasDeceased,
                nonStandardAccountDetailsRequiredForBacs, sortCodeBankName)),
            None))
      case _ => None
    }
}

case class PersonalAccountDetails(accountName: Option[String],
                                  sortCode: Option[String],
                                  accountNumber: Option[String],
                                  rollNumber: Option[String] = None,
                                  accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None,
                                  accountExists: Option[ReputationResponseEnum] = None,
                                  nameMatches: Option[ReputationResponseEnum] = None,
                                  addressMatches: Option[ReputationResponseEnum] = None,
                                  nonConsented: Option[ReputationResponseEnum] = None,
                                  subjectHasDeceased: Option[ReputationResponseEnum] = None,
                                  nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
                                  sortCodeBankName: Option[String] = None
                                 )

object PersonalAccountDetails {
  def apply(request: PersonalVerificationRequest, response: BarsPersonalAssessResponse): PersonalAccountDetails =
    PersonalAccountDetails(
      Some(request.accountName),
      Some(request.sortCode),
      Some(request.accountNumber),
      request.rollNumber,
      Some(response.accountNumberWithSortCodeIsValid),
      Some(response.accountExists),
      Some(response.nameMatches),
      Some(response.addressMatches),
      Some(response.nonConsented),
      Some(response.subjectHasDeceased),
      response.nonStandardAccountDetailsRequiredForBacs,
      response.sortCodeBankName
    )
}

case class BusinessSession(companyName: Option[String],
                           companyRegistrationNumber: Option[String],
                           sortCode: Option[String],
                           accountNumber: Option[String],
                           rollNumber: Option[String] = None,
                           accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None,
                           accountExists: Option[ReputationResponseEnum] = None,
                           companyNameMatches: Option[ReputationResponseEnum] = None,
                           companyPostCodeMatches: Option[ReputationResponseEnum] = None,
                           companyRegistrationNumberMatches: Option[ReputationResponseEnum] = None,
                           nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
                           sortCodeBankName: Option[String] = None
                          )

object BusinessSession {
  def toCompleteResponse(session: Session): Option[CompleteResponse] =
    session match {
      case Session(
      _,
      address,
      _,
      Some(BusinessSession(
      Some(companyName),
      companyRegistrationNumber,
      Some(sortCode),
      Some(accountNumber),
      rollNumber,
      Some(accountNumberWithSortCodeIsValid),
      accountExists,
      companyNameMatches,
      companyPostCodeMatches,
      companyRegistrationNumberMatches,
      nonStandardAccountDetailsRequiredForBacs,
      sortCodeBankName))
      ) =>
        Some(
          CompleteResponse(
            Business,
            None,
            Some(
              BusinessCompleteResponse(
                address.map(a => CompleteResponseAddress(a.lines, a.town, a.postcode)),
                companyName, companyRegistrationNumber, sortCode, accountNumber, rollNumber,
                accountNumberWithSortCodeIsValid, accountExists, companyNameMatches, companyPostCodeMatches,
                companyRegistrationNumberMatches, nonStandardAccountDetailsRequiredForBacs, sortCodeBankName))))
      case _ =>
        None
    }
}

case class BusinessAccountDetails(companyName: Option[String],
                                  companyRegistrationNumber: Option[String],
                                  sortCode: Option[String],
                                  accountNumber: Option[String],
                                  rollNumber: Option[String] = None,
                                  accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None,
                                  nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
                                  accountExists: Option[ReputationResponseEnum] = None,
                                  compayNameMatches: Option[ReputationResponseEnum] = None,
                                  compayPostCodeMatches: Option[ReputationResponseEnum] = None,
                                  compayRegistrationNumberMatches: Option[ReputationResponseEnum] = None,
                                  sortCodeBankName: Option[String] = None
                                 )

object BusinessAccountDetails {
  def apply(request: BusinessVerificationRequest, response: BarsBusinessAssessResponse): BusinessAccountDetails =
    BusinessAccountDetails(
      Some(request.companyName),
      request.companyRegistrationNumber,
      Some(request.sortCode),
      Some(request.accountNumber),
      request.rollNumber,
      Some(response.accountNumberWithSortCodeIsValid),
      response.nonStandardAccountDetailsRequiredForBacs,
      Some(response.accountExists),
      Some(response.companyNameMatches),
      Some(response.companyPostCodeMatches),
      Some(response.companyRegistrationNumberMatches),
      response.sortCodeBankName
    )

}

case class PersonalAccountDetailsUpdate(expiryDate: ZonedDateTime, data: PersonalAccountDetails)

case class BusinessAccountDetailsUpdate(expiryDate: ZonedDateTime, data: BusinessAccountDetails)

case class AccountTypeUpdate(expiryDate: ZonedDateTime, accountType: AccountTypeRequestEnum)
