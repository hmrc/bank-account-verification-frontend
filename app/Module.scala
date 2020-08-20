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

import akka.actor.ActorSystem
import bankaccountverification.connector.BankAccountReputationConnector
import bankaccountverification.{AppConfig, JourneyRepository, RemoteMessagesApiProvider}
import com.google.inject.{AbstractModule, Provides}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws
import play.api.libs.ws.{DefaultWSCookie, WSClient}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

class Module(environment: Environment, playConfig: Configuration) extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    super.configure()
    bind(classOf[AppConfig])
    bind(classOf[RemoteMessagesApiProvider])
    bind(classOf[JourneyRepository])
    bind(classOf[BankAccountReputationConnector])
  }
}
