# Relay Hub — Security

## Security

Relay Hub has security controls around both API access and outbound webhook delivery.

### Passwords

User passwords are hashed using BCrypt before being stored.

### API Keys

API keys are generated when users are created. Only the SHA-256 hash of the API key is stored in the database.

The raw API key is returned during account creation.

When an API key is supplied with a request, Relay Hub hashes it and looks up the stored hash.

### JWT

User login produces a signed JWT.

The API uses stateless authentication and does not maintain server-side HTTP sessions.

JWTs are validated by a request filter before protected endpoints are processed.

### Webhook URL Validation

Webhook URLs are supplied by clients, so Relay Hub validates them before they are used for outbound requests.

This is important because allowing arbitrary server-side HTTP requests can create an SSRF risk.

The webhook URL validation rejects unsafe destinations such as loopback, private, link-local, and other restricted addresses.

### Redirect Handling

The webhook HTTP client does not automatically follow redirects.

This prevents a URL that was initially validated from automatically redirecting the request to another destination.

---

## Authentication & Authorization

Relay Hub supports two authentication mechanisms.

### JWT Authentication

JWT authentication is primarily used for normal user interaction with the API.

```text
POST /auth/login
       │
       ▼
Email + Password
       │
       ▼
AuthenticationManager
       │
       ▼
Signed JWT
       │
       ▼
Authorization: Bearer <token>
```

### API-Key Authentication

API keys provide an authentication mechanism for machine-to-machine clients.

```text
X-API-KEY
    │
    ▼
SHA-256 hash
    │
    ▼
Database lookup
    │
    ▼
Authenticated User
```

### Roles

Relay Hub currently has two roles:

```text
USER
ADMIN
```

Administrative endpoints under:

```text
/api/admin/**
```

require the `ADMIN` role.

---

### Why disable automatic redirects?

Webhook URLs are supplied by clients.

Even if the initial URL passes validation, automatically following a redirect could cause the HTTP client to contact a different destination.

Relay Hub therefore disables automatic redirect handling in the webhook HTTP client.

---