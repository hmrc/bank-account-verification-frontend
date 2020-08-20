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
import bankaccountverification.{AppConfig, Journey, JourneyRepository, RemoteMessagesApiProvider}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import play.twirl.api.Html
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
  remoteMessagesApiProvider: RemoteMessagesApiProvider,
  startView: JourneyStart,
  errorTemplate: ErrorTemplate,
  journeyRepository: JourneyRepository,
  verificationService: VerificationService,
  partialsConnector: PartialsConnector
) extends FrontendController(mcc) {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def start(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository.findById(id) flatMap {
            case Some(journey) =>
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)

              getCustomisations(journey) map {
                case (headerBlock, beforeContentBlock, footerBlock) =>
                  Ok(
                    startView(
                      journeyId,
                      journey.serviceIdentifier,
                      VerificationRequest.form,
                      headerBlock,
                      beforeContentBlock,
                      footerBlock
                    )
                  )

              }
            case None => Future.successful(NotFound(journeyIdError))
          }
        case Failure(exception) =>
          Future.successful(BadRequest)
      }
    }

  def verifyDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository.findById(id) flatMap {
            case Some(journey) =>
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)

              getCustomisations(journey) flatMap {
                case (headerBlock, beforeContentBlock, footerBlock) =>
                  val form = VerificationRequest.form.bindFromRequest()
                  (if (!form.hasErrors) verificationService.verify(id, form)
                   else Future.successful(form)) map {
                    case form if form.hasErrors =>
                      BadRequest(
                        startView(
                          journeyId,
                          journey.serviceIdentifier,
                          form,
                          headerBlock,
                          beforeContentBlock,
                          footerBlock
                        )
                      )
                    case _ => SeeOther(s"${journey.continueUrl}/$journeyId")
                  }
              }
            case None => Future.successful(NotFound(journeyIdError))
          }
        case Failure(exception) =>
          Future.successful(BadRequest)
      }
    }

  private def getCustomisations(
    journey: Journey
  )(implicit request: Request[_], messages: Messages): Future[(Option[Html], Option[Html], Option[Html])] =
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

  private def journeyIdError(implicit request: Request[_], messages: Messages) =
    errorTemplate(
      messages("error.journeyId.pageTitle"),
      messages("error.journeyId.heading"),
      messages("error.journeyId.message")
    )(request, messages, appConfig)
}
