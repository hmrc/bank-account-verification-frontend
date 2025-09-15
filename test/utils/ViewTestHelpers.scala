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
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html

import scala.concurrent.Future

trait ViewTestHelpers { self: BaseSpec with GuiceOneAppPerSuite =>

  trait ViewTest {
    implicit lazy val baseFakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    implicit lazy val mcc: Messages = app.injector.instanceOf[MessagesApi].preferred(baseFakeRequest)
    
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
}
