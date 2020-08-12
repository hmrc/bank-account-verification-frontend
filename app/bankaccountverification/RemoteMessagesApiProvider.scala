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

package bankaccountverification

import bankaccountverification.connector.StringMessageSource
import javax.inject.Inject
import play.api.http.HttpConfiguration
import play.api.i18n.{DefaultMessagesApi, DefaultMessagesApiProvider, Langs, Messages}
import play.api.{Configuration, Environment}

class RemoteMessagesApiProvider @Inject() (
  environment: Environment,
  config: Configuration,
  langs: Langs,
  httpConfiguration: HttpConfiguration
) extends DefaultMessagesApiProvider(environment, config, langs, httpConfiguration) {

  def getRemoteMessagesApi(response: String) = {
    val remoteMessages = Messages
      .parse(StringMessageSource(response), "remote")
      .getOrElse(Map[String, String]())

    val allMessages = loadAllMessages.map { case (s, m) => if (s == "default") s -> (m ++ remoteMessages) else s -> m }

    new DefaultMessagesApi(
      allMessages,
      langs,
      langCookieName = langCookieName,
      langCookieSecure = langCookieSecure,
      langCookieHttpOnly = langCookieHttpOnly,
      langCookieSameSite = langCookieSameSite,
      httpConfiguration = httpConfiguration
    )
  }
}
