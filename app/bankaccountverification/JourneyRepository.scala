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

import bankaccountverification.JourneyRepository.{ExpiryDateIndex, expireAfterSeconds}
import bankaccountverification.web.AccountTypeRequestEnum
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions}
import org.mongodb.scala.model.Updates.set
import play.api.libs.json.{JsObject, OWrites}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JourneyRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Journey](
      mongoComponent = mongo,
      collectionName = "bank-account-verification-session-store",
      domainFormat = Journey.format,
      indexes = Seq(
        IndexModel(ascending("expiryDate"), IndexOptions().name(ExpiryDateIndex).expireAfter(expireAfterSeconds, TimeUnit.SECONDS))
      ),
      replaceIndexes = true
    ) {


  def findById(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Journey]] = {
    collection.find(filter = equal("_id", id)).toFuture().map(_.headOption)
  }

  def renewExpiryDate(id: ObjectId)(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.findOneAndUpdate(
      filter = equal("_id", id),
      update = set("expiryDate", Journey.expiryDate)
    ).toFuture().map(_ => true)
  }

  def insert(journey: Journey): Future[ObjectId] = {
    collection.insertOne(journey).toFuture()
              .map(_.getInsertedId.asObjectId().getValue)
  }

  def create(authProviderId: Option[String], serviceIdentifier: String, continueUrl: String,
             messages: Option[JsObject] = None, customisationsUrl: Option[String] = None,
             address: Option[Address] = None, prepopulatedData: Option[PrepopulatedData] = None,
             directDebitConstraints: Option[BACSRequirements], timeoutConfig: Option[TimeoutConfig])(implicit ec: ExecutionContext): Future[ObjectId] = {
    val journeyId = ObjectId.get()

    insert(Journey.createExpiring(journeyId, authProviderId, serviceIdentifier, continueUrl, messages, customisationsUrl,
      address, prepopulatedData, directDebitConstraints, timeoutConfig)).map(_ => journeyId)
  }

  def updatePersonalAccountDetails(id: ObjectId, data: PersonalAccountDetails)(implicit formats: OWrites[Journey], ec: ExecutionContext): Future[Boolean] = {
    import Journey.personalAccountDetailsWrites

    collection.findOneAndUpdate(
      filter = equal("_id", id),
      update = Seq(set("data.personal", Codecs.toBson(data)), set("expiryDate", Journey.expiryDate))).toFuture()
              .map(_ => true)
  }

  def updateBusinessAccountDetails(id: ObjectId, data: BusinessAccountDetails)(implicit formats: OWrites[Journey], ec: ExecutionContext): Future[Boolean] = {
    import Journey.businessAccountDetailsWrites

    collection.findOneAndUpdate(
      filter = equal("_id", id),
      update = Seq(set("data.business", Codecs.toBson(data)), set("expiryDate", Journey.expiryDate))).toFuture()
              .map(_ => true)
  }

  // Is there a better way to do this than to copy the Journey?
  def updateAccountType(id: ObjectId, accountType: AccountTypeRequestEnum)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      journeyOption <- collection.find(filter = equal("_id", id)).toFuture().map(r => r.headOption)
      journeyUpdate = journeyOption.map(oj => oj.copy(expiryDate = Journey.expiryDate, data = oj.data.copy(accountType = Some(accountType))))
      if journeyUpdate.isDefined
      result <- collection.replaceOne(
        filter = equal("_id", id),
        replacement = journeyUpdate.get,
        options = ReplaceOptions().upsert(true)).toFuture()
                          .map(_ => true)
    } yield result
  }
}

object JourneyRepository {
  val expireAfterSeconds: Long = 0

  private lazy val ExpiryDateIndex = "expiryDateIndex"
}
