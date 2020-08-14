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

import bankaccountverification.{AppConfig, RemoteMessagesApiProvider}
import javax.inject.Inject
import play.api.i18n.{Messages, MessagesApi, MessagesProvider}
import play.api.i18n.Messages.{MessageSource, UrlMessageSource}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.{ExecutionContext, Future}

class PartialsConnector @Inject() (
  httpClient: HttpClient,
  remoteMessagesApiProvider: RemoteMessagesApiProvider
) {

  val failedPartial: HtmlPartial = HtmlPartial.Failure()
  def header(baseUrl: Option[String])(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[HtmlPartial] =
    baseUrl.fold(Future.successful(failedPartial)) { bUrl =>
      httpClient.GET[HtmlPartial](s"$bUrl/header")
    }

  def footer(baseUrl: Option[String])(implicit ec: ExecutionContext, hc: HeaderCarrier) =
    baseUrl.fold(Future.successful(failedPartial)) { bUrl =>
      httpClient.GET[HtmlPartial](s"$bUrl/footer")
    }

  def messages(baseUrl: Option[String])(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[MessagesApi] = {
    import HttpReads.Implicits.readRaw

    baseUrl.fold(Future.successful(remoteMessagesApiProvider.get)) { bUrl =>
      httpClient.GET[HttpResponse](s"$bUrl/messages") map {
        case r if r.status == 200 =>
          remoteMessagesApiProvider.getRemoteMessagesApi(r.body)
        case _ => remoteMessagesApiProvider.get
      }
    }
  }
}

case class StringMessageSource(source: String) extends MessageSource {
  override def read: String = source
}
