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
