# LoomyTrip Mobile Contribution

This is the Android side of LoomyTrip. It is written in Kotlin with Jetpack Compose.

## My part

I am Wang Boliang and I mainly worked on the Mobile traveller app.

- Login, registration, log out and profile editing.
- Keeping the login token after the app is reopened.
- AI import: paste travel text, wait for the AI processing result, show errors for invalid input or service problems, and view earlier import records.
- Review extracted places: rename/delete a place, add another instruction, validate places, choose trip duration and assign places to days.
- Confirming the AI result and showing the created trip, including the start date, weather result and nearby suggestions returned by the backend.
- Trip page: see different days, edit activities and start times, add/delete days, delete a trip and use smart reorder.
- Map page: choose a trip and day, see the route/crowd information, compare public transport, driving, cycling and walking, and open Google Maps for directions.
- Showing import success/failure notifications and letting the user jump from a notification to the related page.
- Fixing Mobile navigation problems, for example returning from Trips to Profile and stopping the Trips tab from opening the Map page by mistake.
- Connecting the Android UI to the backend API, while keeping small local data fallbacks for some explore/recommendation screens.
- Some unit tests, lint/code-scanning fixes and Android network settings.

I also changed a few Mobile pages after comparing them with the Web flow, mainly so the buttons and trip data behave in a similar way.

## Basic flow

Sign in → AI import → review places → confirm trip → view or edit itinerary → map/navigation.

## How to run

Open `Frontend_Android` in Android Studio. After Gradle sync finishes, select the `mobile` configuration and run it on an API 26+ emulator.

The app connects to the team backend. For local testing, the debug configuration allows the listed development hosts only.
