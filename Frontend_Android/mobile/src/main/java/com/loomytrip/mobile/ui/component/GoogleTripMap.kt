package com.loomytrip.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.loomytrip.mobile.data.model.TripActivity

@Composable
fun GoogleTripMap(
    activities: List<TripActivity>,
    modifier: Modifier = Modifier
) {
    val mappedActivities = remember(activities) { activities.filter(TripActivity::hasMapCoordinates) }
    val points = remember(mappedActivities) {
        mappedActivities.map { LatLng(it.latitude, it.longitude) }
    }
    val cameraState = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    var selectedIndex by remember(mappedActivities) {
        mutableIntStateOf(if (mappedActivities.isEmpty()) -1 else 0)
    }
    val selectedActivity = mappedActivities.getOrNull(selectedIndex)
    val routeColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(points, mapLoaded) {
        if (!mapLoaded || points.isEmpty()) return@LaunchedEffect

        val update = if (points.size == 1) {
            CameraUpdateFactory.newLatLngZoom(points.first(), 14f)
        } else {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 120)
        }
        cameraState.animate(update)
    }

    Box(
        modifier = modifier.semantics { contentDescription = "Google trip map" }
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            uiSettings = MapUiSettings(
                compassEnabled = true,
                mapToolbarEnabled = false,
                zoomControlsEnabled = false
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            if (points.size > 1) {
                Polyline(
                    points = points,
                    color = routeColor,
                    width = 10f
                )
            }

            mappedActivities.forEachIndexed { index, activity ->
                val position = LatLng(activity.latitude, activity.longitude)
                Marker(
                    state = remember(activity.id, position) { MarkerState(position) },
                    title = "${index + 1}. ${activity.title}",
                    snippet = "${activity.startTime} - ${activity.category}",
                    onClick = {
                        selectedIndex = index
                        false
                    }
                )
            }
        }

        selectedActivity?.let { activity ->
            StopCard(
                activity = activity,
                stopNumber = selectedIndex + 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun StopCard(
    activity: TripActivity,
    stopNumber: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stopNumber.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(activity.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "${activity.startTime} - ${activity.category}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun TripActivity.hasMapCoordinates(): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        (latitude != 0.0 || longitude != 0.0)
