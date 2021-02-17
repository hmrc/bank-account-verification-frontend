/*
 * Copyright 2021 HM Revenue & Customs
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

package bankaccountverification.web.business

import bankaccountverification.BusinessSession
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist._

case class BusinessAccountExistsIndeterminateViewModel(journeyId: String,
                                                       session: BusinessSession,
                                                       serviceIdentifier: String,
                                                       continueUrl: String,
                                                       welshTranslationsAvailable: Boolean) {

  val changeUrl = bankaccountverification.web.business.routes.BusinessVerificationController.getAccountDetails(journeyId).url

  def fixRows(rows: Seq[SummaryListRow])(implicit messages: Messages): Seq[SummaryListRow] =
    rows.collect {
      case r if r == rows.head => r.copy(classes = r.classes + " account-details-summary-row--first")
      case r if r == rows.last => r.copy(
        classes = r.classes.replace("govuk-summary-list__row--no-border", "")
          + " account-details-summary-row--last",
        actions = Some(Actions(items = Seq(
          ActionItem(
            href = changeUrl,
            content = HtmlContent(
              s"""
                  <span aria-hidden="true">${messages("label.change")}</span>
                  <span class="govuk-visually-hidden">${messages("label.change")} ${messages("label.accountDetails.heading")}</span>
              """
            )
          )))))
      case r => r
    }
}
