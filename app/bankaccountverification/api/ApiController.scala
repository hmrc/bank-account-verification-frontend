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

package bankaccountverification.api

import bankaccountverification.DirectDebitConstraints
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification._

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class ApiController @Inject()(appConfig: AppConfig, mcc: MessagesControllerComponents,
                              journeyRepository: JourneyRepository,
                              val authConnector: AuthConnector) extends FrontendApiController(mcc) with AuthorisedFunctions {

  implicit val config: AppConfig = appConfig

  private val logger = Logger(this.getClass.getSimpleName)

  def init: Action[AnyContent] =
    Action.async { implicit request =>
      import InitRequest._

      authorised().retrieve(AuthProviderId.retrieval) {
        authProviderId =>
          request.body.asJson match {
            case Some(json) =>
              json.validate[InitRequest]
                .fold(
                  err => Future.successful(BadRequest(Json.obj("errors" -> err.flatMap { case (_, e) => e.map(_.message) }))),
                  init => {
                    val prepopulatedData = init.prepopulatedData.map(p =>
                      PrepopulatedData(accountType = p.accountType, name = p.name, sortCode = p.sortCode,
                        accountNumber = p.accountNumber, p.rollNumber))

                    journeyRepository
                      .create(
                        Some(authProviderId),
                        init.serviceIdentifier,
                        init.continueUrl,
                        init.messages.map(m => Json.toJsObject(m)),
                        init.customisationsUrl,
                        address = init.address.map(a => Address(a.lines, a.town, a.postcode)),
                        prepopulatedData,
                        init.directDebitConstraints.map(ddc => DirectDebitConstraints(ddc.directDebitRequired, ddc.directCreditRequired)).orElse(Some(DirectDebitConstraints.defaultDirectDebitConstraints)),
                        init.timeoutConfig.map(tc => TimeoutConfig(tc.timeoutUrl, tc.timeoutAmount, tc.timeoutKeepAliveUrl))
                      )
                      .map { journeyId =>
                        import bankaccountverification._

                        val startUrl = web.routes.AccountTypeController.getAccountType(journeyId.stringify).url
                        val completeUrl = api.routes.ApiController.complete(journeyId.stringify).url

                        val detailsUrl = prepopulatedData.map {
                          case p if p.accountType == Personal =>
                            web.personal.routes.PersonalVerificationController.getAccountDetails(journeyId.stringify).url
                          case p if p.accountType == Business =>
                            web.business.routes.BusinessVerificationController.getAccountDetails(journeyId.stringify).url
                        }

                        import InitResponse._
                        Ok(Json.toJson(InitResponse(journeyId.stringify, startUrl, completeUrl, detailsUrl)))
                      }
                  }
                )
            case None =>
              Future.successful(BadRequest(Json.obj("error" -> "No json")))
          }
      } recoverWith { case _ =>
        Future.successful(Unauthorized)
      }
    }

  def complete(journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    import bankaccountverification.Session
    authorised().retrieve(AuthProviderId.retrieval) {
      authProviderId =>
        BSONObjectID.parse(journeyId) match {
          case Success(id) =>
            journeyRepository
              .findById(id)
              .map { x =>
                x.flatMap { j =>
                  if (j.authProviderId.isEmpty || j.authProviderId.get == authProviderId) {
                    Session.toCompleteResponseJson(j.data)
                  } else None
                }
              }
              .map {
                case Some(x) => Ok(x)
                case _ => NotFound
              }
              .recoverWith { case x =>
                logger.warn(s"Something bad happened: ${x.getMessage}", x)
                Future.successful(InternalServerError)
              }
          case Failure(e) => Future.successful(BadRequest)
        }
    } recoverWith { case _ =>
      Future.successful(Unauthorized)
    }
  }
}
