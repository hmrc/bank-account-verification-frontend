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

import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.web.business.{routes => businessRoutes}
import bankaccountverification.web.personal.{routes => personalRoutes}
import bankaccountverification.web.views.html.AccountTypeView
import bankaccountverification.{AppConfig, RemoteMessagesApiProvider}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AccountTypeController @Inject()(val appConfig: AppConfig,
                                      mcc: MessagesControllerComponents,
                                      remoteMessagesApiProvider: RemoteMessagesApiProvider,
                                      accountTypeView: AccountTypeView,
                                      verificationService: VerificationService,
                                      withCustomisations: ActionWithCustomisationsProvider
                                     ) extends FrontendController(mcc) {

  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def getAccountType(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
      implicit val messages: Messages = remoteMessagesApi.preferred(request)
      val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))

      Future.successful(Ok(
        accountTypeView(journeyId, journey.serviceIdentifier, welshTranslationsAvailable, AccountTypeRequest.form)))
    }

  def postAccountType(journeyId: String): Action[AnyContent] =
    withCustomisations.action(journeyId).async { implicit request =>
      val journey = request.journey

      val remoteMessagesApi = remoteMessagesApiProvider.getRemoteMessagesApi(journey.messages)
      implicit val messages: Messages = remoteMessagesApi.preferred(request)
      val welshTranslationsAvailable = journey.messages.exists(_.keys.contains("cy"))

      val form = AccountTypeRequest.form.bindFromRequest()
      if (!form.hasErrors)
        verificationService.setAccountType(journey.id, form.value.get.accountType) map { _ =>
          form.value.get.accountType match {
            // At this stage the account type has to be here otherwise the rest of the flow will not work!
            case Personal => Redirect(personalRoutes.PersonalVerificationController.getAccountDetails(journeyId))
            case Business => Redirect(businessRoutes.BusinessVerificationController.getAccountDetails(journeyId))
          }
        }
      else
        Future.successful(BadRequest(
          accountTypeView(journeyId, appConfig.contactFormServiceIdentifier, welshTranslationsAvailable, form)))
    }
}
