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

package bankaccountverification.api

import bankaccountverification.connector.ReputationResponseEnum
import bankaccountverification.web.AccountTypeRequestEnum
import play.api.libs.json.{Json, OWrites, Reads}

case class CompleteResponseAddress(lines: List[String], town: Option[String], postcode: Option[String])

case class CompleteResponse(accountType: AccountTypeRequestEnum, personal: Option[PersonalCompleteResponse],
                            business: Option[BusinessCompleteResponse])

object CompleteResponse {
  implicit val completeResponseWrites: OWrites[CompleteResponse] = Json.writes[CompleteResponse]
  implicit val completeResponseReads: Reads[CompleteResponse] = Json.reads[CompleteResponse]
}

case class PersonalCompleteResponse(address: Option[CompleteResponseAddress],
                                    accountName: String,
                                    sortCode: String,
                                    accountNumber: String,
                                    accountNumberWithSortCodeIsValid: ReputationResponseEnum,
                                    rollNumber: Option[String] = None,
                                    accountExists: Option[ReputationResponseEnum] = None,
                                    nameMatches: Option[ReputationResponseEnum] = None,
                                    nonConsented: Option[ReputationResponseEnum] = None,
                                    subjectHasDeceased: Option[ReputationResponseEnum] = None,
                                    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None)

object PersonalCompleteResponse {
  implicit val completeResponseAddressWrites: OWrites[CompleteResponseAddress] = Json.writes[CompleteResponseAddress]
  implicit val completeResponseAddressReads: Reads[CompleteResponseAddress] = Json.reads[CompleteResponseAddress]

  implicit val completeResponseWrites: OWrites[PersonalCompleteResponse] = Json.writes[PersonalCompleteResponse]
  implicit val completeResponseReads: Reads[PersonalCompleteResponse] = Json.reads[PersonalCompleteResponse]
}

case class BusinessCompleteResponse(address: Option[CompleteResponseAddress],
                                    companyName: String,
                                    companyRegistrationNumber: Option[String],
                                    sortCode: String,
                                    accountNumber: String,
                                    rollNumber: Option[String] = None,
                                    accountNumberWithSortCodeIsValid: ReputationResponseEnum,
                                    accountExists: Option[ReputationResponseEnum] = None,
                                    companyNameMatches: Option[ReputationResponseEnum],
                                    companyPostCodeMatches: Option[ReputationResponseEnum],
                                    companyRegistrationNumberMatches: Option[ReputationResponseEnum],
                                    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None)

object BusinessCompleteResponse {
  implicit val completeResponseAddressWrites: OWrites[CompleteResponseAddress] = Json.writes[CompleteResponseAddress]
  implicit val completeResponseAddressReads: Reads[CompleteResponseAddress] = Json.reads[CompleteResponseAddress]

  implicit val completeResponseWrites: OWrites[BusinessCompleteResponse] = Json.writes[BusinessCompleteResponse]
  implicit val completeResponseReads: Reads[BusinessCompleteResponse] = Json.reads[BusinessCompleteResponse]
}
