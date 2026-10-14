# event-driven-order-platform

![CI](https://github.com/chandrapaldeora0803-creator/event-driven-order-platform/actions/workflows/ci.yml/badge.svg)

An event-driven order processing service built with **Spring Boot 3 / Java 17**, **Kafka**, **PostgreSQL** and **Redis**.
It shows the patterns that keep a distributed system correct when things fail: the **transactional outbox**,
**idempotent consumers**, **optimistic locking**, a validated **state machine**, and **cache eviction on write**.

## Architecture

```
 POST /api/orders
        |
        v
 +--------------+   one DB transaction    +-------------------------+
 | OrderService | ----------------------> | orders  +  outbox_events |  (PostgreSQL)
 +--------------+                         +-------------------------+
                                                     |
                                OutboxPublisher (polls every 1s)
                                                     |
                                                     v
                                         Kafka topic: orders.events
                                         (key = orderId, 3 partitions)
                                                     |
                                                     v
                                 OrderEventConsumer (risk check, idempotent)
                                                     |
                                  CONFIRMED / REJECTED  + processed_events row

 GET /api/orders/{id}  ->  Redis cache (10 min TTL)  ->  PostgreSQL on miss
```

## Order lifecycle

```
CREATED --> CONFIRMED --> CANCELLED
   |
   +-----> REJECTED
   |
   +-----> CANCELLED
```

Illegal moves (for example `CANCELLED -> CONFIRMED`) throw and return **409 Conflict**.

## Key design decisions

| Problem | Solution in this repo |
|---|---|
| Dual write: saving to the DB and publishing to Kafka can't be atomic | **Transactional outbox** - the event row is written in the same transaction as the order; `OutboxPublisher` ships it later |
| Kafka is at-least-once, so events can be delivered twice | **Idempotent consumer** - handled `eventId`s are stored in `processed_events` in the same transaction as the status change |
| Events for one order must be processed in order | Kafka **message key = orderId** keeps them on one partition; the publisher **stops at the first failure** instead of skipping ahead |
| Two updates racing on the same order | JPA `@Version` **optimistic locking** |
| Late event for an order the customer already cancelled | State machine rejects it; consumer logs it and still marks the event processed (no poison-pill retry loop) |
| Hot reads hitting the DB | `@Cacheable` read-through **Redis cache**, `@CacheEvict` on every status change, TTL as a safety net |
| Schema drift | **Flyway** migrations own the schema; Hibernate runs with `ddl-auto: validate` |

## Tech stack

Java 17, Spring Boot 3.3 (Web, Data JPA, Validation, Cache, Actuator), Spring for Apache Kafka, PostgreSQL 16, Redis 7,
Flyway, JUnit 5, Mockito, AssertJ, Docker, GitHub Actions.

## Run it locally

```bash
docker compose up --build
```

Create an order (amounts up to 10,000 are auto-approved, larger ones are rejected):

```bash
curl -s -X POST localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-42","amount":250.00}'
```

Read it back a second later - the status will have moved from `CREATED` to `CONFIRMED`:

```bash
curl -s localhost:8080/api/orders/<id>
```

Cancel it:

```bash
curl -s -X POST localhost:8080/api/orders/<id>/cancel
```

Health check: `GET /actuator/health`

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/orders` | Create an order. Returns **201** with `Location` header |
| GET | `/api/orders/{id}` | Get an order (served from Redis when cached) |
| POST | `/api/orders/{id}/cancel` | Cancel an order. **409** if the transition is not allowed |

Errors use RFC 7807 `ProblemDetail` responses; invalid input returns **400**.

## Tests

```bash
mvn test
```

Unit tests run without any external services (Kafka, DB and Redis are mocked) and cover:
the outbox write, ordering on publish failure, Kafka keying, duplicate-event handling,
late events for cancelled orders, and state-machine rules.

## Project layout

```
src/main/java/com/chandrapal/orders
  api/         REST controller, DTOs, error handling
  domain/      Order entity, OrderStatus state machine, repository
  service/     OrderService (transactions, caching)
  outbox/      OutboxEvent, OutboxPublisher (scheduled poller)
  messaging/   OrderEvent, OrderEventConsumer, processed-event store
  config/      Kafka topic and Redis cache configuration
src/main/resources/db/migration   Flyway SQL
```

## Possible next steps

- Replace the polling publisher with **Debezium CDC** on the outbox table
- Dead-letter topic with retry/backoff for malformed messages
- Integration tests with **Testcontainers** (Kafka + PostgreSQL)
- Split the risk check into its own service and add a Saga for payment
- Micrometer metrics for outbox lag and consumer throughput
