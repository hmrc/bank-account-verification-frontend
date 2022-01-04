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

package bankaccountverification.connector

import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results._
import play.api.routing.sird.{POST => SPOST, _}
import play.api.test.Helpers._
import play.core.server.{Server, ServerConfig}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class BankAccountReputationConnectorSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  val barsPort = 11222
  override lazy val app =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.bank-account-reputation.port" -> barsPort
      )
      .build()

  "Validate bank details" should {

    "handle a 200 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") =>
            Action(Ok(
            """{
              |    "accountNumberWithSortCodeIsValid": "yes",
              |    "nonStandardAccountDetailsRequiredForBacs": "no",
              |    "sortCodeIsPresentOnEISCD": "error"
              |}""".stripMargin).withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe Success(BarsValidationResponse(Yes, No, None))
      }
    }

    "handle a 200 json response that differs from the expected format" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") => Action(Ok(
            """{
              |    "accountNumberIsWellFormatted": "yes",
              |    "OWAITWHATISTHIS": "no",
              |    "sortCodeIsPresentMEGALOLSOnEISCD": "error"
              |}""".stripMargin).withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 200 non-json response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") =>
            Action(Ok("NOJSON4U").withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 500 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") => Action(InternalServerError)
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe a[Failure[_]]
      }
    }
  }

  "Personal assess" should {

    "handle a 200 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case r @ SPOST(p"/verify/personal") =>
            r.headers.get("True-Calling-Client") shouldBe Some("example-service")
            Action(Ok(
            """{
              |  "accountNumberIsWellFormatted": "yes",
              |  "accountExists": "yes",
              |  "nameMatches": "yes",
              |  "sortCodeIsPresentOnEISCD": "yes",
              |  "sortCodeSupportsDirectDebit": "yes",
              |  "sortCodeSupportsDirectCredit": "yes",
              |  "nonStandardAccountDetailsRequiredForBacs": "no"
              |}""".stripMargin).withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessPersonal("Mr Joe Bloggs", "20-30-40", "12345678", None, "example-service"))
        response shouldBe Success(
          BarsPersonalAssessSuccessResponse(Yes, Yes, Yes, Yes, Yes, Yes, Some(No), None, None)
        )
      }
    }

    "handle a 200 json response that differs from the expected format" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/personal") =>
            Action(
              Ok(
                """{
                  |    "accountNumberIsWellFormatted": "yes",
                  |    "OWAITWHATISTHIS": "no",
                  |    "sortCodeIsPresentMEGALOLSOnEISCD": "error"
                  |}""".stripMargin)
                .withHeaders("Content-Type" -> "application/json")
            )
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessPersonal("Joe Bloggs", "203040", "12345678", None, "example-service"))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 200 non-json response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/personal") =>
            Action(Ok("NOJSON4U").withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessPersonal("Joe Bloggs", "203040", "12345678", None, "example-service"))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 400 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/personal") => Action(BadRequest(
            """{"code": "SORT_CODE_ON_DENY_LIST", "desc": "083200: sort code is in deny list"}"""))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessPersonal("Joe Bloggs", "203040", "12345678", None, "example-service"))
        response shouldBe a[Success[BarsPersonalAssessBadRequestResponse]]
      }
    }

    "handle a 500 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/personal") => Action(InternalServerError)
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessPersonal("Joe Bloggs", "203040", "12345678", None, "example-service"))
        response shouldBe a[Failure[_]]
      }
    }
  }

  "Business assess" should {

    "handle a 200 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case r @ SPOST(p"/verify/business") =>
            r.headers.get("True-Calling-Client") shouldBe Some("example-service")
            Action(Ok(
            """{
              |  "accountNumberIsWellFormatted": "yes",
              |  "sortCodeIsPresentOnEISCD": "yes",
              |  "sortCodeSupportsDirectDebit": "yes",
              |  "sortCodeSupportsDirectCredit": "yes",
              |  "sortCodeBankName": "Some Company",
              |  "accountExists": "yes",
              |  "nameMatches": "yes",
              |  "nonStandardAccountDetailsRequiredForBacs": "no"
              |}""".stripMargin).withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessBusiness("Some Company", None, "20-30-40", "12345678", None, "example-service"))
        response shouldBe Success(
          BarsBusinessAssessSuccessResponse(Yes, Yes, Some("Some Company"), Yes, Yes, Yes, Yes, Some(No), None)
        )
      }
    }

    "handle a 200 json response that differs from the expected format" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/business") =>
            Action(
              Ok(
                """{
                  |    "accountNumberIsWellFormatted": "yes",
                  |    "OWAITWHATISTHIS": "no",
                  |    "sortCodeIsPresentMEGALOLSOnEISCD": "error"
                  |}""".stripMargin)
                .withHeaders("Content-Type" -> "application/json")
            )
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessBusiness("Some Company", None, "20-30-40", "12345678", None, "example-service"))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 200 non-json response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/business") =>
            Action(Ok("NOJSON4U").withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessBusiness("Some Company", None, "20-30-40", "12345678", None, "example-service"))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 400 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/business") => Action(BadRequest(
            """{"code": "SORT_CODE_ON_DENY_LIST", "desc": "083200: sort code is in deny list"}"""))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessBusiness("Some Company", None, "20-30-40", "12345678", None, "example-service"))
        response shouldBe a[Success[BarsBusinessAssessBadRequestResponse]]
      }
    }

    "handle a 500 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/verify/business") => Action(InternalServerError)
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.assessBusiness("Some Company", None, "20-30-40", "12345678", None, "example-service"))
        response shouldBe a[Failure[_]]
      }
    }
  }
}
