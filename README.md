# telecom-ivr-api

REST API backing a **Cisco Call Studio IVR** flow for a telecom operator. It exposes four
operations the IVR calls mid-conversation: a VIP lookup used to route the caller, a prepaid
balance recharge, a balance transfer between subscribers, and a templated SMS send.

| # | Endpoint                                  | Purpose                                  |
|---|-------------------------------------------|------------------------------------------|
| 1 | `GET /customer/{phoneNumber}/vip-status`  | Is this caller a VIP?                    |
| 2 | `POST /balance/recharge`                  | Top up a balance from a payment card     |
| 3 | `POST /balance/transfer`                  | Move balance between two subscribers     |
| 4 | `POST /sms`                               | Send a templated SMS to a subscriber     |

Built with Spring Boot 3.3.4 on Java 17. **This phase has no database** — all logic is in-memory
and stateless (see [Design decision: no database yet](#design-decision-no-database-yet)).

---

## Table of contents

- [How the IVR uses this API](#how-the-ivr-uses-this-api)
- [Running the project](#running-the-project)
- [Endpoints](#endpoints)
  - [GET /customer/{phoneNumber}/vip-status](#get-customerphonenumbervip-status)
  - [POST /balance/recharge](#post-balancerecharge)
  - [POST /balance/transfer](#post-balancetransfer)
  - [POST /sms](#post-sms)
  - [Error responses](#error-responses)
- [Project structure](#project-structure)
- [Validation strategy](#validation-strategy)
- [Logging](#logging)
- [Tests](#tests)
- [Design decision: no database yet](#design-decision-no-database-yet)

---

## How the IVR uses this API

The Call Studio flow captures the caller's phone number automatically via **ANI** (Automatic
Number Identification) before this API is ever called. The number then travels as an ordinary
path variable or JSON field.

**The API has no knowledge of ANI.** It receives a phone number, validates it like any other
input, and answers. That separation is deliberate: the same endpoints work unchanged from a web
portal, a mobile app, or an integration test.

One constraint the IVR does impose: Call Studio's `Rest_Client` element cannot cope with an empty
or non-JSON response body — it fails the whole element rather than reading a status code, which
the caller experiences as a dropped call. Every response this API can produce, including 400,
404, 405 and 500, is therefore a well-formed JSON document.

## Running the project

**Requirements:** JDK 17 or newer. Maven is *not* required — the project ships with the Maven
Wrapper, which downloads the correct Maven version on first use.

```bash
# Run the application (Linux/macOS)
./mvnw spring-boot:run

# Run the application (Windows)
mvnw.cmd spring-boot:run
```

The API starts on **http://localhost:8085** (set by `server.port` in `application.yml`).

```bash
# Run the full test suite
./mvnw test

# Build an executable jar
./mvnw clean package
java -jar target/telecom-ivr-api-1.0.0.jar
```

To run on a different port without editing any file:

```bash
java -jar target/telecom-ivr-api-1.0.0.jar --server.port=8099
```

---

## Endpoints

### GET /customer/{phoneNumber}/vip-status

Tells the IVR whether the caller should be routed to the VIP queue.

**Rule:** a customer is a VIP when **the sum of the digits of their phone number is a prime
number**. Example: `01234567892` → digits sum to 47 → 47 is prime → VIP.

| Parameter     | In   | Rules                          |
|---------------|------|--------------------------------|
| `phoneNumber` | path | Required, **exactly 11 digits** |

**Request**

```bash
curl http://localhost:8085/customer/01234567892/vip-status
```

**200 OK**

```json
{ "isVip": true }
```

A well-formed number that is simply not a VIP is still a **200**, with `"isVip": false` — "not a
VIP" is an answer, not an error:

```bash
curl http://localhost:8085/customer/01234567890/vip-status
```

```json
{ "isVip": false }
```

**400 Bad Request** — number missing, too short/long, or containing non-digits:

```bash
curl http://localhost:8085/customer/123/vip-status
```

```json
{ "success": false, "message": "Invalid data received" }
```

---

### POST /balance/recharge

Recharges a subscriber's prepaid balance from a payment card.

**Request**

```bash
curl -X POST http://localhost:8085/balance/recharge \
  -H "Content-Type: application/json" \
  -d '{
        "phoneNumber": "01234567890",
        "cardNumber": "4242424242424242",
        "expiryDate": "12/34",
        "securityCode": "567",
        "amount": 50.0
      }'
```

| Field          | Type   | Rules                                                             |
|----------------|--------|-------------------------------------------------------------------|
| `phoneNumber`  | string | Required, **exactly 11 digits**                                   |
| `cardNumber`   | string | Required, **exactly 16 digits**                                   |
| `expiryDate`   | string | Required, `MM/yy`, **must not be in the past** (business rule)    |
| `securityCode` | string | Required, **exactly 3 digits**                                    |
| `amount`       | number | Required, greater than 0, **must not exceed 1000** (business rule)|

**200 OK**

```json
{ "success": true, "message": "Balance has been recharged successfully" }
```

**400 Bad Request** — any validation failure:

```json
{ "success": false, "message": "Invalid data received" }
```

Two boundaries are worth stating explicitly, because they are easy to get wrong:

- **`amount` of exactly `1000` is accepted.** The ceiling is inclusive; `1000.01` is the smallest
  rejected value.
- **A card expiring in the current month is accepted.** A card is valid to the *end* of its expiry
  month, so in August 2026 an expiry of `08/26` still works. `07/26` does not.

Every rejection returns the same generic message. The API deliberately does **not** say which
field was wrong: an endpoint that reports "the security code is wrong but the card number is
fine" is a free card-testing oracle for an attacker. The specific reason is written to the
application log instead, where support staff can see it.

---

### POST /balance/transfer

Moves balance from one subscriber to another.

**Request**

```bash
curl -X POST http://localhost:8085/balance/transfer   -H "Content-Type: application/json"   -d '{
        "fromNumber": "01234567890",
        "toNumber": "01234567899",
        "amount": 25.0
      }'
```

| Field        | Type   | Rules                                                                  |
|--------------|--------|------------------------------------------------------------------------|
| `fromNumber` | string | Required, **exactly 11 digits** (shared `@PhoneNumber` constraint)      |
| `toNumber`   | string | Required, **exactly 11 digits**; **same length and same first 3 digits as `fromNumber`** (business rules) |
| `amount`     | number | Required, greater than 0, **must not exceed 1000** (business rule)      |

**200 OK**

```json
{ "success": true, "message": "Balance has been transferred successfully" }
```

**400 Bad Request** — any validation failure:

```json
{ "success": false, "message": "Invalid data received" }
```

The two cross-field rules are the interesting part:

- **Same first three digits.** `01234567890 → 01234567899` is accepted; `01234567890 → 09934567890`
  is not. Exactly three digits are compared - a matching fourth digit cannot rescue a mismatched
  third.
- **Same length.** Enforced in the service even though `@PhoneNumber` currently makes a mismatch
  unreachable through the API: two values that are each exactly 11 digits are necessarily the same
  length. It is implemented and tested anyway, because it is the rule as specified and because it
  is what would stop a cross-length transfer the day a market with a different number length is
  added.
- **`amount` of exactly `1000` is accepted**, `1000.01` is not - the same inclusive boundary as a
  recharge, but a separate constant, because they are separate commercial policies.

---

### POST /sms

Sends a templated SMS to a subscriber.

**Request**

```bash
curl -X POST http://localhost:8085/sms   -H "Content-Type: application/json"   -d '{
        "phoneNumber": "01234567890",
        "tempelateCode": "INTERNET_PACKAGES"
      }'
```

| Field           | Type   | Rules                                                            |
|-----------------|--------|------------------------------------------------------------------|
| `phoneNumber`   | string | Required, **exactly 11 digits** (shared `@PhoneNumber` constraint)|
| `tempelateCode` | string | Required, one of `INTERNET_PACKAGES`, `CALL_TONES`, `PROMOTIONS`  |

**On the field name.** The IVR contract spells this field `tempelateCode`. That spelling is the
wire format and the API accepts it exactly. It is pinned with `@JsonProperty("tempelateCode")` on
a correctly named Java component, so the typo lives on one line instead of spreading through the
codebase, and `@JsonAlias("templateCode")` additionally accepts the correct spelling - so a
corrected IVR flow, or any other client, keeps working without a redeployment.

Template codes are matched **exactly and case-sensitively**: `internet_packages` and
`INTERNET_PACKAGE` are both rejected. The IVR sends a fixed string from its own configuration, so
a differently-cased value means something is misconfigured upstream, and quietly accepting it
would hide that.

**200 OK**

```json
{ "success": true, "message": "The SMS has been sent successfully." }
```

**400 Bad Request** — unknown template, or a malformed phone number:

```json
{ "success": false, "message": "Invalid data received" }
```

---

### Error responses

Every error, from every endpoint, uses one envelope:

```json
{ "success": false, "message": "..." }
```

| Status | When                                                | Message                             |
|--------|-----------------------------------------------------|-------------------------------------|
| 400    | Failed validation, unparseable or missing JSON body | `Invalid data received`             |
| 404    | Unknown URL                                         | `Requested resource was not found`  |
| 405    | Known URL, wrong HTTP verb                          | `Requested method is not supported` |
| 500    | Anything unanticipated                              | `Service is temporarily unavailable`|

Stack traces, exception class names and framework messages are **never** serialised — they are
logged and nothing more.

---

## Project structure

Organised **package-by-layer**, matching the small, cohesive surface of this API: four endpoints
sharing one response envelope, one exception handler, and one phone-number constraint.
Package-by-feature would put `customer`, `balance` and `sms` in separate silos and then need a
shared package for all three of those — more structure than four endpoints can pay for.

```
com.vodafone.ivr
├── TelecomIvrApiApplication.java     entry point
├── ApplicationConfig.java            Clock bean (see Validation strategy)
├── controller
│   ├── CustomerController.java       GET /customer/{phoneNumber}/vip-status
│   ├── BalanceController.java        POST /balance/recharge, POST /balance/transfer
│   └── SmsController.java            POST /sms
├── service
│   ├── VipStatusService.java         digit sum + primality rule
│   ├── RechargeService.java          expiry and amount business rules
│   ├── TransferService.java          cross-field number rules + amount ceiling
│   ├── SmsService.java               template catalogue rule
│   ├── SmsTemplate.java              the allowed template codes
│   └── SensitiveDataMasker.java      redaction for log output
├── dto
│   ├── request
│   │   ├── RechargeRequest.java      request body + structural constraints
│   │   ├── TransferRequest.java
│   │   └── SmsRequest.java
│   └── response
│       ├── ApiResponse.java          { success, message } envelope
│       ├── VipStatusResponse.java    { isVip }
│       └── ResponseMessages.java     caller-facing message text
├── exception
│   ├── BusinessRuleViolationException.java
│   ├── GlobalExceptionHandler.java   @RestControllerAdvice
│   └── JsonErrorController.java      JSON for container-level errors
└── validation
    ├── ValidationPatterns.java       every accepted input format, in one place
    └── PhoneNumber.java              shared @PhoneNumber constraint (all four endpoints)
```

**Controllers hold no business logic.** They bind input, log the request, call a service, and wrap
the result. That is what makes the persistence phase cheap: a repository is injected into a
service, and no controller, DTO, or IVR script changes.

## Validation strategy

Validation is split across two layers on a single principle: **is this rule about the shape of the
data, or about what the business permits?**

**Structural rules → Bean Validation annotations on the DTO.** Presence, exact length, character
set. These are properties of the value itself and are declarative enough that an annotation says
everything there is to say.

Every format is a named constant in `ValidationPatterns`, never a literal at the point of use, so
one rule cannot be spelled two different ways in two different files:

| Field          | Constant                          | Regex                    | Accepts            |
|----------------|-----------------------------------|--------------------------|--------------------|
| `phoneNumber`  | `ValidationPatterns.PHONE_NUMBER` | `\d{11}`                 | `01234567890`      |
| `cardNumber`   | `ValidationPatterns.CARD_NUMBER`  | `\d{16}`                 | `4242424242424242` |
| `securityCode` | `ValidationPatterns.SECURITY_CODE`| `\d{3}`                  | `567`              |
| `expiryDate`   | `ValidationPatterns.EXPIRY_DATE`  | `(0[1-9]\|1[0-2])/\d{2}` | `12/34`            |

`@Pattern` anchors the whole value, so `\d{11}` means *exactly* eleven digits - nothing longer or
shorter matches.

`phoneNumber` gets one more layer, because it is accepted in **five request positions across four
endpoints**: the VIP lookup's path variable, the recharge body, both ends of a transfer, and the
SMS body. Rather than repeat `@NotBlank @Pattern` at each site, they share a **composed
constraint**, `@PhoneNumber`, which carries the two rules itself. One place to change, and every
site changes with it. `TransferRequest` overrides only the *message*, so a log line names
`fromNumber` or `toNumber` rather than a generic "phoneNumber" - the rule itself stays shared.

A validation rule that holds on one endpoint but not another is a security gap, not merely an
inconsistency, so `PhoneNumberValidationConsistencyTest` runs the same values through all five
positions and demands the same verdict - it fails the build if they ever drift.

Note what is deliberately **not** validated: the card number is checked for shape only, not for a
Luhn checksum or against any scheme's issuer ranges. The spec calls for 16 digits, and that is
what the API enforces.

**Business rules → explicit code in the service layer.** Five rules qualify:

| Rule                          | Service            | Why it is not an annotation                                                                                                                 |
|-------------------------------|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Expiry date in the future     | `RechargeService`  | Depends on *when the request arrives*. `@Future` does not understand `MM/yy`, and "not expired" means valid to the end of the month, not from today. |
| Amount ≤ 1000                 | `RechargeService`, `TransferService` | A commercial policy, not a fact about numbers. Tomorrow it may vary by customer tier or come from configuration. `@Max(1000)` would freeze policy into the transport layer. |
| Numbers share a 3-digit prefix| `TransferService`  | **Cross-field** — it compares two fields, which a field-level annotation cannot see.                                                        |
| Numbers are the same length   | `TransferService`  | Also cross-field.                                                                                                                            |
| Template code is in the catalogue | `SmsService`   | The catalogue is a marketing artefact that changes with campaigns and will move to a table. Listing today's codes in a `@Pattern`, or typing the field as the enum, would freeze the catalogue into the transport layer — and put the check where no service-layer unit test can reach it. |

The expiry rule needs the current date, so `RechargeService` takes a `java.time.Clock` through its
constructor rather than calling `YearMonth.now()`. Tests inject a fixed clock and assert against
2026 for ever; a test written against the real clock would pass today and silently start failing
next month.

## Logging

SLF4J, via Spring Boot's default Logback backend.

- **INFO** on every request — the endpoint, the (masked) subscriber, the outcome.
- **WARN** on every rejection, including the specific reason that is *not* sent to the caller.
- **ERROR** only for genuinely unexpected failures, with the stack trace.

Nothing that could be replayed as a payment reaches a log file:

- Card numbers appear as `************4242` — last four digits only.
- Phone numbers appear as `********890` — enough to correlate one call's log lines. This applies
  to **every** number, including both ends of a transfer and the SMS recipient.
- **The security code is never logged at all**, in any form. PCI DSS forbids retaining it after
  authorisation, and a redacted 3-digit value is nearly recoverable anyway.

Sample output from a rejected recharge:

```
INFO  c.v.ivr.controller.BalanceController - POST /balance/recharge for subscriber ********890
INFO  c.v.ivr.service.RechargeService      - Recharge requested for subscriber ********890 using card ************4242, amount=1000.01
WARN  c.v.i.e.GlobalExceptionHandler       - Business rule rejected the request: Amount 1000.01 exceeds the maximum of 1000
```

## Tests

**261 tests, all passing.** Run them with `./mvnw test`.

| Test class                            | Kind             | Covers                                                          |
|---------------------------------------|------------------|-----------------------------------------------------------------|
| `VipStatusServiceTest`                | Unit             | Digit sum, primality, the VIP rule, malformed input             |
| `RechargeServiceTest`                 | Unit             | Expiry and amount rules against a fixed clock                   |
| `TransferServiceTest`                 | Unit             | Prefix, length and amount rules; ordering of the checks         |
| `SmsServiceTest`                      | Unit             | Template catalogue: exact matches, near-misses, unknown codes   |
| `SensitiveDataMaskerTest`             | Unit             | Redaction; that a full card number can never pass through       |
| `CustomerControllerIntegrationTest`   | `@SpringBootTest`| Routing, path validation, exact JSON, the 404 path              |
| `BalanceControllerIntegrationTest`    | `@SpringBootTest`| Recharge: success and every failure path, end to end            |
| `TransferControllerIntegrationTest`   | `@SpringBootTest`| Transfer: success and every failure path, end to end            |
| `SmsControllerIntegrationTest`        | `@SpringBootTest`| SMS: every template, both field spellings, failure paths        |
| `BalanceControllerWebLayerTest`       | `@WebMvcTest`    | Controller in isolation with a Mockito-mocked service           |
| `PhoneNumberValidationConsistencyTest`| `@SpringBootTest`| That all five request positions enforce `phoneNumber` identically |
| `TelecomIvrApiApplicationTests`       | Smoke            | The context wires up                                            |

Edge cases the suite pins down deliberately:

- digit sum of **1** (`00000000001`) and **0** (`00000000000`) — neither is prime
- **2** is prime despite being even; **9, 49, 121** are not, which an off-by-one square-root bound
  would miss
- `amount` of exactly **1000** accepted, **1000.01** rejected, **1000.00** accepted (scale must not
  affect the comparison)
- expiry of **exactly the current month** accepted, previous month rejected
- exact field lengths: 10- and 12-digit phone numbers, 15- and 17-digit card numbers, and 2- and
  4-digit security codes are all rejected
- **leading zeros** preserved — `0000000007` sums to 7, and a long digit string would overflow an
  `int` if it were parsed numerically instead of walked character by character
- transfer prefix rule: a mismatch at the 1st, 2nd or 3rd digit is rejected; a difference at the
  4th digit onwards is irrelevant
- transfer amount of exactly **1000** accepted, **1000.01** rejected - and the ceiling is a
  separate constant from the recharge ceiling
- SMS template near-misses rejected: `internet_packages`, `INTERNET_PACKAGE`, ` INTERNET_PACKAGES`
- SMS accepts both `tempelateCode` (the spec's spelling) and `templateCode` (via `@JsonAlias`)
- malformed JSON, an empty body, and a wrong HTTP verb all return JSON, never an empty response
- a service failure returns a clean 500 with no exception name or internal address in the body

## Design decision: no database yet

This phase is **intentionally stateless**. There is no JPA dependency, no datasource, no entity,
and no repository. The API computes its answers from the request alone.

**Why:** the four operations here are genuinely computable without stored state. The VIP rule is
arithmetic over the phone number; the recharge and transfer rules are checks against the submitted
values and fixed ceilings; the SMS catalogue is three constants. Adding a database before it is
needed would mean schema, migrations, connection management and slower tests, in exchange for
nothing this phase actually uses.

**What that costs today, stated honestly:** a recharge validates but does not move money, a
transfer moves no balance between subscribers, and `POST /sms` sends no message - there is no SMS
gateway. All three services validate, log, and return normally. They are validation gates, not
transactions — and the code says so rather than pretending otherwise by returning an invented
transaction id.

**The structure is already shaped for persistence.** Every rule lives in a service that a
controller merely delegates to, so the change is additive:

```
com.vodafone.ivr
├── model         (new)  Customer, RechargeTransaction, TransferTransaction, SmsTemplate
├── repository    (new)  CustomerRepository, TransactionRepository, SmsTemplateRepository
└── service       (edit) inject the repositories; endpoints and DTOs unchanged
```

Concretely, in a later phase:

1. Add `spring-boot-starter-data-jpa` and a driver; configure the datasource; add Flyway or
   Liquibase for schema migrations.
2. Add `@Entity` classes under `model` and Spring Data interfaces under `repository`.
3. Inject `CustomerRepository` into `VipStatusService`. The VIP rule likely becomes a stored flag,
   with the digit-sum rule kept as the fallback for unknown numbers — a change entirely inside
   `isVip`.
4. Inject the transaction repositories into `RechargeService` and `TransferService`, and add the
   payment-gateway call. Mark the methods `@Transactional` - a transfer especially, since a debit
   without its matching credit is the worst possible failure mode - and have them return receipts.
   `SmsTemplate` becomes a repository lookup behind `SmsService.resolveTemplate`, and `SmsService`
   gains a real gateway client.
5. Only then does the response contract grow — for example a transaction id in the recharge
   response. Everything up to that point is invisible to the IVR.

**Nothing in `controller`, `dto`, `exception` or `validation` needs to change** for steps 1–4.
That is the whole point of keeping the controllers thin.
