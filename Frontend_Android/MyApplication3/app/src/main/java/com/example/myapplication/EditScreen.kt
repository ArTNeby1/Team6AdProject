package com.example.myapplication

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun EditScreen(
    activities: MutableList<TripActivity>,
    onBack: () -> Unit = {}, 
    onSave: () -> Unit = {}
) {
    val localActivities = remember {
        mutableStateListOf<TripActivity>().apply { addAll(activities) }
    }
    
    // Track manually added days
    var manualDayCount by remember(activities) { 
        mutableIntStateOf(activities.map { it.day }.maxOrNull() ?: 1) 
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Drag and Drop State
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Mint)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = JadeDeep,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "编辑行程",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    activities.clear()
                    activities.addAll(localActivities)
                    onSave()
                }) {
                    Text(
                        text = "保存",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = JadeDeep
                    )
                }
            }
        },
        containerColor = Surface
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { item -> 
                                    offset.y.toInt() in item.offset..(item.offset + item.size) 
                                }
                                ?.let { item ->
                                    if (item.key.toString().startsWith("activity_")) {
                                        draggingItemIndex = localActivities.indexOfFirst { "activity_${it.id}" == item.key }
                                    }
                                }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                            
                            val currentIndex = draggingItemIndex ?: return@detectDragGesturesAfterLongPress
                            
                            // Simple swapping logic based on threshold
                            val threshold = 50f 
                            if (dragOffset > threshold && currentIndex < localActivities.size - 1) {
                                val item = localActivities.removeAt(currentIndex)
                                val newItem = item.copy(day = localActivities[currentIndex].day)
                                localActivities.add(currentIndex + 1, newItem)
                                draggingItemIndex = currentIndex + 1
                                dragOffset = 0f
                            } else if (dragOffset < -threshold && currentIndex > 0) {
                                val item = localActivities.removeAt(currentIndex)
                                val newItem = item.copy(day = localActivities[currentIndex - 1].day)
                                localActivities.add(currentIndex - 1, newItem)
                                draggingItemIndex = currentIndex - 1
                                dragOffset = 0f
                            }
                            
                            // Auto-scroll logic
                            val layoutInfo = listState.layoutInfo
                            val viewportHeight = layoutInfo.viewportEndOffset
                            val touchY = change.position.y
                            
                            if (touchY > viewportHeight - 100f) {
                                if (autoScrollJob?.isActive != true) {
                                    autoScrollJob = scope.launch {
                                        listState.scrollBy(10f)
                                    }
                                }
                            } else if (touchY < 100f) {
                                if (autoScrollJob?.isActive != true) {
                                    autoScrollJob = scope.launch {
                                        listState.scrollBy(-10f)
                                    }
                                }
                            } else {
                                autoScrollJob?.cancel()
                            }
                        },
                        onDragEnd = {
                            draggingItemIndex = null
                            dragOffset = 0f
                            autoScrollJob?.cancel()
                        },
                        onDragCancel = {
                            draggingItemIndex = null
                            dragOffset = 0f
                            autoScrollJob?.cancel()
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val totalDays = manualDayCount
            for (day in 1..totalDays) {
                item(key = "header_$day") {
                    Text(
                        text = "Day $day",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                
                val dayActivities = localActivities.filter { it.day == day }
                
                itemsIndexed(
                    items = dayActivities,
                    key = { _, activity -> "activity_${activity.id}" }
                ) { _, activity ->
                    val isDragging = draggingItemIndex != null && localActivities[draggingItemIndex!!].id == activity.id
                    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f)
                    val elevation by animateFloatAsState(if (isDragging) 8f else 0f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                scaleX = scale
                                scaleY = scale
                            }
                            .shadow(elevation.dp, RoundedCornerShape(14.dp))
                    ) {
                        EditItem(
                            name = activity.name,
                            onDelete = { localActivities.remove(activity) }
                        )
                    }
                }
                
                item(key = "add_$day") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                val lastIndex = localActivities.indexOfLast { it.day == day }
                                val newIndex = if (lastIndex == -1) {
                                    // If no activities on this day, insert after the last activity of the previous day
                                    val lastPrevDayIdx = localActivities.indexOfLast { it.day < day }
                                    if (lastPrevDayIdx == -1) 0 else lastPrevDayIdx + 1
                                } else lastIndex + 1
                                localActivities.add(newIndex, TripActivity(java.util.UUID.randomUUID().toString(), "新景点", day))
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Jade),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+ 为 Day $day 添加景点", color = JadeDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        // Show the add day button only for the last day
                        if (day == totalDays) {
                            IconButton(
                                onClick = { manualDayCount++ },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Mint)
                                    .border(1.dp, Jade, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Day",
                                    tint = JadeDeep,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun EditItem(
    name: String, 
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, LineSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Long press to drag",
            tint = Muted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Coral,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditPreview() {
    val activities = remember {
        mutableStateListOf(
            TripActivity("1", "契迪龙寺", 1),
            TripActivity("2", "塔佩门", 1)
        )
    }
    MyApplicationTheme {
        EditScreen(activities = activities)
    }
}