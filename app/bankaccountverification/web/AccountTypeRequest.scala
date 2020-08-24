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

package bankaccountverification.web

import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, Mapping}
import play.api.libs.json.Json

case class AccountTypeRequest(
  accountType: String
)

object AccountTypeRequest {
  object formats {
    implicit val accountTypeReads  = Json.reads[AccountTypeRequest]
    implicit val accountTypeWrites = Json.writes[AccountTypeRequest]
  }

  final val personalAccountType = "personal"
  final val businessAccountType = "business"

  val form: Form[AccountTypeRequest] =
    Form(
      mapping(
        "accountType" -> accountTypeMapping
      )(AccountTypeRequest.apply)(AccountTypeRequest.unapply)
    )

  // Need to do this as if the radio buttons are not selected then we don't get the parameter at all.
  def accountTypeMapping: Mapping[String] = {
    def permissiveStringFormatter: Formatter[String] =
      new Formatter[String] {
        def bind(key: String, data: Map[String, String]) = Right(data.getOrElse(key, ""))
        def unbind(key: String, value: String)           = Map(key -> value)
      }

    of[String](permissiveStringFormatter).verifying(accountTypeConstraint())
  }

  def accountTypeConstraint(): Constraint[String] =
    Constraint[String](Some("constraints.accountType"), Seq()) { input =>
      if (Set(personalAccountType, businessAccountType).contains(input)) Valid
      else Invalid(ValidationError("error.accountType.required"))
    }
}
