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

package bankaccountverification.testOnly

import bankaccountverification.api.InitResponse
import bankaccountverification.models.HttpErrorResponse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import play.api.Application
import play.api.http.Status.BAD_REQUEST
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import utils.{MockitoTestSpec, ViewTestHelpers}

import scala.concurrent.Future

class TestSetupControllerSpec extends MockitoTestSpec with ViewTestHelpers {
  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockService: TestSetupService = mock[TestSetupService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, mockService)
  }
  
  def mockAuth(isSuccessful: Boolean = true): OngoingStubbing[Future[Unit]] = {
    val result: Future[Unit] = if (isSuccessful) Future.successful() else Future.failed(AuthorisationException.fromString("MissingBearerToken"))
    
    when(mockAuthConnector.authorise[Unit](any(), any())(any(), any()))
      .thenReturn(result)
  }
  
  def mockInitCall(result: Either[HttpErrorResponse, InitResponse]): OngoingStubbing[Future[Either[HttpErrorResponse, InitResponse]]] = {
    when(mockService.makeInitCall(any())(any()))
      .thenReturn(Future.successful(result))
  }
  
  def mockCompleteCall(): OngoingStubbing[Future[Either[HttpErrorResponse, JsValue]]] = {
    val result = Future.successful(Right(Json.obj(
      "key1" -> "value1",
      "key2" -> "value2",
      "key3" -> "value3",
      "key4" -> "value4"
    )))
    
    when(mockService.completeCall(any())(any()))
      .thenReturn(result)
  }

  lazy val app: Application = {
    new GuiceApplicationBuilder()
      .overrides(bind[AuthConnector].to(mockAuthConnector))
      .overrides(bind[TestSetupService].to(mockService))
      .build()
  }

  lazy val controller: TestSetupController = app.injector.instanceOf[TestSetupController]
  
  lazy val expectedWarningContent: String = "Warning All fields except for serviceIdentifier and continueURL are optional"
  lazy val expectedJsonExample: String = controller.fullExampleJson
  
  ".show" should {
    
    "render the page successfully" when {
      
      "the user is authorised" which {
        implicit lazy val viewTest: PageViewTest = new PageViewTest {
          mockAuth()
          
          override val call: Future[Result] = controller.show().apply(baseFakeRequest)
          override val title: String = "Stub starting a new BAVFE journey for V3 - GOV.UK"
          override val heading: String = "Stub starting a new BAVFE journey for V3"
        }
        
        viewTest.basicChecks()
        checkTextArea(
          Some(Json.prettyPrint(Json.obj(
            "serviceIdentifier" -> "bank-account-verification-frontend",
            "continueUrl" -> "/bank-account-verification/test-only/test-complete"
          ))),
          "#json-setup"
        )
        checkDetails(
          "Example of a full json model",
          "#json-example",
          DetailsCheck(expectedJsonExample, Some("pre > code"))
        )
        checkContentOnPage(expectedWarningContent, ".govuk-warning-text__text")
        checkButton("Start new journey", selector = "#main-content > div > div > form > button")
      }
    }
    
    "redirect the user to the auth login stub" when {
      
      "the user is not authorised" which {
        implicit lazy val viewTest: RedirectTest = new RedirectTest {
          mockAuth(isSuccessful = false)
          
          override val call: Future[Result] = controller.show().apply(baseFakeRequest)
          override val redirectUrl: String = "http://localhost:9949/auth-login-stub/gg-sign-in/?continue=http://localhost:9903/bank-account-verification/test-only/test-setup"
        }
        
        viewTest.basicChecks()
      }
    }
  }
  
  ".submit" should {
    lazy val fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "/bank-account-verification/test-only/test-setup")
      .withFormUrlEncodedBody(
        "json-setup" -> Json.prettyPrint(Json.obj(
          "serviceIdentifier" -> "bank-account-verification-frontend",
          "continueUrl" -> "/bank-account-verification/test-only/test-complete"
        ))
      )
    
    "render the setup page with an error" when {
      
      "invalid json is submitted" which {
        implicit lazy val viewTest: PageViewTest = new PageViewTest {
          mockAuth()
          mockInitCall(Right(InitResponse(
            "journeyId",
            "startUrl",
            "completeUrl",
            None
          )))

          val requestWithBadJson: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "/bank-account-verification/test-only/test-setup")
            .withFormUrlEncodedBody("json-setup" -> "this is not json")
          
          override val call: Future[Result] = controller.submit().apply(requestWithBadJson)
          override val title: String = "Stub starting a new BAVFE journey for V3 - GOV.UK"
          override val heading: String = "Stub starting a new BAVFE journey for V3"
        }

        viewTest.basicChecks(BAD_REQUEST)
        checkContentOnPage("There is a problem", "#main-content > div > div > div > div > h2")
        checkLinkOnPage("Must be a valid json structure.", "#json-setup", "#main-content > div > div > div > div > div > ul > li > a")
      }
      
      "valid json is submitted, but it cannot be parsed into the InitRequest model" which {
        implicit lazy val viewTest: PageViewTest = new PageViewTest {
          mockAuth()
          mockInitCall(Right(InitResponse(
            "journeyId",
            "startUrl",
            "completeUrl",
            None
          )))

          val requestWithBadJson: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "/bank-account-verification/test-only/test-setup")
            .withFormUrlEncodedBody("json-setup" -> Json.prettyPrint(Json.obj(
              "someField" -> "not ganna parse"
            )))

          override val call: Future[Result] = controller.submit().apply(requestWithBadJson)
          override val title: String = "Stub starting a new BAVFE journey for V3 - GOV.UK"
          override val heading: String = "Stub starting a new BAVFE journey for V3"
        }

        viewTest.basicChecks(BAD_REQUEST)
        checkContentOnPage("There is a problem", "#main-content > div > div > div > div > h2")
        checkLinkOnPage("Must match the expected json structure of the InitRequest model.", "#json-setup", "#main-content > div > div > div > div > div > ul > li > a")
      }
    }
    
    "make the v3 init call and redirect to the start url it returns" when {
      
      "the user is authorised and the init call is successful" which {
        implicit lazy val viewTest: RedirectTest = new RedirectTest {
          override val redirectUrl: String = "startUrl"
          override val call: Future[Result] = {
            mockAuth()
            mockInitCall(Right(InitResponse(
              "journeyId",
              "startUrl",
              "completeUrl",
              None
            )))
            controller.submit().apply(fakeRequest)
          }
        }
        
        viewTest.basicChecks()
      }
    }
    
    "return a BadRequest" when {
      
      "the init call fails" which {
        implicit lazy val viewTest: ErrorTest = new ErrorTest {
          override val call: Future[Result] = {
            mockAuth()
            mockInitCall(Left(HttpErrorResponse("Something went wrong")))
            controller.submit().apply(fakeRequest)
          }
        }
        
        viewTest.basicChecks()
      }
    }
    
    "redirects to the auth login stub" when {
      
      "the user is not authorised" which {
        
        implicit lazy val viewTest: RedirectTest = new RedirectTest {
          override val call: Future[Result] = {
            mockAuth(isSuccessful = false)
            controller.submit().apply(fakeRequest)
          }
          override val redirectUrl: String = "http://localhost:9949/auth-login-stub/gg-sign-in/?continue=http://localhost:9903/bank-account-verification/test-only/test-setup"
        }
        
        viewTest.basicChecks()
      }
    }
  }
  
  ".complete(:journeyId)" should {
    
    "display the journey complete page" when {
      
      "the user is authorised" which {
        
        implicit lazy val viewTest: PageViewTest = new PageViewTest {
          mockAuth()
          mockCompleteCall()
          
          override val call: Future[Result] = controller.complete("journeyId").apply(baseFakeRequest)
          override val title: String = "Bank Account Verification Result - GOV.UK"
          override val heading: String = "Bank Account Verification Result"
        }
        
        viewTest.basicChecks()
        checkContentOnPage(
          Json.prettyPrint(Json.obj(
            "key1" -> "value1",
            "key2" -> "value2",
            "key3" -> "value3",
            "key4" -> "value4"
          )),
          "#main-content > div > div > pre > code"
        )
        checkButton(
          "Return to BAVFE test setup",
          selector = "#main-content > div > div > a",
          href = Some("/bank-account-verification/test-only/test-setup")
        )
      }
    }
  }
  
}
