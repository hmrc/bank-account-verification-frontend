/*
 * Copyright 2025 HM Revenue & Customs
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

package utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Port
import play.api.mvc.Result
import play.api.routing.sird.{POST, GET}
import play.core.server.{Server, ServerConfig}

trait ItTestSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {
  val barsPort = 11222
  
  def barsGetTest(endpoint: String, response: Result)(testBlock: Port => Unit): Unit = {
    Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
      import components.{defaultActionBuilder => BuildAction}
      {
        case GET(header) if header.uri.contains(endpoint) => BuildAction(response)
      }
    }(testBlock)
  }
  
  def barsPostTest(endpoint: String, response: Result)(testBlock: Port => Unit): Unit = {
    Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
      import components.{defaultActionBuilder => BuildAction}
      {
        case POST(header) if header.uri.contains(endpoint) => BuildAction(response)
      }
    }(testBlock)
  }
  
}
