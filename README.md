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

### Create account

`POST /api/accounts`

```json
{
  "ownerName": "Mario",
  "ownerSurname": "Rossi",
  "initialBalance": 123.45
}
```

Returns `201 Created` with the new account and its `Location` header, or `400 Bad Request` if the payload is invalid.

### Retrieve account
`GET /api/accounts/{id}`

Returns `200 OK` with the account, or `404 Not Found` if it does not exist.

### List accounts
`GET /api/accounts`

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
- `src/main/java/io/github/bred91/moneytransfer/advice`: centralized exception handling for API Bad Request errors.
- `src/main/java/io/github/bred91/moneytransfer/enumeration`: domain enums representing application states and statuses.
- `src/main/resources/static`: SQL helper scripts used for manual checks and sample data.
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

## Database

The application uses PostgreSQL and persists data in two tables:

- `accounts`: stores the account owner, balance, status (`ACTIVE`, `FROZEN`, `CLOSED`), and timestamps.
- `transfer_logs`: stores every consumed transfer event, its status (`RECEIVED`, `FAILED`, `SUCCESS`), and the failure reason when applicable.

For local development the schema is managed by Hibernate with `ddl-auto: update`, which keeps the tables in sync with the entities without manual migrations.
For production deployments this should be switched to `validate` (or `none`) together with a migration tool.
The property is set in the `docker-compose.yml` file.

You can find some useful queries in the `src/main/resources/static` directory.

## Key technical choices

- **Spring Boot 4 + Java 25**: the latest LTS Java release on the current Spring generation.
- **REST for account operations**: `create` and `get` are exposed through REST endpoints because they are straightforward to consume and easy to test. A `list` operation is also available to support the general workflow.
- **Kafka for transfer processing**: money transfers are handled asynchronously through Kafka, decoupling event ingestion from business execution.
- **PostgreSQL as the real database**: chosen for durable persistence, transactional guarantees, and realistic production behavior; no in-memory database is used.
- **Transactional transfer processing**: balance updates and the transfer log are written in a single database transaction, so a transfer is either fully applied or not applied at all.
- **Idempotency by transaction ID**: each transfer event is keyed by `transactionId` to avoid double processing in case of retries or duplicate delivery.
- **UUID identifiers**: accounts and transfer logs use time-ordered UUIDv7 primary keys, generated by Hibernate. They are not guessable or enumerable like sequential IDs, can be generated without a database round trip, and stay index-friendly thanks to their time ordering.
- **Pessimistic ordered locking on accounts**: balances are updated under row-level locks acquired in a consistent order (by account ID), keeping concurrent transfers consistent and avoiding deadlocks.
- **No lost events**: the consumer group starts from the earliest offset when it has no committed position, so transfers published before the service first started are still processed; idempotency makes redelivery safe.
- **Validation at the boundary**: request DTOs and Kafka payloads are validated early to reject malformed input before business logic runs.
- **Docker-first local setup**: Docker Compose brings up the app, PostgreSQL, and Kafka together so the project can be started without extra local dependencies.
- **Production-oriented logging**: the service logs key business events and failures to make troubleshooting and operational monitoring easier.

## Assumptions

- Transfer events are JSON messages with `transactionId`, `fromAccountId`, `toAccountId`, and `amount`; account IDs are UUIDs.
- If any field is missing or invalid (including malformed UUIDs), the event is logged and discarded.
- Only transfers with a positive amount (greater than zero) are processed.
- Source and destination accounts must be different.
- The application only consumes transfer events; it does not produce them.
- Each `transactionId` uniquely identifies a transfer: a transfer is processed at most once, and any later event with the same `transactionId` is ignored.
- Accounts are created in `ACTIVE` status.
- If either account does not exist or is not `ACTIVE`, the event is logged, stored in the database, and marked as `FAILED`.
- If the source account balance is lower than the transfer amount, the event is logged, stored in the database, and marked as `FAILED`.

## Roadmap
Possible areas for future improvement:

- Authentication and authorization
- Better retry policy with a dead-letter queue
- Stronger validation for Kafka consumer messages (for example, using Protobuf or Avro schemas)
- Better observability with metrics and tracing
- Stronger API documentation and versioning
- Database migrations (e.g. Flyway) instead of relying on schema auto-update
- More resilience patterns for production traffic
