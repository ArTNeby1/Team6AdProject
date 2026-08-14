package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.loomytrip.mobile.data.model.TripActivity
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface EditorRow {
    val key: String

    data class DayHeader(val day: Int) : EditorRow {
        override val key = "header:$day"
    }

    data class Activity(val item: TripActivity, val position: Int) : EditorRow {
        override val key = "activity:${item.id}"
    }

    data class AddStop(val day: Int) : EditorRow {
        override val key = "add:$day"
    }
}

private data class DropTarget(val day: Int, val position: Int)

@Composable
fun EditTripScreen(
    activities: List<TripActivity>,
    initialDay: Int,
    totalDays: Int,
    onReorder: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (TripActivity, Int) -> Unit,
    onAdd: (Int, String, String) -> Unit,
    onUpdateActivity: (String, String) -> Unit,
    onAddDay: () -> Unit,
    onSave: () -> Unit,
    onSaveAndSmartReorder: () -> Unit = {},
    isSaving: Boolean = false,
    isGenerating: Boolean = false,
    errorMessage: String? = null
) {
    var addToDay by remember { mutableStateOf<Int?>(null) }
    var editActivity by remember { mutableStateOf<TripActivity?>(null) }
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDay) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    var dropTarget by remember { mutableStateOf<DropTarget?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dayCount = totalDays.coerceAtLeast(1)
    val rows = buildList {
        (1..dayCount).forEach { day ->
            add(EditorRow.DayHeader(day))
            activities.filter { it.day == day }.forEachIndexed { index, activity ->
                add(EditorRow.Activity(activity, index))
            }
            add(EditorRow.AddStop(day))
        }
    }

    LaunchedEffect(initialDay, dayCount) {
        selectedDay = initialDay.coerceIn(1, dayCount)
        val target = rows.indexOfFirst { it is EditorRow.DayHeader && it.day == selectedDay }
        if (target >= 0) listState.scrollToItem(target)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Shape your trip", style = MaterialTheme.typography.headlineMedium)
            DaySelector(
                selectedDay = selectedDay,
                dayCount = dayCount,
                onDaySelected = { day ->
                    selectedDay = day
                    val target = rows.indexOfFirst { it is EditorRow.DayHeader && it.day == day }
                    if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                }
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DragHandle, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Hold and drag to reorder, or use the menu for exact moves",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is EditorRow.DayHeader -> {
                            val dayItems = activities.filter { it.day == row.day }
                            EditorDayHeader(
                                day = row.day,
                                stopCount = dayItems.size,
                                durationMinutes = dayItems.sumOf { it.durationMinutes },
                                isDropTarget = dropTarget?.day == row.day && draggedId != null
                            )
                        }

                        is EditorRow.Activity -> {
                            val activity = row.item
                            val isDragging = draggedId == activity.id
                            DraggableActivityCard(
                                activity = activity,
                                totalDays = dayCount,
                                isDragging = isDragging,
                                isDropTarget = dropTarget?.day == activity.day &&
                                    dropTarget?.position == row.position && !isDragging,
                                dragDistance = if (isDragging) draggedDistance else 0f,
                                onEdit = { editActivity = activity },
                                onMoveToDay = { day ->
                                    val oldDay = activity.day
                                    val oldPosition = activities.filter { it.day == oldDay }
                                        .indexOfFirst { it.id == activity.id }
                                    onReorder(activity.id, day, activities.count { it.day == day })
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Moved ${activity.title} to Day $day",
                                            actionLabel = "Undo",
                                            withDismissAction = true
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onReorder(activity.id, oldDay, oldPosition)
                                        }
                                    }
                                },
                                onDelete = {
                                    val oldPosition = activities.filter { it.day == activity.day }
                                        .indexOfFirst { it.id == activity.id }
                                    onDelete(activity.id)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Removed ${activity.title}",
                                            actionLabel = "Undo",
                                            withDismissAction = true
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestore(activity, oldPosition)
                                        }
                                    }
                                },
                                modifier = Modifier.pointerInput(activity.id, dayCount) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = activity.id
                                            draggedDistance = 0f
                                            dropTarget = DropTarget(activity.day, row.position)
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            draggedDistance = 0f
                                            dropTarget = null
                                        },
                                        onDragEnd = {
                                            val target = dropTarget
                                            val oldDay = activity.day
                                            val oldPosition = activities.filter { it.day == oldDay }
                                                .indexOfFirst { it.id == activity.id }
                                            draggedId = null
                                            draggedDistance = 0f
                                            dropTarget = null
                                            if (target != null &&
                                                (target.day != oldDay || target.position != oldPosition)
                                            ) {
                                                onReorder(activity.id, target.day, target.position)
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Moved ${activity.title} to Day ${target.day}",
                                                        actionLabel = "Undo",
                                                        withDismissAction = true
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        onReorder(activity.id, oldDay, oldPosition)
                                                    }
                                                }
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            draggedDistance += amount.y
                                            val draggedInfo = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == "activity:${activity.id}" }
                                            if (draggedInfo != null) {
                                                val draggedCenter = draggedInfo.offset +
                                                    draggedInfo.size / 2 + draggedDistance
                                                val closest = listState.layoutInfo.visibleItemsInfo.minByOrNull { info ->
                                                    abs((info.offset + info.size / 2f) - draggedCenter)
                                                }
                                                val targetRow = closest?.index?.let { rows.getOrNull(it) }
                                                dropTarget = when (targetRow) {
                                                    is EditorRow.DayHeader -> DropTarget(targetRow.day, 0)
                                                    is EditorRow.Activity -> DropTarget(
                                                        targetRow.item.day,
                                                        targetRow.position
                                                    )
                                                    is EditorRow.AddStop -> DropTarget(
                                                        targetRow.day,
                                                        activities.count { it.day == targetRow.day }
                                                    )
                                                    null -> dropTarget
                                                }
                                            }
                                        }
                                    )
                                }
                            )
                        }

                        is EditorRow.AddStop -> {
                            OutlinedButton(
                                onClick = { addToDay = row.day },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add a stop to Day ${row.day}")
                            }
                        }
                    }
                }

                item(key = "add-day") {
                    TextButton(onClick = onAddDay, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add another day")
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving…" else "Save itinerary", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSaveAndSmartReorder,
                modifier = Modifier.fillMaxWidth(),
                enabled = activities.isNotEmpty() && !isSaving && !isGenerating
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (isGenerating) "AI is reorganizing…" else "Save & smart reorder",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 66.dp)
        )
    }

    addToDay?.let { day ->
        AddActivityDialog(
            day = day,
            onDismiss = { addToDay = null },
            onAdd = { title, time ->
                onAdd(day, title, time)
                addToDay = null
            }
        )
    }

    editActivity?.let { activity ->
        EditStartTimeDialog(
            activity = activity,
            onDismiss = { editActivity = null },
            onSave = { startTime ->
                onUpdateActivity(activity.id, startTime)
                editActivity = null
            }
        )
    }
}

@Composable
private fun EditorDayHeader(
    day: Int,
    stopCount: Int,
    durationMinutes: Int,
    isDropTarget: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isDropTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(14.dp),
        border = if (isDropTarget) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Day $day", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (stopCount == 0) "A fresh day" else "$stopCount stops  •  ${durationSummary(durationMinutes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            if (isDropTarget) {
                Text(
                    "Drop here",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DraggableActivityCard(
    activity: TripActivity,
    totalDays: Int,
    isDragging: Boolean,
    isDropTarget: Boolean,
    dragDistance: Float,
    onEdit: () -> Unit,
    onMoveToDay: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationY = dragDistance
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                alpha = if (isDragging) 0.94f else 1f
                shadowElevation = if (isDragging) 18.dp.toPx() else 0f
            }
            .semantics { contentDescription = "Hold and drag ${activity.title} to reorder" },
        colors = CardDefaults.cardColors(
            containerColor = if (isDropTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag ${activity.title}",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(horizontal = 5.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(activity.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${activity.startTime}  •  ${activity.durationLabel}  •  ${activity.category}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${activity.title}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit start time") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    if (totalDays > 1) {
                        HorizontalDivider()
                        Text(
                            "Move to",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        (1..totalDays).filter { it != activity.day }.forEach { day ->
                            DropdownMenuItem(
                                text = { Text("Day $day") },
                                onClick = {
                                    menuExpanded = false
                                    onMoveToDay(day)
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditStartTimeDialog(
    activity: TripActivity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var startTime by remember(activity.id) { mutableStateOf(activity.startTime) }
    val validTime = remember(startTime) {
        Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(startTime)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(activity.title) },
        text = {
            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it.take(5) },
                label = { Text("Start time") },
                supportingText = { Text(if (validTime) "24-hour format" else "Use a time such as 09:30") },
                isError = !validTime,
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(startTime) }, enabled = validTime) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

internal fun durationSummary(minutes: Int): String = when {
    minutes <= 0 -> "0 min"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}
