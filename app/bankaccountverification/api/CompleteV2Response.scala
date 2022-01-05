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

case class CompleteV2Response(accountType: AccountTypeRequestEnum, personal: Option[PersonalCompleteV2Response],
                            business: Option[BusinessCompleteV2Response])

object CompleteV2Response {
  implicit val completeResponseWrites: OWrites[CompleteV2Response] = Json.writes[CompleteV2Response]
  implicit val completeResponseReads: Reads[CompleteV2Response] = Json.reads[CompleteV2Response]
}

case class PersonalCompleteV2Response(accountName: String,
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
                                      iban: Option[String] = None)

object PersonalCompleteV2Response {
  implicit val completeResponseWrites: OWrites[PersonalCompleteV2Response] = Json.writes[PersonalCompleteV2Response]
  implicit val completeResponseReads: Reads[PersonalCompleteV2Response] = Json.reads[PersonalCompleteV2Response]
}

case class BusinessCompleteV2Response(companyName: String,
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
                                      iban: Option[String] = None)

object BusinessCompleteV2Response {
  implicit val completeResponseWrites: OWrites[BusinessCompleteV2Response] = Json.writes[BusinessCompleteV2Response]
  implicit val completeResponseReads: Reads[BusinessCompleteV2Response] = Json.reads[BusinessCompleteV2Response]
}
