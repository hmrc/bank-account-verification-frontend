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

package access

/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

import bankaccountverification.AppConfig
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.mvc.Request
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AccessChecker @Inject()(config: Configuration, servicesConfig: ServicesConfig) extends AppConfig(config, servicesConfig) {
  def isClientAllowed()(implicit request: Request[_]): Boolean = !checkAllowList || {
    val userAgent = request.headers.get(HeaderNames.USER_AGENT)

    userAgent.fold(false)(allowedClients.contains)
  }
}

object AccessChecker {
  val accessControlEnabledKey = "access-control.enabled"
  val accessControlEnabledAbsoluteKey = s"microservice.services.$accessControlEnabledKey"
  val accessControlAllowListKey = "access-control.allow-list"
  val accessControlAllowListAbsoluteKey = s"microservice.services.$accessControlAllowListKey"
}