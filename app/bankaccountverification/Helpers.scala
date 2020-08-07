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

import bankaccountverification.connector.BankAccountReputationValidationResponse
import bankaccountverification.connector.ReputationResponseEnum.Yes
import bankaccountverification.web.VerificationRequest
import bankaccountverification.web.VerificationRequest.verificationForm
import play.api.data.Form

object Helpers {

  implicit class ValidationFormWrapper(form: Form[VerificationRequest]) {

    def validateBarsResponse(response: BankAccountReputationValidationResponse): Form[VerificationRequest] =
      if (response.accountNumberWithSortCodeIsValid != Yes)
        verificationForm
          .fill(form.get)
          .withError("sortCode", "error.sortcode.eiscdInvalid")
          .withError("accountNumber", "error.accountNumber.eiscdInvalid")
      else if (response.nonStandardAccountDetailsRequiredForBacs == Yes)
        verificationForm
          .fill(form.get)
          .withError("rollNumber", "error.rollNumber.required")
      else form

  }

}
