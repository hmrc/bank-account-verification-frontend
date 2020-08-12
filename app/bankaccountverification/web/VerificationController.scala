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

package bankaccountverification.web

import bankaccountverification.connector.PartialsConnector
import bankaccountverification.web.html.{ErrorTemplate, JourneyStart}
import bankaccountverification.{AppConfig, SessionDataRepository}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class VerificationController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  startView: JourneyStart,
  errorTemplate: ErrorTemplate,
  sessionRepository: SessionDataRepository,
  verificationService: VerificationService,
  partialsConnector: PartialsConnector
) extends FrontendController(mcc) {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def start(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          for {
            header            <- partialsConnector.header()
            footer            <- partialsConnector.footer()
            remoteMessagesApi <- partialsConnector.messages()
            session           <- sessionRepository.findById(id)
          } yield {

            val headerBlock = header match {
              case HtmlPartial.Success(_, content) => Some(content)
              case _                               => None
            }

            val footerBlock = footer match {
              case HtmlPartial.Success(_, content) => Some(content)
              case _                               => None
            }

            implicit val messages: Messages = remoteMessagesApi.preferred(request)

            session match {
              case Some(_) => Ok(startView(journeyId, VerificationRequest.form, headerBlock, footerBlock))
              case None    => NotFound(journeyIdError)
            }
          }
        case Failure(exception) =>
          Future.successful(BadRequest)
      }
    }

  def verifyDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val messages: Messages = messagesApi.preferred(request)

      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          sessionRepository.findById(id).flatMap {
            case Some(_) =>
              val form = VerificationRequest.form.bindFromRequest()
              (if (!form.hasErrors) verificationService.verify(id, form)
               else Future.successful(form)) map {
                case form if form.hasErrors => BadRequest(startView(journeyId, form))
                case _                      => SeeOther(config.mtdContinueUrl)
              }

            case None => Future.successful(NotFound(journeyIdError))
          }
        case Failure(_) =>
          Future.successful(BadRequest)
      }
    }

  private def journeyIdError(implicit request: Request[_], messages: Messages) =
    errorTemplate(
      messages("error.journeyId.pageTitle"),
      messages("error.journeyId.heading"),
      messages("error.journeyId.message")
    )(request, messages, appConfig)
}
