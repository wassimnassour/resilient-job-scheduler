# Resilient Job Scheduler

A Spring Boot application for scheduling and executing durable background jobs.

The project is being built incrementally. Its goal is to reliably run work after
the request that created it has finished, while preserving job state so pending
work can be recovered and retried.

## Current Progress

The foundation for the scheduler is in place:

- Jobs are modelled as JPA entities and persisted in an H2 database.
- A job has a type, JSON payload, schedule time, timestamps, and lifecycle
  status.
- Supported states are `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`,
  `RETRYING`, and `EXHAUSTED`.
- `JobRepository.findReadyJobs(...)` finds pending jobs whose scheduled time
  has arrived.
- A `JobHandlerRegistry` maps each job type to exactly one handler and fails
  fast for unsupported or duplicate types.
- `JobScheduler` delegates a job payload to the matching handler.
- `LOG` is the first available job type; its handler writes the payload to the
  application log.

## Architecture

```text
Job producer
    |
    v
Job table (type, payload, status, scheduledAt)
    |
    v
Ready-job query
    |
    v
Job scheduler
    |
    v
Job handler registry ---> LOG handler (current)
                         Email / other handlers (planned)
```

Each `JobHandler` owns the behavior for one job type:

```java
public interface JobHandler {
    String getType();

    void execute(JsonNode payload) throws Exception;
}
```

To add a new job type, create a Spring `@Component` that implements this
interface. The registry automatically discovers it through Spring injection.

## Roadmap

### 1. Job Lifecycle

- Add a service and API for creating jobs with a type, payload, and scheduled
  execution time.
- Validate job types and payloads before persisting a job.
- Expose job details and status for inspection.

### 2. Scheduled Execution

- Poll for jobs that are due using `findReadyJobs(...)`.
- Claim jobs safely by moving them from `PENDING` to `RUNNING`.
- Execute jobs asynchronously and mark successful executions as `SUCCEEDED`.

### 3. Failure Handling and Retries

- Record failures and transition unsuccessful jobs to `RETRYING`.
- Store attempt counts, error details, and the next retry time.
- Use a backoff policy and mark jobs `EXHAUSTED` once retries are depleted.

### 4. Resilience and Recovery

- Recover jobs left in `RUNNING` after an application restart or worker crash.
- Make execution idempotent where a handler can be retried safely.
- Prevent multiple workers from executing the same job concurrently.

### 5. Operations and Hardening

- Add structured logs, metrics, and health checks for queue depth, latency, and
  failures.
- Add administrative actions for retrying, cancelling, and inspecting jobs.
- Move from the in-memory H2 setup to a production database configuration.

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA / Hibernate
- H2 (development database)
- Maven

## Run Locally

Prerequisites: JDK 21 and Maven 3.8+, or the included Maven wrapper.

```bash
./mvnw spring-boot:run
```

The application starts on port `8080` by default. During local development, the
H2 console is available at `http://localhost:8080/h2-console`.

Use the following connection details:

```text
JDBC URL: jdbc:h2:mem:jobscheduler
User:     sa
Password: <empty>
```

## Test

```bash
./mvnw test
```

## Project Structure

```text
src/main/java/com/example/jobscheduler/
├── config/                 # Spring configuration
├── job/
│   ├── domain/             # Persisted Job entity
│   ├── enums/              # Job types and lifecycle statuses
│   ├── handler/            # JobHandler contract, registry, implementations
│   ├── repository/         # Ready-job persistence queries
│   └── scheduler/          # Handler delegation entry point
└── ResilientJobSchedulerApplication.java
```
