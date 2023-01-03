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

package bankaccountverification.web

import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromAllowlist, OnlyRelative, RedirectUrl}

import scala.util.{Failure, Success, Try}

class RelativeOrAbsoluteWithHostnameFromWhitelist(private val allowedHosts: Set[String]) {
  private val absoluteWithHostnameFromWhitelist = AbsoluteWithHostnameFromAllowlist(allowedHosts)
  private val relativeUrlsOnly = OnlyRelative

  def url(theUrl: RedirectUrl): String = url(theUrl.unsafeValue)
  def url(theUrl: String): String = {
    val relRes = Try(RedirectUrl(theUrl).get(relativeUrlsOnly).url)
    val absRes = Try(RedirectUrl(theUrl).get(absoluteWithHostnameFromWhitelist).url)

    (relRes, absRes) match {
      case (Success(url), _) => url
      case (_, Success(url)) => url
      case (Failure(e), _) => throw e
      case (_, Failure(e)) => throw e
    }
  }
}
