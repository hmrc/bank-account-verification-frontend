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
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.web.view.html.{AccountTypeView, ErrorTemplate}
import bankaccountverification.web.{AccountTypeRequest, VerificationService}
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
class AccountTypeController @Inject() (
  val appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  remoteMessagesApiProvider: RemoteMessagesApiProvider,
  accountTypeView: AccountTypeView,
  val errorTemplate: ErrorTemplate,
  journeyRepository: JourneyRepository,
  verificationService: VerificationService,
  val partialsConnector: PartialsConnector
) extends FrontendController(mcc)
    with ControllerHelper {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def getAccountType(journeyId: String): Action[AnyContent] =
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
                    accountTypeView(
                      journeyId,
                      journey.serviceIdentifier,
                      welshTranslationsAvailable,
                      AccountTypeRequest.form,
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

  def postAccountType(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository.findById(id) flatMap {
            case Some(journey) =>
              val remoteMessagesApi           = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
              implicit val messages: Messages = remoteMessagesApi.preferred(request)
              val welshTranslationsAvailable  = journey.messages.exists(_.keys.contains("cy"))

              val form = AccountTypeRequest.form.bindFromRequest()
              if (!form.hasErrors)
                verificationService.setAccountType(id, form.value.get.accountType) map { _ =>
                  form.value.get.accountType match { //At this stage the account type has to be here otherwise the rest of the flow will not work!
                    case Personal => Redirect(routes.PersonalVerificationController.getAccountDetails(journeyId))
                    case Business => Redirect(routes.BusinessVerificationController.getAccountDetails(journeyId))
                  }
                }
              else
                getCustomisations(journey) map {
                  case (headerBlock, beforeContentBlock, footerBlock) =>
                    BadRequest(
                      accountTypeView(
                        journeyId,
                        appConfig.contactFormServiceIdentifier,
                        welshTranslationsAvailable,
                        form,
                        headerBlock,
                        beforeContentBlock,
                        footerBlock
                      )
                    )
                }
            case None => Future.successful(NotFound(journeyIdError))
          }
        case Failure(_) =>
          Future.successful(BadRequest)
      }
    }
}
