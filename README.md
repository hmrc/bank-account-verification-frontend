# Bank Account Verification Frontend

`bank-account-verification-frontend` (`BAVFE`) provides a common frontend implementation for client services that need to capture, validate and verify bank account information.
It provides mechanisms to customise messaging, eg page titles, element labels, etc, to ensure that it _blends_ with the calling service.

**Please note**, you are viewing documentation for the latest version of the `BAVFE` API (v2). [You can view the readme for the old version of the API here.](https://github.com/hmrc/bank-account-verification-frontend/blob/main/README-v1.md)

## Run this service locally
There are two steps you must take in order to run this service.
1. Run the dependencies by running
    ```bash
    sm2 --start BANK_ACCOUNT_VERIFICATION
    ```
2. Run the stubbed reputation third parties microservice
    ```bash
   sm2 --start BANK_ACCOUNT_REPUTATION_THIRD_PARTIES_STUB
    ```
3. Stop the front end with
   ```bash
   sm2 --stop BANK_ACCOUNT_VERIFICATION_FRONTEND
   ```
   (or do step 3 first)
4. Run the `runLocal.sh` file to start the service.

## Using the test-only setup endpoint
The service supports a test-only endpoint that allows you to set up a journey with any parameters you want. This comes prefilled with a complete endpoint that is prefilled for you that displays the json you get back from the api.

The endpoint for this is : `/bank-account-verification/test-only/test-setup`

The complete endpoint (which is prefilled for you) is : `/bank-account-verification/test-only/test-complete` (this gets the journey id placed on the end automatically).


## Flow
There are 3 parts to the usage of `BAVFE`:
1. **Initiate a journey** - Initialises a new journey with a unique id (provided by `BAVFE`) and customisation parameters. 
2. **Perform the journey** - Hands control to `BAVFE` which progresses the journey to capture, validate and verify bank account details.
3. **Complete the journey** - The client service calls `BAVFE` to collect the entered data and verification results. Completed journeys will have a sort code, account number and roll number that are valid and appropriately populated, plus an `accountExists` result of `yes`, `indeterminate` or `error`. A result of `error` occurs when there was a problem with the third party API used to populate these fields. Such errors are infrequent, but do occur. In such cases, we recommend the user journey proceed as normal. 

In steps 2 & 3, if an invalid `journeyId` is provided or is missing, a `BadRequest` or `NotFound` is returned respectively.

### Initiate a journey
To initiate a journey a `POST` call must be made to `/api/v2/init` with a payload that has the following data model:
```scala
case class InitRequest(
    serviceIdentifier: String, 
    continueUrl: String,
    prepopulatedData: Option[InitRequestPrepopulatedData] = None,
    address: Option[InitRequestAddress] = None,
    messages: Option[InitRequestMessages] = None, 
    customisationsUrl: Option[String] = None,
    bacsRequirements: Option[InitBACSRequirements] = None,
    timeoutConfig: Option[InitRequestTimeoutConfig], 
    signOutUrl: Option[String] = None,
    maxCallConfig: Option[InitRequestMaxCallConfig] = None,
    useNewGovUkServiceNavigation: Option[Boolean] = None
)
```
```scala
case class InitRequestTimeoutConfig(timeoutUrl: String, timeoutAmount: Int, timeoutKeepAliveUrl: Option[String])
// timeoutUrl must be a relative url, or a full url with a host that has been allow-listed

// This is passed to configure direct debit/credit payment support requirements. The default, if not specified, is to require both debit and credit support.
case class InitBACSRequirements(directDebitRequired: Boolean, directCreditRequired: Boolean)

// This is passed to limit the number of attempts a user has to enter valid account details. The default, if not specified, is to allow infinite attempts.
// If they enter invalid details ${count} times, they will be sent back to the ${redirectUrl}
case class InitRequestMaxCallConfig(count: Int, redirectUrl: String)
```

The init endpoint will respond in the following format:
```scala
case class InitResponse(
  journeyId: String, 
  startUrl: String, // relative URL for you to redirect to begin the journey
  completeUrl: String, // relative API URL for you to retrieve data at the end of the journey
  detailsUrl: Option[String]) // relative URL for you to redirect to the account details page, skipping the account type question.
```

Please note, the `detailsUrl` will only be populated if you provide the `prePopulatedData` block on the init call, and allows you to skip asking for account type for example if you already have this information.

As can be seen, the only mandatory parameters are `serviceIdentifier` - the name of the calling service, and `continueUrl` - the callback url to call the `BAVFE` journey segment is completed. The `customisationsUrl` is used by `BAVFE` to retrieve `HtmlPartials` for customisation of `header`, `beforeContent` and `footer` sections of the pages.

#### Pre-populating data
If you have already captured some of the relevant bank account information in another part of your journey, you can supply this on the init call and BAVFEFE will pre-populate the relevant fields for you. You can also supply prepopulated data to support a 'Check your answers' flow between your service and BAVFEFE.

You must supply an `accountType` in order to use this feature.

The optional `prepopulatedData` parameter has the following model:
```scala
case class InitRequestPrepopulatedData(
  accountType: AccountTypeRequestEnum, // "personal" or "business" (lower case)
  name: Option[String] = None,
  sortCode: Option[String] = None,
  accountNumber: Option[String] = None,
  rollNumber: Option[String] = None)
```

#### Other parameters

The optional `address` parameter has the following model:
```scala
case class InitRequestAddress(
  lines: List[String], 
  town: Option[String], 
  postcode: Option[String]
)
```

The optional `messages` parameter has the following model:
```scala
case class InitRequestMessages(
    en: JsObject, 
    cy: Option[JsObject]
)
```

All messages contained in [message.en](https://github.com/hmrc/bank-account-verification-frontend/blob/master/conf/messages.en) can be customised. If the client service supports Welsh translations, then Welsh customisations should be provided for every English equivalent.

Messages that appear on the account details screen by default are used for both personal and business account flows. In order to customise these per account type, you can append ".business" or ".personal" and these will take precedence in the appropriate journey, e.g:

```scala
InitRequestMessages(
    en = Json.obj(
      "service.name" -> "My service"
      "label.accountDetails.heading.business" -> "Business bank or building society account details",
      "label.accountDetails.heading.personal" -> "Personal bank or building society account details",
    ))
```

### Perform the journey
Once the initiating call has been made and a `journeyId` retrieved, the journey can be started with a call to `/bank-account-verification/start/:journeyId` where `:journeyId` is that returned by the `initiate` call.

Control at this stage passes to `BAVFE` until the journey is complete. At the end of the journey, `BAVFE` calls back to the `continueUrl` (provided in the initiate call) to indicate to the client service that the journey is complete and results can be retrieved.

It should be noted that the `InitBACSRequirements` settings will influence how the form level validation is handled in `BAVFE`. If a requirement is not met, the journey will not be permitted to continue. 

### Complete the journey
Once the `continueUrl` has been called by `BAVFE`, a call can be made to `/api/v2/complete/:journeyId` to retrieve the results of the journey. The following data model describes the payload that is returned:

```scala
case class CompleteResponse(
    accountType: AccountTypeRequestEnum, 
    personal: Option[PersonalCompleteResponse],
    business: Option[BusinessCompleteResponse]
)
``` 

`accountType` can be either `personal` or `business`. This corresponds to the `personal` being defined or the `business` being defined in `CompleteResponse` above.

`personal` has the following data model:
```scala
case class PersonalCompleteResponse(
    address: Option[CompleteResponseAddress],
    accountName: String,
    sortCode: String,
    accountNumber: String,
    accountNumberIsWellFormatted: ReputationResponseEnum,
    rollNumber: Option[String] = None,
    accountExists: Option[ReputationResponseEnum] = None,
    nameMatches: Option[ReputationResponseEnum] = None,
    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
    sortCodeBankName: Option[String] = None,
    iban: Option[String] = None
)
```
`business` has the following data model:
```scala
case class BusinessCompleteResponse(
    address: Option[CompleteResponseAddress],
    companyName: String,
    sortCode: String,
    accountNumber: String,
    rollNumber: Option[String] = None,
    accountNumberIsWellFormatted: ReputationResponseEnum,
    accountExists: Option[ReputationResponseEnum] = None,
    companyNameMatches: Option[ReputationResponseEnum],
    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
    sortCodeBankName: Option[String] = None,
    iban: Option[String] = None
  )
```
#### Handling error values
The `ReputationResponseEnum` range of values includes `error`. This occurs when there was a problem with the third party API used to populate these fields. Such errors are infrequent, but do occur. In such cases, we recommend the user journey proceed as normal. 

### Supporting a 'Check your answers flow'
At the end of your journey, you may display information provided by the user in a summary screen and allow them to skip back to a specific section in order to amend their answers. You may want to configure this summary to show data gathered by your service as well as the data gathered by BAVFEFE.

In order to allow the user to change the data gathered by BAVFEFE, you should handle the 'change' link by initiating a *new journey* with BAVFEFE and using the `prepopulatedData` block to provide the previous answers. You should also set the `continueUrl` to a seperate landing page in your service that then returns to the 'Check your answers' page at the end of the flow.

### Writing acceptance tests for a service that uses `BAVFE` 

We suggest that you stub or mock out `BAVFE` in any acceptance tests that you write. This will speed up your tests and allow us to make changes to the GUI without affecting your tests.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
