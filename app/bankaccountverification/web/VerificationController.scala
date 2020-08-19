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

import bankaccountverification.web.html.{ErrorTemplate, JourneyStart}
import bankaccountverification.{AppConfig, JourneyRepository, RemoteMessagesApiProvider}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class VerificationController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  remoteMessagesApiProvider: RemoteMessagesApiProvider,
  startView: JourneyStart,
  errorTemplate: ErrorTemplate,
  journeyRepository: JourneyRepository,
  verificationService: VerificationService
) extends FrontendController(mcc) {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def start(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          for {
            journey <- journeyRepository.findById(id)
          } yield journey match {
            case Some(j) =>
              val messageResponse             = j.messages.map(_("en").as[Map[String, String]]).getOrElse(Map())
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(messageResponse)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)

              val headerBlock        = j.headerHtml.map(html => Html(html))
              val beforeContentBlock = j.beforeContentHtml.map(html => Html(html))
              val footerBlock        = j.footerHtml.map(html => Html(html))

              Ok(
                startView(
                  journeyId,
                  j.serviceIdentifier,
                  VerificationRequest.form,
                  headerBlock,
                  beforeContentBlock,
                  footerBlock
                )
              )
            case None => NotFound(journeyIdError)
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
          journeyRepository.findById(id).flatMap {
            case Some(j) =>
              val messageResponse             = j.messages.map(_("en").as[Map[String, String]]).getOrElse(Map())
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(messageResponse)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)

              val headerBlock        = j.headerHtml.map(html => Html(html))
              val beforeContentBlock = j.beforeContentHtml.map(html => Html(html))
              val footerBlock        = j.footerHtml.map(html => Html(html))

              val form = VerificationRequest.form.bindFromRequest()
              (if (!form.hasErrors) verificationService.verify(id, form)
               else Future.successful(form)) map {
                case form if form.hasErrors =>
                  BadRequest(
                    startView(journeyId, j.serviceIdentifier, form, headerBlock, beforeContentBlock, footerBlock)
                  )
                case _ => SeeOther(s"${j.continueUrl}/$journeyId")
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
