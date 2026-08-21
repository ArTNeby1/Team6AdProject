# XieMaonan (climbQMLM) — Contribution Summary

## Features delivered

| ID        | Feature                                | What it delivers                                             | Tech stack                                                   |
| --------- | -------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **US-22** | **Admin login**                        | A separate, secure admin login with role-based access (`admin` / `super_admin`): a dedicated admin login endpoint; tokens that distinguish regular users from admins; back-office APIs authorized by role — **403 on over-privilege, 401 on unauthenticated/expired**. | Spring Security, JWT (`typ` claim + per-request DB role resolution), `@PreAuthorize`, a standalone React admin front-end, Flyway，AWS Secrets Manager |
| **US-23** | **User list & search**                 | A read-only user list with pagination and email search, exposing no sensitive fields (never the password hash). | Spring Boot admin API (paged read-only projection), React table (search / pagination) |
| **US-24** | **LLM extraction-accuracy evaluation** | Scores every real import on content-level **Precision / Recall / F1 / Groundedness**, averages across all successful imports, and lets you drill into any single one (source text, extracted output, matched/missed places). Shows **N/A instead of a misleading 0%** when there's no reference; table is paginated. | React, Spring Boot, Python ML (Bedrock LLM-as-judge)         |

## Detailed history (by PR)

### [#17] Admin RBAC foundation + login & user list *(US-22, US-23)*

Built the admin console end-to-end. **Backend:** a JWT `typ` claim (`USER`/`ADMIN`) with `PrincipalType`, so `JwtAuthenticationFilter` loads the principal from the correct table via `AdminDetailsService`; admin roles are resolved **from the DB on every request** (a role change takes effect immediately, no re-issued token); defense-in-depth authorization in `SecurityConfig` (route-level) *and* `@PreAuthorize` (method-level); admin login (`AdminAuthController`/`AdminAuthService`); paged, hash-free user list (`AdminUserController`/`AdminUserService`, `PageResponse`). **Web:** admin auth context, protected routes, layout, and login / dashboard / users pages.

### ⭐ [#48] LLM Evaluation console page + live dashboard *(US-24 foundation)*

The extract agent returns Pydantic-valid JSON — but schema validity says nothing about whether it actually captured the real places. 

- **`AdminEvalPage.jsx`** (`/admin/eval`, behind `AdminProtectedRoute`, new sidebar item) — shows the source text, the agent's structured JSON output, and a **Precision / Recall / F1 + Groundedness** scorecard, making the point concretely: valid output can still miss most of the real content.
- **`ML/eval/evaluate_extraction.py`** — a standalone script computing the same content-level metrics against a gold-labelled sample; the page mirrors its numbers, so the UI and the offline script agree by construction.
- Turned the S0 placeholder dashboard into a **live** one (real total-user count from the admin API + the signed-in role), and fixed a CORS issue so Vite's fallback ports (5174/5175) don't break admin login.

This established both the **metric definitions** and the **"deterministic, explainable scoring"** philosophy that every later eval PR built on.

### [#49] Coverage tooling + admin unit tests

**JaCoCo** on the backend (`pom.xml`) with a Mockito `AdminUserServiceTest` (page/size clamping, search branch, DTO mapping, and a check that the password hash never leaks); **Vitest** on the web with tests for `api`, `AdminEvalPage`, and `AdminDashboardPage`. Admin source files reached 100% line coverage.

### [#65] Rollback of the admin web module

Cleanly reverted `Frontend_Web/src/admin/` to its #48 baseline on request (verified byte-for-byte).

### [#66] Live LLM Evaluation over real imports *(US-24)*

Rebuilt `/admin/eval` to score **every** import. Added an ML endpoint `POST /evaluate-extraction` (`ML/app/extraction_eval.py`) using a **Bedrock LLM-as-judge** to list the "gold" places, then a backend layer (`AgentValidationEvalService`, `AiPlanningClient`, DTOs) that averages the four metrics — **computed deterministically** from the judge's gold list, so scoring is explainable rather than a black-box model call.

### [#67] Frontend-only re-implementation

Re-derived the same scoring **entirely in the browser** against the already-deployed audit log (`GET /api/v1/admin/agent-validations`) — no new backend/ML endpoint, no Bedrock dependency, so it ships in ~2–3 min via S3 sync alone.

### [#68] Per-import evaluation detail page

Split the drill-down into a bookmarkable route `/admin/eval/:id` (`AdminEvalDetailPage.jsx`); extracted shared scoring into `evalScoring.js` + `EvalScoreCards.jsx` so list and detail pages score identically.

### [#69] Correct 401 for unauthenticated requests

Added an `AuthenticationEntryPoint` in `SecurityConfig` returning a proper **401** (instead of a bodyless 403), so expired/missing JWTs trigger the client's auto-logout and redirect. This is the piece that makes US-22's "401 on unauthenticated/expired" acceptance criterion actually hold.

### [#70] Show N/A instead of a misleading 0% *(US-24)*

When no gold reference can be detected (e.g. lowercase place names), P/R/F1 now render **N/A** rather than 0% — which had falsely read as "the model got everything wrong." Averages count only scoreable imports; Groundedness stays exact.

### ⭐ [#71] Seed default admin from AWS Secrets Manager

A security fix with real depth. `V2__seed_default_admin.sql` had seeded a `super_admin` whose password (`Admin@12345`) was **publicly known and its BCrypt hash committed to the repo** — meaning anyone who could read the source could log into the back-office of every environment, production included. XieMaonan re-based admin seeding onto the **same Secrets Manager pattern the project already used for the DB and JWT secrets**:

- **Terraform** — `random_password.admin_seed` written to Secrets Manager (`.../admin-seed`, email + random password); the ECS Java task injects `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD`; the execution role's `GetSecretValue` allowlist gains the new secret ARN (missing this step would fail ECS startup).
- **Backend** — a new `AdminSeeder` (`ApplicationRunner`) that creates the `super_admin` from the injected credentials, BCrypt-hashing **at runtime**; it's a no-op if either value is blank or the admin already exists, so it never overwrites a rotated password and is safe to run on every boot. Paired with `V13__remove_compromised_default_admin.sql`, which deletes the old account **only if** it still carries the leaked hash (so a manually-changed password is left alone), while leaving V2 untouched to preserve Flyway checksums.
- **Config** — production relies on ECS injection; `application-dev.yml` keeps the local one-click login.

Net result: **no production admin password anywhere in the repo** — the live initial password is random and readable only by someone with Secrets Manager access, while local dev is unaffected.

### [#73] Pagination for the per-import table *(US-24)*

Client-side paging on `AdminEvalPage.jsx` (5/10/20/50 rows), with the averaged metrics still covering all records.

