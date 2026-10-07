# Relay Hub — Configuration

## Configuration

Relay Hub uses environment variables for values that differ between environments.

Example:

```env
POSTGRES_DB=relayhub
POSTGRES_USER=relayhub
POSTGRES_PASSWORD=your-password

JWT_SECRET=your-base64-secret

RELAYHUB_ADMIN_EMAIL=admin@relayhub.dev
RELAYHUB_ADMIN_PASSWORD=your-admin-password

SPRING_PROFILES_ACTIVE=local
```

Worker behavior can be configured using:

```text
relayhub.worker.poll-interval
relayhub.worker.batch-size
relayhub.worker.processing-recovery-interval
relayhub.worker.processing-timeout
relayhub.notification.max-retries
```

The current configuration uses:

```text
Worker poll interval:          5 seconds
Worker batch size:             10
Processing recovery interval:  120 seconds
Processing timeout:             5 minutes
Maximum retries:                5
```

Sensitive values such as database passwords and JWT secrets should be supplied through environment variables rather than committed to source control.

---

## Running Locally

### Prerequisites

- Docker
- Docker Compose

### 1. Clone the repository

```bash
git clone <repository-url>
cd relay-hub
```

### 2. Configure environment variables

Create the environment file expected by Docker Compose.

Example:

```env
POSTGRES_DB=relayhub
POSTGRES_USER=relayhub
POSTGRES_PASSWORD=your-password

JWT_SECRET=your-base64-secret

RELAYHUB_ADMIN_EMAIL=admin@relayhub.dev
RELAYHUB_ADMIN_PASSWORD=your-admin-password
```

### 3. Start Relay Hub

```bash
docker compose up --build
```

Docker Compose starts:

```text
Relay Hub API
      +
PostgreSQL
```

The PostgreSQL health check must pass before the API container starts.

Flyway applies the database migrations automatically during application startup.

### Application

```text
http://localhost:8082
```

### Swagger UI

```text
http://localhost:8082/swagger-ui/index.html
```

### PostgreSQL

PostgreSQL is exposed locally on:

```text
localhost:5434
```

The application connects to PostgreSQL using the Docker Compose service name when both containers are running together.

---

## Local Development

Relay Hub supports local development profiles.

When running with the `dev` or `local` profile, the application can bootstrap an administrator account if no administrator currently exists.

The bootstrap password and email are configurable through environment variables.

This behavior is intended for local/development use and is not required for normal production account provisioning.

---

## Database Migrations

Flyway is used to manage database schema changes.

Migration files are stored under:

```text
src/main/resources/db/migration/
```

Migrations are applied automatically when the application starts.

Using Flyway keeps schema changes versioned alongside the application instead of relying on Hibernate to generate database changes.

---