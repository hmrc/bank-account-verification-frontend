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

import bankaccountverification.{AppConfig, SessionDataRepository}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.mvc._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.{I18nSupport, Messages}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import bankaccountverification.web.BankAccountDetails.bankAccountDetailsForm
import bankaccountverification.web.html.JourneyStart

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class BankAccountVerificationController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  startView: JourneyStart,
  sessionRepository: SessionDataRepository
) extends FrontendController(mcc)
    with I18nSupport {

  implicit val config: AppConfig = appConfig

  def start(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          sessionRepository.findById(id).map {
            case Some(data) =>
              Ok(startView(journeyId, bankAccountDetailsForm()))
            case None =>
              NotFound
          }
        case Failure(exception) =>
          Future.successful(BadRequest)
      }
    }

  def verifyDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      bankAccountDetailsForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful(
              BadRequest(startView(journeyId, formWithErrors))
            ),
          form => Future.successful(Redirect(config.mtdContinueUrl))
        )
    }
}
