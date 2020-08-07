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

import bankaccountverification.connector.{BankAccountReputationConnector, BankAccountReputationValidationRequest, BankAccountReputationValidationRequestAccount, BankAccountReputationValidationResponse}
import bankaccountverification.web.VerificationRequest.verificationForm
import bankaccountverification.web.html.{ErrorTemplate, JourneyStart}
import bankaccountverification.{AppConfig, SessionData, SessionDataRepository}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class BankAccountVerificationController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  startView: JourneyStart,
  errorTemplate: ErrorTemplate,
  sessionRepository: SessionDataRepository,
  bankAccountReputationConnector: BankAccountReputationConnector
) extends FrontendController(mcc)
    with I18nSupport {
  private val logger = Logger(this.getClass)

  implicit val config: AppConfig = appConfig

  def start(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      val messages = messagesApi.preferred(request)

      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          sessionRepository.findById(id).map {
            case Some(data) =>
              Ok(startView(journeyId, verificationForm))
            case None =>
              NotFound(
                errorTemplate(
                  messages("error.journeyId.pageTitle"),
                  messages("error.journeyId.heading"),
                  messages("error.journeyId.message")
                )
              )
          }
        case Failure(exception) =>
          Future.successful(BadRequest)
      }
    }

  def verifyDetails(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      import bankaccountverification.Helpers._

      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          (verificationForm.bindFromRequest() match {
            case form if form.hasErrors =>
              Future.successful((form, None))
            case form =>
              bankAccountReputationConnector.validateBankDetails(toBankAccountReputationRequest(form.get)).map {
                case Success(response) =>
                  (form.validateBarsResponse(response), Some(response))
                case Failure(e) =>
                  logger.warn("Received error response from bank-account-reputation.validateBankDetails")
                  (form, None)
              }
          }) flatMap {
            case (form, validationResponse) =>
              form.fold(
                formWithErrors => Future.successful(BadRequest(startView(journeyId, formWithErrors))),
                verificationRequest => {
                  import bankaccountverification.MongoSessionData._

                  val sessionData = SessionData(
                    Some(verificationRequest.accountName),
                    Some(verificationRequest.sortCode),
                    Some(verificationRequest.accountNumber),
                    verificationRequest.rollNumber,
                    validationResponse.map(_.accountNumberWithSortCodeIsValid)
                  )

                  sessionRepository
                    .findAndUpdateById(id, sessionData)
                    .map(success => Redirect(config.mtdContinueUrl))
                }
              )
          }

        case Failure(exception) =>
          Future.successful(BadRequest)
      }

    }

  private def toBankAccountReputationRequest(vr: VerificationRequest): BankAccountReputationValidationRequest =
    BankAccountReputationValidationRequest(BankAccountReputationValidationRequestAccount(vr.sortCode, vr.accountNumber))
}
