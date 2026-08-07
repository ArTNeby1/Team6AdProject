# LoomyTrip Backend

Spring Boot 3.5 / Java 21 API for the LoomyTrip travel planning platform.

## Stack

- Spring Web, Data JPA, Security (JWT), Validation, Actuator
- MySQL database: **`LoomyTrip`**
- Flyway migrations under `src/main/resources/db/migration`
- Layered packages: `controller` / `service` / `repository` / `entity` / `dto` / `client` / `security`

## Local setup

1. Create the database (Flyway creates tables only):

```sql
CREATE DATABASE LoomyTrip CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. Set credentials (do **not** commit real passwords; use env vars):

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="replace-with-a-long-random-secret-at-least-32-bytes"
```

`application-dev.yml` reads `DB_PASSWORD` from the environment (no default password in repo).

3. Run:

```powershell
cd D:\NUS\ADproject\backend
mvn spring-boot:run
```

Health: `GET http://localhost:8080/api/v1/health`

## API prefix

All business APIs are under `/api/v1`:

| Module | Path | Notes |
|--------|------|-------|
| Auth | `/api/v1/auth/register`, `/login` | Implemented (JWT) |
| Planning | `/api/v1/planning-sessions` | Create/list/chat; AI refine/confirm placeholders |
| Trips | `/api/v1/trips` | Create/list/get; generate is placeholder |
| Destinations | `/api/v1/destinations` | Search |
| Recommendations | `/api/v1/recommendations` | Stub (uses trip context later) |

Protected routes require `Authorization: Bearer <token>`.

## External clients

- `AiPlanningClient` (`AiPlanningClientHttp`) — calls the local Python/LLM planning
  service under `ML/app` (`uvicorn main:app --app-dir ML/app --port 8001`) over HTTP.
  Base URL configurable via `loomytrip.ai.base-url` / `AI_SERVICE_BASE_URL` (default
  `http://localhost:8001`). `extractTravelInfo` is wired to the Python service's
  `/extract-travel-info`; if that service isn't running the client degrades to a
  `STUB`-shaped response instead of failing the request. `generateDailyItinerary`
  is still a stub — the Python side (`ML/app/orchestrator.py`) currently only runs
  the extraction + recommendation agents, not day-by-day itinerary generation.
- `MapPlacesClient` — Places / geocoding validation (still stub)
- `RoutingClient` — Routes / travel time (still stub)

## Existing MySQL schema

If `LoomyTrip` already has tables matching V1, baseline Flyway before first start:

```powershell
mvn flyway:baseline -Dflyway.url=jdbc:mysql://127.0.0.1:3306/LoomyTrip -Dflyway.user=root -Dflyway.password=...
```

Or use an empty database and let `V1__init_loomytrip_schema.sql` create everything.

## Tests

```powershell
mvn test
```

Tests use the `test` profile (H2 in-memory, Flyway disabled, Hibernate `create-drop`).
