# Relay Hub — Engineering

## Project Structure

```text
src/
└── main/
    ├── java/
    │   └── dev/venkat/relayhub/
    │       ├── bootstrap/
    │       ├── config/
    │       ├── controller/
    │       ├── dto/
    │       │   ├── internal/
    │       │   ├── request/
    │       │   └── response/
    │       ├── entity/
    │       ├── enums/
    │       ├── exception/
    │       ├── mapper/
    │       ├── repository/
    │       ├── security/
    │       ├── service/
    │       ├── util/
    │       └── worker/
    │
    └── resources/
        ├── db/
        │   └── migration/
        └── application*.yml
```

### Important Components

| Component | Responsibility |
| :--- | :--- |
| `NotificationController` | Notification API endpoints |
| `NotificationService` | User-facing notification operations |
| `NotificationCreationService` | Transactional notification and idempotency record creation |
| `NotificationProcessor` | Claiming, finalizing, retrying, and recovering notifications |
| `DeliveryService` | Makes outbound webhook requests |
| `NotificationWorker` | Polls and processes eligible notifications |
| `NotificationProcessingRecoveryWorker` | Recovers stuck `PROCESSING` notifications |
| `IdempotencyCleanupWorker` | Removes expired idempotency records |
| `JwtFilter` | Processes JWT authentication |
| `ApiKeyFilter` | Processes API-key authentication |
| `WebhookUrlValidator` | Validates webhook destinations |
| `NotificationHistoryService` | Records notification state transitions |
| `GlobalExceptionHandler` | Converts application exceptions into API error responses |

---

## Design Decisions

### Why use PostgreSQL as the source of truth?

Notification state needs to survive application restarts.

If the application stops while notifications are waiting for delivery, those notifications should still exist when the application starts again.

Keeping notification state in PostgreSQL provides that durability and also allows workers to query notifications based on their current state and scheduled time.

### Why use a database-backed worker instead of an in-memory queue?

The main requirement of Relay Hub is reliable delivery.

An in-memory queue would lose queued work if the application process stopped.

With PostgreSQL, notification state is persisted and can be recovered after an application restart.

It also gives multiple worker instances a shared source of truth.

### Why `FOR UPDATE SKIP LOCKED`?

Multiple workers may attempt to process notifications at the same time.

`FOR UPDATE` locks the selected rows, while `SKIP LOCKED` allows another worker to skip rows that another worker has already claimed.

This allows workers to coordinate through PostgreSQL without processing the same notification concurrently.

### Why separate claiming and delivery?

The worker first claims the notification and commits the state change to `PROCESSING`.

Only after that does it make the external HTTP request.

This avoids keeping a database transaction open while waiting for an external server.

It also gives the recovery worker enough information to identify notifications that were left in `PROCESSING`.

### Why finalize delivery in a separate transaction?

The external HTTP request happens outside the transaction used to claim the notification.

After the request completes, `finalizeDelivery()` starts a new transaction and locks the notification using a pessimistic write lock before updating its state.

This keeps the database transaction focused on the state update rather than the network request.

### Why idempotency?

Clients can retry an API request when they do not receive a response because of a network problem.

Without idempotency, the retry could create another notification even though the original request had already succeeded.

The idempotency key associates repeated requests with the notification that was already created.

The database unique constraint also protects against concurrent requests using the same key.

### Why notification history?

The current notification status only tells us where the notification is now.

For example:

```text
PENDING → PROCESSING → RETRYING → PROCESSING → SUCCESS
```

Without a history table, the intermediate states would no longer be available after the notification reaches `SUCCESS`.

The history table keeps those transitions and their associated delivery information.

### Why JSONB for the payload?

Webhook payloads can have different structures depending on the client and event.

PostgreSQL `JSONB` allows Relay Hub to store structured JSON without forcing every webhook type into the same relational schema.

### Why disable automatic redirects?

Webhook URLs are supplied by clients.

Even if the initial URL passes validation, automatically following a redirect could cause the HTTP client to contact a different destination.

Relay Hub therefore disables automatic redirect handling in the webhook HTTP client.

---

## Error Handling

API errors are returned using a consistent response structure.

Example:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-10-07T10:00:00Z"
}
```

The global exception handler handles application-level errors such as:

- Validation failures
- Missing request headers
- Invalid parameters
- Duplicate users
- Missing users
- Missing notifications
- Invalid credentials
- Unsafe webhook URLs
- Unexpected server errors

Authentication failures return a generic invalid-credentials message rather than exposing whether the email or password was incorrect.

---

## Observability

Relay Hub uses application logging to record important events during request handling and background processing.

Examples include:

- User registration
- Login attempts
- Notification creation
- Worker batch processing
- Webhook delivery attempts
- Successful deliveries
- Retry scheduling
- Notifications moved to `DEAD`
- Processing recovery
- Idempotency hits
- Invalid API-key attempts
- Unsafe webhook URL rejection

The administrator API also exposes counts for:

```text
PENDING
RETRYING
SUCCESS
DEAD
```

These provide a basic view of the current notification state.

---

## What I Learned Building Relay Hub

The main goal of Relay Hub was not just to build another CRUD application. I wanted to work through the problems that appear when an application has to handle asynchronous work and unreliable external systems.

Some of the areas I worked on were:

- Designing a persistent notification lifecycle
- Handling concurrent workers with PostgreSQL row locking
- Implementing idempotency at the database level
- Separating database transactions from external HTTP calls
- Designing retry and exponential backoff behavior
- Recovering work left behind by failed processing
- Validating user-controlled webhook destinations
- Implementing multiple authentication mechanisms
- Maintaining a history of state transitions
- Running PostgreSQL and the application together using Docker Compose
- Testing database and external HTTP behavior with Testcontainers and WireMock

The project is intentionally built around failure handling because reliable delivery is mostly about what happens when the normal path does not work.

---