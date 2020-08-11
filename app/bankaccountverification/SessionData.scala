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

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import bankaccountverification.api.CompleteResponse
import bankaccountverification.connector.ReputationResponseEnum
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class SessionData(
  accountName: Option[String],
  sortCode: Option[String],
  accountNumber: Option[String],
  rollNumber: Option[String] = None,
  accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None
)

object SessionData {
  def toCompleteResponse(sessionData: SessionData): Option[CompleteResponse] =
    sessionData match {
      case SessionData(
            Some(accountName),
            Some(sortCode),
            Some(accountNumber),
            rollNumber,
            Some(accountNumberWithSortCodeIsValid)
          ) =>
        Some(api.CompleteResponse(accountName, sortCode, accountNumber, accountNumberWithSortCodeIsValid, rollNumber))
      case _ => None
    }
}

case class MongoSessionData(id: BSONObjectID, expiryDate: ZonedDateTime, data: Option[SessionData] = None)

object MongoSessionData {
  def createExpiring(id: BSONObjectID, data: Option[SessionData] = None): MongoSessionData =
    MongoSessionData(id, expiryDate = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60), data)

  implicit val objectIdFormats                        = ReactiveMongoFormats.objectIdFormats
  implicit val sessionDataReads: Reads[SessionData]   = Json.reads[SessionData]
  implicit val sessionDataWrites: Writes[SessionData] = Json.writes[SessionData]

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

  def defaultReads: Reads[MongoSessionData] =
    (__ \ "_id")
      .read[BSONObjectID]
      .and((__ \ "expiryDate").read[ZonedDateTime])
      .and((__ \ "data").readNullable[SessionData])(
        (id: BSONObjectID, expiryDate: ZonedDateTime, data: Option[SessionData]) =>
          MongoSessionData.apply(id, expiryDate, data)
      )

  implicit def defaultWrites: OWrites[MongoSessionData] =
    (__ \ "_id")
      .write[BSONObjectID]
      .and((__ \ "expiryDate").write[ZonedDateTime])
      .and((__ \ "data").writeNullable[SessionData])(
        unlift(MongoSessionData.unapply)
      )

  val format: Format[MongoSessionData] = Format(defaultReads, defaultWrites)
}
