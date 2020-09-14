# bank-account-verification-frontend

`bank-account-verification-frontend` (`BAVFE`) is intended to provide a single common frontend implementation for capturing, validating and verifying bank account information.
It provides mechanisms to customise messaging, eg page titles, element labels, etc, to ensure that it _blends_ with the calling service.

For an example of a service that utilises this service, see [bank-account-verification-example-frontend](https://github.com/hmrc/bank-account-verification-example-frontend) (`BEVEFE`)

## Flow
There are 3 parts to the usage of `BAVFE`:
1. Initiate a journey
   This sets up a new journey with a unique id (provided by `BAVFE`) and customisation parameters. 
2. Perform the journey 
   This hands control over to `BAVFE` and progresses the journey to capture, validate and verify bank account details.
3. Complete the journey
   This involves a final call from the calling service to `BAVFE` to collect entered data as well as the results of verification.

In steps 2 & 3, if an invalid `journeyId` is provided or is missing, a `BadRequest` or 'NotFound' is returned respectively.

### Initiate a journey
To initiate a journey a `POST` call must be made to `/api/init` with a payload that has the following data model:
```scala
case class InitRequest(
    serviceIdentifier: String, 
    continueUrl: String, 
    address: Option[InitRequestAddress] = None,
    messages: Option[InitRequestMessages] = None, 
    customisationsUrl: Option[String] = None
)
```

As can be seen, the only mandatory parameters are `serviceIdentifier` - the name of the calling service, and `continueUrl` - the callback url to call when journey in `BAVFE` is completed. The `customisationsUrl` is the url that will be called to retrieve `HtmlPartials` for customisation of `header`, `beforeContent` and `footer` sections of the pages. See [CustomisationsController](https://github.com/hmrc/bank-account-verification-example-frontend/blob/master/app/uk/gov/hmrc/bankaccountverificationexamplefrontend/bavf/CustomisationsController.scala) for an example implementation.

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

To see what messages can be customised, take a look at (message.en)[https://github.com/hmrc/bank-account-verification-frontend/blob/master/conf/messages.en]. Every message defined in this file can be customised. Bear in mind that, where welsh is supported, welsh customisations should be provided for every english customisation.

### Perform the journey
Once the initiating call has been made and a journeyId retrieved, the journey can be started with a call to the `/bank-account-verification/start/:journeyId` where `:journeyId` is journey id returned by the initiate call.
Control at this stage passes to `BAVFE` until the journey is complete, at which point `BAVFE` calls back to the `continueUrl` provided in the initiate call to indicate to the initiating service that the journey is complete and results can be retreived.

### Complete the journey
Once the `continueUrl` has been called by `BAVFE`, a call can be made to `/api/complete/:journeyId` to retrieve the results of the journey. The following data model describes the payload that is returned:

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
    accountNumberWithSortCodeIsValid: ReputationResponseEnum,
    rollNumber: Option[String] = None,
    accountExists: Option[ReputationResponseEnum] = None,
    nameMatches: Option[ReputationResponseEnum] = None,
    addressMatches: Option[ReputationResponseEnum] = None,
    nonConsented: Option[ReputationResponseEnum] = None,
    subjectHasDeceased: Option[ReputationResponseEnum] = None,
    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None
)
```
`business` has the following data model:
```scala
case class BusinessCompleteResponse(
    address: Option[CompleteResponseAddress],
    companyName: String,
    companyRegistrationNumber: Option[String],
    sortCode: String,
    accountNumber: String,
    rollNumber: Option[String] = None,
    accountNumberWithSortCodeIsValid: ReputationResponseEnum,
    accountExists: Option[ReputationResponseEnum] = None,
    companyNameMatches: Option[ReputationResponseEnum],
    companyPostCodeMatches: Option[ReputationResponseEnum],
    companyRegistrationNumberMatches: Option[ReputationResponseEnum],
    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None
  )
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
