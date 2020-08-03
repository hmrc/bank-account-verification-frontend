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

package uk.gov.hmrc.bankaccountverificationfrontend.controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bankaccountverificationfrontend.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.duration._

class BankAccountVerificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {
  implicit val timeout = 1 second

  private val injector   = app.injector
  private val controller = injector.instanceOf[BankAccountVerificationController]

//  private val env           = Environment.simple()
//  private val configuration = Configuration.load(env)
//
//  private val serviceConfig = new ServicesConfig(configuration)

  "GET /start" should {
    "return 303" in {
      val fakeRequest = FakeRequest("GET", "/start/some_journey_id").withMethod("GET")
      val result      = controller.start("some_journey_id").apply(fakeRequest)
      status(result)           shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("http://consumer-service/continue")
    }
  }
}
