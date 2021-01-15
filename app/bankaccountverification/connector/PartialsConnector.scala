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

package bankaccountverification.connector

import javax.inject.Inject
import play.api.i18n.Messages
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.{ExecutionContext, Future}

class PartialsConnector @Inject() (httpClient: HttpClient) {

  val failedPartial: Future[HtmlPartial] = Future.successful(HtmlPartial.Failure())

  def header(
    baseUrl: Option[String]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] =
    baseUrl.fold(failedPartial) { bUrl =>
      httpClient
        .GET[HtmlPartial](s"$bUrl/header", Seq(), Seq("Cookie" -> s"PLAY_LANG=${messages.lang.code}")) recoverWith {
        case _ => failedPartial
      }
    }

  def beforeContent(
    baseUrl: Option[String]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] =
    baseUrl.fold(failedPartial) { bUrl =>
      httpClient.GET[HtmlPartial](
        s"$bUrl/beforeContent",
        Seq(),
        Seq("Cookie" -> s"PLAY_LANG=${messages.lang.code}")
      ) recoverWith {
        case _ => failedPartial
      }
    }

  def footer(
    baseUrl: Option[String]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, messages: Messages): Future[HtmlPartial] =
    baseUrl.fold(failedPartial) { bUrl =>
      httpClient
        .GET[HtmlPartial](s"$bUrl/footer", Seq(), Seq("Cookie" -> s"PLAY_LANG=${messages.lang.code}")) recoverWith {
        case _ => failedPartial
      }
    }
}
