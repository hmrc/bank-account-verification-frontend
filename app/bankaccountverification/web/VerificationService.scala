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

package bankaccountverification.web

import bankaccountverification.connector.ReputationResponseEnum._
import bankaccountverification.connector.{BankAccountReputationConnector, BarsPersonalAssessResponse, BarsValidationRequest, BarsValidationRequestAccount, BarsValidationResponse}
import bankaccountverification.{AccountDetails, JourneyRepository}
import javax.inject.Inject
import play.api.Logger
import play.api.data.Form
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerificationService @Inject() (connector: BankAccountReputationConnector, repository: JourneyRepository) {
  private val logger = Logger(this.getClass)

  def setAccountType(journeyId: BSONObjectID, accountType: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Boolean] =
    repository.updateAccountType(journeyId, accountType)

  def verify(journeyId: BSONObjectID, form: Form[VerificationRequest])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Form[VerificationRequest]] =
    connector.validateBankDetails(toBankAccountReputationRequest(form.get)).map {
      case Success(response) => (form.validateUsingBarsValidateResponse(response), response)
      case Failure(e) =>
        logger.warn("Received error response from bank-account-reputation.validateBankDetails")
        (form, validationErrorResponse)
    } flatMap {
      case (form, response) =>
        form.fold(
          formWithErrors => Future.successful(formWithErrors),
          verificationRequest => {
            import bankaccountverification.Journey._

            val accountDetails = AccountDetails(
              Some(verificationRequest.accountName),
              Some(verificationRequest.sortCode),
              Some(verificationRequest.accountNumber),
              verificationRequest.rollNumber,
              Some(response.accountNumberWithSortCodeIsValid)
            )

            repository.updateAccountDetails(journeyId, accountDetails).map(_ => form)
          }
        )
    }

  def assess(journeyId: BSONObjectID, form: Form[VerificationRequest])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Form[VerificationRequest]] = {
    val formData = form.get

    connector
      .assessPersonal(
        formData.accountName,
        VerificationRequest.stripSortCode(formData.sortCode),
        formData.accountNumber
      )
      .map {
        case Success(response) => (form.validateUsingBarsPersonalAssessResponse(response), response)
        case Failure(e) =>
          logger.warn("Received error response from bank-account-reputation.validateBankDetails")
          (form, personalAssessErrorResponse)
      } flatMap {
      case (form, response) =>
        form.fold(
          formWithErrors => Future.successful(formWithErrors),
          verificationRequest => {
            import bankaccountverification.Journey._

            val accountDetails = AccountDetails(
              Some(verificationRequest.accountName),
              Some(verificationRequest.sortCode),
              Some(verificationRequest.accountNumber),
              verificationRequest.rollNumber,
              Some(response.accountNumberWithSortCodeIsValid),
              Some(response.accountExists),
              Some(response.nameMatches),
              Some(response.nonConsented),
              Some(response.subjectHasDeceased),
              response.nonStandardAccountDetailsRequiredForBacs
            )

            repository.updateAccountDetails(journeyId, accountDetails).map(_ => form)
          }
        )
    }
  }

  private def toBankAccountReputationRequest(vr: VerificationRequest): BarsValidationRequest =
    BarsValidationRequest(
      BarsValidationRequestAccount(VerificationRequest.stripSortCode(vr.sortCode), vr.accountNumber)
    )

  private def validationErrorResponse: BarsValidationResponse =
    BarsValidationResponse(Error, Error, None)

  private def personalAssessErrorResponse: BarsPersonalAssessResponse =
    BarsPersonalAssessResponse(Error, Error, Error, Error, Error, Error, Some(Error))
}
