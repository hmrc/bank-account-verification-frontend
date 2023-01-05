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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SanitizedStringSpec extends AnyWordSpec with Matchers {
  import Implicits._

  "SanitizedString" when {
    "given a string with whitespace" should {
      "remove all whitepaces" in {
        " a whitespaced string ".stripSpaces() shouldBe "awhitespacedstring"
      }
    }
    "given a string with whitespace and dashes" should {
      "remove all whitepaces and dashes" in {
        " a- whitespaced string ".stripSpacesAndDashes() shouldBe "awhitespacedstring"
      }
    }
    "given a string that is too short" should {
      "left pad to the desired length with the desired char" in {
        "1234".leftPadToLength(8, '0') shouldBe "00001234"
      }
    }
    "given a string that has non-standard ASCII characters" should {
      "replace those with ASCII equivalents" in {
        "Câfe Latte glyn".toAscii() shouldBe "Cafe Latte glyn"
      }
      "remove where there is no ASCII equivalent" in {
        "Cßfe Latte glyn".toAscii() shouldBe "Cfe Latte glyn"
      }
    }
  }
}
