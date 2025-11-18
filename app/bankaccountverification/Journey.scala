/*
 * Copyright 2023 HM Revenue & Customs
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
import bankaccountverification.connector.ReputationResponseEnum
import bankaccountverification.web.AccountTypeRequestEnum
import org.bson.types.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.time.Instant
import java.time.temporal.ChronoUnit


case class BACSRequirements(directDebitRequired: Boolean, directCreditRequired: Boolean)
object BACSRequirements {
  val defaultBACSRequirements: BACSRequirements = BACSRequirements(directDebitRequired = true, directCreditRequired = true)
}


case class Journey(id: ObjectId, authProviderId: Option[String], expiryDate: Instant,
                   serviceIdentifier: String, continueUrl: String, data: Session, messages: Option[JsObject] = None,
                   customisationsUrl: Option[String] = None, bacsRequirements: Option[BACSRequirements] = None,
                   timeoutConfig: Option[TimeoutConfig] = None, signOutUrl: Option[String] = None,
                   maxCallCount: Option[Int] = None,
                   maxCallCountRedirectUrl: Option[String] = None,
                   useNewGovUkServiceNavigation: Option[Boolean]) {

  def getBACSRequirements: BACSRequirements = bacsRequirements.getOrElse(defaultBACSRequirements)
}

object Journey {
  def callCountSessionKey = "callCount"

  def expiryDate: Instant = Instant.now.plus(60, ChronoUnit.MINUTES)

  private def createSession(address: Option[Address], prepopulatedData: Option[PrepopulatedData]): Session = {
    val session = Session(address = address)

    prepopulatedData match {
      case None => session
      case Some(p) =>
        p.accountType match {
          case AccountTypeRequestEnum.Personal =>
            session.copy(
              accountType = Some(p.accountType),
              personal = Some(PersonalAccountDetails(accountName = p.name, sortCode = p.sortCode, accountNumber = p.accountNumber, rollNumber = p.rollNumber, iban = None, matchedAccountName = None)))
          case AccountTypeRequestEnum.Business =>
            session.copy(
              accountType = Some(p.accountType),
              business = Some(BusinessAccountDetails(companyName = p.name, sortCode = p.sortCode, accountNumber = p.accountNumber, rollNumber = p.rollNumber, iban = None, matchedAccountName = None)))
        }
    }
  }

  def createExpiring(id: ObjectId, authProviderId: Option[String], serviceIdentifier: String, continueUrl: String,
                     messages: Option[JsObject] = None, customisationsUrl: Option[String] = None,
                     address: Option[Address] = None, prepopulatedData: Option[PrepopulatedData] = None,
                     directDebitConstraints: Option[BACSRequirements] = None, timeoutConfig: Option[TimeoutConfig],
                     signOutUrl: Option[String], maxCallCount: Option[Int], maxCallCountRedirectUrl: Option[String],
                     useNewGovUkServiceNavigation: Option[Boolean]): Journey =
    Journey(id, authProviderId, expiryDate, serviceIdentifier, continueUrl, createSession(address, prepopulatedData),
      messages, customisationsUrl, directDebitConstraints, timeoutConfig, signOutUrl, maxCallCount, maxCallCountRedirectUrl, useNewGovUkServiceNavigation)

  implicit val objectIdFormats: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val datetimeFormat: Format[Instant] = uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat

  implicit val sessionAddressReads: Reads[Address] = Json.reads[Address]
  implicit val sessionAddressWrites: Writes[Address] = Json.writes[Address]

  implicit val timeoutConfigReads: Reads[TimeoutConfig] = Json.reads[TimeoutConfig]
  implicit val timeoutConfigWrites: Writes[TimeoutConfig] = Json.writes[TimeoutConfig]

  implicit val directDebitConstraintsReads: Reads[BACSRequirements] = Json.reads[BACSRequirements]
  implicit val directDebitConstraintsWrites: Writes[BACSRequirements] = Json.writes[BACSRequirements]

implicit def defaultReads: Reads[Journey] =
  (__ \ "_id")
    .read[ObjectId]
    .and((__ \ "authProviderId").readNullable[String])
    .and((__ \ "expiryDate").read[Instant])
    .and((__ \ "serviceIdentifier").read[String])
    .and((__ \ "continueUrl").read[String])
    .and((__ \ "data").read[Session])
    .and((__ \ "messages").readNullable[JsObject])
    .and((__ \ "customisationsUrl").readNullable[String])
    .and((__ \ "directDebitConstraints").readNullable[BACSRequirements])
    .and((__ \ "timeoutConfig").readNullable[TimeoutConfig])
    .and((__ \ "signOutUrl").readNullable[String])
    .and((__ \ "maxCallCount").readNullable[Int])
    .and((__ \ "maxCallCountRedirectUrl").readNullable[String])
    .and((__ \ "useNewGovUkServiceNavigation").readNullable[Boolean])(
        (
          id: ObjectId,
          authProviderId: Option[String],
          expiryDate: Instant,
          serviceIdentifier: String,
          continueUrl: String,
          data: Session,
          messages: Option[JsObject],
          customisationsUrl: Option[String],
          directDebitConstraints: Option[BACSRequirements],
          timeoutConfig: Option[TimeoutConfig],
          signOutUrl: Option[String],
          maxCallCount: Option[Int],
          maxCallCountRedirectUrl: Option[String],
          useNewGovUkServiceNavigation: Option[Boolean]
        ) => Journey.apply(id, authProviderId, expiryDate, serviceIdentifier, continueUrl, data, messages,
          customisationsUrl, directDebitConstraints, timeoutConfig, signOutUrl, maxCallCount, maxCallCountRedirectUrl,
          useNewGovUkServiceNavigation)
      )

  implicit def defaultWrites: OWrites[Journey] =
    (__ \ "_id")
      .write[ObjectId]
      .and((__ \ "authProviderId").write[Option[String]])
      .and((__ \ "expiryDate").write[Instant])
      .and((__ \ "serviceIdentifier").write[String])
      .and((__ \ "continueUrl").write[String])
      .and((__ \ "data").write[Session])
      .and((__ \ "messages").writeNullable[JsObject])
      .and((__ \ "customisationsUrl").writeNullable[String])
      .and((__ \ "directDebitConstraints").writeNullable[BACSRequirements])
      .and((__ \ "timeoutConfig").writeNullable[TimeoutConfig])
      .and((__ \ "signOutUrl").writeNullable[String])
      .and((__ \ "maxCallCount").writeNullable[Int])
      .and((__ \ "maxCallCountRedirectUrl").writeNullable[String])
      .and((__  \ "useNewGovUkServiceNavigation").writeNullable[Boolean])
  {
        unlift(Journey.unapply)
      }

  implicit def personalAccountDetailsWrites: OWrites[PersonalAccountDetails] =
    (__ \ "accountName")
      .writeNullable[String]
      .and((__ \ "sortCode").writeNullable[String])
      .and((__ \ "accountNumber").writeNullable[String])
      .and((__ \ "rollNumber").writeOptionWithNull[String])
      .and((__ \ "accountNumberWithSortCodeIsValid").writeNullable[ReputationResponseEnum])
      .and((__ \ "accountNumberIsWellFormatted").writeNullable[ReputationResponseEnum])
      .and((__ \ "accountExists").writeNullable[ReputationResponseEnum])
      .and((__ \ "nameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "nonStandardAccountDetailsRequiredForBacs").writeNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeBankName").writeOptionWithNull[String])
      .and((__ \ "sortCodeSupportsDirectDebit").writeOptionWithNull[ReputationResponseEnum])
      .and((__ \ "sortCodeSupportsDirectCredit").writeOptionWithNull[ReputationResponseEnum])
      .and((__ \ "iban").writeOptionWithNull[String])
      .and((__ \ "matchedAccountName").writeOptionWithNull[String])(
        unlift(PersonalAccountDetails.unapply)
      )

  implicit def personalAccountDetailsReads: Reads[PersonalAccountDetails] =
    (__ \ "accountName")
      .readNullable[String]
      .and((__ \ "sortCode").readNullable[String])
      .and((__ \ "accountNumber").readNullable[String])
      .and((__ \ "rollNumber").readNullable[String])
      .and((__ \ "accountNumberWithSortCodeIsValid").readNullable[ReputationResponseEnum])
      .and((__ \ "accountNumberIsWellFormatted").readNullable[ReputationResponseEnum])
      .and((__ \ "accountExists").readNullable[ReputationResponseEnum])
      .and((__ \ "nameMatches").readNullable[ReputationResponseEnum])
      .and((__ \ "nonStandardAccountDetailsRequiredForBacs").readNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeBankName").readNullable[String])
      .and((__ \ "sortCodeSupportsDirectDebit").readNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeSupportsDirectCredit").readNullable[ReputationResponseEnum])
      .and((__ \ "iban").readNullable[String])
      .and((__ \ "matchedAccountName").readNullable[String])(
        PersonalAccountDetails.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _)
      )

  implicit def businessAccountDetailsWrites: OWrites[BusinessAccountDetails] =
    (__ \ "companyName")
      .writeNullable[String]
      .and((__ \ "sortCode").writeNullable[String])
      .and((__ \ "accountNumber").writeNullable[String])
      .and((__ \ "rollNumber").writeOptionWithNull[String])
      .and((__ \ "accountNumberWithSortCodeIsValid").writeNullable[ReputationResponseEnum])
      .and((__ \ "accountNumberIsWellFormatted").writeNullable[ReputationResponseEnum])
      .and((__ \ "accountExists").writeNullable[ReputationResponseEnum])
      .and((__ \ "companyNameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "nameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "nonStandardAccountDetailsRequiredForBacs").writeNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeBankName").writeOptionWithNull[String])
      .and((__ \ "sortCodeSupportsDirectDebit").writeOptionWithNull[ReputationResponseEnum])
      .and((__ \ "sortCodeSupportsDirectCredit").writeOptionWithNull[ReputationResponseEnum])
      .and((__ \ "iban").writeOptionWithNull[String])
      .and((__ \ "matchedAccountName").writeOptionWithNull[String])(
        unlift(BusinessAccountDetails.unapply)
      )

  implicit def businessAccountDetailsReads: Reads[BusinessAccountDetails] =
    (__ \ "companyName")
      .readNullable[String]
      .and((__ \ "sortCode").readNullable[String])
      .and((__ \ "accountNumber").readNullable[String])
      .and((__ \ "rollNumber").readNullable[String])
      .and((__ \ "accountNumberWithSortCodeIsValid").readNullable[ReputationResponseEnum])
      .and((__ \ "accountNumberIsWellFormatted").readNullable[ReputationResponseEnum])
      .and((__ \ "accountExists").readNullable[ReputationResponseEnum])
      .and((__ \ "companyNameMatches").readNullable[ReputationResponseEnum])
      .and((__ \ "nameMatches").readNullable[ReputationResponseEnum])
      .and((__ \ "nonStandardAccountDetailsRequiredForBacs").readNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeBankName").readNullable[String])
      .and((__ \ "sortCodeSupportsDirectDebit").readNullable[ReputationResponseEnum])
      .and((__ \ "sortCodeSupportsDirectCredit").readNullable[ReputationResponseEnum])
      .and((__ \ "iban").readNullable[String])
      .and((__ \ "matchedAccountName").readNullable[String])(
        BusinessAccountDetails.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
      )

  implicit val sessionReads: Reads[Session] = Json.reads[Session]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]

  val format: Format[Journey] = Format(defaultReads, defaultWrites)
}
