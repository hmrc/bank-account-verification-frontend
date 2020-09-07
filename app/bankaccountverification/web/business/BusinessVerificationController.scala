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

package bankaccountverification.web.business

import bankaccountverification.connector.ReputationResponseEnum.Yes
import bankaccountverification.web.{ActionWithCustomisationsProvider, VerificationService}
import bankaccountverification.web.business.html.{BusinessAccountDetailsView, BusinessAccountExistsIndeterminate}
import bankaccountverification.{AppConfig, RemoteMessagesApiProvider}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class BusinessVerificationController @Inject()(val appConfig: AppConfig,
                                               mcc: MessagesControllerComponents,
                                               remoteMessagesApiProvider: RemoteMessagesApiProvider,
                                               businessAccountDetailsView: BusinessAccountDetailsView,
                                               businessAccountExistsIndeterminate: BusinessAccountExistsIndeterminate,
                                               verificationService: VerificationService,
                                               withCustomisations: ActionWithCustomisationsProvider
                                              ) extends FrontendController(mcc) {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def getAccountDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
      implicit val messages: Messages = remoteMessagesApi.preferred(request)
      val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))

      val businessVerificationForm = journey.data.get.business
        .map(bs => BusinessVerificationRequest(bs.companyName.getOrElse(""), bs.companyRegistrationNumber,
          bs.sortCode.getOrElse(""), bs.accountNumber.getOrElse(""), bs.rollNumber))
        .map(BusinessVerificationRequest.form.fill)
        .getOrElse(BusinessVerificationRequest.form)

      Future.successful(Ok(businessAccountDetailsView(journeyId, journey.serviceIdentifier, welshTranslationsAvailable,
        businessVerificationForm)))
    }

  def postAccountDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))
      val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
      implicit val messages: Messages = remoteMessagesApi.preferred(request)

      val form = BusinessVerificationRequest.form.bindFromRequest()
      if (form.hasErrors)
        Future.successful(BadRequest(businessAccountDetailsView(
          journeyId, journey.serviceIdentifier, welshTranslationsAvailable, form)))
      else
        for {
          response <- verificationService.assessBusiness(form.get, journey.data.flatMap(_.address))
          updatedForm <- verificationService.processBusinessAssessResponse(journey.id, response, form)
        } yield
          updatedForm match {
            case uform if uform.hasErrors =>
              BadRequest(businessAccountDetailsView(journeyId, journey.serviceIdentifier, welshTranslationsAvailable, uform))
            case _ =>
              if (response.isFailure || response.get.accountExists == Yes)
                SeeOther(s"${journey.continueUrl}/$journeyId")
              else
                Redirect(routes.BusinessVerificationController.getConfirmDetails(journeyId))
          }
    }

  def getConfirmDetails(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))
      val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
      implicit val messages: Messages = remoteMessagesApi.preferred(request)

      Future.successful(Ok(
        businessAccountExistsIndeterminate(journeyId, journey.data.get.business.get, journey.serviceIdentifier,
          s"${journey.continueUrl}/$journeyId", welshTranslationsAvailable)))
    }
}
