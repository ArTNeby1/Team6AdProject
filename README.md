# LoomyTrip — Access & Demo Notes

Quick reference for deployed URLs and demo accounts (restored from the previous project root README).

## Admin Web

- **URL:** http://ad-project-frontend-web-dev.s3-website-ap-southeast-1.amazonaws.com/admin
- **Account:** admin@loomytrip.local
- **Password:** II9WMLvhiG_uP(iOh!!=

## Traveler (User)

- **Account (or register a new one):** `admin@qq.com`
- **Password:** `admin123`

## Teammate note (Zhang Yuhao)

Use GitHub Actions to build the CI/CD pipeline and AWS deployment.
**Scope:** `.github/workflows/...`, `ML/Dockerfile`, `backend/Dockerfile`,AWS terraform deploymengt
details in `.github/workflows/readme.md`

## Teammate note (Zheng Chaorui)
| **DB design** | Initial LoomyTrip schema (users, trips, planning drafts, destinations, transports) plus later migrations for notifications and agent-validation audit logs |
| **Backend** | Auth, planning import pipeline, trip CRUD & ownership, route estimation, recommendations, notifications, admin analytics |
| **Web (partial)** | Connected traveler UI to `/api/v1`, import day-split UX, MapPage / auth error fixes, admin eval page updates for validation logs |

## Teammate note (Weng Yuhao)
As the core frontend developer, I independently architected and implemented the user-facing Web application. Details in Frontend_Web/src/readme.md

## Teammate note (Wang Boliang)

I worked on the Android Mobile app. **Scope:** `Frontend_Android/mobile/...`

Login/registration and saved login state; AI import, review, place editing, validation and multi-day confirmation; trip viewing/editing/reordering; route map, transport links and Google Maps navigation; profile and import notifications. I also fixed Mobile navigation issues and added Mobile tests, lint/code-scanning follow-up and network settings.

## Teammate note (Xie Maonan)

As a full-stack developer, I built the Admin Web console end-to-end — admin authentication & RBAC, the read-only user management list, and the LLM extraction-accuracy evaluation dashboard (Precision / Recall / F1 / Groundedness). It spans the React admin frontend, the Spring Boot admin APIs, the Python ML evaluation service, and AWS Secrets Manager for admin credential seeding.

**Scope:** `Frontend_Web/src/admin/...`, backend admin controllers/services, `ML/eval/...`, `terraform/` (admin secret). Details in `Frontend_Web/src/admin/README.md`

## Teammate note (Zhang Mingchang)

Built and owned the ML/AI service end-to-end: text-to-structured-trip extraction (schema design + validation), grounded place recommendations, weather- and geography-aware itinerary ordering, and model selection/evaluation across Bedrock and local providers. Plus smaller integration fixes in Mobile, Web and backend to wire the ML service into the rest of the app.
Scope: ML/...
