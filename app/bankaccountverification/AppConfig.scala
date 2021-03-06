/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.collection.JavaConverters.asScalaBufferConverter

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {
  val allowedHosts: Set[String] = config.underlying.getStringList("microservice.hosts.allowList").asScala.toSet
  val footerLinkItems: Seq[String] = config.getOptional[Seq[String]]("footerLinkItems").getOrElse(Seq())

  val barsBaseUrl                = servicesConfig.baseUrl("bank-account-reputation")
  val barsValidateBankDetailsUrl = s"$barsBaseUrl/v2/validateBankDetails"
  val barsPersonalAssessUrl      = s"$barsBaseUrl/personal/v3/assess"
  val barsBusinessAssessUrl      = s"$barsBaseUrl/business/v2/assess"

  val contactFormServiceIdentifier = "bank-account-verification"
  val appName: String = config.get[String]("appName")
}

case class BankAccountReputationConfig(validateBankDetailsUrl: String)
