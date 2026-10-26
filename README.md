# Painting Gallery Store

[![CI](https://github.com/Nitika-code11/ecommerce-painting/actions/workflows/ci.yml/badge.svg)](https://github.com/Nitika-code11/ecommerce-painting/actions/workflows/ci.yml)

A full-stack catalogue for an online painting store: a Spring Boot REST API and a React single-page app.

| | |
| --- | --- |
| **Backend** | Java 17, Spring Boot 3.3, Spring Data JPA, Bean Validation, Flyway, PostgreSQL (H2 for tests and no-Docker runs), Maven |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS v4, React Router, Vitest + Testing Library |
| **CI / security** | GitHub Actions: backend `verify`, frontend audit + test + build, CodeQL, dependency review; Dependabot for updates |

## Quick start

Prerequisites: JDK 17+ and Node.js 20+ (the Maven wrapper downloads Maven itself).

```bash
# 1. API on http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 2. UI on http://localhost:5173 (in a second terminal)
cd frontend
npm install
npm run dev
```

**With Docker instead** (no JDK or Node needed): `docker compose up --build`, then open http://localhost:3000. This starts PostgreSQL too, and **your data is kept** between restarts (until you run `docker compose down -v`). Setup steps for Windows, backups, configuration and troubleshooting are in [docs/docker.md](docs/docker.md).

The default `dev` profile loads ten public-domain paintings (images from Wikimedia Commons), so the catalogue is populated on first start. Without Docker the data lives in memory and resets on every restart; with Docker it is stored in PostgreSQL.

Browsing is public. To add, edit or delete paintings, click **Sign in** and use the dev administrator `admin` / `admin12345` (created by the `dev` profile only; see [Security](#security)).

## Architecture

```text
Browser ──► React SPA (Vite) ──/api──► Spring Boot ──► PostgreSQL (Docker) / H2 (tests, no-Docker)
            validates responses        controller → service → repository
            at runtime                 DTOs at the edge, JPA entity inside
```

- **Layering:** `PaintingController` (HTTP) → `PaintingService` (use cases, transactions) → `PaintingRepository` (Spring Data JPA).
- **API contract:** requests and responses are DTOs (`PaintingRequest`, `PaintingResponse`), so the JSON shape is independent of the database schema. Validation lives on the request DTO.
- **Security:** stateless JWT bearer authentication (Spring Security resource server). Reads are public; writes require the `ADMIN` role.
- **Errors:** every failure, including framework errors such as `405` or malformed JSON, returns the same `ApiError` body (see below). Stack traces are never exposed.
- **Frontend safety:** every API response is checked at runtime before it is rendered, and `400` field errors are shown next to the matching form fields.
- **Dev networking:** Vite proxies `/api` to the backend; the backend also allows the configured origins via CORS for deployed setups.

## API

Interactive documentation (Swagger UI) is at http://localhost:8080/swagger-ui.html and the raw OpenAPI document at `/v3/api-docs`. Both are enabled in the `dev` profile only. Use **Authorize** in Swagger UI and paste the `accessToken` from `POST /api/auth/login` to try the admin endpoints.

Base path: `/api/paintings`

| Method | Path | Who | Description | Success |
| --- | --- | --- | --- | --- |
| `GET` | `/api/paintings?title=&page=&size=` | public | Paged list; `title` is a case-insensitive substring match | `200` |
| `GET` | `/api/paintings/{id}` | public | One painting | `200` |
| `POST` | `/api/paintings` | `ADMIN` | Create | `201` |
| `PUT` | `/api/paintings/{id}` | `ADMIN` | Replace all editable fields | `200` |
| `DELETE` | `/api/paintings/{id}` | `ADMIN` | Delete | `204` |
| `POST` | `/api/auth/register` | public | Create a `CUSTOMER` account | `201` |
| `POST` | `/api/auth/login` | public | Exchange credentials for a token | `200` |
| `GET` | `/actuator/health` | public | Liveness | `200` |

A missing or invalid token gets `401`; a valid token without the `ADMIN` role gets `403`. Both use the standard error body below.

`page` is zero-based (default `0`); `size` defaults to `12` and is capped at `100`. Results are ordered by `id`.

Page response:

```json
{ "content": [ { "id": 1, "title": "Sunset", "artist": "A. Sharma", "description": "Acrylic", "price": 4500.00, "stock": 3, "imageUrl": "https://example.com/sunset.jpg" } ],
  "page": 0, "size": 12, "totalElements": 1, "totalPages": 1 }
```

Validation rules: `title` ≤ 150 chars and `artist` ≤ 120 chars (both required), `description` ≤ 2000, `price` ≥ 0.01 with at most 2 decimals, `stock` ≥ 0, `imageUrl` an `http(s)` URL ≤ 1000 chars (optional).

Error response (all failures):

```json
{ "timestamp": "2026-01-01T10:00:00Z", "status": 400, "error": "Bad Request",
  "message": "Request validation failed", "path": "/api/paintings",
  "fieldErrors": { "title": "must not be blank" } }
```

Example (sign in, then create a painting):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin12345"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -X POST http://localhost:8080/api/paintings -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"Sunset Over Hills","artist":"A. Sharma","price":4500.00,"stock":3}'
```

## Security

- **Authentication:** `POST /api/auth/login` verifies the password against a BCrypt hash and returns an HS256-signed JWT (claims: `sub` = username, `roles`, `iat`, `exp`; lifetime `APP_JWT_TTL`, default 60 minutes). Clients send it as `Authorization: Bearer <token>`.
- **Authorisation:** `GET` on paintings and the auth endpoints are public. Everything else needs a valid token, and `POST`/`PUT`/`DELETE` on paintings are restricted with `@PreAuthorize("hasRole('ADMIN')")` on the controller. Unknown routes are denied by default.
- **Roles:** `CUSTOMER` (self-service sign-up via `/api/auth/register`) and `ADMIN` (never self-service; created from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` at startup if that account does not exist).
- **Hygiene:** usernames are unique ignoring case; wrong password and unknown user return the same `401` message and do the same BCrypt work; passwords are never logged or returned; the app refuses to start without a signing key of at least 32 bytes.
- **Stateless, so no CSRF:** the token is sent explicitly in a header, not in a cookie. The UI keeps it in `sessionStorage`, so it is gone when the tab closes; the trade-off is that script injection (XSS) could read it, which is why the UI renders no unescaped HTML.
- **UI:** guests see **Sign in**; admins additionally see Add/Edit/Delete; management routes redirect guests to sign in. This is convenience only; the API enforces every rule.

## Configuration

Backend (`backend/src/main/resources/application*.yml`):

| Setting | Default | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` adds the sample-data migration (`db/dev`) and the H2 console (`/h2-console`, JDBC URL `jdbc:h2:mem:paintingdb`, user `sa`, empty password). Any other profile runs only the schema migration. |

### Database migrations

The schema is owned by [Flyway](https://flywaydb.org); Hibernate only validates it (`ddl-auto: validate`).

| Location | Applied in | Contents |
| --- | --- | --- |
| `backend/src/main/resources/db/migration` | every profile | Schema (`V1__create_paintings.sql`). Add new changes as new versioned files; never edit an applied one. |
| `backend/src/main/resources/db/dev` | `dev` only | Sample paintings (`V1000__…`). Versions start at 1000 so they sort after schema migrations. |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | in-memory H2 | The database. docker-compose sets these for its PostgreSQL. **Required** (no H2 fallback) when the profile is not `dev`, so a misconfigured deployment can't quietly lose data. |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed to call `/api/**`. |
| `APP_JWT_SECRET` | none (required outside `dev`) | HMAC signing key, at least 32 bytes. Keep it in a secrets manager; rotating it signs everyone out. |
| `APP_JWT_TTL` | `60m` | Access-token lifetime. |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | empty | Creates this `ADMIN` account at startup if it does not exist. The `dev` profile sets `admin` / `admin12345`: public, throwaway values for local use only. |

Frontend: see [`frontend/README.md`](frontend/README.md) (`VITE_API_BASE_URL`, `VITE_ENABLE_MUTATIONS`).

## Testing

```bash
cd backend  && ./mvnw verify    # unit (Mockito), repository (@DataJpaTest) and API integration tests (MockMvc + H2),
                                # plus tests against a real PostgreSQL started by Testcontainers (needs Docker; skipped without it)
cd frontend && npm test         # component, page, API-client and validation tests
k6 run load-tests/catalogue.js  # load test with pass/fail latency targets (see docs/performance.md)
```

## Repository layout

```text
backend/    Spring Boot API (Maven wrapper included)
frontend/   React app
docker-compose.yml, backend/Dockerfile, frontend/Dockerfile   Container setup (see docs/docker.md)
load-tests/ k6 load test
docs/       Specs, roadmap, performance targets, AWS OIDC guide
.github/    Workflows (CI, CodeQL, dependency review, AWS OIDC check) and Dependabot config
```

## Scope and known limitations

- **Authentication is deliberately minimal:** no refresh tokens or logout-on-server (a token stays valid until it expires, and its role is trusted until then), no login rate limiting or lockout, no password reset or email verification, no user-management endpoints. All are noted in [`docs/roadmap.md`](docs/roadmap.md).
- **The database is a single local PostgreSQL container.** Data persists in a Docker volume, but there are no automated backups, replication or managed hosting (Amazon RDS is on the roadmap). Running without Docker uses in-memory H2, which loses data on restart.
- **Images are URLs only** (no upload/storage yet). The seed data hot-links Wikimedia Commons.
- Checkout, payments and orders are out of scope for this milestone.

## License

[MIT](LICENSE)
