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

import bankaccountverification.connector.{BankAccountReputationConnector, BarsValidationRequest, BarsValidationRequestAccount, BarsValidationResponse}
import bankaccountverification.{SessionData, SessionDataRepository}
import javax.inject.Inject
import play.api.Logger
import play.api.data.Form
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.http.HeaderCarrier
import bankaccountverification.connector.ReputationResponseEnum._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerificationService @Inject() (connector: BankAccountReputationConnector, repository: SessionDataRepository) {
  private val logger = Logger(this.getClass)

  def verify(journeyId: BSONObjectID, form: Form[VerificationRequest])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Form[VerificationRequest]] =
    connector.validateBankDetails(toBankAccountReputationRequest(form.get)).map {
      case Success(response) => (form.validateUsingBarsResponse(response), response)
      case Failure(e) =>
        logger.warn("Received error response from bank-account-reputation.validateBankDetails")
        (form, errorResponse)
    } flatMap {
      case (form, response) =>
        form.fold(
          formWithErrors => Future.successful(formWithErrors),
          verificationRequest => {
            import bankaccountverification.MongoSessionData._

            val sessionData = SessionData(
              Some(verificationRequest.accountName),
              Some(verificationRequest.sortCode),
              Some(verificationRequest.accountNumber),
              verificationRequest.rollNumber,
              Some(response.accountNumberWithSortCodeIsValid)
            )

            repository.updateJourney(journeyId, sessionData).map(_ => form)
          }
        )
    }

  private def toBankAccountReputationRequest(vr: VerificationRequest): BarsValidationRequest =
    BarsValidationRequest(
      BarsValidationRequestAccount(VerificationRequest.stripSortCode(vr.sortCode), vr.accountNumber)
    )

  private def errorResponse: BarsValidationResponse =
    BarsValidationResponse(Error, Error, None)
}
