package com.loomytrip.mobile.ui.screen

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.CrowdHintDto
import com.loomytrip.mobile.data.network.MapConfigDto
import com.loomytrip.mobile.data.network.NearbyRecommendationDto
import com.loomytrip.mobile.data.network.TripRouteDto
import com.loomytrip.mobile.ui.component.LeafletTripMap
import com.loomytrip.mobile.ui.component.hasMapCoordinates
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.hypot

data class MapTripOption(
    val id: Long,
    val name: String
)

@Composable
fun RouteScreen(
    activities: List<TripActivity>,
    tripName: String,
    startDate: String?,
    tripStatus: String?,
    totalDays: Int,
    isUpdatingStartDate: Boolean = false,
    startDateError: String? = null,
    isDeletingTrip: Boolean = false,
    deleteErrorMessage: String? = null,
    travelStyle: String? = null,
    preferTransport: String? = null,
    isSavingPreferences: Boolean = false,
    preferencesErrorMessage: String? = null,
    isGenerating: Boolean = false,
    generateErrorMessage: String? = null,
    generateSummary: String? = null,
    onStartDateChange: (LocalDate) -> Unit = {},
    onViewMap: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onSavePreferences: (String, String) -> Unit = { _, _ -> },
    onSmartReorder: () -> Unit = {},
    onDeleteTrip: () -> Unit = {}
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(1) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    val dayCount = totalDays.coerceAtLeast(1)
    val dayActivities = activities.filter { it.day == selectedDay }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("$tripName • $dayCount ${if (dayCount == 1) "day" else "days"}", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "${activities.size} stops in this trip",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        OutlinedButton(
            onClick = {
                showStartDatePicker(
                    context = context,
                    currentDate = startDate,
                    onDateSelected = onStartDateChange
                )
            },
            enabled = startDate != null && tripStatus != "FINISHED" && !isUpdatingStartDate,
            modifier = Modifier.semantics { contentDescription = "Change start date" }
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(
                if (isUpdatingStartDate) "Updating start date..." else "Start date: ${startDate ?: "TBD"}"
            )
        }
        startDateError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = tripStatus != "FINISHED") { showPreferencesDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Preferences for this trip", fontWeight = FontWeight.Bold)
                    Text(
                        "${travelStyle ?: "Balanced"} · ${preferTransport ?: "Public transport"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                    )
                }
                Text("Edit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        preferencesErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        DaySelector(selectedDay = selectedDay, dayCount = dayCount, onDaySelected = { selectedDay = it })
        if (dayActivities.isEmpty()) {
            EmptyDay(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(dayActivities, key = { _, activity -> activity.id }) { index, activity ->
                    ItineraryActivityCard(index = index, activity = activity)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onViewMap(selectedDay) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "View Day $selectedDay map" }
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Map")
            }
            OutlinedButton(
                onClick = { onEdit(selectedDay) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Edit day")
            }
        }
        deleteErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Button(
            onClick = { showGenerateDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = activities.isNotEmpty() && tripStatus != "FINISHED" && !isGenerating
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(if (isGenerating) "AI is reorganizing…" else "Smart reorder with AI", fontWeight = FontWeight.Bold)
        }
        generateSummary?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        generateErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        TextButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDeletingTrip
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(
                if (isDeletingTrip) "Deleting itinerary…" else "Delete itinerary",
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $tripName?") },
            text = { Text("This removes the complete itinerary from Mobile, Web and the database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteTrip()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPreferencesDialog) {
        TripPreferencesDialog(
            currentTravelStyle = travelStyle,
            currentTransport = preferTransport,
            isSaving = isSavingPreferences,
            onDismiss = { if (!isSavingPreferences) showPreferencesDialog = false },
            onSave = { style, transport ->
                onSavePreferences(style, transport)
                showPreferencesDialog = false
            }
        )
    }

    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text("Let AI reorganize this trip?") },
            text = {
                Text("AI will reorder all saved stops using their locations and available planning information. You can still edit the result afterwards.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGenerateDialog = false
                        onSmartReorder()
                    }
                ) { Text("Reorganize") }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private val tripStyleOptions = listOf("Balanced", "Relaxed", "Cultural", "Food-focused", "Fast-paced")
private val tripTransportOptions = listOf("Walking", "Public transport", "Driving", "Mixed")

@Composable
private fun TripPreferencesDialog(
    currentTravelStyle: String?,
    currentTransport: String?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var travelStyle by remember(currentTravelStyle) {
        mutableStateOf(currentTravelStyle?.takeIf { it in tripStyleOptions } ?: "Balanced")
    }
    var transport by remember(currentTransport) {
        mutableStateOf(currentTransport?.takeIf { it in tripTransportOptions } ?: "Public transport")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trip preferences") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("These choices apply only to this itinerary.")
                Text("Travel style", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(tripStyleOptions) { option ->
                        FilterChip(
                            selected = travelStyle == option,
                            onClick = { travelStyle = option },
                            label = { Text(option) }
                        )
                    }
                }
                Text("Preferred transport", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(tripTransportOptions) { option ->
                        FilterChip(
                            selected = transport == option,
                            onClick = { transport = option },
                            label = { Text(option) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(travelStyle, transport) }, enabled = !isSaving) {
                Text(if (isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

@Composable
private fun ItineraryActivityCard(index: Int, activity: TripActivity) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(activity.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    activity.category,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${activity.startTime} • ${activity.durationLabel}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                Text(
                    activity.address,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            IconButton(
                onClick = { openExternalNavigation(context, activity) }
            ) {
                Icon(
                    Icons.Default.Directions,
                    contentDescription = "Navigate to ${activity.title}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun AddActivityDialog(
    day: Int,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    val validTime = time.isBlank() || Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(time)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add activity to Day $day") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Activity name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it.take(5) },
                    label = { Text("Preferred start time (optional)") },
                    supportingText = {
                        Text(
                            if (time.isBlank()) {
                                "We will place it after the last stop"
                            } else if (validTime) {
                                "It will move later if this time overlaps"
                            } else {
                                "Use a time such as 09:30"
                            }
                        )
                    },
                    isError = !validTime,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, time) },
                enabled = title.isNotBlank() && validTime
            ) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MapScreen(
    activities: List<TripActivity>,
    initialDay: Int,
    totalDays: Int,
    tripOptions: List<MapTripOption> = emptyList(),
    selectedTripId: Long? = null,
    mapConfig: MapConfigDto? = null,
    routeSummary: TripRouteDto? = null,
    nearbyPlaces: List<NearbyRecommendationDto> = emptyList(),
    crowdHint: CrowdHintDto? = null,
    showCrowd: Boolean = false,
    isMapDataLoading: Boolean = false,
    mapDataError: String? = null,
    onTripSelected: (Long) -> Unit = {},
    onDaySelected: (Int) -> Unit = {},
    onMapClick: (Double, Double) -> Unit = { _, _ -> },
    onToggleCrowd: () -> Unit = {},
    onRetryRoute: () -> Unit = {},
    onEdit: (Int) -> Unit
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDay) }
    var displayedTripId by remember { mutableStateOf(selectedTripId) }
    val dayCount = totalDays.coerceAtLeast(selectedDay)
    val dayActivities = activities.filter { it.day == selectedDay }
    val mappedStopCount = dayActivities.count(TripActivity::hasMapCoordinates)
    val context = LocalContext.current

    LaunchedEffect(initialDay) {
        selectedDay = initialDay
        onDaySelected(initialDay)
    }
    LaunchedEffect(selectedTripId) {
        if (displayedTripId != selectedTripId) {
            displayedTripId = selectedTripId
            selectedDay = 1
            onDaySelected(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tripOptions.isNotEmpty()) {
            TripSelector(
                options = tripOptions,
                selectedTripId = selectedTripId,
                onTripSelected = onTripSelected
            )
        }
        DaySelector(
            selectedDay = selectedDay,
            dayCount = dayCount,
            onDaySelected = {
                selectedDay = it
                onDaySelected(it)
            }
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp)
        ) {
            EnhancedTripMapComponent(
                activities = dayActivities,
                mapConfig = mapConfig,
                nearbyPlaces = nearbyPlaces,
                crowdHint = crowdHint,
                showCrowd = showCrowd,
                onMapClick = onMapClick
            )
        }
        RouteSummaryCard(
            selectedDay = selectedDay,
            fallbackStopCount = dayActivities.size,
            routeSummary = routeSummary,
            isLoading = isMapDataLoading
        )
        mapDataError?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 2
                )
                TextButton(onClick = onRetryRoute) { Text("Retry") }
            }
        }
        if (mappedStopCount < dayActivities.size) {
            Text(
                "${dayActivities.size - mappedStopCount} stop(s) are waiting for coordinates.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showCrowd,
                onClick = onToggleCrowd,
                label = { Text("Crowd level") }
            )
            if (dayActivities.any(TripActivity::hasMapCoordinates)) {
                TextButton(
                    onClick = {
                        dayActivities.firstOrNull(TripActivity::hasMapCoordinates)?.let {
                            onMapClick(it.latitude, it.longitude)
                        }
                    }
                ) {
                    Text("Find nearby")
                }
            }
            if (isMapDataLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        if (showCrowd && crowdHint != null) {
            Text(
                "${crowdHint.level.lowercase().replaceFirstChar(Char::uppercase)} crowd • ${crowdHint.note}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (nearbyPlaces.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(nearbyPlaces) { _, place ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(
                            modifier = Modifier
                                .width(170.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(place.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            Text(
                                listOfNotNull(
                                    place.category,
                                    place.distanceKm?.let { "${"%.1f".format(it)} km" }
                                ).joinToString(" • "),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        if (dayActivities.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        routeSummary?.googleMapsUrl?.let { openGoogleMapsRoute(context, it) }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !routeSummary?.googleMapsUrl.isNullOrBlank() && !isMapDataLoading
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open Google Maps")
                }
                OutlinedButton(
                    onClick = { onEdit(selectedDay) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit day")
                }
            }
        }
        Text(
            "Leaflet uses the same map config as Web. Tap the map for nearby places; use Google Maps for navigation.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun RouteSummaryCard(
    selectedDay: Int,
    fallbackStopCount: Int,
    routeSummary: TripRouteDto?,
    isLoading: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RouteMetric("Day", selectedDay.toString(), Modifier.weight(1f))
            RouteMetric("Stops", (routeSummary?.stopCount ?: fallbackStopCount).toString(), Modifier.weight(1f))
            RouteMetric(
                "Distance",
                if (isLoading) "…" else routeSummary?.totalDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "—",
                Modifier.weight(1f)
            )
            RouteMetric(
                "Travel",
                if (isLoading) "…" else routeSummary?.totalDurationMinutes?.let { "$it min" } ?: "—",
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RouteMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        Text(
            label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun EnhancedTripMapComponent(
    activities: List<TripActivity>,
    mapConfig: MapConfigDto?,
    nearbyPlaces: List<NearbyRecommendationDto>,
    crowdHint: CrowdHintDto?,
    showCrowd: Boolean,
    onMapClick: (Double, Double) -> Unit
) {
    when {
        activities.isEmpty() -> EmptyDay(modifier = Modifier.fillMaxSize())
        mapConfig != null && activities.any(TripActivity::hasMapCoordinates) -> {
            LeafletTripMap(
                activities = activities,
                mapConfig = mapConfig,
                nearbyPlaces = nearbyPlaces,
                crowdHint = crowdHint,
                showCrowd = showCrowd,
                onMapClick = onMapClick,
                modifier = Modifier.fillMaxSize()
            )
        }
        else -> InteractiveRouteMap(activities)
    }
}

private fun showStartDatePicker(
    context: Context,
    currentDate: String?,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val parsedDate = runCatching { LocalDate.parse(currentDate) }.getOrNull()
    val initialDate = parsedDate?.takeUnless { it.isBefore(today) } ?: today

    DatePickerDialog(
        context,
        { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth
    ).apply {
        datePicker.minDate = today
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        show()
    }
}

@Composable
private fun TripSelector(
    options: List<MapTripOption>,
    selectedTripId: Long?,
    onTripSelected: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Choose itinerary",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.id }) { option ->
                FilterChip(
                    selected = option.id == selectedTripId,
                    onClick = { onTripSelected(option.id) },
                    label = {
                        Text(
                            option.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun DaySelector(selectedDay: Int, dayCount: Int, onDaySelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..dayCount).forEach { day ->
            FilterChip(
                selected = selectedDay == day,
                onClick = { onDaySelected(day) },
                label = { Text("Day $day") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptyDay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(8.dp))
        Text("No activities on this day yet")
    }
}

@Composable
private fun InteractiveRouteMap(activities: List<TripActivity>) {
    var zoom by remember(activities) { mutableStateOf(1f) }
    var pan by remember(activities) { mutableStateOf(Offset.Zero) }
    var selectedIndex by remember(activities) { mutableIntStateOf(if (activities.isEmpty()) -1 else 0) }
    val selectedActivity = activities.getOrNull(selectedIndex)
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2EFE7))
            .semantics { contentDescription = "Route map preview" }
    ) {
        // Draw the route locally because the map API is not connected yet.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activities, zoom, pan) {
                    detectTapGestures { tap ->
                        val points = routeMapPoints(activities, size.width.toFloat(), size.height.toFloat(), zoom, pan)
                        val nearest = points.withIndex().minByOrNull { (_, point) ->
                            hypot((tap.x - point.x).toDouble(), (tap.y - point.y).toDouble())
                        }
                        if (nearest != null && (tap - nearest.value).getDistance() <= 48.dp.toPx()) {
                            selectedIndex = nearest.index
                        }
                    }
                }
                .pointerInput(activities) {
                    detectTransformGestures { _, movement, scaleChange, _ ->
                        zoom = (zoom * scaleChange).coerceIn(0.8f, 3.5f)
                        pan += movement
                    }
                }
        ) {
            val points = routeMapPoints(activities, size.width, size.height, zoom, pan)
            val roadColor = Color(0xFFD6D0C3)
            val minorRoadColor = Color(0xFFE4DED2)

            repeat(7) { index ->
                val x = size.width * (index + 1) / 8f + pan.x * 0.22f
                drawLine(minorRoadColor, Offset(x, 0f), Offset(x - size.width * 0.16f, size.height), 3.dp.toPx())
            }
            repeat(6) { index ->
                val y = size.height * (index + 1) / 7f + pan.y * 0.18f
                drawLine(roadColor, Offset(0f, y), Offset(size.width, y + size.height * 0.08f), 5.dp.toPx())
            }

            val river = Path().apply {
                moveTo(size.width * 0.12f + pan.x * 0.08f, 0f)
                cubicTo(
                    size.width * 0.34f,
                    size.height * 0.24f,
                    size.width * 0.02f,
                    size.height * 0.56f,
                    size.width * 0.23f + pan.x * 0.08f,
                    size.height
                )
            }
            drawPath(river, Color(0xFFB9DCE5), style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round))

            if (points.size > 1) {
                val route = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(route, Color.White.copy(alpha = 0.9f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                drawPath(route, primary, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            }

            val numberPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 12.sp.toPx()
                isFakeBoldText = true
            }
            points.forEachIndexed { index, point ->
                val isSelected = index == selectedIndex
                if (isSelected) drawCircle(primary.copy(alpha = 0.2f), 25.dp.toPx(), point)
                drawCircle(Color.White, 18.dp.toPx(), point)
                drawCircle(primary, 14.dp.toPx(), point)
                drawContext.canvas.nativeCanvas.drawText(
                    "${index + 1}",
                    point.x,
                    point.y + 4.dp.toPx(),
                    numberPaint
                )
            }
        }

        if (selectedActivity != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
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
                            .background(primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${selectedIndex + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(selectedActivity.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${selectedActivity.startTime} • ${selectedActivity.category}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private fun routeMapPoints(
    activities: List<TripActivity>,
    width: Float,
    height: Float,
    zoom: Float,
    pan: Offset
): List<Offset> {
    if (activities.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val minLatitude = activities.minOf { it.latitude }
    val maxLatitude = activities.maxOf { it.latitude }
    val minLongitude = activities.minOf { it.longitude }
    val maxLongitude = activities.maxOf { it.longitude }
    val latitudeSpan = (maxLatitude - minLatitude).coerceAtLeast(0.008)
    val longitudeSpan = (maxLongitude - minLongitude).coerceAtLeast(0.008)
    val padding = (width.coerceAtMost(height) * 0.16f).coerceAtLeast(44f)
    val center = Offset(width / 2f, height / 2f)

    return activities.map { activity ->
        val base = Offset(
            x = padding + (((activity.longitude - minLongitude) / longitudeSpan).toFloat() * (width - padding * 2f)),
            y = padding + (((maxLatitude - activity.latitude) / latitudeSpan).toFloat() * (height - padding * 2f))
        )
        center + (base - center) * zoom + pan
    }
}

private fun openExternalNavigation(context: Context, activity: TripActivity) {
    val googleNavigation = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${activity.latitude},${activity.longitude}&mode=w")
    ).apply { setPackage("com.google.android.apps.maps") }

    try {
        context.startActivity(googleNavigation)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "geo:${activity.latitude},${activity.longitude}?q=${activity.latitude},${activity.longitude}(" +
                    Uri.encode(activity.title) + ")"
            )
        )
        context.startActivity(fallback)
    }
}

private fun openGoogleMapsRoute(context: Context, url: String) {
    val routeUri = Uri.parse(url)
    val googleMapsIntent = Intent(Intent.ACTION_VIEW, routeUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(googleMapsIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, routeUri))
    }
}
