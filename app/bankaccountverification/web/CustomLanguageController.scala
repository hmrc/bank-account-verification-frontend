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

import javax.inject.Inject
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.language.{LanguageController, LanguageUtils}

class CustomLanguageController @Inject() (
  configuration: Configuration,
  languageUtils: LanguageUtils,
  messagesControllerComponents: MessagesControllerComponents
) extends LanguageController(languageUtils, messagesControllerComponents) {

  //This can be from a configuration value. If you are using play-language's html, this should be from the configuration value set below
  override def languageMap: Map[String, Lang] =
    Map(
      "english" -> Lang("en"),
      "cymraeg" -> Lang("cy")
    )

  override def fallbackURL: String = "https://www.gov.uk/fallback"
}
