/*
 * Copyright 2022 HM Revenue & Customs
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

case class CompleteV3Response(accountType: AccountTypeRequestEnum, personal: Option[PersonalCompleteV3Response],
                            business: Option[BusinessCompleteV3Response])

object CompleteV3Response {
  implicit val completeResponseWrites: OWrites[CompleteV3Response] = Json.writes[CompleteV3Response]
  implicit val completeResponseReads: Reads[CompleteV3Response] = Json.reads[CompleteV3Response]
}

case class PersonalCompleteV3Response(accountName: String,
                                      sortCode: String,
                                      accountNumber: String,
                                      accountNumberIsWellFormatted: ReputationResponseEnum,
                                      rollNumber: Option[String] = None,
                                      accountExists: Option[ReputationResponseEnum] = None,
                                      nameMatches: Option[ReputationResponseEnum] = None,
                                      nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
                                      sortCodeBankName: Option[String] = None,
                                      sortCodeSupportsDirectDebit: Option[ReputationResponseEnum] = None,
                                      sortCodeSupportsDirectCredit: Option[ReputationResponseEnum] = None,
                                      iban: Option[String] = None,
                                      matchedAccountName: Option[String] = None)

object PersonalCompleteV3Response {
  implicit val completeResponseWrites: OWrites[PersonalCompleteV3Response] = Json.writes[PersonalCompleteV3Response]
  implicit val completeResponseReads: Reads[PersonalCompleteV3Response] = Json.reads[PersonalCompleteV3Response]
}

case class BusinessCompleteV3Response(companyName: String,
                                      sortCode: String,
                                      accountNumber: String,
                                      rollNumber: Option[String] = None,
                                      accountNumberIsWellFormatted: ReputationResponseEnum,
                                      accountExists: Option[ReputationResponseEnum] = None,
                                      nameMatches: Option[ReputationResponseEnum],
                                      nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
                                      sortCodeBankName: Option[String] = None,
                                      sortCodeSupportsDirectDebit: Option[ReputationResponseEnum] = None,
                                      sortCodeSupportsDirectCredit: Option[ReputationResponseEnum] = None,
                                      iban: Option[String] = None,
                                      matchedAccountName: Option[String] = None)

object BusinessCompleteV3Response {
  implicit val completeResponseWrites: OWrites[BusinessCompleteV3Response] = Json.writes[BusinessCompleteV3Response]
  implicit val completeResponseReads: Reads[BusinessCompleteV3Response] = Json.reads[BusinessCompleteV3Response]
}
