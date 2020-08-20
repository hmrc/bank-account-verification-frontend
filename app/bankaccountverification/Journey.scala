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

package bankaccountverification

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class Journey(
  id: BSONObjectID,
  expiryDate: ZonedDateTime,
  serviceIdentifier: String,
  continueUrl: String,
  messages: Option[JsObject] = None,
  customisationsUrl: Option[String] = None,
  data: Option[Session] = None
)

object Journey {
  def expiryDate = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

  def createExpiring(
    id: BSONObjectID,
    serviceIdentifier: String,
    continueUrl: String,
    messages: Option[JsObject] = None,
    customisationsUrl: Option[String] = None,
    data: Option[Session] = None
  ): Journey =
    Journey(
      id,
      expiryDate,
      serviceIdentifier,
      continueUrl,
      messages,
      customisationsUrl,
      data
    )

  def updateExpiring(data: Session) = SessionUpdate(expiryDate, Some(data))

  implicit val objectIdFormats: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val sessionDataReads: Reads[Session]      = Json.reads[Session]
  implicit val sessionDataWrites: Writes[Session]    = Json.writes[Session]

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

  def defaultReads: Reads[Journey] =
    (__ \ "_id")
      .read[BSONObjectID]
      .and((__ \ "expiryDate").read[ZonedDateTime])
      .and((__ \ "serviceIdentifier").read[String])
      .and((__ \ "continueUrl").read[String])
      .and((__ \ "messages").readNullable[JsObject])
      .and((__ \ "customisationsUrl").readNullable[String])
      .and((__ \ "data").readNullable[Session])(
        (
          id: BSONObjectID,
          expiryDate: ZonedDateTime,
          serviceIdentifier: String,
          continueUrl: String,
          messages: Option[JsObject],
          customisationsUrl: Option[String],
          data: Option[Session]
        ) =>
          Journey.apply(
            id,
            expiryDate,
            serviceIdentifier,
            continueUrl,
            messages,
            customisationsUrl,
            data
          )
      )

  implicit def defaultWrites: OWrites[Journey] =
    (__ \ "_id")
      .write[BSONObjectID]
      .and((__ \ "expiryDate").write[ZonedDateTime])
      .and((__ \ "serviceIdentifier").write[String])
      .and((__ \ "continueUrl").write[String])
      .and((__ \ "messages").writeNullable[JsObject])
      .and((__ \ "customisationsUrl").writeNullable[String])
      .and((__ \ "data").writeNullable[Session])(
        unlift(Journey.unapply)
      )

  implicit def updateWrites: OWrites[SessionUpdate] =
    (__ \ "$set" \ "expiryDate")
      .write[ZonedDateTime]
      .and((__ \ "$set" \ "data").writeNullable[Session])(
        unlift(SessionUpdate.unapply)
      )

  val format: Format[Journey] = Format(defaultReads, defaultWrites)
}
