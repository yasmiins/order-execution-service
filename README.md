# Order Execution Service

Spring Boot + PostgreSQL backend for idempotent order submission, cancellation, and simulated executions.

## Project overview

This service manages the lifecycle of equity orders and executions, including idempotent order creation using the `Idempotency-Key` header, cancellation, and query endpoints. A scheduled simulated fill engine generates full and partial fills using deterministic stub prices and marketable limit-order checks. Orders and executions are persisted in PostgreSQL with Flyway migrations, and basic metrics/logging are exposed via Micrometer and Actuator.

## Architecture summary

- Spring Boot REST API
- JPA/Hibernate for persistence
- Flyway migrations on startup
- PostgreSQL database
- Scheduled simulated fill engine with deterministic stub prices, marketable limit checks, and full/partial fills
- Domain events published after transaction commit
- Structured lifecycle logs and Micrometer counters

## Endpoints

- `POST /orders`
  - Optional header: `Idempotency-Key`
  - Body: `symbol`, `side`, `quantity`, `price` (limit), `orderType`
- `GET /orders/{id}`
- `GET /orders?symbol=&status=`
- `POST /orders/{id}/cancel`

## How to run locally

Make sure Docker Desktop (or another Docker daemon) is running before you execute these commands.

1. Start Postgres (example using Docker Compose):
   ```bash
   docker compose up -d postgres
   ```
2. Run the API:
   ```bash
   ./mvnw spring-boot:run
   ```

Defaults are configured in `src/main/resources/application.yml`:

- DB host: `localhost`
- DB port: `5432`
- DB name: `ordertrade`
- DB user: `ordertrade`
- DB pass: `ordertrade`

Override using environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`.

## How to run with Docker Compose

Make sure Docker Desktop (or another Docker daemon) is running before you execute these commands.

```bash
docker compose up --build
```

Ports:

- API: `http://localhost:8080`
- Postgres: `localhost:5432`

Stop:

```bash
docker compose down -v
```

## How to run tests

```bash
./mvnw verify
```

This runs unit tests and Testcontainers-based integration tests.

## Demo flow examples

PowerShell demo script:

```powershell
.\scripts\demo.ps1
```

Manual curl examples:

```bash
# Create
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","side":"BUY","quantity":10,"price":100.5,"orderType":"LIMIT"}'

# Idempotent create
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-123" \
  -d '{"symbol":"AAPL","side":"BUY","quantity":10,"price":100.5,"orderType":"LIMIT"}'

# Get
curl -s http://localhost:8080/orders/<id>

# Cancel
curl -s -X POST http://localhost:8080/orders/<id>/cancel
```

PowerShell-safe examples:

```powershell
# Create
@'
{"symbol":"AAPL","side":"BUY","quantity":10,"price":100.5,"orderType":"LIMIT"}
'@ | curl.exe -s -X POST "http://localhost:8080/orders" -H "Content-Type: application/json" --data-binary "@-"

# Idempotent create
@'
{"symbol":"AAPL","side":"BUY","quantity":10,"price":100.5,"orderType":"LIMIT"}
'@ | curl.exe -s -X POST "http://localhost:8080/orders" -H "Content-Type: application/json" -H "Idempotency-Key: demo-123" --data-binary "@-"

# Get
curl.exe -s "http://localhost:8080/orders/<id>"

# Cancel
curl.exe -s -X POST "http://localhost:8080/orders/<id>/cancel"
```

Note: the simulator can fill orders quickly; cancel immediately after create or set `simulator.enabled: false` for deterministic cancels.

## Metrics and Actuator

Actuator endpoints (local):

- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`

Custom counters:

- `orders.accepted`
- `orders.rejected` with tag `reason=validation|idempotency`
- `orders.canceled`
- `orders.fills.created` with tag `type=partial|full`

## Future work

- Add authentication and authorization
- Add pagination and filtering improvements to order queries
- Add OpenAPI documentation
- Add retry/backoff for external dependencies (if added later)
- Improve Docker image build caching and size
