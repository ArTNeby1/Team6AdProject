# Loomytrip Mobile

Android app for Loomytrip, built with Kotlin and Jetpack Compose.

## Current progress

Sprint 1:

- local sign in and registration
- home page and trip planner entry
- paste travel guide text (`F-02`)
- review the extracted places (`F-06`)
- confirm the selected places as a trip

Sprint 2:

- browse the itinerary by day (`F-10`)
- add, delete and reorder activities (`F-11`)
- route map with numbered stops (`F-13`)
- open external navigation (`F-15`)

## Notes

The backend connection is not finished yet. Login, place extraction and itinerary data currently use local mock data. The map is also drawn locally, so it does not need a map API key.

## Run

Open the `Frontend_Android` directory in Android Studio, allow Gradle sync to finish, select the `mobile` run configuration, and launch an API 26+ emulator.
