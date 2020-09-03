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

import bankaccountverification.connector.PartialsConnector
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, Yes}
import bankaccountverification.web.view.html.{AccountExistsIndeterminate, ErrorTemplate, PersonalAccountDetailsView}
import bankaccountverification.web.{PersonalVerificationRequest, VerificationService}
import bankaccountverification.{AppConfig, JourneyRepository, RemoteMessagesApiProvider}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class PersonalVerificationController @Inject() (
  val appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  remoteMessagesApiProvider: RemoteMessagesApiProvider,
  accountDetailsView: PersonalAccountDetailsView,
  accountExistsIndeterminate: AccountExistsIndeterminate,
  val errorTemplate: ErrorTemplate,
  journeyRepository: JourneyRepository,
  verificationService: VerificationService,
  val partialsConnector: PartialsConnector
) extends FrontendController(mcc)
    with ControllerHelper {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def getAccountDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository.findById(id) flatMap {
            case Some(journey) =>
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)
              val welshTranslationsAvailable  = journey.messages.exists(_.keys.contains("cy"))

              getCustomisations(journey) map {
                case (headerBlock, beforeContentBlock, footerBlock) =>
                  Ok(
                    accountDetailsView(
                      journeyId,
                      journey.serviceIdentifier,
                      welshTranslationsAvailable,
                      PersonalVerificationRequest.form,
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

  def postAccountDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository.findById(id) flatMap {
            case Some(journey) =>
              val accountType = journey.data
                .flatMap(_.accountType)
                .getOrElse(throw new IllegalStateException("??? accountType is missing"))

              val welshTranslationsAvailable  = journey.messages.exists(_.keys.contains("cy"))
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)

              getCustomisations(journey) flatMap {
                case (headerBlock, beforeContentBlock, footerBlock) =>
                  val form = PersonalVerificationRequest.form.bindFromRequest()
                  if (form.hasErrors)
                    Future.successful(
                      BadRequest(
                        accountDetailsView(
                          journeyId,
                          journey.serviceIdentifier,
                          welshTranslationsAvailable,
                          form,
                          headerBlock,
                          beforeContentBlock,
                          footerBlock
                        )
                      )
                    )
                  else
                    for {
                      response    <- verificationService.assessPersonal(form.get)
                      updatedForm <- verificationService.processAssessResponse(id, response, form)
                    } yield updatedForm match {
                      case uform if uform.hasErrors =>
                        BadRequest(
                          accountDetailsView(
                            journeyId,
                            journey.serviceIdentifier,
                            welshTranslationsAvailable,
                            uform,
                            headerBlock,
                            beforeContentBlock,
                            footerBlock
                          )
                        )
                      case _ =>
                        if (response.isFailure || response.get.accountExists == Yes)
                          SeeOther(s"${journey.continueUrl}/$journeyId")
                        else
                          Redirect(routes.PersonalVerificationController.getConfirmDetails(journeyId))
                    }
              }
            case None => Future.successful(NotFound(journeyIdError))
          }
        case Failure(exception) =>
          Future.successful(BadRequest)
      }
    }

  def getConfirmDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository.findById(id) flatMap {
            case Some(journey) =>
              val welshTranslationsAvailable  = journey.messages.exists(_.keys.contains("cy"))
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)

              getCustomisations(journey) map {
                case (headerBlock, beforeContentBlock, footerBlock) =>
                  Ok(
                    accountExistsIndeterminate(
                      id.stringify,
                      journey.data.get.personal.get,
                      journey.serviceIdentifier,
                      welshTranslationsAvailable,
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
}
