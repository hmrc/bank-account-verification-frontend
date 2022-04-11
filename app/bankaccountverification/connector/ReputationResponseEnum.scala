/*
 * Copyright 2022 HM Revenue & Customs
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

sealed trait ReputationResponseEnum

object ReputationResponseEnum extends Enumerable.Implicits {

  case object Yes extends WithName("yes") with ReputationResponseEnum
  case object Partial extends WithName("partial") with ReputationResponseEnum
  case object No extends WithName("no") with ReputationResponseEnum
  case object Indeterminate extends WithName("indeterminate") with ReputationResponseEnum
  case object Inapplicable extends WithName("inapplicable") with ReputationResponseEnum
  case object Error extends WithName("error") with ReputationResponseEnum

  val values: Seq[ReputationResponseEnum] = Seq(Yes, Partial, No, Indeterminate, Inapplicable, Error)

  implicit val enumerable: Enumerable[ReputationResponseEnum] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
