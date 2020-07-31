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

package uk.gov.hmrc.bankaccountverificationfrontend.model

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class MongoSessionData(id: BSONObjectID, expiryDate: ZonedDateTime)

object MongoSessionData {
  def createExpiring(id: BSONObjectID): MongoSessionData =
    MongoSessionData(id, ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60))

  implicit val format = ReactiveMongoFormats.objectIdFormats

  implicit val localDateTimeRead: Reads[ZonedDateTime] =
    (__ \ "$date").read[Long].map { dateTime =>
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneOffset.UTC)
    }

  implicit val localDateTimeWrite: Writes[ZonedDateTime] = new Writes[ZonedDateTime] {
    def writes(dateTime: ZonedDateTime): JsValue =
      Json.obj(
        "$date" -> dateTime.toInstant.toEpochMilli
      )
  }
  implicit val datetimeFormat: Format[ZonedDateTime] = Format(localDateTimeRead, localDateTimeWrite)

  val mongoSessionDataReads: Reads[MongoSessionData]             = Json.reads[MongoSessionData]
  val mongoSessionDataWrites: Writes[MongoSessionData]           = Json.writes[MongoSessionData]
  implicit val mongoSessionDataFormats: Format[MongoSessionData] = Format(mongoSessionDataReads, mongoSessionDataWrites)
}
