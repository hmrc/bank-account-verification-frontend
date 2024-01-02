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

package bankaccountverification.web.personal

import bankaccountverification.connector.BarsPersonalAssessSuccessResponse
import bankaccountverification.connector.ReputationResponseEnum.Yes
import bankaccountverification.web.personal.html.{PersonalAccountDetailsView, PersonalAccountExistsIndeterminate}
import bankaccountverification.web.{ActionWithCustomisationsProvider, VerificationService}
import bankaccountverification.{AppConfig, Journey, RemoteMessagesApiProvider}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}


@Singleton
class PersonalVerificationController @Inject()(val appConfig: AppConfig, mcc: MessagesControllerComponents,
                                               remoteMessagesApiProvider: RemoteMessagesApiProvider,
                                               accountDetailsView: PersonalAccountDetailsView,
                                               accountExistsIndeterminate: PersonalAccountExistsIndeterminate,
                                               verificationService: VerificationService,
                                               withCustomisations: ActionWithCustomisationsProvider,
                                               auditConnector: AuditConnector
                                              )(implicit ec: ExecutionContext) extends FrontendController(mcc) {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def getAccountDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      if (journey.data.accountType.isDefined) {
        val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
        implicit val messages: Messages = remoteMessagesApi.preferred(request)
        val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))

        val personalVerificationForm = journey.data.personal
          .map(ps => PersonalVerificationRequest(ps.accountName.getOrElse(""), ps.sortCode.getOrElse(""),
            ps.accountNumber.getOrElse(""), ps.rollNumber))
          .map(PersonalVerificationRequest.form.fill)
          .getOrElse(PersonalVerificationRequest.form)

        Future.successful(Ok(accountDetailsView(
          journeyId, journey.serviceIdentifier, welshTranslationsAvailable, personalVerificationForm)))
      }
      else
        Future.successful(Redirect(bankaccountverification.web.routes.AccountTypeController.getAccountType(journeyId)))
    }

  def postAccountDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))
      val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
      implicit val messages: Messages = remoteMessagesApi.preferred(request)

      val form = PersonalVerificationRequest.form.bindFromRequest()
      val callCount = request.session.get(Journey.callCountSessionKey)
        .flatMap(s => Try(s.toInt).toOption).getOrElse(0) + 1

      val dataEvent = DataEvent(
        appConfig.appName,
        "AccountDetailsEntered",
        detail = Map(
          "accountType" -> "personal",
          "trueCallingService" -> journey.serviceIdentifier,
          "callCount" -> callCount.toString,
          "maxCallCount" -> journey.maxCallCount.map(_.toString).getOrElse(""),
          "journeyId" -> journey.id.toHexString)
          ++ form.data.view.filterKeys {
          case "csrfToken" | "continue" => false
          case _ => true
        })

      auditConnector.sendEvent(dataEvent)

      if (form.hasErrors)
        Future.successful(BadRequest(accountDetailsView(
          journeyId, journey.serviceIdentifier, welshTranslationsAvailable, form)))
      else {
        val verificationRequestFromForm = PersonalVerificationRequest.convertNameToASCII(form.get)
        for {
          response <- verificationService.assessPersonal(verificationRequestFromForm,
            journey.data.address, journey.serviceIdentifier)
          updatedForm <- verificationService.processPersonalAssessResponse(journey.id,
            journey.getBACSRequirements, response, form)
        } yield
          updatedForm match {
            case uform if uform.hasErrors =>
              journey.maxCallCount match {
                case Some(max) if callCount == max =>
                  SeeOther(s"${journey.maxCallCountRedirectUrl.get}/$journeyId")
                case _ =>
                  BadRequest(accountDetailsView(journeyId, journey.serviceIdentifier, welshTranslationsAvailable, uform))
                    .withSession(request.session + (Journey.callCountSessionKey -> callCount.toString))
              }
            case _ =>
              response match {
                case Success(success: BarsPersonalAssessSuccessResponse) if success.accountExists == Yes =>
                  SeeOther(s"${journey.continueUrl}/$journeyId")
                case _ =>
                  journey.maxCallCount match {
                    case Some(x) if callCount == x =>
                      SeeOther(s"${journey.maxCallCountRedirectUrl.get}/$journeyId")
                    case _ =>
                      Redirect(routes.PersonalVerificationController.getConfirmDetails(journeyId))
                        .withSession(request.session + (Journey.callCountSessionKey -> callCount.toString))
                  }
              }
          }
      }
    }

  def getConfirmDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      if (journey.data.personal.isDefined) {
        val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))
        val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
        implicit val messages: Messages = remoteMessagesApi.preferred(request)

        Future.successful(Ok(accountExistsIndeterminate(
          PersonalAccountExistsIndeterminateViewModel(journeyId, journey.data.personal.get, journey.serviceIdentifier,
            s"${journey.continueUrl}/$journeyId", welshTranslationsAvailable))))
      }
      else Future.successful(NotFound(withCustomisations.journeyIdError(request)))
    }
}
