# Money Transfer Microservice

Spring Boot microservice for account management and asynchronous, event-driven money transfer processing.

**Author:** Raffaele Pane

## Tech stack

- Java 25
- Spring Boot 4.2 (Spring Framework 7)
- Spring Web MVC
- Spring Data JPA (Hibernate 7)
- Spring for Apache Kafka 4 (Jackson 3)
- Apache Kafka 4 (KRaft mode, no ZooKeeper)
- PostgreSQL 18
- Flyway (database migrations)
- springdoc-openapi 3 (Swagger UI)
- Docker / Docker Compose
- JUnit 6
- Testcontainers 2

## Prerequisites

- Docker with Docker Compose (required to run the stack and the integration tests)
- JDK 25 (only needed to build or run the tests outside Docker; Maven is provided through the `mvnw` wrapper)

## Run locally with Docker

### Start the full stack

From the project root:
```bash
docker compose up -d --build
```

### Services exposed by default
- Application: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI document: http://localhost:8080/v3/api-docs
- PostgreSQL: localhost:5432
- Kafka: localhost:9093 (from the host), kafka:9092 (inside the Docker network)

### Stop the stack
```bash
docker compose stop
```

### Remove the stack
```bash
docker compose down        # keeps the database volume
docker compose down -v     # also deletes the database volume
```

## Running the tests

```bash
./mvnw test
```

- **Unit tests** (`src/test/java/.../junit`) cover controllers, services, and the Kafka listener with Mockito.
- **Integration tests** (`src/test/java/.../integration`) start real PostgreSQL and Kafka instances through Testcontainers, so a running Docker daemon is required.

## API

The full, interactive API documentation is available in Swagger UI (see above).

### Versioning
The REST API is versioned through the first path segment, using the native API versioning of Spring Framework 7:
the current version is `v1` (e.g. `/v1/accounts`). Requests for an unsupported version are rejected with `400 Bad Request`.

### Timestamps
Timestamps are returned as ISO 8601 instants in UTC, with microsecond precision (e.g. `2026-11-07T17:55:50.123456Z`).

### Errors
All errors are returned as [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) with content type `application/problem+json`.
Validation errors also include an `errors` object mapping each invalid field to its message:

```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains invalid fields",
  "instance": "/v1/accounts",
  "errors": {
    "ownerName": "Name is required",
    "initialBalance": "Initial balance must not be negative"
  }
}
```

### Create account

`POST /v1/accounts`

```json
{
  "ownerName": "Mario",
  "ownerSurname": "Rossi",
  "initialBalance": 123.45
}
```

Returns `201 Created` with the new account and its `Location` header, or `400 Bad Request` if the payload is invalid.

### Retrieve account
`GET /v1/accounts/{id}`

Returns `200 OK` with the account, `400 Bad Request` if the ID is not a valid UUID, or `404 Not Found` if it does not exist.

### List accounts
`GET /v1/accounts`

## Project structure

This project follows a layered architecture with a clear separation of concerns.
At a high level, the flow of a generic API call is:

**Controller -> Service -> Repository -> Database**

For asynchronous money transfers, Kafka acts as the entry point and the listener delegates the processing to the service layer.

- `src/main/java/io/github/bred91/moneytransfer/controller`: REST controllers exposing the API.
- `src/main/java/io/github/bred91/moneytransfer/service`: application services containing the business logic and orchestration.
- `src/main/java/io/github/bred91/moneytransfer/repository`: Spring Data JPA repositories used to access PostgreSQL.
- `src/main/java/io/github/bred91/moneytransfer/entity`: JPA entities mapped to database tables.
- `src/main/java/io/github/bred91/moneytransfer/dto`: request/response objects and Kafka payloads used at the application boundary.
- `src/main/java/io/github/bred91/moneytransfer/kafka`: Kafka consumer components that receive transfer events and forward them to the business layer.
- `src/main/java/io/github/bred91/moneytransfer/advice`: centralized exception handling, rendering errors as Problem Details.
- `src/main/java/io/github/bred91/moneytransfer/config`: application configuration (OpenAPI metadata, Kafka payload conversion, validation, retries and dead-lettering).
- `src/main/java/io/github/bred91/moneytransfer/enumeration`: domain enums representing application states and statuses.
- `src/main/resources/db/migration`: Flyway migrations defining the database schema.
- `scripts`: optional SQL helper scripts (sample data for manual testing).
- `src/test/java`: unit and integration tests covering the main application layers.

## Kafka transfer processing
The service consumes transfer events from the `money-transfers` topic.

Example payload:
```json
{
  "transactionId": "tx-1",
  "fromAccountId": "019a3f6e-1c2b-7d4e-9a10-3b5c7d9e1f20",
  "toAccountId": "019a3f6e-4d5e-7f60-8b21-4c6d8e0f2a31",
  "amount": 25.50
}
```

Example producer command inside the Kafka container:
```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic money-transfers
```

> On Windows with Git Bash, prefix the command with `MSYS_NO_PATHCONV=1` so the container path is not converted into a Windows path.

After running the command, paste the JSON payload for the transfer event, using the account IDs returned by the API:
```json
{"transactionId":"tx-2","fromAccountId":"019a3f6e-4d5e-7f60-8b21-4c6d8e0f2a31","toAccountId":"019a3f6e-1c2b-7d4e-9a10-3b5c7d9e1f20","amount":25.50}
```

Any other Kafka client (for example an IDE Kafka plugin) can also be used, connecting to `localhost:9093`.

### Error handling and dead-letter topic
Events that cannot be processed are published, byte for byte as received, to the `money-transfers.DLT` dead-letter topic,
with headers describing the original record (topic, partition, offset) and the failure (exception class, message, stack trace):

- **Malformed or invalid events** (records without a value, unparseable JSON, malformed UUIDs, missing fields, invalid amounts,
  same source and destination) are never retried and are dead-lettered immediately.
- **Transient failures** (for example the database being unavailable) are retried with exponential back-off
  (1s, 2s, 4s, ... up to 30s, 6 retries by default, configurable under `app.kafka.retry`); the event is dead-lettered
  once the retries are exhausted.
- **Business rejections** (missing or inactive account, insufficient funds) are not errors: they are stored as `FAILED` transfer logs.

The offset of an event is committed only after it has been processed or dead-lettered, so no event is lost.
Since processing is idempotent, a dead-lettered event can safely be published again to `money-transfers` once the cause is fixed.

To inspect the dead-letter topic with its headers:
```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic money-transfers.DLT --from-beginning --property print.headers=true
```

## Database

The application uses PostgreSQL and persists data in two tables:

- `accounts`: stores the account owner, balance, status (`ACTIVE`, `FROZEN`, `CLOSED`), and timestamps.

All timestamps are stored as `TIMESTAMP WITH TIME ZONE`, so they denote the same instant whatever the time zone of the application or of the database session.
- `transfer_logs`: stores every consumed transfer event, its status (`RECEIVED`, `FAILED`, `SUCCESS`), and the failure reason when applicable.

The schema is versioned with **Flyway**: migrations live in `src/main/resources/db/migration` and are applied automatically at startup.
Hibernate runs with `ddl-auto: validate`, so it never changes the schema and the application fails fast if the entities and the database diverge.
Schema changes are made by adding a new migration (`V2__description.sql`, ...); migrations that have already been applied must never be edited.

The schema also enforces some invariants at database level, such as a non-negative account balance and a unique `transaction_id` per transfer log.

Optional sample data for manual testing:
```bash
docker compose exec -T postgres psql -U my_user -d my_database < scripts/sample-data.sql
```

## Key technical choices

- **Spring Boot 4 + Java 25**: the latest LTS Java release on the current Spring generation.
- **Versioned, documented REST contract**: native Spring Framework 7 API versioning (`/v1/...`), OpenAPI 3.1 documentation generated from annotated controllers and DTOs, and standard RFC 9457 error responses.
- **REST for account operations**: `create` and `get` are exposed through REST endpoints because they are straightforward to consume and easy to test. A `list` operation is also available to support the general workflow.
- **Kafka for transfer processing**: money transfers are handled asynchronously through Kafka, decoupling event ingestion from business execution.
- **Versioned schema with Flyway**: the database schema is defined by reviewed, versioned SQL migrations instead of being generated from the entities, making changes explicit and repeatable across environments.
- **PostgreSQL as the real database**: chosen for durable persistence, transactional guarantees, and realistic production behavior; no in-memory database is used.
- **Transactional transfer processing**: balance updates and the transfer log are written in a single database transaction, so a transfer is either fully applied or not applied at all.
- **Idempotency by transaction ID**: each transfer event is keyed by `transactionId` to avoid double processing in case of retries or duplicate delivery.
- **UUID identifiers**: accounts and transfer logs use time-ordered UUIDv7 primary keys, generated by Hibernate. They are not guessable or enumerable like sequential IDs, can be generated without a database round trip, and stay index-friendly thanks to their time ordering.
- **Pessimistic ordered locking on accounts**: balances are updated under row-level locks acquired in a consistent order (by account ID), keeping concurrent transfers consistent and avoiding deadlocks.
- **No lost events**: the consumer group starts from the earliest offset when it has no committed position, so transfers published before the service first started are still processed; idempotency makes redelivery safe.
- **Retries and dead-letter topic**: transient failures are retried with exponential back-off, and events that are invalid or keep failing are forwarded unchanged to a dead-letter topic instead of being dropped (see [Error handling and dead-letter topic](#error-handling-and-dead-letter-topic)).
- **Validation at the boundary**: request DTOs and Kafka payloads are validated with Bean Validation to reject malformed input before business logic runs. Amounts must fit the `NUMERIC(19, 2)` balance columns, so the database never has to round them.
- **Docker-first local setup**: Docker Compose brings up the app, PostgreSQL, and Kafka together so the project can be started without extra local dependencies.
- **Production-oriented logging**: the service logs key business events and failures to make troubleshooting and operational monitoring easier.

## Assumptions

- Transfer events are JSON messages with `transactionId`, `fromAccountId`, `toAccountId`, and `amount`; account IDs are UUIDs.
- If the event is not valid JSON, or any field is missing or invalid (including malformed UUIDs), the event is not processed and is published to the dead-letter topic.
- Only transfers with a positive amount (greater than zero) with at most 2 decimal places and 17 integer digits are processed.
- `money-transfers` is not a compacted topic, so a record without a value (tombstone) is not a valid transfer event and is published to the dead-letter topic.
- Source and destination accounts must be different.
- The application only consumes transfer events; it does not produce them.
- Each `transactionId` uniquely identifies a transfer: a transfer is processed at most once, and any later event with the same `transactionId` is ignored.
- Accounts are created in `ACTIVE` status.
- If either account does not exist or is not `ACTIVE`, the event is logged, stored in the database, and marked as `FAILED`.
- If the source account balance is lower than the transfer amount, the event is logged, stored in the database, and marked as `FAILED`.

## Roadmap
Possible areas for future improvement:

- Authentication and authorization
- Stronger validation for Kafka consumer messages (for example, using Protobuf or Avro schemas)
- Better observability with metrics and tracing
- AsyncAPI documentation for the Kafka transfer events
- More resilience patterns for production traffic
