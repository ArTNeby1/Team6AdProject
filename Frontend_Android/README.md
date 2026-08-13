# LoomyTrip Android

Open this folder in Android Studio and run the `mobile` configuration.

## Google Maps

The app keeps its local route preview when no Maps key is configured. To use the real map:

1. Enable **Maps SDK for Android** in the team's Google Cloud project.
2. Add this line to `Frontend_Android/local.properties`:

   ```properties
   MAPS_API_KEY=your_android_maps_key
   ```

3. Restrict the key to the Android package `com.loomytrip.mobile` and the signing certificate used by the team.
4. Rebuild the app.

Do not commit `local.properties` or the API key.
