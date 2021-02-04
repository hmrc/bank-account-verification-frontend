/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.Logger
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrlPolicy.Id
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, RedirectUrl, RedirectUrlPolicy}

import java.io.File
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class TimeoutController @Inject()(appConfig: AppConfig, mcc: MessagesControllerComponents,
                                  journeyRepository: JourneyRepository,
                                  val authConnector: AuthConnector,
                                  withCustomisations: ActionWithCustomisationsProvider)
  extends FrontendApiController(mcc) with AuthorisedFunctions {

  implicit val config: AppConfig = appConfig

  private val logger = Logger(this.getClass.getSimpleName)

  def renewSession(journeyId: String) = withCustomisations.action(journeyId).async { implicit request =>
    journeyRepository.renewExpiryDate(request.journey.id).map(_ =>
      Ok.sendFile(new File("conf/renewSession.jpg")).as("image/jpeg")
    )
  }

  val policy: RedirectUrlPolicy[Id] = OnlyRelative
  def timeoutSession(journeyId: String, timeoutUrl: RedirectUrl) = withCustomisations.action(journeyId).async { implicit request =>
    Future.successful(Redirect(timeoutUrl.get(policy).url))
  }
}
