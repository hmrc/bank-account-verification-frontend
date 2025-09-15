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

import bankaccountverification.models.HttpErrorResponse
import play.api.libs.json.{JsObject, Json}
import utils.BaseSpec

class HttpErrorResponseSpec extends BaseSpec {

  val singleErrorJson: JsObject = Json.obj(
    "error" -> "Some error"
  )
  
  val multipleErrorsJson: JsObject = Json.obj(
    "errors" -> Json.arr(
      "some error",
      "some other error"
    )
  )
  
  val singleErrorModel: HttpErrorResponse = HttpErrorResponse("Some error")
  val multipleErrorsModel = HttpErrorResponse("some error, some other error")
  
  "HttpErrorResponse" should {
    
    "parse a single error" in {
      singleErrorJson.as[HttpErrorResponse] shouldBe singleErrorModel
    }
    
    "parse multiple errors" in {
      multipleErrorsJson.as[HttpErrorResponse](HttpErrorResponse.errorsFormat) shouldBe multipleErrorsModel
    }
    
    "parse into json" in {
      singleErrorModel.asJson shouldBe singleErrorJson
      multipleErrorsModel.asJson shouldBe Json.obj(
        "error" -> "some error, some other error"
      )
    }
  }
  
}
