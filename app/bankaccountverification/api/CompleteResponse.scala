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
import play.api.libs.json.Json

case class CompleteResponse(
  accountType: String,
  accountName: String,
  sortCode: String,
  accountNumber: String,
  accountNumberWithSortCodeIsValid: ReputationResponseEnum,
  rollNumber: Option[String] = None,
  accountExists: Option[ReputationResponseEnum] = None,
  nameMatches: Option[ReputationResponseEnum] = None,
  nonConsented: Option[ReputationResponseEnum] = None,
  subjectHasDeceased: Option[ReputationResponseEnum] = None,
  nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None
)

object CompleteResponse {
  implicit val completeResponseWrites = Json.writes[CompleteResponse]
  implicit val completeResponseReads  = Json.reads[CompleteResponse]
}
