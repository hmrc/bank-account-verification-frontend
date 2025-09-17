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
import bankaccountverification.api.{InitBACSRequirements, InitRequest, InitRequestAddress, InitRequestMaxCallConfig, InitRequestMessages, InitRequestPrepopulatedData, InitRequestTimeoutConfig}
import bankaccountverification.testOnly.html.{TestCompleteJsonFeedbackView, TestSetupView}
import bankaccountverification.web.AccountTypeRequestEnum.Business
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
  
  val fullExampleJson = Json.prettyPrint(Json.toJson(InitRequest(
    serviceIdentifier = "bank-account-verification-frontend",
    continueUrl = "bank-account-verification/test-only/complete",
    prepopulatedData = Some(InitRequestPrepopulatedData(
      accountType = Business,
      name = Some("British Airways Ltd"),
      sortCode = Some("010013"),
      accountNumber = Some("03766284"),
      rollNumber = Some("1234567890")
    )),
    address = Some(InitRequestAddress(
      lines = List("Line1", "Line2"),
      town = Some("Town"),
      postcode = Some("AA00AA")
    )),
    messages = Some(InitRequestMessages(
      en = Json.obj(
        "service.name" -> "My service",
        "label.accountDetails.heading.business" -> "Business bank or building society account details", 
        "label.accountDetails.heading.personal" -> "Personal bank or building society account details",
      ),
      cy = Some(Json.obj(
        "service.name" -> "Fy ngwasanaeth",
        "label.accountDetails.heading.business" -> "Manylion cyfrif banc neu gymdeithas adeiladu busnes",
        "label.accountDetails.heading.personal" -> "Manylion cyfrif banc neu gymdeithas adeiladu personol",
      ))
    )),
    customisationsUrl = Some("some-url"),
    bacsRequirements = Some(InitBACSRequirements(directDebitRequired = true, directCreditRequired = true)),
    timeoutConfig = Some(InitRequestTimeoutConfig(
      timeoutUrl = "/time-out",
      timeoutAmount = 3600,
      timeoutKeepAliveUrl = Some("/keep-alive")
    )),
    signOutUrl = Some("/sign-out"),
    maxCallConfig = Some(InitRequestMaxCallConfig(count = 3, redirectUrl = "/redirect"))
  ))) 
  
  def show(): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      val basicForm = TestSetupForm.form.fill(Json.prettyPrint(Json.obj(
        "serviceIdentifier" -> "bank-account-verification-frontend",
        "continueUrl" -> "/bank-account-verification/test-only/test-complete"
      )))
      
      Future.successful(Ok(view(basicForm, fullExampleJson)))
    }.recoverWith { case e: Throwable =>
      logger.error(s"[TestSetupController][show] - Error: ${e.getMessage}")
      unAuthRedirect
    }
  }
  
  def submit(): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      TestSetupForm.form.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(BadRequest(view(formWithErrors, fullExampleJson)))
        },
        jsonString => {
          val jsonBody = Json.parse(jsonString)

          service.makeInitCall(jsonBody).map {
            case Right(initModel) => Redirect(initModel.startUrl)
              .withHeaders(request.headers.headers: _*)
            case Left(errorModel) =>
              logger.error(s"[TestSetupController][submit] - Error: ${errorModel.asPrettyJson}")
              BadRequest
          }
        }
      )
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
    }.recoverWith( _ => unAuthRedirect)
    
  }
  
}
