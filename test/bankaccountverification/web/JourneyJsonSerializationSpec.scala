/*
 * Copyright 2021 HM Revenue & Customs
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

import bankaccountverification.BACSRequirements

import java.time.{ZoneOffset, ZonedDateTime}
import bankaccountverification.connector.ReputationResponseEnum._
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import bankaccountverification.{BusinessSession, Journey, PersonalSession, Session, TimeoutConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

class JourneyJsonSerializationSpec extends AnyWordSpec with Matchers {
  "Journey" when {
    "PersonalAccountDetails" should {
      val id = BSONObjectID.generate()
      val theExpiryDate = ZonedDateTime.now(ZoneOffset.UTC)
      val personalJourney = Journey(
        id = id,
        Some("1234"),
        expiryDate = theExpiryDate,
        serviceIdentifier = "some-service",
        continueUrl = "some-url",
        messages = None,
        customisationsUrl = None,
        data = Session(
          accountType = Some(Personal),
          personal = Some(PersonalSession(
            accountName = Some("an-account-name"),
            sortCode = Some("123498"),
            accountNumber = Some("98765432"),
            rollNumber = Some("A.BC"),
            accountNumberWithSortCodeIsValid = Some(Yes),
            accountExists = Some(Indeterminate),
            nameMatches = Some(No),
            nonConsented = Some(Indeterminate),
            subjectHasDeceased = Some(Inapplicable),
            nonStandardAccountDetailsRequiredForBacs = Some(Error),
            sortCodeBankName = Some("sort-code-bank-name")
          )),
          business = None
        ),
        bacsRequirements = Some(BACSRequirements(true, false)),
        timeoutConfig = Some(TimeoutConfig("url", 100, Some("keepAlive")))
      )

      import Journey._

      val personalJourneyJsValue = Json.toJson(personalJourney)

      "serialize to JSON correctly" in {

        (personalJourneyJsValue \ "_id").as[BSONObjectID] shouldBe id
        (personalJourneyJsValue \ "authProviderId").as[String] shouldBe "1234"
        (personalJourneyJsValue \ "expiryDate").as[ZonedDateTime] shouldBe theExpiryDate
        (personalJourneyJsValue \ "serviceIdentifier").as[String] shouldBe "some-service"
        (personalJourneyJsValue \ "continueUrl").as[String] shouldBe "some-url"
        (personalJourneyJsValue \ "messages").isEmpty shouldBe true
        (personalJourneyJsValue \ "customisationsUrl").isEmpty shouldBe true

        (personalJourneyJsValue \ "data" \ "personal").isEmpty shouldBe false

        (personalJourneyJsValue \ "data" \ "accountType").as[String] shouldBe "personal"
        (personalJourneyJsValue \ "data" \ "personal" \ "accountName").as[String] shouldBe "an-account-name"
        (personalJourneyJsValue \ "data" \ "personal" \ "sortCode").as[String] shouldBe "123498"
        (personalJourneyJsValue \ "data" \ "personal" \ "accountNumber").as[String] shouldBe "98765432"
        (personalJourneyJsValue \ "data" \ "personal" \ "rollNumber").as[String] shouldBe "A.BC"
        (personalJourneyJsValue \ "data" \ "personal" \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (personalJourneyJsValue \ "data" \ "personal" \ "accountExists").as[String] shouldBe "indeterminate"
        (personalJourneyJsValue \ "data" \ "personal" \ "nameMatches").as[String] shouldBe "no"
        (personalJourneyJsValue \ "data" \ "personal" \ "nonConsented").as[String] shouldBe "indeterminate"
        (personalJourneyJsValue \ "data" \ "personal" \ "subjectHasDeceased").as[String] shouldBe "inapplicable"
        (personalJourneyJsValue \ "data" \ "personal" \ "nonStandardAccountDetailsRequiredForBacs").as[String] shouldBe "error"
        (personalJourneyJsValue \ "data" \ "personal" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name"

        (personalJourneyJsValue \ "data" \ "business").isEmpty shouldBe true

        (personalJourneyJsValue \ "directDebitConstraints" \ "directDebitRequired").as[Boolean] shouldBe true
        (personalJourneyJsValue \ "directDebitConstraints" \ "directCreditRequired").as[Boolean] shouldBe false

        (personalJourneyJsValue \ "timeoutConfig" \ "timeoutUrl").as[String] shouldBe "url"
        (personalJourneyJsValue \ "timeoutConfig" \ "timeoutAmount").as[Int] shouldBe 100
        (personalJourneyJsValue \ "timeoutConfig" \ "timeoutKeepAliveUrl").as[String] shouldBe "keepAlive"
      }

      "de-serialize from JSON correctly" in {
        val personalJourneyFromJsValueResult = Json.fromJson[Journey](personalJourneyJsValue)

        personalJourneyFromJsValueResult.isSuccess shouldBe true

        val personalJourneyFromJsValue = personalJourneyFromJsValueResult.get

        personalJourneyFromJsValue shouldEqual personalJourney
      }
    }

    "BusinessAccountDetails" should {
      val id = BSONObjectID.generate()
      val theExpiryDate = ZonedDateTime.now(ZoneOffset.UTC)
      val businessJourney = Journey(
        id = id,
        Some("1234"),
        expiryDate = theExpiryDate,
        serviceIdentifier = "some-service",
        continueUrl = "some-url",
        messages = None,
        customisationsUrl = None,
        data = Session(
          accountType = Some(Business),
          business = Some(BusinessSession(
            companyName = Some("a-company-name"),
            sortCode = Some("123498"),
            accountNumber = Some("98765432"),
            rollNumber = Some("A.BC"),
            accountNumberWithSortCodeIsValid = Some(Yes),
            accountExists = Some(Indeterminate),
            companyNameMatches = Some(No),
            companyPostCodeMatches = Some(Indeterminate),
            companyRegistrationNumberMatches = Some(Inapplicable),
            nonStandardAccountDetailsRequiredForBacs = Some(Error),
            sortCodeBankName = Some("sort-code-bank-name-business")
          )),
          personal = None
        ),
        bacsRequirements = Some(BACSRequirements(false, true)),
        timeoutConfig = Some(TimeoutConfig("url", 100, Some("keepAlive")))
      )

      import Journey._

      val businessJourneyJsValue = Json.toJson(businessJourney)

      "serialize to JSON correctly" in {

        (businessJourneyJsValue \ "_id").as[BSONObjectID] shouldBe id
        (businessJourneyJsValue \ "authProviderId").as[String] shouldBe "1234"
        (businessJourneyJsValue \ "expiryDate").as[ZonedDateTime] shouldBe theExpiryDate
        (businessJourneyJsValue \ "serviceIdentifier").as[String] shouldBe "some-service"
        (businessJourneyJsValue \ "continueUrl").as[String] shouldBe "some-url"
        (businessJourneyJsValue \ "messages").isEmpty shouldBe true
        (businessJourneyJsValue \ "customisationsUrl").isEmpty shouldBe true

        (businessJourneyJsValue \ "data" \ "business").isEmpty shouldBe false

        (businessJourneyJsValue \ "data" \ "accountType").as[String] shouldBe "business"
        (businessJourneyJsValue \ "data" \ "business" \ "companyName").as[String] shouldBe "a-company-name"
        (businessJourneyJsValue \ "data" \ "business" \ "sortCode").as[String] shouldBe "123498"
        (businessJourneyJsValue \ "data" \ "business" \ "accountNumber").as[String] shouldBe "98765432"
        (businessJourneyJsValue \ "data" \ "business" \ "rollNumber").as[String] shouldBe "A.BC"
        (businessJourneyJsValue \ "data" \ "business" \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (businessJourneyJsValue \ "data" \ "business" \ "accountExists").as[String] shouldBe "indeterminate"
        (businessJourneyJsValue \ "data" \ "business" \ "companyNameMatches").as[String] shouldBe "no"
        (businessJourneyJsValue \ "data" \ "business" \ "companyPostCodeMatches").as[String] shouldBe "indeterminate"
        (businessJourneyJsValue \ "data" \ "business" \ "companyRegistrationNumberMatches").as[String] shouldBe "inapplicable"
        (businessJourneyJsValue \ "data" \ "business" \ "nonStandardAccountDetailsRequiredForBacs").as[String] shouldBe "error"
        (businessJourneyJsValue \ "data" \ "business" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-business"

        (businessJourneyJsValue \ "data" \ "personal").isEmpty shouldBe true

        (businessJourneyJsValue \ "directDebitConstraints" \ "directDebitRequired").as[Boolean] shouldBe false
        (businessJourneyJsValue \ "directDebitConstraints" \ "directCreditRequired").as[Boolean] shouldBe true

        (businessJourneyJsValue \ "timeoutConfig" \ "timeoutUrl").as[String] shouldBe "url"
        (businessJourneyJsValue \ "timeoutConfig" \ "timeoutAmount").as[Int] shouldBe 100
        (businessJourneyJsValue \ "timeoutConfig" \ "timeoutKeepAliveUrl").as[String] shouldBe "keepAlive"
      }

      "de-serialize from JSON correctly" in {
        val businessJourneyFromJsValueResult = Json.fromJson[Journey](businessJourneyJsValue)

        businessJourneyFromJsValueResult.isSuccess shouldBe true

        val businessJourneyFromJsValue = businessJourneyFromJsValueResult.get

        businessJourneyFromJsValue shouldEqual businessJourney
      }

    }
  }
}
