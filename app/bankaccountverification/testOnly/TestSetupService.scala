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

import bankaccountverification.AppConfig
import bankaccountverification.api.InitResponse
import bankaccountverification.models.HttpErrorResponse
import play.api.http.HeaderNames
import play.api.http.Status.OK
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TestSetupService @Inject()(
                                  client: HttpClientV2,
                                  appConfig: AppConfig
                                )(implicit ec: ExecutionContext) {

  def makeInitCall(jsonBody: JsValue)(implicit headerCarrier: HeaderCarrier): Future[Either[HttpErrorResponse, InitResponse]] = {
    val url = s"${appConfig.bavfeBaseUrl}${bankaccountverification.api.routes.ApiV3Controller.init.url}"
    
    client.post(url"$url")
          .setHeader(HeaderNames.USER_AGENT -> "test-setup")
          .withBody(jsonBody)
          .execute
          .map { response =>
            response.status match {
              case OK =>
                Right(response.json.as[InitResponse])
              case errorStatus =>
                val finalModel = response.json.validate[HttpErrorResponse](
                  HttpErrorResponse.format.orElse(HttpErrorResponse.errorsFormat)
                )
                Left(finalModel.fold(
                  _ => HttpErrorResponse(s"Something went wrong with an unexpected status of '$errorStatus'"),
                  error => error
                ))
            }
          }
          .recover { case throwable: Throwable =>
            Left(HttpErrorResponse(throwable.getMessage))
          }
  }
  
  def completeCall(journeyId: String)(implicit headerCarrier: HeaderCarrier): Future[Either[HttpErrorResponse, JsValue]] = {
    val url = s"${appConfig.bavfeBaseUrl}${bankaccountverification.api.routes.ApiV3Controller.complete(journeyId)}"
    
    client.get(url"$url")
          .setHeader(HeaderNames.USER_AGENT -> "test-setup")
          .execute
          .map { response =>
            response.status match {
              case OK => Right(response.json)
              case unexpectedStatus@_ => Left(HttpErrorResponse(s"Unexpected status: $unexpectedStatus\nError body: ${response.body}")) 
            }
          }
          .recover { case throwable: Throwable =>
            Left(HttpErrorResponse(throwable.getMessage))
          }
  }
  
}
