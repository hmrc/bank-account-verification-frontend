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

import play.api.mvc.{MessagesBaseController, MessagesControllerComponents, RequestHeader}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.{Utf8MimeTypes, WithJsonBody}

trait FrontendApiBaseController
  extends MessagesBaseController
    with Utf8MimeTypes
    with WithJsonBody
    with FrontendApiHeaderCarrierProvider

abstract class FrontendApiController(override val controllerComponents: MessagesControllerComponents)
  extends FrontendApiBaseController

trait FrontendApiHeaderCarrierProvider {
  implicit protected def hc(implicit request: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSessionAndRequest(request.headers, None, Some(request))
}
