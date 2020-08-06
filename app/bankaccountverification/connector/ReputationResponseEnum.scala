package bankaccountverification.connector

import play.api.libs.json.{Json, OFormat}

sealed trait ReputationResponseEnum

object ReputationResponseEnum extends Enumerable.Implicits {

  case object Yes extends WithName("yes") with ReputationResponseEnum
  case object No extends WithName("no") with ReputationResponseEnum
  case object Indeterminate extends WithName("indeterminate") with ReputationResponseEnum
  case object Inapplicable extends WithName("inapplicable") with ReputationResponseEnum
  case object Error extends WithName("error") with ReputationResponseEnum

  val values: Seq[ReputationResponseEnum] = Seq(Yes, No, Indeterminate, Inapplicable, Error)

  implicit val enumerable: Enumerable[ReputationResponseEnum] =
    Enumerable(values.map(v => v.toString -> v): _*)
}

case class ValidateBankDetailsModel(
  accountNumberWithSortCodeIsValid: ReputationResponseEnum,
  nonStandardAccountDetailsRequiredForBacs: ReputationResponseEnum,
  supportsBACS: Option[ReputationResponseEnum]
)
object ValidateBankDetailsModel {
  implicit val format: OFormat[ValidateBankDetailsModel] = Json.format[ValidateBankDetailsModel]
}
