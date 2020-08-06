package bankaccountverification.connector

import bankaccountverification.AppConfig
import bankaccountverification.web.VerificationRequest
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.Request
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class BankAccountReputationConnector @Inject() (httpClient: HttpClient, appConfig: AppConfig) {
  import VerificationRequest.formats._

  private val bankAccountReputationConfig = appConfig.bankAccountReputationConfig

  def validateBankDetails(
    bankDetailsModel: VerificationRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Try[ValidateBankDetailsModel]] = {
    import ValidateBankDetailsModel._

    httpClient
      .POST[VerificationRequest, HttpResponse](
        url = bankAccountReputationConfig.validateBankDetailsUrl,
        body = bankDetailsModel
      )
      .map {
        case httpResponse if httpResponse.status == 200 =>
          Json.fromJson[ValidateBankDetailsModel](httpResponse.json) match {
            case JsSuccess(result, _) =>
              Success(result)
            case JsError(errors) =>
              Failure(new HttpException("Could not parse Json response from BARs", httpResponse.status))
          }
        case httpResponse => Failure(new HttpException(httpResponse.body, httpResponse.status))
      }
  }
}
