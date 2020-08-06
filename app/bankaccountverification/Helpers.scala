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
