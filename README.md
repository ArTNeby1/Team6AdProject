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

## Teammate note (Zheng Chaorui)
| **DB design** | Initial LoomyTrip schema (users, trips, planning drafts, destinations, transports) plus later migrations for notifications and agent-validation audit logs |
| **Backend** | Auth, planning import pipeline, trip CRUD & ownership, route estimation, recommendations, notifications, admin analytics |
| **Web (partial)** | Connected traveler UI to `/api/v1`, import day-split UX, MapPage / auth error fixes, admin eval page updates for validation logs |

## Teammate note (Weng Yuhao)
As the core frontend developer, I independently architected and implemented the user-facing Web application. Details in Frontend_Web/src/readme.md
