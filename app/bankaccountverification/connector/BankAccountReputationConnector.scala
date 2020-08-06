package bankaccountverification.connector

import bankaccountverification.AppConfig
import bankaccountverification.web.BankAccountDetails
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BankAccountReputationConnector @Inject() (httpClient: HttpClient, appConfig: AppConfig) {
  import bankaccountverification.MongoSessionData._

  private val bankAccountReputationConfig = appConfig.bankAccountReputationConfig

  def validateBankDetails(
    bankAccountDetails: BankAccountDetails
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] =
//    httpClient.POST(bankAccountReputationConfig.validateBankDetailsUrl, Json.toJson(bankAccountDetails))
    ???
}
