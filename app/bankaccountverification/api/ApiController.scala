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

package bankaccountverification.api

import bankaccountverification.{AppConfig, JourneyRepository, Session}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class ApiController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  journeyRepository: JourneyRepository
) extends FrontendController(mcc) {

  implicit val config: AppConfig = appConfig

  private val logger = Logger(this.getClass.getSimpleName)

  def init: Action[AnyContent] =
    Action.async { implicit request =>
      request.body.asJson match {
        case Some(json) =>
          json
            .validate[InitRequest]
            .fold(
              err =>
                Future.successful(BadRequest(Json.obj("errors" -> err.flatMap { case (_, e) => e.map(_.message) }))),
              init =>
                journeyRepository
                  .create(
                    init.continueUrl,
                    init.customisationsUrl
                  )
                  .map(journeyId => Ok(Json.toJson(journeyId.stringify)))
            )
        case None =>
          Future.successful(BadRequest(Json.obj("error" -> "No json")))
      }
    }

  def complete(journeyId: String): Action[AnyContent] =
    Action.async {
      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          journeyRepository
            .findById(id)
            .map {
              case Some(x) => Ok(Json.toJson(x.data.flatMap(Session.toCompleteResponse)))
              case None    => NotFound
            }
            .recoverWith {
              case x =>
                logger.warn(s"Something bad happened: ${x.getMessage}", x)
                Future.successful(InternalServerError)
            }
        case Failure(e) => Future.successful(BadRequest)
      }
    }
}
