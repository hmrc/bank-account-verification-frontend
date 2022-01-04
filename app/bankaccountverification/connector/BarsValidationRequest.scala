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

package bankaccountverification.connector

import play.api.libs.json.Json

case class BarsValidationRequest(account: BarsValidationRequestAccount)

case class BarsValidationRequestAccount(sortCode: String, accountNumber: String)
object BarsValidationRequestAccount {
  implicit val bankAccountReputationValidationRequestAccountReads =
    Json.reads[BarsValidationRequestAccount]
  implicit val bankAccountReputationValidationRequestAccountWrites =
    Json.writes[BarsValidationRequestAccount]
}

object BarsValidationRequest {
  import BarsValidationRequestAccount._

  def apply(sortCode: String, accountNumber: String): BarsValidationRequest =
    BarsValidationRequest(BarsValidationRequestAccount(sortCode, accountNumber))

  implicit val bankAccountReputationValidationRequestReads  = Json.reads[BarsValidationRequest]
  implicit val bankAccountReputationValidationRequestWrites = Json.writes[BarsValidationRequest]
}
