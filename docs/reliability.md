# Relay Hub — Reliability

## Reliability & Retry Strategy

Relay Hub uses PostgreSQL as a database-backed work queue.

When a notification is ready for delivery, the worker selects eligible notifications using:

```sql
FOR UPDATE SKIP LOCKED
```

Eligible notifications are:

- `PENDING` notifications whose scheduled time has arrived
- `RETRYING` notifications whose next retry time has arrived

The worker claims a batch and changes the notifications to `PROCESSING`.

The transaction used to claim the notifications is committed before the external HTTP request is made. This prevents a slow or unavailable external service from holding database locks while the request is in progress.

### Retryable failures

The following failures are retried:

- HTTP `408 Request Timeout`
- HTTP `429 Too Many Requests`
- HTTP `5xx` server errors
- Network failures
- Connection and timeout failures where no HTTP status is available

### Permanent failures

HTTP `4xx` responses are treated as permanent failures, except for retryable statuses such as `408` and `429`.

These notifications are moved to `DEAD`.

A notification is also moved to `DEAD` after reaching the configured maximum retry count.

### Exponential Backoff

The retry delay is calculated from the current retry count:

```text
delay = 2 ^ retryCount minutes
```

With the current implementation:

```text
Retry 1 → 1 minute
Retry 2 → 2 minutes
Retry 3 → 4 minutes
Retry 4 → 8 minutes
Retry 5 → 16 minutes
```

The maximum retry count is configurable.

---

## Processing Recovery

A notification can enter `PROCESSING` and then become stuck if the application fails after claiming it but before the delivery result is finalized.

Without recovery, that notification could remain in `PROCESSING` indefinitely.

Relay Hub has a separate recovery worker that periodically searches for notifications that have remained in `PROCESSING` beyond the configured processing timeout.

The recovery flow is:

```text
PROCESSING
     │
     │ processing timeout
     ▼
Recovery Worker
     │
     ├── retry limit not reached
     │        ↓
     │     RETRYING
     │
     └── retry limit reached
              ↓
             DEAD
```

Recovered notifications are also recorded in notification history.

---

## Idempotency

Creating a notification requires an `Idempotency-Key` header.

The key is stored together with the user and the notification.

The database enforces uniqueness on:

```text
(key_name, user_id)
```

This prevents the same user from successfully creating multiple notifications using the same idempotency key.

### First request

```text
Client
   │
   │ Idempotency-Key: abc123
   ▼
Relay Hub
   │
   │ Key already exists?
   ├────────────── No
   │
   ▼
Create Notification
   │
   ▼
Create Idempotency Record
   │
   ▼
Return Notification
```

### Repeated request

```text
Client
   │
   │ Same Idempotency-Key: abc123
   ▼
Relay Hub
   │
   │ Existing record found
   ▼
Return previously created Notification
```

### Concurrent requests

There is a race when two requests using the same idempotency key arrive at approximately the same time.

Relay Hub handles this at the database level.

The unique constraint determines which request successfully creates the idempotency record. If another request loses that race, the application catches the database constraint violation and retrieves the existing idempotency record.

This means idempotency is not dependent only on an application-level "check then insert" operation.

### Idempotency cleanup

Idempotency records older than 24 hours are periodically removed by a scheduled cleanup worker.

---

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