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

package api

import config.AppConfig
import javax.inject.{Inject, Singleton}
import model.MongoSessionData
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import reactivemongo.bson.BSONObjectID
import store.MongoSessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class ApiController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  sessionRepo: MongoSessionRepository
) extends FrontendController(mcc) {

  implicit val config: AppConfig = appConfig

  private val logger = Logger(this.getClass.getSimpleName)

  def init: Action[AnyContent] =
    Action.async {
      val journeyId   = BSONObjectID.generate()
      val sessionData = MongoSessionData.createExpiring(journeyId)
      sessionRepo.insert(sessionData).map(_ => Ok(journeyId.stringify))
    }

  def complete(journeyId: String): Action[AnyContent] =
    Action.async {

      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          sessionRepo
            .findById(id)
            .map {
              case Some(x) => Ok(Json.toJson(x))
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
