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
import bankaccountverification.connector._
import bankaccountverification.web.business.BusinessVerificationRequest
import bankaccountverification.web.personal.PersonalVerificationRequest
import bankaccountverification.{Address, BusinessAccountDetails, JourneyRepository, PersonalAccountDetails}
import javax.inject.Inject
import play.api.Logger
import play.api.data.Form
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class VerificationService @Inject()(connector: BankAccountReputationConnector, repository: JourneyRepository) {
  private val logger = Logger(this.getClass)

  def setAccountType(journeyId: BSONObjectID, accountType: AccountTypeRequestEnum)
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean] =
    repository.updateAccountType(journeyId, accountType)

  def assessPersonal(request: PersonalVerificationRequest, address: Option[Address])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Try[BarsPersonalAssessResponse]] =
    connector.assessPersonal(
      request.accountName,
      Forms.stripSortCode(request.sortCode),
      request.accountNumber,
      address.map(a => BarsAddress(a.lines, a.town, a.postcode)).getOrElse(BarsAddress.emptyAddress))

  def processPersonalAssessResponse(journeyId: BSONObjectID,
                                    assessResponse: Try[BarsPersonalAssessResponse],
                                    form: Form[PersonalVerificationRequest]
                                   )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Form[PersonalVerificationRequest]] = {

    val (updatedForm, response) = assessResponse match {
      case Success(response) => (form.validateUsingBarsPersonalAssessResponse(response), response)
      case Failure(e) =>
        logger.warn("Received error response from bank-account-reputation.validateBankDetails")
        (form, BarsPersonalAssessErrorResponse())
    }

    updatedForm.fold(
      formWithErrors => Future.successful(formWithErrors),
      verificationRequest => {
        import bankaccountverification.Journey._

        val accountDetails = PersonalAccountDetails(verificationRequest, response)
        repository.updatePersonalAccountDetails(journeyId, accountDetails).map(_ => form)
      }
    )
  }

  def assessBusiness(request: BusinessVerificationRequest, address: Option[Address])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Try[BarsBusinessAssessResponse]] =
    connector.assessBusiness(
      request.companyName,
      None,
      Forms.stripSortCode(request.sortCode),
      request.accountNumber,
      address.map(a => BarsAddress(a.lines, a.town, a.postcode)))

  def processBusinessAssessResponse(journeyId: BSONObjectID,
                                    assessResponse: Try[BarsBusinessAssessResponse],
                                    form: Form[BusinessVerificationRequest]
                                   )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Form[BusinessVerificationRequest]] = {

    val (updatedForm, response) = assessResponse match {
      case Success(response) => (form.validateUsingBarsBusinessAssessResponse(response), response)
      case Failure(e) =>
        logger.warn("Received error response from bank-account-reputation.validateBankDetails")
        (form, BarsBusinessAssessErrorResponse())
    }

    updatedForm.fold(
      formWithErrors => Future.successful(formWithErrors),
      verificationRequest => {
        import bankaccountverification.Journey._

        val accountDetails = BusinessAccountDetails(verificationRequest, response)
        repository.updateBusinessAccountDetails(journeyId, accountDetails).map(_ => form)
      }
    )
  }
}
