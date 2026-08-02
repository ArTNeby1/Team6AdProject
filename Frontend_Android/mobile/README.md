# Loomytrip Mobile

Android client built with Kotlin and Jetpack Compose.

## Sprint 1 prototype

The implemented Sprint 1 mobile scope is:

- local mock sign in and account registration
- traveler home and AI planner entry point
- paste travel-guide text (`F-02`)
- deterministic mock extraction through a replaceable repository
- review and include/exclude extracted places (`F-06`)
- confirm the reviewed result into the existing trip prototype

The end-to-end flow is:

`Sign in/Register -> Home -> Import guide -> Review extracted places -> Confirm itinerary`

All authentication and extraction content is local mock data. The mock planning repository is intentionally isolated so it can later be replaced by the Spring Boot/FastAPI contract without rewriting the screens.

## Sprint 2 implementation

The local Sprint 2 work adds:

- day-by-day itinerary browsing (`F-10`)
- shared itinerary state across trip, edit, and map screens
- add, delete, and reorder itinerary activities (`F-11`)
- an interactive native route map with numbered markers and route lines (`F-13`)
- external walking navigation through Google Maps with a `geo:` fallback (`F-15`)

The route map supports pinch-to-zoom, drag-to-pan, and tappable stop details. It is rendered locally, so it does not require an API key or network access.

## Run

Open the `Frontend_Android` directory in Android Studio, allow Gradle sync to finish, select the `mobile` run configuration, and launch an API 26+ emulator.
