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

package bankaccountverification.connector

import play.api.http.HeaderNames
import play.api.i18n.Messages
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.partials.HtmlPartial

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PartialsConnector @Inject() (httpClient: HttpClientV2) {

  def header(baseUrl: Option[String])
            (implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] =
    getPartial("header", baseUrl)

  def beforeContent(baseUrl: Option[String])
                   (implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] =
    getPartial("beforeContent", baseUrl)

  def footer(baseUrl: Option[String])
            (implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] =
    getPartial("footer", baseUrl)


  private def getPartial(path: String, baseUrl: Option[String])
                        (implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] = {
    val failedPartial: Future[HtmlPartial] = Future.successful(HtmlPartial.Failure())
    baseUrl.fold(failedPartial) { bUrl =>
      httpClient
        .get(url"$bUrl/$path")
        .setHeader(HeaderNames.COOKIE -> s"PLAY_LANG=${messages.lang.code}")
        .execute[HtmlPartial]
        .recoverWith {
          case _ => failedPartial
        }
    }
  }
}
