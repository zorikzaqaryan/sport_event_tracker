# Sports Live Events Tracker

A small Java 21 / Spring Boot microservice that tracks live sports events. For every event marked `LIVE`,
the service polls an external score API every 10 seconds, transforms the response into a message,
and publishes it to a Kafka topic. When the event becomes `NOT_LIVE`, polling is cancelled.
---

## Tech stack

- Java 21 (compiles and runs on JDK 21+)
- Spring Boot
- Spring Web MVC, RestClient, Spring Validation, Spring Kafka, Spring Boot Actuator
- Micrometer + Prometheus
- springdoc-openapi (Swagger UI)
- Lombok
- JUnit 5, Mockito, Testcontainers (Kafka)
- Docker / Docker Compose

---

## Architecture

```
                +---------------------------------------+
                |  POST /events/status                   |
                |    EventStatusController               |
                |        |                               |
                |        v                               |
                |    EventStatusService                  |
                |        | (compute() per eventId)       |
                |        v                               |
                |    LiveEventScheduler                  |
                |        | (start/stop ScheduledFuture)  |
                |        v                               |
                |    ScorePollingJob (every 10s)         |
                |     /                  \                |
                |    v                    v               |
                |  ExternalScoreClient   ScorePublisher   |
                |  (RestClient)         (Kafka, retried)  |
                +---------------------------------------+
                                                     |
                                                     v
                                            Kafka topic:
                                            live-score-updates
```


### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (Desktop or compatible) for `docker compose` and the integration test

### One-shot: build + bring up the full stack

Builds the app image, starts Kafka, the app, and Prometheus — all wired together:

```bash
mvn clean package -DskipTests
docker compose up --build
```

Services started:

| Service    | URL                                              |
|------------|--------------------------------------------------|
| App        | http://localhost:8080                            |
| Swagger UI | http://localhost:8080/swagger-ui/index.html      |
| Health     | http://localhost:8080/actuator/health            |
| Prometheus | http://localhost:9090                            |
| Kafka-UI   | http://localhost:9081                            |
| Kafka      | localhost:9092 (host) / kafka:9093 (compose net) |

To tear it all down:

```bash
docker compose down
```

### Run the app outside Docker

You can run the app with Maven directly against a Kafka started separately (e.g. `docker compose up kafka prometheus`):

```bash
mvn spring-boot:run
```

By default it expects Kafka on `localhost:9092` and points the external score API at its own internal mock at `http://localhost:8080/mock`.

Override via environment variables:

```bash
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
EXTERNAL_SCORE_API_BASE_URL=http://my-real-upstream/api \
APP_POLLING_INTERVAL=5s \
mvn spring-boot:run
```

> **Prometheus target when running locally:** Prometheus runs inside Docker, so `app:8080` doesn't resolve to your Mac. Change the target in `docker/prometheus/prometheus.yml` to `host.docker.internal:8080`, then restart Prometheus:
> ```bash
> # edit docker/prometheus/prometheus.yml: targets: ["host.docker.internal:8080"]
> docker compose restart prometheus
> ```

---

## REST API

`POST /events/status` accepts a JSON body with two fields:

| Field     | Accepted JSON types  | Maps to                                                     |
| --------- | -------------------- |-------------------------------------------------------------|
| `eventId` | string **or** number | `String` (a JSON number like `1234` is coerced to `"1234"`) |
| `status`  | boolean **or** string| `EventStatus` enum: `live` / `not live`                     |




### Mark an event LIVE 

```bash
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" \
  -d '{"eventId":"1234","status":"live"}'
```

All return:

```json
{ "eventId": "1234", "status": "live", "message": "Polling started for event" }
```

A duplicate `LIVE` request is idempotent and returns `"Polling already active for event"`.

### Mark an event NOT_LIVE

```bash
# Canonical
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" \
  -d '{"eventId":"1234","status":"not live"}'

```

A `not live` request for an unknown event returns `"Event was not active"` (still HTTP 200 — idempotent).

### Validation examples (all return 400)

```bash
# Missing eventId
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" -d '{"status":"live"}'

# Blank eventId
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" -d '{"eventId":"   ","status":"live"}'

# Missing status
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" -d '{"eventId":"1234"}'

# Invalid status string
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" -d '{"eventId":"1234","status":"PAUSED"}'

# Numeric status (not boolean, not string)
curl -X POST http://localhost:8080/events/status \
  -H "Content-Type: application/json" -d '{"eventId":"1234","status":1}'
```



### Mock external score API (for local dev)

```bash
curl http://localhost:8080/mock/scores/1234
# {"eventId":"1234","currentScore":"0:0"}
```
The mock slowly increments scores so consecutive calls produce a believable stream.

---

## Kafka

- Topic: `live-score-updates`
- Key: `eventId` (as `String`) — guarantees per-event ordering on a single partition
- Value (JSON):

```json
{
  "eventId": "1234",
  "currentScore": "1:0",
  "publishedAt": "2026-11-11T11:11:11.123Z"
}
```

The producer is configured with `acks=all`, `retries=3`, and `enable.idempotence=true`.

The publisher (`KafkaScorePublisher`) wraps each send in a `whenComplete` future and additionally retries up to `app.kafka.publish-max-attempts` times on transient send failures (timeouts, broker errors).



## Observability

### Actuator + Prometheus

- `GET /actuator/health` — health
- `GET /actuator/metrics` — metric index
- `GET /actuator/prometheus` — Prometheus exposition format

### Custom business metrics

| Metric                          | Type    | Meaning                                                           |
| ------------------------------- | ------- | ----------------------------------------------------------------- |
| `live_events_count`             | Gauge   | Number of events currently being polled                           |
| `score_poll_success_total`      | Counter | Successful external score API calls                               |
| `score_poll_failure_total`      | Counter | Failed external score API calls                                   |
| `score_publish_success_total`   | Counter | Successful Kafka publishes                                        |
| `score_publish_failure_total`   | Counter | Kafka publishes that exhausted the retry budget                   |

### Prometheus

Scrapes the app every 5s. Open http://localhost:9090 and try queries like:

```
live_events_count
rate(score_poll_success_total[1m])
rate(score_publish_failure_total[5m])
```

---

## Swagger / OpenAPI

- http://localhost:8080/swagger-ui/index.html
- http://localhost:8080/v3/api-docs

---

## Tests

Run everything (unit + Kafka integration test via Testcontainers):

```bash
mvn test
```

Run just the unit tests:

```bash
mvn test -Dtest='!KafkaScorePublisherIntegrationTest'
```

> The integration test needs a working Docker daemon. 
> If Docker isn't running it will fail fast with a clear "Could not find a valid Docker environment" message.
> Start Docker Desktop and re-run.

### Manual load testing

`scripts/load_test.sh` simulates a realistic stream of status updates against the running service,
so you can watch Prometheus metrics accumulate in real time.
To validate messages open Kafka-UI
http://localhost:8081/


```bash
# Default: target localhost:8080, run for 2 minutes
./scripts/load_test.sh

# Custom URL and duration (e.g. 5 minutes)
./scripts/load_test.sh http://localhost:8080 300
```

What the script does:

- Waits for `/actuator/health` to return `UP` before sending any requests.
- Warms up by immediately setting the first 3 events LIVE so polling jobs and Kafka publishes start straight away.
- Randomly picks one of 8 event IDs, assigns LIVE (70 % chance) or NOT_LIVE (30 %), waits 1–8 seconds, and repeats — keeping several events active at once while cycling some in and out.
- Prints a live metrics snapshot in the terminal every 10 requests.
- On exit (Ctrl+C or timer), sets all events NOT_LIVE and prints a final snapshot.

Useful Prometheus queries once the script is running (`http://localhost:9090`):

| Query | What it shows |
| ----- | ------------- |
| `live_events_count` | Events currently being polled |
| `rate(score_poll_success_total[1m])` | Successful external API calls per second |
| `rate(score_publish_success_total[1m])` | Kafka publishes per second |
| `score_poll_failure_total` | Cumulative API errors |
| `score_publish_failure_total` | Cumulative Kafka publish errors |

Requirements: `curl` (pre-installed on macOS). `jq` is optional — responses are pretty-printed if it is present.



## AI usage

AI assistance (Cursor's agent (opus 4.6),ChatGPT ) was used to:
#### ChatGPT
- Discuss initial idea and main concepts

#### Cursor
- propose an initial project skeleton (package layout, file list, `pom.xml`),
- draft the dynamic-scheduling pattern (`ThreadPoolTaskScheduler` + `ConcurrentHashMap<String, ScheduledFuture<?>>`),
- generate a first pass at the test classes,
- draft README.
- Generate script for Manual load testing

What was reviewed, changed, or added by me on top of that:

- Added top-level `try/catch (Exception)` in `ScorePollingJob.run()` and a regression test,
- because a thrown exception in a `ScheduledExecutorService` task silently kills the schedule.
- Switched config from scattered `@Value` to a typed `@ConfigurationProperties` record with bean validation.
- Added `@PreDestroy` cleanup in `LiveEventScheduler` so all `ScheduledFuture`s are cancelled before shutdown.
- - The original AI-generated implementation crammed state mutation, scheduler calls, logging, and outcome tracking all 
- inside a single `ConcurrentHashMap.compute()` lambda — readable, but too dense. After the review, 
- the method was split into two private helpers (`handleLive` / `handleNotLive`)

