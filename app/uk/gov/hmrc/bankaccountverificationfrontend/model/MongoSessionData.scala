package uk.gov.hmrc.bankaccountverificationfrontend.model

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import play.api.http.{DefaultWriteables, Writeable}
import play.api.libs.json.{Format, JsValue, Json, Reads, Writes, __}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class MongoSessionData(id: BSONObjectID, expiryDate: ZonedDateTime)

object MongoSessionData {
  def apply(id: BSONObjectID): MongoSessionData =
    MongoSessionData(id, ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60))

  implicit val format = ReactiveMongoFormats.objectIdFormats

  implicit val mongoSessionDataFormats = Json.format[MongoSessionData]

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
}
