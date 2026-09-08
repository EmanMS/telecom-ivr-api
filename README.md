# Telecom IVR API

REST API for a telecom IVR system integrated with Cisco Call Studio, covering VIP status lookup,
balance recharge, balance transfer, and SMS notifications.

**Stack:** Java 17 · Spring Boot · Spring Data JPA · MySQL · Maven

---

## Running the project

Requires JDK 17+ and a running MySQL instance. Maven is not required — the project ships with the
Maven Wrapper.

```bash
./mvnw spring-boot:run          # start the app (http://localhost:8085)
./mvnw test                     # run the test suite
./mvnw clean package            # build an executable jar
```

To run on a different port: `java -jar target/telecom-ivr-api-1.0.0.jar --server.port=8099`

The caller's phone number is captured by Cisco Call Studio via ANI before this API is ever
called — the API just receives it as a normal field and validates it like any other input.

---

## Endpoints

### GET /customer/{phoneNumber}/vip-status

A customer is VIP when the sum of their phone number's digits is prime.

```bash
curl http://localhost:8085/customer/01234567892/vip-status
```
```json
{ "isVip": true }
```

| Field | Rules |
|---|---|
| `phoneNumber` (path) | Exactly 11 digits |

---

### POST /balance/recharge

```bash
curl -X POST http://localhost:8085/balance/recharge \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"01234567890","cardNumber":"4242424242424242","expiryDate":"12/34","securityCode":"567","amount":50.0}'
```
```json
{ "success": true, "message": "Balance has been recharged successfully" }
```

| Field | Rules |
|---|---|
| `phoneNumber` | Exactly 11 digits |
| `cardNumber` | Exactly 16 digits |
| `expiryDate` | `MM/yy`, must not be in the past (valid through end of expiry month) |
| `securityCode` | Exactly 3 digits |
| `amount` | > 0, up to 1000 (inclusive) |

---

### POST /balance/transfer

```bash
curl -X POST http://localhost:8085/balance/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromNumber":"01234567890","toNumber":"01234567899","amount":25.0}'
```
```json
{ "success": true, "message": "Balance has been transferred successfully" }
```

| Field | Rules |
|---|---|
| `fromNumber` / `toNumber` | Exactly 11 digits each |
| `toNumber` | Same length and same first 3 digits as `fromNumber` |
| `amount` | > 0, up to 1000 (inclusive; a separate limit from recharge) |

---

### POST /sms

```bash
curl -X POST http://localhost:8085/sms \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"01234567890","tempelateCode":"INTERNET_PACKAGES"}'
```
```json
{ "success": true, "message": "The SMS has been sent successfully." }
```

| Field | Rules |
|---|---|
| `phoneNumber` | Exactly 11 digits |
| `tempelateCode` | One of `INTERNET_PACKAGES`, `CALL_TONES`, `PROMOTIONS` (also accepts the correctly-spelled `templateCode`) |

---

## Error responses

Every error, from every endpoint, uses the same envelope — no field-level detail is ever returned,
to avoid giving an attacker a way to probe which specific input was wrong:

```json
{ "success": false, "message": "..." }
```

| Status | When |
|---|---|
| 400 | Failed validation or malformed request body |
| 404 | Unknown URL |
| 405 | Wrong HTTP verb |
| 500 | Unexpected server error |

---

## Design notes

**Persistence (MySQL).** Three tables: `customers` (`phone_number`, `balance`, `is_vip`),
`transactions` (recharge/transfer log), `sms_logs`. A customer row is created on first contact
with any endpoint, starting at `balance = 0`. `is_vip` is computed once at creation and stored,
since the underlying rule never changes for a given number. A transfer's debit and credit run in
one `@Transactional` boundary, so a failure can't leave money debited from one side without
crediting the other.

**Validation.** Structural rules (length, format) live as Bean Validation annotations; business
rules that depend on the current date, cross-field comparisons, or policy (like the 1000 ceiling)
live in the service layer instead. `phoneNumber` is validated by one shared `@PhoneNumber`
constraint everywhere it appears, so all five usage sites can't drift out of sync.

**Tests.** 261 tests (unit + integration), run via `./mvnw test`.

**Known limitation.** `POST /sms` validates and logs a send but has no real SMS gateway — no
message actually reaches a handset yet.
