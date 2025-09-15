/*
 * Copyright 2025 HM Revenue & Customs
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

package bankaccountverification.testOnly

import bankaccountverification.AppConfig
import bankaccountverification.testOnly.html.{TestCompleteJsonFeedbackView, TestSetupView}
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TestSetupController@Inject()(
                                    mcc: MessagesControllerComponents,
                                    view: TestSetupView,
                                    completedView: TestCompleteJsonFeedbackView,
                                    service: TestSetupService,
                                    val authConnector: AuthConnector
                                  )(implicit appConfig: AppConfig, ec: ExecutionContext) extends FrontendController(mcc) with AuthorisedFunctions with Logging {
  
  private lazy val unAuthRedirect = Future.successful(Redirect(appConfig.authStubUrl + s"/?continue=${appConfig.testOnlyUrl}${bankaccountverification.testOnly.routes.TestSetupController.show().url}")) 
  
  def show(): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      Future.successful(Ok(view()))
    }.recoverWith { case e: Throwable =>
      logger.error(s"[TestSetupController][show] - Error: ${e.getMessage}")
      unAuthRedirect
    }
  }
  
  def submit(): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      val jsonBody = Json.parse(TestSetupForm.form.bindFromRequest().get)

      service.makeInitCall(jsonBody).map {
        case Right(initModel) => Redirect(initModel.startUrl)
          .withHeaders(request.headers.headers: _*)
        case Left(errorModel) =>
          logger.error(s"[TestSetupController][submit] - Error: ${errorModel.asPrettyJson}")
          BadRequest
      }
    }.recoverWith( _ => unAuthRedirect )
  }
  
  def complete(journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      service.completeCall(journeyId).map {
        case Right(jsResult) =>
          val niceJson = Json.prettyPrint(jsResult)
          
          Ok(completedView(journeyId, niceJson))
        case Left(errorModel) =>
          logger.error(s"[TestSetupController][complete] - Error: ${errorModel.asPrettyJson}")
          BadRequest
      }
    }
    
  }
  
}
