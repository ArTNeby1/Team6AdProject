# LoomyTrip Android

Open this folder in Android Studio and run the `mobile` configuration.

## Maps

The in-app map follows the Web implementation: Leaflet renders the tile URL and default view returned by `GET /api/v1/map/config`. Trip markers come from the selected trip and day.

`GET /api/v1/trips/{tripId}/route?day={day}` supplies distance, travel time, and the multi-stop Google Maps URL. `Open Google Maps` opens that whole-day route in the Google Maps app or browser.

No Google Maps SDK key is required for either flow.
