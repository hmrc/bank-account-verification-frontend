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

import bankaccountverification.web.VerificationRequest
import play.api.libs.json.{Json, OFormat}

sealed trait ReputationResponseEnum

object ReputationResponseEnum extends Enumerable.Implicits {

  case object Yes extends WithName("yes") with ReputationResponseEnum
  case object No extends WithName("no") with ReputationResponseEnum
  case object Indeterminate extends WithName("indeterminate") with ReputationResponseEnum
  case object Inapplicable extends WithName("inapplicable") with ReputationResponseEnum
  case object Error extends WithName("error") with ReputationResponseEnum

  val values: Seq[ReputationResponseEnum] = Seq(Yes, No, Indeterminate, Inapplicable, Error)

  implicit val enumerable: Enumerable[ReputationResponseEnum] =
    Enumerable(values.map(v => v.toString -> v): _*)
}

case class BarsValidationResponse(
  accountNumberWithSortCodeIsValid: ReputationResponseEnum,
  nonStandardAccountDetailsRequiredForBacs: ReputationResponseEnum,
  supportsBACS: Option[ReputationResponseEnum]
)

object BarsValidationResponse {
  implicit val format: OFormat[BarsValidationResponse] =
    Json.format[BarsValidationResponse]
}

case class BarsValidationRequestAccount(sortCode: String, accountNumber: String)
object BarsValidationRequestAccount {
  implicit val bankAccountReputationValidationRequestAccountReads =
    Json.reads[BarsValidationRequestAccount]
  implicit val bankAccountReputationValidationRequestAccountWrites =
    Json.writes[BarsValidationRequestAccount]
}

case class BarsValidationRequest(account: BarsValidationRequestAccount)
object BarsValidationRequest {
  import BarsValidationRequestAccount._

  def apply(sortCode: String, accountNumber: String): BarsValidationRequest =
    BarsValidationRequest(BarsValidationRequestAccount(sortCode, accountNumber))

  implicit val bankAccountReputationValidationRequestReads  = Json.reads[BarsValidationRequest]
  implicit val bankAccountReputationValidationRequestWrites = Json.writes[BarsValidationRequest]
}
