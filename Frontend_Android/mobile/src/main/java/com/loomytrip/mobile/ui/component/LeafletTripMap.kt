package com.loomytrip.mobile.ui.component

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.CrowdHintDto
import com.loomytrip.mobile.data.network.MapConfigDto
import com.loomytrip.mobile.data.network.NearbyRecommendationDto
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun LeafletTripMap(
    activities: List<TripActivity>,
    mapConfig: MapConfigDto,
    nearbyPlaces: List<NearbyRecommendationDto> = emptyList(),
    crowdHint: CrowdHintDto? = null,
    showCrowd: Boolean = false,
    onMapClick: (Double, Double) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val bridge: LeafletBridge = remember {
        LeafletBridge { latitude, longitude -> currentOnMapClick(latitude, longitude) }
    }
    val html = remember(activities, mapConfig, nearbyPlaces, crowdHint, showCrowd) {
        buildLeafletHtml(activities, mapConfig, nearbyPlaces, crowdHint, showCrowd)
    }

    AndroidView(
        modifier = modifier.semantics { contentDescription = "Leaflet trip map" },
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                webViewClient = WebViewClient()
                addJavascriptInterface(bridge, "LoomyTripMap")
                setBackgroundColor(android.graphics.Color.rgb(242, 239, 231))
            }
        },
        update = { webView ->
            if (webView.tag != html.hashCode()) {
                webView.tag = html.hashCode()
                webView.loadDataWithBaseURL(
                    "https://loomytrip.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("LoomyTripMap")
            webView.destroy()
        }
    )
}

class LeafletBridge(
    private val onMapClick: (Double, Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun mapClicked(latitude: Double, longitude: Double) {
        mainHandler.post { onMapClick(latitude, longitude) }
    }
}

private fun buildLeafletHtml(
    activities: List<TripActivity>,
    mapConfig: MapConfigDto,
    nearbyPlaces: List<NearbyRecommendationDto>,
    crowdHint: CrowdHintDto?,
    showCrowd: Boolean
): String {
    val stops = JSONArray().apply {
        activities.filter(TripActivity::hasMapCoordinates).forEachIndexed { index, activity ->
            put(JSONObject().apply {
                put("number", index + 1)
                put("name", activity.title)
                put("category", activity.category)
                put("time", activity.startTime)
                put("lat", activity.latitude)
                put("lng", activity.longitude)
            })
        }
    }
    val recommendations = JSONArray().apply {
        nearbyPlaces.forEach { place ->
            val latitude = place.latitude
            val longitude = place.longitude
            if (latitude != null && longitude != null) {
                put(JSONObject().apply {
                    put("name", place.name)
                    put("category", place.category.orEmpty())
                    put("reason", place.reason.orEmpty())
                    put("lat", latitude)
                    put("lng", longitude)
                })
            }
        }
    }
    val crowdColor = when (crowdHint?.level?.uppercase()) {
        "HIGH" -> "#e85d4a"
        "MEDIUM" -> "#f0a038"
        else -> "#16a394"
    }

    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="icon" href="data:," />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; background: #f2efe7; }
            .route-pin, .nearby-pin { border: 3px solid white; border-radius: 50%; color: white;
              font: 700 12px system-ui; display: flex; align-items: center; justify-content: center;
              box-shadow: 0 2px 8px rgba(0,0,0,.28); }
            .route-pin { width: 27px; height: 27px; background: #168a75; }
            .nearby-pin { width: 23px; height: 23px; background: #f0a038; font-size: 14px; }
            .leaflet-popup-content { font: 13px system-ui; line-height: 1.35; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            const stops = $stops;
            const nearby = $recommendations;
            const map = L.map('map', { zoomControl: true }).setView(
              [${mapConfig.defaultLatitude}, ${mapConfig.defaultLongitude}], ${mapConfig.defaultZoom}
            );
            const tiles = L.tileLayer(${JSONObject.quote(mapConfig.tileUrlTemplate)}, {
              maxZoom: 19,
              attribution: ${JSONObject.quote(mapConfig.attribution)}
            }).addTo(map);

            const routePoints = [];
            stops.forEach(stop => {
              const point = [stop.lat, stop.lng];
              routePoints.push(point);
              const icon = L.divIcon({
                className: '',
                html: `<div class="route-pin">${'$'}{stop.number}</div>`,
                iconSize: [33, 33],
                iconAnchor: [16, 16]
              });
              L.marker(point, { icon })
                .addTo(map)
                .bindPopup(`<b>${'$'}{stop.number}. ${'$'}{escapeHtml(stop.name)}</b><br>${'$'}{escapeHtml(stop.time)} · ${'$'}{escapeHtml(stop.category)}`);
            });
            if (routePoints.length > 1) {
              L.polyline(routePoints, { color: '#168a75', weight: 5, opacity: .9 }).addTo(map);
            }
            function sizeAndFitMap() {
              map.invalidateSize(false);
              if (routePoints.length > 0) {
                map.fitBounds(routePoints, { padding: [38, 38], maxZoom: 15 });
              }
            }
            setTimeout(sizeAndFitMap, 100);
            setTimeout(sizeAndFitMap, 600);
            new ResizeObserver(() => map.invalidateSize(false)).observe(document.getElementById('map'));

            nearby.forEach(place => {
              const icon = L.divIcon({
                className: '',
                html: '<div class="nearby-pin">★</div>',
                iconSize: [29, 29],
                iconAnchor: [14, 14]
              });
              L.marker([place.lat, place.lng], { icon })
                .addTo(map)
                .bindPopup(`<b>Nearby: ${'$'}{escapeHtml(place.name)}</b><br>${'$'}{escapeHtml(place.category)}<br>${'$'}{escapeHtml(place.reason)}`);
            });

            if (${showCrowd && crowdHint != null} && routePoints.length > 0) {
              L.circle(routePoints[0], {
                radius: 900,
                color: '$crowdColor',
                fillColor: '$crowdColor',
                fillOpacity: .18,
                weight: 2
              }).addTo(map);
            }

            map.on('click', event => {
              if (window.LoomyTripMap) {
                window.LoomyTripMap.mapClicked(event.latlng.lat, event.latlng.lng);
              }
            });

            function escapeHtml(value) {
              const div = document.createElement('div');
              div.textContent = value || '';
              return div.innerHTML;
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

fun TripActivity.hasMapCoordinates(): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        (latitude != 0.0 || longitude != 0.0)
