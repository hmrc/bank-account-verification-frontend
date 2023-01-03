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

package bankaccountverification.web

import bankaccountverification._
import bankaccountverification.api.FrontendApiController
import bankaccountverification.web.views.html.ErrorTemplate
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{MessagesControllerComponents, Request}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import java.io.File
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class TimeoutController @Inject()(appConfig: AppConfig,
                                  mcc: MessagesControllerComponents,
                                  journeyRepository: JourneyRepository,
                                  val authConnector: AuthConnector,
                                  errorTemplate: ErrorTemplate,
                                  withCustomisations: ActionWithCustomisationsProvider)
  extends FrontendApiController(mcc) with I18nSupport with AuthorisedFunctions {

  implicit val config: AppConfig = appConfig

  private val logger = Logger(this.getClass.getSimpleName)

  def renewSession(journeyId: String) = withCustomisations.action(journeyId).async { implicit request =>
    journeyRepository.renewExpiryDate(request.journey.id).map(_ =>
      Ok.sendFile(new File("conf/renewSession.jpg")).as("image/jpeg")
    )
  }

  private val policy = new RelativeOrAbsoluteWithHostnameFromWhitelist(appConfig.allowedHosts)

  def timeoutSession(journeyId: String, timeoutUrl: RedirectUrl) = withCustomisations.action(journeyId).async {
    implicit request =>
    Future.successful {
      Try(policy.url(timeoutUrl)) match {
        case Success(url) => Redirect(url)
        case Failure(e) =>
          logger.error(s"timeoutUrl '${timeoutUrl.unsafeValue}' is not whitelisted")
          timeoutUrlError(mcc.messagesApi.preferred(request))
      }
    }
  }

  private def timeoutUrlError(messages: Messages)(implicit request: Request[_]) = {
    NotFound(
      errorTemplate(messages("error.pageTitle"), messages("error.timeoutUrl.heading"), messages("error.timeoutUrl.message")))
  }
}
