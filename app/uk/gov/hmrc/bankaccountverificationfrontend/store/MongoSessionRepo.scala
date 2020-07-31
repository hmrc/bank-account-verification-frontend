package uk.gov.hmrc.bankaccountverificationfrontend.store

import java.time.{ZoneOffset, ZonedDateTime}

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import uk.gov.hmrc.bankaccountverificationfrontend.model.MongoSessionData
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MongoSessionRepo @Inject() (component: ReactiveMongoComponent, collectionName: String)
    extends ReactiveRepository[MongoSessionData, BSONObjectID](
      collectionName,
      component.mongoConnector.db,
      MongoSessionData.mongoSessionDataFormats
    ) {

  val expireAfterSeconds: Long = 0

  private lazy val ExpiryDateIndex       = "expiryDateIndex"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    import reactivemongo.bson.DefaultBSONHandlers._

    val indexes = collection.indexesManager.list()
    indexes.flatMap { idxs =>
      val expiry = idxs.find(index =>
        index.eventualName == ExpiryDateIndex
          && index.options.getAs[BSONLong](OptExpireAfterSeconds).fold(false)(_.as[Long] != expireAfterSeconds)
      )

      Future.sequence(Seq(ensureExpiryDateIndex(expiry)))
    }
  }

  private def ensureExpiryDateIndex(existingIndex: Option[Index])(implicit ec: ExecutionContext) = {
    Logger.info(s"Creating time to live for entries in ${collection.name} to $expireAfterSeconds seconds")

    existingIndex
      .fold(Future.successful(0))(idx => collection.indexesManager.drop(idx.eventualName))
      .flatMap { _ =>
        collection.indexesManager.ensure(
          Index(
            key = Seq("expiryDate" -> IndexType.Ascending),
            name = Some(ExpiryDateIndex),
            options = BSONDocument(OptExpireAfterSeconds -> expireAfterSeconds)
          )
        )
      }
  }
}
