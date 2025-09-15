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

package testOnly

import bankaccountverification.api.InitResponse
import bankaccountverification.models.HttpErrorResponse
import bankaccountverification.testOnly.TestSetupService
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.ItTestSpec

class TestSetupServiceSpec extends ItTestSpec {
  
  override lazy val app: Application = GuiceApplicationBuilder()
    .configure(
      "testOnly.url" -> s"http://localhost:$barsPort"
    )
    .build()

  lazy val service: TestSetupService = app.injector.instanceOf[TestSetupService]
  
  val initResponse = InitResponse(
    "journey-id",
    "startUrl",
    "completeUrl",
    None
  )
  
  ".makeInitCall" should {
    
    "return an init response" when {
      
      "the v3 init endpoint returns a successful response" in {
        barsPostTest("/api/v3/init", Ok(Json.prettyPrint(Json.toJson(initResponse)))) { _ =>
          implicit val hc: HeaderCarrier = HeaderCarrier()
          
          val result = await(service.makeInitCall(Json.obj()))
          result shouldBe Right(initResponse)
        }
      }
    }
    
    "return an error" when {
      
      "the v3 init endpoint returns an error" in {
        val jsonBody = Json.obj(
          "error" -> "some kind of error"
        )
        
        barsPostTest("/api/v3/init", BadRequest(jsonBody)) { _ =>
          implicit val hc: HeaderCarrier = HeaderCarrier()
          
          val result = await(service.makeInitCall(Json.obj()))
          
          val returnedError = HttpErrorResponse("""POST of 'http://localhost:11222/api/v3/init' returned 400 (Bad Request). Response body '{"error":"some kind of error"}'""")
          result shouldBe Left(returnedError)
        }
      }
    }
  }
  
  ".completeCall" should {
    
    "return the json" when {
      
      "the complete endpoint returns some json" in {
        val journeyId = "some-journey-id"
        
        val returnedJson = Json.obj(
          "key1" -> "value1",
          "key2" -> "value2",
          "key3" -> "value3",
          "key4" -> "value4"
        )
        
        barsGetTest(s"/api/v3/complete/$journeyId", Ok(returnedJson)) { _ =>
          implicit val hc: HeaderCarrier = HeaderCarrier()
          
          val result = await(service.completeCall(journeyId))
          result shouldBe Right(returnedJson)
        }
      }
    }
    
    "return an error" when {
      
      "the complete endpoint returns an error" in {
        val journeyId = "some-journey-id"
        
        barsGetTest(s"/api/v3/complete/$journeyId", BadRequest(Json.obj("error" -> "some kind of error"))) { _ =>
          implicit val hc: HeaderCarrier = HeaderCarrier()
          
          val result = await(service.completeCall(journeyId))
          result shouldBe Left(HttpErrorResponse("""GET of 'http://localhost:11222/api/v3/complete/some-journey-id' returned 400 (Bad Request). Response body '{"error":"some kind of error"}'"""))
        }
      }
    }
  }
  
}
