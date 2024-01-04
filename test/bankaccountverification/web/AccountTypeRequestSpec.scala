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

import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BarsValidationResponse, ReputationResponseEnum}
import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.data.FormError
import play.api.i18n.{Messages, MessagesApi}

class AccountTypeRequestSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    fakeApplication()
  }

  "AccountType form" should {
    val messagesApi       = app.injector.instanceOf[MessagesApi]
    implicit val messages: Messages = messagesApi.preferred(Seq())

    "validate personal successfully" when {
      "personal account is selected" in {
        val accountTypeRequest = AccountTypeRequest(AccountTypeRequestEnum.Personal)
        val accountTypeForm    = AccountTypeRequest.form.fillAndValidate(accountTypeRequest)
        accountTypeForm.hasErrors shouldBe false
      }

      "validate business successfully" when {
        "business account is selected" in {
          val accountTypeRequest = AccountTypeRequest(AccountTypeRequestEnum.Business)
          val accountTypeForm    = AccountTypeRequest.form.fillAndValidate(accountTypeRequest)
          accountTypeForm.hasErrors shouldBe false
        }
      }

      "flag account type validation errors" when {
        "accountType is not selected" in {
          val accountTypeRequest = AccountTypeRequest(AccountTypeRequestEnum.Error)
          val accountTypeForm    = AccountTypeRequest.form.fillAndValidate(accountTypeRequest)
          accountTypeForm.hasErrors shouldBe true

          val error = accountTypeForm.errors.find(e => e.key == "accountType")
          error shouldNot be(None)
          error.get.message shouldBe "error.accountType.required"
        }

        "accountType has invalid value" in {
          val accountTypeRequest = AccountTypeRequest(AccountTypeRequestEnum.Error)
          val accountTypeForm    = AccountTypeRequest.form.fillAndValidate(accountTypeRequest)
          accountTypeForm.hasErrors shouldBe true

          val error = accountTypeForm.errors.find(e => e.key == "accountType")
          error shouldNot be(None)
          error.get.message shouldBe "error.accountType.required"
        }
      }
    }
  }
}
