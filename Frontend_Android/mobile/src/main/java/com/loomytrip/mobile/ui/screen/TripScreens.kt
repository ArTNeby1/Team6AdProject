package com.loomytrip.mobile.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlin.math.hypot

@Composable
fun RouteScreen(
    activities: List<TripActivity>,
    onViewMap: (Int) -> Unit,
    onEdit: (Int) -> Unit
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(1) }
    val dayActivities = activities.filter { it.day == selectedDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Chiang Mai · 3 days", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "${activities.size} stops in this trip",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        DaySelector(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
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
    }
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
                        "${activity.startTime} · ${activity.durationLabel}",
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
fun EditTripScreen(
    activities: List<TripActivity>,
    initialDay: Int,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (Int, String, String) -> Unit,
    onSave: () -> Unit
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDay) }
    var showAddDialog by remember { mutableStateOf(false) }
    val dayActivities = activities.filter { it.day == selectedDay }

    LaunchedEffect(initialDay) { selectedDay = initialDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Edit itinerary", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "Add, remove, or change the order for this day.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            lineHeight = 20.sp
        )
        DaySelector(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            itemsIndexed(dayActivities, key = { _, item -> item.id }) { index, activity ->
                EditActivityCard(
                    activity = activity,
                    canMoveUp = index > 0,
                    canMoveDown = index < dayActivities.lastIndex,
                    onMoveUp = { onMove(activity.id, -1) },
                    onMoveDown = { onMove(activity.id, 1) },
                    onDelete = { onDelete(activity.id) }
                )
            }
        }
        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add activity to Day $selectedDay")
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save changes", fontWeight = FontWeight.Bold)
        }
    }

    if (showAddDialog) {
        AddActivityDialog(
            day = selectedDay,
            onDismiss = { showAddDialog = false },
            onAdd = { title, time ->
                onAdd(selectedDay, title, time)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EditActivityCard(
    activity: TripActivity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(activity.title, fontWeight = FontWeight.Bold)
                Text(
                    "${activity.startTime} · ${activity.durationLabel}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move ${activity.title} up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move ${activity.title} down")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${activity.title}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddActivityDialog(
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
    onEdit: (Int) -> Unit
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDay) }
    val dayActivities = activities.filter { it.day == selectedDay }
    val context = LocalContext.current

    LaunchedEffect(initialDay) { selectedDay = initialDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DaySelector(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp)
        ) {
            InteractiveRouteMap(activities = dayActivities)
        }
        Text(
            "Day $selectedDay route · ${dayActivities.size} stops",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        if (dayActivities.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { openExternalNavigation(context, dayActivities.first()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
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
            "Drag to move. Pinch to zoom. Tap a stop for details.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun DaySelector(selectedDay: Int, onDaySelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..3).forEach { day ->
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
                            "${selectedActivity.startTime} · ${selectedActivity.category}",
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
