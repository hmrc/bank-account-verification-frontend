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
  
  implicit val journeyFormat: OFormat[Journey] = Json.format[Journey]
  
  implicit val businessAccountDetailsFormat: OFormat[BusinessAccountDetails] = Json.format[BusinessAccountDetails]
  
  implicit val personalAccountDetailsFormat: OFormat[PersonalAccountDetails] = Json.format[PersonalAccountDetails]
  
  implicit val sessionReads: Reads[Session] = Json.reads[Session]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]
  
}
