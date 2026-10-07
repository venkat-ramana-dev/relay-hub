# Relay Hub — Performance

## Performance & Reliability Testing

Relay Hub was tested with application-level load tests, database/index benchmarking, worker processing tests, and retry/failure workflow tests. The results below were collected from the local development environment and should not be interpreted as production capacity measurements.

### API Ingestion Load Test

The `POST /api/notifications` endpoint was tested using k6.

Test configuration:

- Authentication: JWT
- Unique `Idempotency-Key` per request
- Database: PostgreSQL 17
- Notification worker disabled during the test
- HTTP timeout: 5 seconds
- Spring Boot running locally
- PostgreSQL running in Docker
- Machine: Intel Core i5-1135G7, 12 GB RAM
- Environment: Windows

| VUs | Requests | Throughput | P50 | P95 | Error Rate |
|---:|---:|---:|---:|---:|---:|
| 10 | 13,169 | 54.74 RPS | 33.71 ms | 61.95 ms | 0.35% |
| 20 | 10,979 | 45.42 RPS | 40.99 ms | 77.80 ms | 0.61% |
| 30 | 14,092 | 52.06 RPS | 29.24 ms | 67.93 ms | 0.93% |

Additional observations:

- Throughput remained approximately 45–55 RPS as concurrency increased from 10 to 30 VUs.
- P95 latency remained below approximately 80 ms across the tested runs.
- Error rate increased from 0.35% to 0.93% as concurrency increased.
- Some requests experienced significantly higher latency, with maximum latency reaching 30.08–57.43 seconds.
- Local CPU utilization became high during load generation, while memory remained relatively high.

These results represent a local development-machine benchmark and are not presented as the absolute capacity of Relay Hub.

### Mixed Read/Write Load Test

A second k6 workload tested an approximately 80/20 read/write mix:

- 80% `POST /api/notifications`
- 20% `GET /api/notifications/{id}`
- Maximum 15 VUs
- HTTP timeout: 5 seconds
- Failure threshold: `http_req_failed < 1%`

| Run | Throughput | Median | P95 | Error Rate |
|---|---:|---:|---:|---:|
| 1 | 62.02 req/s | 24.10 ms | 54.51 ms | 0.42% |
| 2 | 72.82 req/s | 20.53 ms | 49.08 ms | 0.39% |

Both runs passed the configured `http_req_failed < 1%` threshold.

Run 2 produced higher aggregate throughput and lower median and P95 latency than Run 1.

The maximum latency was significantly higher than the median and P95 because a small number of requests experienced very long delays. No p99 value was collected in the default k6 summary.

These are local-machine benchmark results and are not presented as production capacity measurements.

### Notification Queue Index Benchmark

The actual `NotificationWorker` dequeue query was benchmarked with approximately 1,000,000 rows in the `notifications` table.

The query selects eligible `PENDING` and `RETRYING` notifications, orders them by creation time, limits the batch to 100 rows, and uses `FOR UPDATE SKIP LOCKED`.

Indexes tested:

```sql
CREATE INDEX idx_notifications_queue
ON notifications(status, scheduled_time);

CREATE INDEX idx_notifications_retry
ON notifications(status, next_retry_time);
```

| Configuration | Execution Time |
|---|---:|
| Without indexes | 260.284 ms |
| With indexes | 36.366 ms |

The indexed query was approximately **7.15× faster** for the tested dataset.

Without the indexes, PostgreSQL performed a sequential scan across the `notifications` table. Approximately 1,000,000 rows were scanned, with 10,000 matching the worker condition and approximately 990,000 removed by the filter.

With the indexes, PostgreSQL used both notification indexes through a `BitmapOr`, followed by a `Bitmap Heap Scan`, remaining time-condition filtering, ordering, and `FOR UPDATE SKIP LOCKED`.

This benchmark used the actual worker dequeue query, including the `PENDING` and `RETRYING` conditions, ordering, batching, and row-locking behavior.

### Notification Worker Performance Test

The `NotificationWorker` was tested by seeding 10,001 notifications and allowing the worker to process the pending queue.

Test configuration:

- k6 executor: `constant-arrival-rate`
- Seed rate: 20 requests/second
- Duration: 500 seconds
- Worker batch size: 100
- Worker polling interval: 1 second
- Downstream endpoint: WireMock
- Expected API response: HTTP 201

The performance-test worker configuration was used only for this test and was not committed as a normal application configuration change.

Seeding results:

```text
Notifications created: 10,001
Checks succeeded:      10,001
Checks failed:              0
HTTP request rate:     20.0017 requests/sec
Average HTTP duration: 10.71 ms
P95 HTTP duration:     15.4 ms
Maximum HTTP duration: 62.13 ms
```

Worker processing results:

```text
Worker started:         2026-08-22 04:22:41.332337
Worker finished:        2026-08-22 04:25:39.184019
Total processing time:  177.851682 seconds
Throughput:             56 notifications/second
Remaining PENDING:      0
```

The worker successfully processed the seeded notifications, with zero `PENDING` notifications remaining after processing.

The observed throughput was approximately **56 notifications/second** for this test configuration and environment.

### Retry and Failure Workflow Tests

The notification delivery workflow was tested using the real Relay Hub application, PostgreSQL database, `NotificationWorker`, and WireMock as the downstream service.

Test configuration:

- 100 notifications per scenario
- WireMock as the downstream notification endpoint
- Worker batch size: 100
- Worker polling interval: 1 second
- Retry/backoff timing reduced for testing

| Scenario | Expected Flow | Result |
|---|---|---|
| 4xx response | `DEAD` | Passed |
| Repeated 5xx responses | `RETRYING → DEAD` | Passed |
| Temporary 5xx failures | `RETRYING → SUCCESS` | Passed |

#### 4xx → DEAD

WireMock returned a 4xx response for 100 notifications.

Result:

- All 100 notifications reached `DEAD`.
- The `DEAD` count was verified in the database.

#### 5xx → RETRYING → DEAD

WireMock repeatedly returned a 5xx response.

Result:

- Notifications transitioned through `RETRYING`.
- Retry count was verified as 3.
- Notifications eventually reached `DEAD`.
- Database state was verified.

#### Flaky Downstream → RETRYING → SUCCESS

WireMock was configured to return:

```text
Attempt 1 → HTTP 503
Attempt 2 → HTTP 503
Attempt 3 → HTTP 200
```

Result:

- The notification transitioned to `RETRYING` after the first two failures.
- The third attempt succeeded.
- Final notification state was `SUCCESS`.
- Retry count was verified as 2.
- Notification history was verified to contain the corresponding state transitions.

These workflow tests validate:

- Successful delivery → `SUCCESS`
- 4xx response → `DEAD`
- Maximum retries reached → `DEAD`
- Retryable failures → `RETRYING`
- Retry count and failure reason persistence
- Exponential backoff scheduling
- Notification history persistence

The tests use the real application, database, worker, and WireMock together rather than testing the delivery workflow only through isolated private-method unit tests.