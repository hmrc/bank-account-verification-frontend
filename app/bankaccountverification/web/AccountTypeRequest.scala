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

import bankaccountverification.connector.{Enumerable, WithName}
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, FormError, Mapping}
import play.api.libs.json.Json

sealed trait AccountTypeRequestEnum
object AccountTypeRequestEnum extends Enumerable.Implicits {
  case object Personal extends WithName("personal") with AccountTypeRequestEnum
  case object Business extends WithName("business") with AccountTypeRequestEnum
  case object Error extends WithName("") with AccountTypeRequestEnum

  val values: Seq[AccountTypeRequestEnum] = Seq(Personal, Business)

  implicit val enumerable: Enumerable[AccountTypeRequestEnum] =
    Enumerable(values.map(v => v.toString -> v): _*)
}

case class AccountTypeRequest(accountType: AccountTypeRequestEnum)
object AccountTypeRequest {
  import AccountTypeRequestEnum._

  object formats {
    implicit val accountTypeReads  = Json.reads[AccountTypeRequest]
    implicit val accountTypeWrites = Json.writes[AccountTypeRequest]
  }

  val form: Form[AccountTypeRequest] =
    Form(
      mapping(
        "accountType" -> accountTypeMapping
      )(AccountTypeRequest.apply)(AccountTypeRequest.unapply)
    )

  // Need to do this as if the radio buttons are not selected then we don't get the parameter at all.
  def accountTypeMapping: Mapping[AccountTypeRequestEnum] = {
    def permissiveStringFormatter: Formatter[AccountTypeRequestEnum] =
      new Formatter[AccountTypeRequestEnum] {
        def bind(key: String, data: Map[String, String]): Either[Seq[FormError], AccountTypeRequestEnum] =
          Right[Seq[FormError], AccountTypeRequestEnum] {
            val kv = data.getOrElse(key, "")
            AccountTypeRequestEnum.enumerable.withName(kv).getOrElse(Error)
          }
        def unbind(key: String, value: AccountTypeRequestEnum) = Map(key -> value.toString)
      }

    of[AccountTypeRequestEnum](permissiveStringFormatter).verifying(accountTypeConstraint())
  }

  def accountTypeConstraint(): Constraint[AccountTypeRequestEnum] =
    Constraint[AccountTypeRequestEnum](Some("constraints.accountType"), Seq()) { input =>
      if (input == Error) Invalid(ValidationError("error.accountType.required"))
      else if (AccountTypeRequestEnum.values.contains(input)) Valid
      else Invalid(ValidationError("error.accountType.required"))
    }
}
