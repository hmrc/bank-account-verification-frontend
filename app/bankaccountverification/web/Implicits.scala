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

package bankaccountverification.web

import java.text.Normalizer

object Implicits {
  implicit class SanitizedString(unwrap: String) {
    def stripSpaces(): String = unwrap.replaceAll("""[ ]""", "")
    def stripSpacesAndDashes(): String = unwrap.replaceAll("""[ \-]""", "")
    def leftPadToLength(length: Int, padChar: Char): String = unwrap.reverse.padTo(length, padChar).reverse
    def stripLeadingSpaces(): String = unwrap.stripPrefix(" ")
    def stripTrailingSpaces(): String = unwrap.stripSuffix(" ")
    def toAscii(): String = {
      Normalizer.normalize(unwrap, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")
    }
  }
}
