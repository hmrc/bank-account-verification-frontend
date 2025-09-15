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
import org.scalamock.handlers.{CallHandler2, CallHandler4}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.http.HeaderCarrier
import utils.{MockFactoryTestSpec, ViewTestHelpers}

import scala.concurrent.{ExecutionContext, Future}

class TestSetupControllerSpec extends MockFactoryTestSpec with ViewTestHelpers {

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockService: TestSetupService = mock[TestSetupService]
  
  override implicit lazy val app: Application = {
    new GuiceApplicationBuilder()
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .overrides(bind[TestSetupService].toInstance(mockService))
      .build()
  }
  
  lazy val controller: TestSetupController = app.injector.instanceOf[TestSetupController]
  
  def mockAuth(isSuccessful: Boolean = true): CallHandler4[Predicate, Retrieval[_], HeaderCarrier, ExecutionContext, Future[Any]] = {
    val result = if (isSuccessful) Future.successful() else Future.failed(AuthorisationException.fromString("MissingBearerToken"))
    
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[_])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returns(result)
  }
  
  def mockInitCall(result: Either[HttpErrorResponse, InitResponse]): CallHandler2[JsValue, HeaderCarrier, Future[Either[HttpErrorResponse, InitResponse]]] = {
    (mockService.makeInitCall(_: JsValue)(_: HeaderCarrier))
      .expects(*, *)
      .returns(Future.successful(result))
  }
  
  def mockCompleteCall(): CallHandler2[String, HeaderCarrier, Future[Either[HttpErrorResponse, JsValue]]] = {
    (mockService.completeCall(_: String)(_: HeaderCarrier))
      .expects(*, *)
      .returns(Future.successful(Right(Json.obj(
        "key1" -> "value1",
        "key2" -> "value2",
        "key3" -> "value3",
        "key4" -> "value4"
      ))))
  }
  
  ".show" should {
    
    "render the page successfully" when {
      
      "the user is authorised" which {
        mockAuth()
        
        implicit lazy val viewTest: PageViewTest = new PageViewTest {
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
        checkButton("Start new journey", selector = "#main-content > div > div > form > button")
      }
    }
    
    "redirect the user to the auth login stub" when {
      
      "the user is not authorised" which {
        mockAuth(isSuccessful = false)
        
        implicit lazy val viewTest: RedirectTest = new RedirectTest {
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
    
    "make the v3 init call and redirect to the start url it returns" when {
      
      "the user is authorised and the init call is successful" which {
        mockAuth()
        mockInitCall(Right(InitResponse(
          "journeyId",
          "startUrl",
          "completeUrl",
          None
        )))
        
        implicit lazy val viewTest: RedirectTest = new RedirectTest {
          override val redirectUrl: String = "startUrl"
          override val call: Future[Result] = controller.submit().apply(fakeRequest)
        }
        
        viewTest.basicChecks()
      }
    }
    
    "return a BadRequest" when {
      
      "the init call fails" which {
        mockAuth()
        mockInitCall(Left(HttpErrorResponse("Something went wrong")))
        
        implicit lazy val viewTest: ErrorTest = new ErrorTest {
          override val call: Future[Result] = controller.submit().apply(fakeRequest)
        }
        
        viewTest.basicChecks()
      }
    }
    
    "redirects to the auth login stub" when {
      
      "the user is not authorised" which {
        mockAuth(isSuccessful = false)
        
        implicit lazy val viewTest: RedirectTest = new RedirectTest {
          
          override val call: Future[Result] = controller.submit().apply(fakeRequest)
          override val redirectUrl: String = "http://localhost:9949/auth-login-stub/gg-sign-in/?continue=http://localhost:9903/bank-account-verification/test-only/test-setup"
        }
        
        viewTest.basicChecks()
      }
    }
  }
  
  ".complete(:journeyId)" should {
    
    "display the journey complete page" when {
      
      "the user is authorised" which {
        mockAuth()
        mockCompleteCall()
        
        implicit lazy val viewTest: PageViewTest = new PageViewTest {
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
