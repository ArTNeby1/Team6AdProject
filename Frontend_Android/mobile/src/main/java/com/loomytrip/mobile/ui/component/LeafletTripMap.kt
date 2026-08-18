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
    val crowdLevel = crowdHint?.level?.uppercase().orEmpty()
    val crowdBadgeClass = when (crowdLevel) {
        "HIGH" -> "high"
        "MEDIUM", "MODERATE" -> "moderate"
        else -> "low"
    }

    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="icon" href="data:," />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script src="https://unpkg.com/leaflet.heat@0.2.0/dist/leaflet-heat.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; background: #f2efe7; }
            .route-pin, .nearby-pin { border: 2px solid white; border-radius: 50%; color: white;
              font: 800 14px system-ui; display: flex; align-items: center; justify-content: center;
              box-shadow: 0 4px 12px rgba(0,0,0,.3); position: relative; box-sizing: border-box; }
            .route-pin { width: 32px; height: 32px; background: #168a75; }
            .nearby-pin { width: 29px; height: 29px; background: #f0a038; font-size: 13px; }
            .route-pin::after, .nearby-pin::after { content: ''; position: absolute; bottom: -10px;
              left: 50%; transform: translateX(-50%); border-left: 8px solid transparent;
              border-right: 8px solid transparent; }
            .route-pin::after { border-top: 12px solid #168a75; }
            .nearby-pin::after { border-top: 12px solid #f0a038; }
            .leaflet-popup-content { font: 13px system-ui; line-height: 1.35; }
            .crowd-card { position: absolute; z-index: 900; left: 12px; bottom: 12px; width: 178px;
              padding: 10px; border: 1px solid #168a75; border-radius: 14px; background: rgba(255,255,255,.94);
              box-shadow: 0 8px 24px rgba(0,0,0,.15); font: 12px system-ui; color: #17324d; }
            .crowd-card strong { display: block; margin-bottom: 7px; font-size: 13px; }
            .crowd-card p { display: -webkit-box; margin: 7px 0 0; overflow: hidden; line-height: 1.35;
              color: #65747c; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
            .crowd-badge { display: inline-block; border-radius: 7px; padding: 4px 9px; font-weight: 800; }
            .crowd-badge.high { color: white; background: #e85d4a; }
            .crowd-badge.moderate { color: #7d5000; background: #ffe2a8; }
            .crowd-badge.low { color: #0c6c60; background: #d9f2ec; }
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
                iconSize: [32, 44],
                iconAnchor: [16, 44],
                popupAnchor: [0, -46]
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
                iconSize: [29, 41],
                iconAnchor: [14, 41],
                popupAnchor: [0, -42]
              });
              L.marker([place.lat, place.lng], { icon })
                .addTo(map)
                .bindPopup(`<b>Nearby: ${'$'}{escapeHtml(place.name)}</b><br>${'$'}{escapeHtml(place.category)}<br>${'$'}{escapeHtml(place.reason)}`);
            });

            if (${showCrowd && crowdHint != null} && routePoints.length > 0) {
              const crowdLevel = ${JSONObject.quote(crowdLevel)};
              const intensity = crowdLevel === 'HIGH' ? .85 :
                ((crowdLevel === 'MEDIUM' || crowdLevel === 'MODERATE') ? .58 : .35);
              const heatPoints = [];
              routePoints.forEach((point, routeIndex) => {
                heatPoints.push([point[0], point[1], Math.min(1, intensity * 1.2)]);
                for (let ringIndex = 0; ringIndex < 16; ringIndex++) {
                  const angle = (Math.PI * 2 * ringIndex / 16) + (routeIndex * .37);
                  const spread = .0018 + ((ringIndex % 4) * .0007);
                  heatPoints.push([
                    point[0] + Math.sin(angle) * spread,
                    point[1] + Math.cos(angle) * spread,
                    intensity * (1 - ((ringIndex % 4) * .1))
                  ]);
                }
              });
              L.heatLayer(heatPoints, {
                radius: 42,
                blur: 24,
                maxZoom: 17,
                minOpacity: .42,
                gradient: { .25: '#2b83ba', .5: '#abdda4', .72: '#fdae61', 1: '#d7191c' }
              }).addTo(map);

              const crowdCard = document.createElement('div');
              crowdCard.className = 'crowd-card';
              crowdCard.innerHTML = '<strong>Seasonal crowd level</strong>' +
                '<span class="crowd-badge $crowdBadgeClass">' + escapeHtml(crowdLevel || 'LOW') + '</span>' +
                '<p>' + escapeHtml(${JSONObject.quote(crowdHint?.note.orEmpty())}) + '</p>';
              document.body.appendChild(crowdCard);
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
