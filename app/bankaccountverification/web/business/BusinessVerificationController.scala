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

package bankaccountverification.web.business

import bankaccountverification.connector.BarsBusinessAssessSuccessResponse
import bankaccountverification.connector.ReputationResponseEnum.Yes
import bankaccountverification.web.business.html.{BusinessAccountDetailsView, BusinessAccountExistsIndeterminate}
import bankaccountverification.web.{ActionWithCustomisationsProvider, VerificationService}
import bankaccountverification.{AppConfig, BACSRequirements, Journey, RemoteMessagesApiProvider}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

@Singleton
class BusinessVerificationController @Inject()(val appConfig: AppConfig,
                                               mcc: MessagesControllerComponents,
                                               remoteMessagesApiProvider: RemoteMessagesApiProvider,
                                               businessAccountDetailsView: BusinessAccountDetailsView,
                                               businessAccountExistsIndeterminate: BusinessAccountExistsIndeterminate,
                                               verificationService: VerificationService,
                                               withCustomisations: ActionWithCustomisationsProvider,
                                               auditConnector: AuditConnector
                                              ) extends FrontendController(mcc) {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def getAccountDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      if (journey.data.accountType.isDefined) {
        val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
        implicit val messages: Messages = remoteMessagesApi.preferred(request)
        val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))

        val businessVerificationForm = journey.data.business
          .map(bs => BusinessVerificationRequest(bs.companyName.getOrElse(""), bs.sortCode.getOrElse(""),
            bs.accountNumber.getOrElse(""), bs.rollNumber))
          .map(BusinessVerificationRequest.form.fill)
          .getOrElse(BusinessVerificationRequest.form)

        Future.successful(Ok(businessAccountDetailsView(journeyId, journey.serviceIdentifier, welshTranslationsAvailable,
          businessVerificationForm)))
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

      val form = BusinessVerificationRequest.form.bindFromRequest()
      val callCount = request.session.get(Journey.callCountSessionKey)
        .flatMap(s => Try(s.toInt).toOption).getOrElse(0) + 1

      val dataEvent = DataEvent(
        appConfig.appName,
        "AccountDetailsEntered",
        detail = Map(
          "accountType" -> "business",
          "trueCallingService" -> journey.serviceIdentifier,
          "callCount" -> callCount.toString,
          "maxCallCount" -> journey.maxCallCount.map(_.toString).getOrElse(""),
          "journeyId" -> journey.id.toHexString)
          ++ form.data.filterKeys {
          case "csrfToken" | "continue" => false
          case _ => true
        })

      auditConnector.sendEvent(dataEvent)

      if (form.hasErrors)
        Future.successful(BadRequest(businessAccountDetailsView(
          journeyId, journey.serviceIdentifier, welshTranslationsAvailable, form)))
      else
        for {
          response <- verificationService.assessBusiness(form.get, journey.data.address, journey.serviceIdentifier)
          updatedForm <- verificationService.processBusinessAssessResponse(journey.id,
            journey.bacsRequirements.getOrElse(BACSRequirements.defaultBACSRequirements), response, form)
        } yield
          updatedForm match {
            case uform if uform.hasErrors =>
              journey.maxCallCount match {
                case Some(max) if callCount == max =>
                  SeeOther(s"${journey.maxCallCountRedirectUrl.get}/$journeyId")
                case _ =>
                  BadRequest(businessAccountDetailsView(journeyId, journey.serviceIdentifier,
                    welshTranslationsAvailable, uform)).withSession(request.session + (Journey.callCountSessionKey -> callCount.toString))
              }
            case _ =>
              response match {
                case Success(success: BarsBusinessAssessSuccessResponse) if success.accountExists == Yes =>
                  SeeOther(s"${journey.continueUrl}/$journeyId")
                    .withSession(request.session + (Journey.callCountSessionKey -> callCount.toString))
                case _ =>
                  journey.maxCallCount match {
                    case Some(x) if callCount == x =>
                      SeeOther(s"${journey.maxCallCountRedirectUrl.get}/$journeyId")
                    case _ =>
                      Redirect(routes.BusinessVerificationController.getConfirmDetails(journeyId))
                        .withSession(request.session + (Journey.callCountSessionKey -> callCount.toString))
                  }
              }

          }
    }

  def getConfirmDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      if (journey.data.business.isDefined) {
        val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))
        val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
        implicit val messages: Messages = remoteMessagesApi.preferred(request)

        Future.successful(Ok(
          businessAccountExistsIndeterminate(
            BusinessAccountExistsIndeterminateViewModel(journeyId, journey.data.business.get, journey.serviceIdentifier,
              s"${journey.continueUrl}/$journeyId", welshTranslationsAvailable))))

      }
      else Future.successful(NotFound(withCustomisations.journeyIdError(request)))
    }
}
