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

package bankaccountverification.web.controller

import bankaccountverification.{AppConfig, Journey}
import bankaccountverification.connector.PartialsConnector
import bankaccountverification.web.view.html.ErrorTemplate
import play.api.i18n.Messages
import play.api.mvc.Request
import play.twirl.api.Html
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.{ExecutionContext, Future}

trait ControllerHelper {
  def partialsConnector: PartialsConnector
  def errorTemplate: ErrorTemplate
  def appConfig: AppConfig

  private[controller] def getCustomisations(
    journey: Journey
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[_],
    messages: Messages
  ): Future[(Option[Html], Option[Html], Option[Html])] =
    for {
      header        <- partialsConnector.header(journey.customisationsUrl)
      beforeContent <- partialsConnector.beforeContent(journey.customisationsUrl)
      footer        <- partialsConnector.footer(journey.customisationsUrl)
    } yield {

      val headerBlock = header match {
        case HtmlPartial.Success(_, content) => Some(content)
        case _                               => None
      }

      val beforeContentBlock = beforeContent match {
        case HtmlPartial.Success(_, content) => Some(content)
        case _                               => None
      }

      val footerBlock = footer match {
        case HtmlPartial.Success(_, content) => Some(content)
        case _                               => None
      }

      (headerBlock, beforeContentBlock, footerBlock)
    }

  private[controller] def journeyIdError(implicit request: Request[_], messages: Messages) =
    errorTemplate(
      messages("error.journeyId.pageTitle"),
      messages("error.journeyId.heading"),
      messages("error.journeyId.message")
    )(request, messages, appConfig)

}
