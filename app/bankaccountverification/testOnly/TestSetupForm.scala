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

package bankaccountverification.testOnly

import bankaccountverification.api.InitRequest
import play.api.Logging
import play.api.data.Form
import play.api.data.Forms.nonEmptyText
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.json.Json

import scala.util.Try

object TestSetupForm extends Logging {
  
  private def canBeParsedAsModelConstraint: Constraint[String] = Constraint[String]("can-be-parsed-as-model") { jsonString =>
    Try(Json.parse(jsonString)).fold (
      error => {
        logger.error(s"Could not parse json: $error")
        Invalid("test-setup.start-new-journey.error.invalid-json")
      },
      _.validate[InitRequest].fold (
        errors => {
          logger.error(s"Could not parse json as InitRequest: $errors")
          Invalid("test-setup.start-new-journey.error.cannot-parse")
        },
        _ => Valid
      )
    )
  }
  
  def form: Form[String] = Form(
    "json-setup" -> nonEmptyText.verifying(canBeParsedAsModelConstraint)
  )
  
}
