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

package utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html

import scala.concurrent.Future

trait ViewTestHelpers { self: BaseSpec =>

  trait ViewTest {
    implicit lazy val baseFakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    
    val call: Future[Result]

    lazy val doc: Document = Jsoup.parse(Html(contentAsString(call)).body)

    def select(selector: String): Elements = doc.select(selector)

    def basicChecks(expectedStatus: Int = OK): Unit
  }
  
  trait ErrorTest extends ViewTest {
    override def basicChecks(expectedStatus: Int = BAD_REQUEST): Unit = {
      checkStatus(expectedStatus)(this)
    }
  }
  
  trait RedirectTest extends ViewTest {
    val redirectUrl: String
    
    override def basicChecks(expectedStatus: Int = SEE_OTHER): Unit = {
      checkStatus(SEE_OTHER)(this)
      
      s"has the redirect url of $redirectUrl" in {
        redirectLocation(call).get shouldBe redirectUrl
      }
    }
  }
  
  trait PageViewTest extends ViewTest {
    val title: String
    val heading: String
    
    override def basicChecks(expectedStatus: Int = OK): Unit = {
      checkStatus(expectedStatus)(this)
      checkTitle(title)(this)
      
      s"have the heading of ${heading}" in {
        select("h1").text() shouldBe heading
      }
    }
  }
  
  def checkStatus(expectedStatus: Int)(implicit viewTest: ViewTest): Unit = {
    s"has a status of $expectedStatus" in {
      status(viewTest.call) shouldBe expectedStatus
    }
  }
  
  def checkTitle(titleText: String)(implicit viewTest: ViewTest): Unit = {
    s"the page should have the title $titleText" in {
      viewTest.doc.title() shouldBe titleText
    }
  }
  
  def checkHeading(headingText: String, selector: String = "h1")(implicit viewTest: ViewTest): Unit = {
    s"the page should have the heading $headingText" in {
      viewTest.select(selector).text() shouldBe headingText
    }
  }

  def checkContentOnPage(content: String, selector: String)(implicit viewTest: ViewTest): Unit = {
    val trimmedContent = content.take(20) + (if(content.length > 20) "..." else "")
    s"the page should contain the text $trimmedContent" in {
      viewTest.select(selector).text() shouldBe content
    }
  }

  def checkTextArea(content: Option[String], selector: String)(implicit viewTest: ViewTest): Unit = {
    val textArea = viewTest.select(selector)
    
    s"the page should contain a text area with selector '$selector'" in {
      textArea.size() shouldBe 1
    }
    
    content.fold(
      "the text area should be empty" in {
        textArea.`val`() shouldBe ""
      }
    )(
      textAreaText => {
        val trimmedValue = textAreaText.take(20) + (if(textAreaText.length > 20) "..." else "")
        s"the text area should have the value $trimmedValue" in {
          textArea.`val`() shouldBe textAreaText
        }
      }
    )
  }
  
  def checkButton(content: String, href: Option[String] = None, selector: String)(implicit viewTest: ViewTest): Unit = {
    val button = viewTest.select(selector)
    s"have the content $content" in {
      button.text() shouldBe content
    }
    
    href.foreach { link =>
      s"have the href of $link" in {
        button.attr("href") shouldBe link
      }
    }
  }
  
  case class DetailsCheck(detailsContent: String, relativeSelector: Option[String] = None)
  
  def checkDetails(detailsSummary: String, selector: String, contentChecks: DetailsCheck*)(implicit viewTest: ViewTest): Unit = {
    val details = viewTest.select(selector)

    val trimmedSummary = detailsSummary.take(20) + (if(detailsSummary.length > 20) "..." else "")
    
    s"have the details summary of $trimmedSummary" in {
      details.select("summary").text() shouldBe detailsSummary
    }
    
    contentChecks.foreach { check =>
      val trimmedContent = check.detailsContent.take(20) + (if(check.detailsContent.length > 20) "..." else "")
      s"have the details content of $trimmedContent" in {
        val selectorToUse = check.relativeSelector.fold(
          s"$selector > div"
        )(
          relative => s"$selector > div > $relative"
        )
        
        details.select(selectorToUse).text() shouldBe check.detailsContent
      }
    }
  }
  
  def checkLinkOnPage(linkText: String, linkHref: String, selector: String)(implicit viewTest: ViewTest): Unit = {
    val link = viewTest.select(selector)
    s"have the link text of $linkText" in {
      link.text() shouldBe linkText
    }
    
    s"have the link href of $linkHref" in {
      link.attr("href") shouldBe linkHref
    }
  }
}
