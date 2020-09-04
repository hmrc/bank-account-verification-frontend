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

package bankaccountverification.connector

import play.api.libs.json.{Json, OFormat, Reads, Writes}

case class BarsPersonalAssessResponse(
  accountNumberWithSortCodeIsValid: ReputationResponseEnum,
  accountExists: ReputationResponseEnum,
  nameMatches: ReputationResponseEnum,
  addressMatches: ReputationResponseEnum,
  nonConsented: ReputationResponseEnum,
  subjectHasDeceased: ReputationResponseEnum,
  nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum]
)

object BarsPersonalAssessResponse {
  implicit val reads: Reads[BarsPersonalAssessResponse]   = Json.reads[BarsPersonalAssessResponse]
  implicit val writes: Writes[BarsPersonalAssessResponse] = Json.writes[BarsPersonalAssessResponse]
}

case class BarsBusinessAssessResponse(
  accountNumberWithSortCodeIsValid: ReputationResponseEnum,
  sortCodeIsPresentOnEISCD: ReputationResponseEnum,
  sortCodeBankName: Option[String],
  accountExists: ReputationResponseEnum,
  companyNameMatches: ReputationResponseEnum,
  companyPostCodeMatches: ReputationResponseEnum,
  companyRegistrationNumberMatches: ReputationResponseEnum,
  nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum]
)

object BarsBusinessAssessResponse {
  implicit val reads: Reads[BarsBusinessAssessResponse]   = Json.reads[BarsBusinessAssessResponse]
  implicit val writes: Writes[BarsBusinessAssessResponse] = Json.writes[BarsBusinessAssessResponse]
}
