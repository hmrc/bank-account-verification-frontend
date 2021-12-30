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

import access.AccessChecker.{accessControlAllowListAbsoluteKey, accessControlEnabledKey}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters.asScalaBufferConverter

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig, val environment: Environment) {
  val allowedHosts: Set[String] = config.underlying.getStringList("microservice.hosts.allowList").asScala.toSet
  val footerLinkItems: Seq[String] = config.getOptional[Seq[String]]("footerLinkItems").getOrElse(Seq())

  val barsBaseUrl                = servicesConfig.baseUrl("bank-account-reputation")
  val barsValidateBankDetailsUrl = s"$barsBaseUrl/v2/validateBankDetails"
  val barsPersonalAssessUrl      = s"$barsBaseUrl/verify/personal"
  val barsBusinessAssessUrl      = s"$barsBaseUrl/verify/business"

  val contactFormServiceIdentifier = "bank-account-verification"
  val appName: String = config.get[String]("appName")

  val checkAllowList: Boolean = servicesConfig.getConfString(accessControlEnabledKey, "false").toBoolean
  val allowedClients: Set[String] = {
    val _allowedClients = config.getOptional[Seq[String]](accessControlAllowListAbsoluteKey)
    if(checkAllowList && _allowedClients.isEmpty) throw new RuntimeException(s"Could not find config $accessControlAllowListAbsoluteKey")
    else _allowedClients.getOrElse(Seq()).toSet
  }

}

case class BankAccountReputationConfig(validateBankDetailsUrl: String)