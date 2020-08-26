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

import java.time.ZonedDateTime

import bankaccountverification.api.CompleteResponse
import bankaccountverification.connector.ReputationResponseEnum

case class Session(
  accountName: Option[String],
  sortCode: Option[String],
  accountNumber: Option[String],
  rollNumber: Option[String] = None,
  accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None,
  accountType: Option[String] = None
)

case class AccountDetails(
  accountName: Option[String],
  sortCode: Option[String],
  accountNumber: Option[String],
  rollNumber: Option[String] = None,
  accountNumberWithSortCodeIsValid: Option[ReputationResponseEnum] = None,
  accountExists: Option[ReputationResponseEnum] = None,
  nameMatches: Option[ReputationResponseEnum] = None,
  nonConsented: Option[ReputationResponseEnum] = None,
  subjectHasDeceased: Option[ReputationResponseEnum] = None,
  nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None
)

object Session {
  def toCompleteResponse(session: Session): Option[CompleteResponse] =
    session match {
      case Session(
            Some(accountName),
            Some(sortCode),
            Some(accountNumber),
            rollNumber,
            Some(accountNumberWithSortCodeIsValid),
            Some(accountType)
          ) =>
        Some(
          api.CompleteResponse(
            accountType,
            accountName,
            sortCode,
            accountNumber,
            accountNumberWithSortCodeIsValid,
            rollNumber
          )
        )
      case _ => None
    }
}

case class AccountDetailsUpdate(expiryDate: ZonedDateTime, data: AccountDetails)

case class AccountTypeUpdate(expiryDate: ZonedDateTime, accountType: String)
