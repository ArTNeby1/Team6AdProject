# LoomyTrip Backend — Individual Contribution Summary

**Student:** Zheng Chaorui (`qinkong` / `qinkong02` on GitHub)  
**Project:** LoomyTrip (Team 6 AD Project)  
**Role:** Backend lead (Spring Boot), database design, and traveler Web API integration  
**Repository:** [ArTNeby1/Team6AdProject](https://github.com/ArTNeby1/Team6AdProject)
---

## 1. Overview

I designed and implemented the core Spring Boot backend for LoomyTrip: schema and Flyway migrations, JWT authentication, planning-session / trip APIs, routing and recommendation integrations, import notifications, admin analytics, and unit/integration tests. I also wired the traveler Web frontend to live backend APIs and fixed several Web/backend contract issues during integration.

| Area | Summary |
|------|---------|
| **DB design** | Initial LoomyTrip schema (users, trips, planning drafts, destinations, transports) plus later migrations for notifications and agent-validation audit logs |
| **Backend** | Auth, planning import pipeline, trip CRUD & ownership, route estimation, recommendations, notifications, admin analytics |
| **Web (partial)** | Connected traveler UI to `/api/v1`, import day-split UX, MapPage / auth error fixes, admin eval page updates for validation logs |

---

## 2. Database design

### 2.1 Initial schema & planning domain

- Designed the relational model aligned with the project data dictionary (users, trips / days / schedules, destinations, draft places & activities, planning sessions, preferences, transports).
- Authored the primary Flyway migration and JPA entities/repositories.

**Key files**

| File | Role |
|------|------|
| `backend/src/main/resources/db/migration/V1__init_loomytrip_schema.sql` | Initial MySQL schema |
| `backend/DATA_DICTIONARY_zh.md` | Chinese data-dictionary notes used during design |
| `backend/src/main/java/com/loomytrip/backend/entity/*.java` | JPA entities (`Trip`, `TripDay`, `TripSchedule`, `PlanningSession`, `DraftPlace`, `DraftActivity`, `Destination`, …) |
| `backend/src/main/java/com/loomytrip/backend/repository/*.java` | Spring Data repositories |

### 2.2 Later schema extensions (author)

| Migration | Purpose |
|-----------|---------|
| `V8__add_import_notifications.sql` | `user_notification` table for async AI-import status |
| `V11__add_agent_validation_log.sql` | Persist LLM request/response for admin agent validation |

---

## 3. Backend implementation

### 3.1 Spring Boot foundation & security

- Bootstrapped the Maven project (`pom.xml`), package layout, CORS/JWT config, and REST controllers.
- Implemented register/login with BCrypt password hashing and JWT filters; resource access is gated by ownership helpers (e.g. `loadOwnedTrip` / `loadOwnedSession` → 403 for non-owners).

**Key files**

- `backend/pom.xml`
- `backend/src/main/java/com/loomytrip/backend/config/SecurityConfig.java`
- `backend/src/main/java/com/loomytrip/backend/config/JwtProperties.java`
- `backend/src/main/java/com/loomytrip/backend/security/JwtService.java`
- `backend/src/main/java/com/loomytrip/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/loomytrip/backend/controller/AuthController.java`
- `backend/src/main/java/com/loomytrip/backend/service/AuthService.java` (and related user services)

### 3.2 AI planning / import pipeline

- Replaced early import stubs with a **planning-session** domain: create session → async extract → draft places/activities → validate → confirm into a trip.
- Integrated the ML service via `AiPlanningClient` / HTTP client; hardened validation and failure paths in `PlanningService`.

**Key files**

- `backend/src/main/java/com/loomytrip/backend/controller/PlanningController.java`
- `backend/src/main/java/com/loomytrip/backend/service/PlanningService.java`
- `backend/src/main/java/com/loomytrip/backend/client/AiPlanningClient.java`
- `backend/src/main/java/com/loomytrip/backend/client/AiPlanningClientHttp.java`
- `backend/src/main/java/com/loomytrip/backend/event/InitialImportRequestedEvent.java`
- `backend/src/main/java/com/loomytrip/backend/event/InitialImportListener.java`
- `backend/src/main/java/com/loomytrip/backend/config/AsyncConfig.java`

### 3.3 Trips, routing, maps & recommendations

- Trip list/detail CRUD with per-user ownership; day-level schedules and transports.
- `GET /trips/{id}/route`: pairwise distance/time via `RoutingClientHttp`, persist `trip_transport`, build multi-stop Google Maps URL for external navigation.
- Map config + places geocoding (`MapPlacesClientHttp`, including Photon for online geocode so confirm works without Google Places).
- Recommendation and crowd-hint services wired to ML / map clients.

**Key files**

- `backend/src/main/java/com/loomytrip/backend/controller/TripController.java`
- `backend/src/main/java/com/loomytrip/backend/controller/MapController.java`
- `backend/src/main/java/com/loomytrip/backend/controller/RecommendationController.java`
- `backend/src/main/java/com/loomytrip/backend/service/TripService.java` (`estimateRoute`, dashboard summary, …)
- `backend/src/main/java/com/loomytrip/backend/service/MapService.java`
- `backend/src/main/java/com/loomytrip/backend/service/DestinationService.java`
- `backend/src/main/java/com/loomytrip/backend/service/RecommendationService.java`
- `backend/src/main/java/com/loomytrip/backend/service/CrowdHintService.java`
- `backend/src/main/java/com/loomytrip/backend/client/RoutingClientHttp.java`
- `backend/src/main/java/com/loomytrip/backend/client/MapPlacesClientHttp.java`
- `backend/src/main/java/com/loomytrip/backend/dto/response/TripRouteResponse.java`

### 3.4 Notifications & admin analytics

- Async import-complete notifications for travelers.
- Trip dashboard/summary endpoint; admin analytics over sessions/trips.
- Agent-validation audit log API (store LLM I/O for admin evaluation UI).

**Key files**

- `backend/src/main/java/com/loomytrip/backend/controller/NotificationController.java`
- `backend/src/main/java/com/loomytrip/backend/controller/AdminAnalyticsController.java`
- `backend/src/main/java/com/loomytrip/backend/controller/AdminAgentValidationController.java`
- `backend/src/main/java/com/loomytrip/backend/service/NotificationService.java`
- `backend/src/main/java/com/loomytrip/backend/service/AdminAnalyticsService.java`
- `backend/src/main/java/com/loomytrip/backend/service/AgentValidationLogService.java`
- `backend/src/main/java/com/loomytrip/backend/entity/UserNotification.java`
- `backend/src/main/java/com/loomytrip/backend/entity/AgentValidationLog.java`

### 3.5 Testing

Expanded unit and integration coverage for services, HTTP clients, JWT, mappers, and analytics.

**Key files**

- `backend/src/test/java/com/loomytrip/backend/service/TripServiceTest.java`
- `backend/src/test/java/com/loomytrip/backend/service/PlanningServiceCoreTest.java`
- `backend/src/test/java/com/loomytrip/backend/service/PlanningServiceAuditTest.java`
- `backend/src/test/java/com/loomytrip/backend/service/AuthAndNotificationServiceTest.java`
- `backend/src/test/java/com/loomytrip/backend/service/DestinationMapRecommendationServiceTest.java`
- `backend/src/test/java/com/loomytrip/backend/service/AdminAnalyticsAndCrowdHintServiceTest.java`
- `backend/src/test/java/com/loomytrip/backend/service/AgentValidationLogServiceTest.java`
- `backend/src/test/java/com/loomytrip/backend/client/HttpClientsTest.java`
- `backend/src/test/java/com/loomytrip/backend/mapper/EntityMapperTest.java`
- `backend/src/test/java/com/loomytrip/backend/security/JwtAndDetailsServiceTest.java`
- `backend/src/test/java/com/loomytrip/backend/FeatureInsightsIntegrationTest.java`

---

## 4. Web frontend contributions (selected)

Not the primary Web owner, but I connected and fixed traveler/admin flows against the backend I maintained.

| Work | Files |
|------|--------|
| Wire traveler app to live `/api/v1` (auth, trips, import) | `Frontend_Web/src/api.js`, `context/AuthContext.jsx`, `context/TripContext.jsx`, `pages/LoginPage.jsx`, `RegisterPage.jsx`, `HomePage.jsx`, `ImportPage.jsx`, `ItineraryListPage.jsx`, `ItineraryDetailPage.jsx`, `ProfilePage.jsx` |
| Auto day-split on import + clearer auth errors | `Frontend_Web/src/pages/ImportPage.jsx`, `context/AuthContext.jsx` |
| Fix MapPage crash from mismatched route API fields | `Frontend_Web/src/pages/MapPage.jsx` |
| Admin eval UI for persisted agent-validation logs | `Frontend_Web/src/admin/pages/AdminEvalPage.jsx` (+ tests) |

---

## 5. Must-feature mapping (backend ownership)

| Feature | Backend support (high level) |
|---------|------------------------------|
| F-01 Auth | JWT register/login + filter |
| F-02–F-08 Planning import | `PlanningService` session lifecycle, draft CRUD, confirm |
| F-10 / F-12 Trips by day | `TripService` list/get owned trips |
| F-14 Route distance/time | `TripService.estimateRoute` + `RoutingClientHttp` |
| F-15 External navigation | `googleMapsUrl` in `TripRouteResponse` |

---

