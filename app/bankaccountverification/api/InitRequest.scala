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

import play.api.libs.json.{JsObject, Json, OWrites}

case class InitRequest(
  serviceIdentifier: String,
  continueUrl: String,
  messages: Option[InitRequestMessages] = None,
  customisationsUrl: Option[String] = None
)

case class InitRequestMessages(en: JsObject, cy: Option[JsObject])

object InitRequest {
  implicit val messagesReads                                = Json.reads[InitRequestMessages]
  implicit val messagesWrites: OWrites[InitRequestMessages] = Json.writes[InitRequestMessages]

  implicit val writes = Json.writes[InitRequest]
  implicit val reads  = Json.reads[InitRequest]
}
