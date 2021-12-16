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

package bankaccountverification.api

import bankaccountverification.web.AccountTypeRequestEnum
import play.api.libs.json.{JsObject, Json, OWrites, Reads}

case class InitRequestTimeoutConfig(timeoutUrl: String, timeoutAmount: Int, timeoutKeepAliveUrl: Option[String])

case class InitBACSRequirements(directDebitRequired: Boolean, directCreditRequired: Boolean)

case class InitRequest(serviceIdentifier: String,
                       continueUrl: String,
                       prepopulatedData: Option[InitRequestPrepopulatedData] = None,
                       address: Option[InitRequestAddress] = None,
                       messages: Option[InitRequestMessages] = None,
                       customisationsUrl: Option[String] = None,
                       bacsRequirements: Option[InitBACSRequirements] = None,
                       timeoutConfig: Option[InitRequestTimeoutConfig] = None,
                       signOutUrl: Option[String] = None)

case class InitRequestPrepopulatedData(accountType: AccountTypeRequestEnum,
                                       name: Option[String] = None,
                                       sortCode: Option[String] = None,
                                       accountNumber: Option[String] = None,
                                       rollNumber: Option[String] = None)

case class InitRequestAddress(lines: List[String], town: Option[String], postcode: Option[String])

case class InitRequestMessages(en: JsObject, cy: Option[JsObject])

object InitRequest {
  implicit val prepopulatedDataReads: Reads[InitRequestPrepopulatedData] = Json.reads[InitRequestPrepopulatedData]
  implicit val prepopulatedDataWrites: OWrites[InitRequestPrepopulatedData] = Json.writes[InitRequestPrepopulatedData]

  implicit val messagesReads: Reads[InitRequestMessages] = Json.reads[InitRequestMessages]
  implicit val messagesWrites: OWrites[InitRequestMessages] = Json.writes[InitRequestMessages]

  implicit val addressReads: Reads[InitRequestAddress] = Json.reads[InitRequestAddress]
  implicit val addressWrites: OWrites[InitRequestAddress] = Json.writes[InitRequestAddress]

  implicit val timeoutConfigReads: Reads[InitRequestTimeoutConfig] = Json.reads[InitRequestTimeoutConfig]
  implicit val timeoutConfigWrites: OWrites[InitRequestTimeoutConfig] = Json.writes[InitRequestTimeoutConfig]

  implicit val directDebitConstraintsReads: Reads[InitBACSRequirements] = Json.reads[InitBACSRequirements]
  implicit val directDebitConstraintsWrites: OWrites[InitBACSRequirements] = Json.writes[InitBACSRequirements]

  implicit val writes: OWrites[InitRequest] = Json.writes[InitRequest]
  implicit val reads: Reads[InitRequest] = Json.reads[InitRequest]
}
