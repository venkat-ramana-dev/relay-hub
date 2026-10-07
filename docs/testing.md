# Relay Hub — Testing

## Testing

The project uses Spring's testing support together with Testcontainers and WireMock.

The test setup includes support for:

- Spring application testing
- JPA testing
- MVC/controller testing
- Spring Security testing
- Flyway testing
- PostgreSQL integration testing with Testcontainers
- External webhook simulation with WireMock

Using PostgreSQL through Testcontainers allows database-dependent behavior to be tested against the same database technology used by the application rather than relying only on an in-memory database.

WireMock can be used to simulate external webhook responses and failure scenarios.

### Run tests

Using Maven:

```bash
./mvnw test
```

or:

```bash
mvn test
```

---