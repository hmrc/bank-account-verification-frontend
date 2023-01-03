/*
 * Copyright 2023 HM Revenue & Customs
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

package bankaccountverification

import play.api.libs.json.{JsObject, JsSuccess, Reads}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, SimpleRetrieval}

object AuthProviderId {
  val reads: Reads[String] = Reads[String] { json =>
    JsSuccess(json.as[JsObject].fields.head._2.as[String])
  }

  val retrieval: Retrieval[String] = SimpleRetrieval("authProviderId", reads)
}
