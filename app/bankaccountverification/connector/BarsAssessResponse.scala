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

package bankaccountverification.connector

import play.api.libs.json.{Json, Reads, Writes}

sealed trait BarsPersonalAssessResponse {}

case class BarsPersonalAssessSuccessResponse(accountNumberIsWellFormatted: ReputationResponseEnum, accountExists: ReputationResponseEnum, nameMatches: ReputationResponseEnum, sortCodeIsPresentOnEISCD: ReputationResponseEnum, sortCodeSupportsDirectDebit: ReputationResponseEnum, sortCodeSupportsDirectCredit: ReputationResponseEnum, nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum], sortCodeBankName: Option[String], iban: Option[String]) extends BarsPersonalAssessResponse

case class BarsPersonalAssessBadRequestResponse(code: String, desc: String) extends BarsPersonalAssessResponse

case class BarsPersonalAssessErrorResponse() extends BarsPersonalAssessResponse

object BarsPersonalAssessResponse {
  implicit val reads: Reads[BarsPersonalAssessSuccessResponse] = Json.reads[BarsPersonalAssessSuccessResponse]
  implicit val writes: Writes[BarsPersonalAssessSuccessResponse] = Json.writes[BarsPersonalAssessSuccessResponse]

  implicit val badrequestReads: Reads[BarsPersonalAssessBadRequestResponse] = Json.reads[BarsPersonalAssessBadRequestResponse]
}

sealed trait BarsBusinessAssessResponse {}

case class BarsBusinessAssessSuccessResponse(accountNumberIsWellFormatted: ReputationResponseEnum, sortCodeIsPresentOnEISCD: ReputationResponseEnum, sortCodeBankName: Option[String], accountExists: ReputationResponseEnum, nameMatches: ReputationResponseEnum, sortCodeSupportsDirectDebit: ReputationResponseEnum, sortCodeSupportsDirectCredit: ReputationResponseEnum, nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum], iban: Option[String]) extends BarsBusinessAssessResponse

case class BarsBusinessAssessBadRequestResponse(code: String, desc: String) extends BarsBusinessAssessResponse

case class BarsBusinessAssessErrorResponse() extends BarsBusinessAssessResponse

object BarsBusinessAssessResponse {
  implicit val reads: Reads[BarsBusinessAssessSuccessResponse] = Json.reads[BarsBusinessAssessSuccessResponse]
  implicit val writes: Writes[BarsBusinessAssessSuccessResponse] = Json.writes[BarsBusinessAssessSuccessResponse]

  implicit val badrequestReads: Reads[BarsBusinessAssessBadRequestResponse] = Json.reads[BarsBusinessAssessBadRequestResponse]
}
