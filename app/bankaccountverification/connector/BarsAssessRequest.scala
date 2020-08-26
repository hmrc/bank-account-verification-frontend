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

package bankaccountverification.connector

import play.api.libs.json.{Json, OFormat}

case class BarsPersonalAssessRequest(account: BarsAccount, subject: BarsSubject)

object BarsPersonalAssessRequest {
  implicit val format: OFormat[BarsPersonalAssessRequest] = Json.format[BarsPersonalAssessRequest]
}

case class BarsBusinessAssessRequest(account: BarsAccount, business: Option[BarsBusiness])

object BarsBusinessAssessRequest {
  implicit val format: OFormat[BarsBusinessAssessRequest] = Json.format[BarsBusinessAssessRequest]
}

case class BarsBusiness(
  companyName: String, // Must be between 1 and 70 characters long
  companyRegistrationNumber: Option[String],
  address: Option[BarsAddress]
)

object BarsBusiness {
  implicit val format: OFormat[BarsBusiness] = Json.format[BarsBusiness]
}

case class BarsAccount(
  sortCode: String, // The bank sort code, 6 characters long (whitespace and/or dashes should be removed)
  accountNumber: String // The bank account number, 8 characters long
)

object BarsAccount {
  implicit val format: OFormat[BarsAccount] = Json.format[BarsAccount]
}

case class BarsAddress(
  lines: List[String], // One to four lines; cumulative length must be between 1 and 140 characters.
  town: Option[String], // Must be between 1 and 35 characters long
  postcode: Option[
    String
  ] // Must be between 5 and 8 characters long, all uppercase. The internal space character can be omitted.
)

object BarsAddress {
  implicit val format: OFormat[BarsAddress] = Json.format[BarsAddress]
}

case class BarsSubject(
  title: Option[String], // e.g. "Mr" etc; must >= 2 character and <= 35 characters long
  name: Option[String], // Must be between 1 and 70 characters long
  firstName: Option[String], // Must be between 1 and 35 characters long
  lastName: Option[String], // Must be between 1 and 35 characters long
  dob: Option[String], // date of birth: ISO-8601 YYYY-MM-DD
  address: BarsAddress
) {
  require(
    (name.isEmpty && firstName.isDefined && lastName.isDefined) ||
      (name.isDefined && firstName.isEmpty && lastName.isEmpty)
  )
}

object BarsSubject {
  implicit val format: OFormat[BarsSubject] = Json.format[BarsSubject]
}
