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

package uk.gov.hmrc.bankaccountverificationfrontend.controllers

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.bankaccountverificationfrontend.config.AppConfig
import uk.gov.hmrc.bankaccountverificationfrontend.model.MongoSessionData
import uk.gov.hmrc.bankaccountverificationfrontend.store.MongoSessionRepo
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class ApiController @Inject() (appConfig: AppConfig, mcc: MessagesControllerComponents, sessionRepo: MongoSessionRepo)
    extends FrontendController(mcc) {

  implicit val config: AppConfig = appConfig

  def init: Action[AnyContent] =
    Action.async { implicit request =>
      val journeyId   = BSONObjectID.generate()
      val sessionData = MongoSessionData(journeyId)
      sessionRepo.insert(sessionData).map(_ => Ok(journeyId.toString()))
    }

  def complete(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      import MongoSessionData._

      BSONObjectID.parse(journeyId) match {
        case Success(id) =>
          sessionRepo.findById(id).map {
            case Some(x) => Ok(Json.toJson(x))
            case None    => NotFound
          }
        case Failure(e) => Future.successful(BadRequest)
      }
    }
}
