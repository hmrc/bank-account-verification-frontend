/*
 * Copyright 2023 HM Revenue & Customs
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

package bankaccountverification.utils

import play.api.Environment
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromAllowlist, OnlyRelative, PermitAllOnDev, RedirectUrl}

class RelativeOrAbsoluteWithHostnameFromAllowlist(private val allowedHosts: Set[String], private val environment: Environment) {
  private val absoluteWithHostnameFromAllowlist = AbsoluteWithHostnameFromAllowlist(allowedHosts)
  private val relativeUrlsOnly = OnlyRelative
  private val permitAllOnDev = PermitAllOnDev(environment)

  def url(theUrl: RedirectUrl): String = url(theUrl.unsafeValue)
  def url(theUrl: String): String = {
    RedirectUrl(theUrl).getEither(relativeUrlsOnly | absoluteWithHostnameFromAllowlist | permitAllOnDev) match {
      case Right(safeRedirectUrl) => safeRedirectUrl.url
      case Left(error) => throw new IllegalArgumentException(error)
    }
  }
}
