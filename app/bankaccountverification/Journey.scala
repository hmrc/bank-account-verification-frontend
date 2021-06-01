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

import bankaccountverification.BACSRequirements.defaultBACSRequirements
import bankaccountverification.connector.{Enumerable, ReputationResponseEnum}
import bankaccountverification.web.AccountTypeRequestEnum
import org.bson.types.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import java.time.LocalDateTime


case class BACSRequirements(directDebitRequired: Boolean, directCreditRequired: Boolean)
object BACSRequirements {
  val defaultBACSRequirements: BACSRequirements = BACSRequirements(directDebitRequired = true, directCreditRequired = true)
}


case class Journey(id: ObjectId, authProviderId: Option[String], expiryDate: LocalDateTime,
                   serviceIdentifier: String, continueUrl: String, data: Session, messages: Option[JsObject] = None,
                   customisationsUrl: Option[String] = None, bacsRequirements: Option[BACSRequirements] = None, timeoutConfig: Option[TimeoutConfig] = None) {

  def getBACSRequirements: BACSRequirements = bacsRequirements.getOrElse(defaultBACSRequirements)
}

object Journey {
  def expiryDate: LocalDateTime = LocalDateTime.now.plusMinutes(60)

  def createSession(address: Option[Address], prepopulatedData: Option[PrepopulatedData]): Session = {
    val session = Session(address = address)

    prepopulatedData match {
      case None => session
      case Some(p) => {
        p.accountType match {
          case AccountTypeRequestEnum.Personal =>
            session.copy(
              accountType = Some(p.accountType),
              personal = Some(PersonalSession(accountName = p.name, sortCode = p.sortCode,
                accountNumber = p.accountNumber, rollNumber = p.rollNumber)))
          case AccountTypeRequestEnum.Business =>
            session.copy(
              accountType = Some(p.accountType),
              business = Some(BusinessSession(companyName = p.name, sortCode = p.sortCode,
                accountNumber = p.accountNumber, rollNumber = p.rollNumber)))
        }
      }
    }
  }

  def createExpiring(id: ObjectId, authProviderId: Option[String], serviceIdentifier: String, continueUrl: String,
                     messages: Option[JsObject] = None, customisationsUrl: Option[String] = None,
                     address: Option[Address] = None, prepopulatedData: Option[PrepopulatedData] = None,
                     directDebitConstraints: Option[BACSRequirements] = None, timeoutConfig: Option[TimeoutConfig]): Journey =
    Journey(id, authProviderId, expiryDate, serviceIdentifier, continueUrl, createSession(address, prepopulatedData),
      messages, customisationsUrl, directDebitConstraints, timeoutConfig)

  implicit val objectIdFormats: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val datetimeFormat: Format[LocalDateTime] = uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.localDateTimeFormat
  implicit val personalSessionDataReads: Reads[PersonalSession] = Json.reads[PersonalSession]
  implicit val personalSessionDataWrites: Writes[PersonalSession] = Json.writes[PersonalSession]
  implicit val businessSessionDataReads: Reads[BusinessSession] = Json.reads[BusinessSession]
  implicit val businessSessionDataWrites: Writes[BusinessSession] = Json.writes[BusinessSession]

  implicit val sessionAddressReads: Reads[Address] = Json.reads[Address]
  implicit val sessionAddressWrites: Writes[Address] = Json.writes[Address]

  implicit val sessionReads: Reads[Session] = Json.reads[Session]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]

  implicit val timeoutConfigReads: Reads[TimeoutConfig] = Json.reads[TimeoutConfig]
  implicit val timeoutConfigWrites: Writes[TimeoutConfig] = Json.writes[TimeoutConfig]

  implicit val directDebitConstraintsReads: Reads[BACSRequirements] = Json.reads[BACSRequirements]
  implicit val directDebitConstraintsWrites: Writes[BACSRequirements] = Json.writes[BACSRequirements]

  implicit def defaultReads: Reads[Journey] =
    (__ \ "_id")
      .read[ObjectId]
      .and((__ \ "authProviderId").readNullable[String])
      .and((__ \ "expiryDate").read[LocalDateTime])
      .and((__ \ "serviceIdentifier").read[String])
      .and((__ \ "continueUrl").read[String])
      .and((__ \ "data").read[Session])
      .and((__ \ "messages").readNullable[JsObject])
      .and((__ \ "customisationsUrl").readNullable[String])
      .and((__ \ "directDebitConstraints").readNullable[BACSRequirements])
      .and((__ \ "timeoutConfig").readNullable[TimeoutConfig])(
        (
          id: ObjectId,
          authProviderId: Option[String],
          expiryDate: LocalDateTime,
          serviceIdentifier: String,
          continueUrl: String,
          data: Session,
          messages: Option[JsObject],
          customisationsUrl: Option[String],
          directDebitConstraints: Option[BACSRequirements],
          timeoutConfig: Option[TimeoutConfig]
        ) => Journey.apply(id, authProviderId, expiryDate, serviceIdentifier, continueUrl, data, messages, customisationsUrl, directDebitConstraints, timeoutConfig)
      )

  implicit def defaultWrites: OWrites[Journey] =
    (__ \ "_id")
      .write[ObjectId]
      .and((__ \ "authProviderId").write[Option[String]])
      .and((__ \ "expiryDate").write[LocalDateTime])
      .and((__ \ "serviceIdentifier").write[String])
      .and((__ \ "continueUrl").write[String])
      .and((__ \ "data").write[Session])
      .and((__ \ "messages").writeNullable[JsObject])
      .and((__ \ "customisationsUrl").writeNullable[String])
      .and((__ \ "directDebitConstraints").writeNullable[BACSRequirements])
      .and((__ \ "timeoutConfig").writeNullable[TimeoutConfig]) {
        unlift(Journey.unapply)
      }

  implicit def personalAccountDetailsWrites: OWrites[PersonalAccountDetails] =
    (__ \ "accountName")
      .writeNullable[String]
      .and((__ \ "sortCode").writeNullable[String])
      .and((__ \ "accountNumber").writeNullable[String])
      .and((__ \ "rollNumber").writeOptionWithNull[String])
      .and((__ \ "accountNumberWithSortCodeIsValid").writeNullable[ReputationResponseEnum])
      .and((__ \ "accountExists").writeNullable[ReputationResponseEnum])
      .and((__ \ "nameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "addressMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "nonConsented").writeNullable[ReputationResponseEnum])
      .and((__ \ "subjectHasDeceased").writeNullable[ReputationResponseEnum])
      .and((__ \ "nonStandardAccountDetailsRequiredForBacs").writeNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeBankName").writeOptionWithNull[String])(
        unlift(PersonalAccountDetails.unapply)
      )

  implicit def businessAccountDetailsWrites: OWrites[BusinessAccountDetails] =
    (__ \ "companyName")
      .writeNullable[String]
      .and((__ \ "sortCode").writeNullable[String])
      .and((__ \ "accountNumber").writeNullable[String])
      .and((__ \ "rollNumber").writeOptionWithNull[String])
      .and((__ \ "accountNumberWithSortCodeIsValid").writeNullable[ReputationResponseEnum])
      .and((__ \ "nonStandardAccountDetailsRequiredForBacs").writeNullable[ReputationResponseEnum])
      .and((__ \ "accountExists").writeNullable[ReputationResponseEnum])
      .and((__ \ "companyNameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "companyPostCodeMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "companyRegistrationNumberMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeBankName").writeOptionWithNull[String])(
        unlift(BusinessAccountDetails.unapply)
      )

  val format: Format[Journey] = Format(defaultReads, defaultWrites)
}
