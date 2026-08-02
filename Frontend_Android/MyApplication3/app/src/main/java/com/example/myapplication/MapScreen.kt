package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb

@Composable
fun MapScreen(
    activities: List<TripActivity>,
    tripTitle: String = "行程地图",
    initialDay: Int = 1,
    onBack: () -> Unit = {}, 
    onBottomTabClick: (String) -> Unit = {}
) {
    // Calculate valid days from activities
    val validDays = remember(activities) {
        activities.map { it.day }.distinct().sorted()
    }
    
    var selectedDay by remember { 
        mutableIntStateOf(if (initialDay in validDays) initialDay else validDays.firstOrNull() ?: 1) 
    }
    val dayActivities = activities.filter { it.day == selectedDay }

    Scaffold(
        bottomBar = { FunctionalBottomNavBar(currentRoute = "map", onTabClick = onBottomTabClick) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Mint)
        ) {
            // Mock Map with dynamic markers
            MockMap(dayActivities)

            // Top Bar Area: Back Button, Trip Title and Day Selection
            Column(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title Row with Back Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = JadeDeep,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = tripTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // Day Selection Pill - Only show valid days
                if (validDays.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            validDays.forEach { day ->
                                val isSelected = selectedDay == day
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(if (isSelected) Ink else Color.Transparent)
                                        .clickable { selectedDay = day }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Day $day",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Muted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Statistics Panel
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp)),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp, 4.dp)
                            .clip(CircleShape)
                            .background(Line)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Day $selectedDay 路线", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("${dayActivities.size} 站", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JadeDeep)
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RouteStat(Modifier.weight(1f), "12.3", "km 全程")
                        RouteStat(Modifier.weight(1f), "~3.2", "h 通勤")
                        RouteStat(Modifier.weight(1f), "35", "min 车程")
                    }
                }
            }
        }
    }
}

@Composable
fun MockMap(dayActivities: List<TripActivity>) {
    val density = LocalDensity.current
    val textPaint = remember(density) {
        Paint().apply {
            color = Ink.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val numberPaint = remember(density) {
        Paint().apply {
            color = Color.White.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val travelTimePaint = remember(density) {
        Paint().apply {
            color = JadeDeep.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val labelBgPaint = remember {
        Paint().apply {
            color = Color.White.copy(alpha = 0.8f).toArgb()
            style = Paint.Style.FILL
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color(0xFFF3EDE0), size = size)
        
        // Define marker positions based on index (Mock positions)
        val positions = dayActivities.mapIndexed { index, _ ->
            Offset(
                x = size.width * (0.2f + (index % 2) * 0.4f),
                y = size.height * (0.25f + index * 0.15f)
            )
        }

        // Draw Path and Travel Times
        if (positions.size > 1) {
            val path = Path().apply {
                moveTo(positions[0].x, positions[0].y)
                for (i in 1 until positions.size) {
                    val prev = positions[i-1]
                    val curr = positions[i]
                    val controlX = (prev.x + curr.x) / 2 + (if (i % 2 == 0) 50f else -50f)
                    val controlY = (prev.y + curr.y) / 2
                    quadraticTo(controlX, controlY, curr.x, curr.y)
                }
            }
            drawPath(
                path = path,
                color = Jade,
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw travel time labels at midpoints
            for (i in 1 until positions.size) {
                val prev = positions[i-1]
                val curr = positions[i]
                
                // Approximate midpoint of the quadratic curve
                val midX = (prev.x + curr.x) / 2 + (if (i % 2 == 0) 25f else -25f)
                val midY = (prev.y + curr.y) / 2
                
                val label = "🚕 15 min"
                val textWidth = travelTimePaint.measureText(label)
                val textHeight = travelTimePaint.textSize
                
                // Draw small background for readability
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    midX - textWidth/2 - 4.dp.toPx(),
                    midY - textHeight/2 - 2.dp.toPx(),
                    midX + textWidth/2 + 4.dp.toPx(),
                    midY + textHeight/2 + 4.dp.toPx(),
                    4.dp.toPx(),
                    4.dp.toPx(),
                    labelBgPaint
                )
                
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    midX,
                    midY + textHeight/3,
                    travelTimePaint
                )
            }
        }

        // Draw Markers and Labels
        positions.forEachIndexed { index, offset ->
            val isFirst = index == 0
            val markerColor = if (isFirst) Amber else Jade
            val markerRadius = 13.dp.toPx()
            
            // Marker circle
            drawCircle(markerColor, radius = markerRadius, center = offset)
            
            // Marker number
            drawContext.canvas.nativeCanvas.drawText(
                (index + 1).toString(),
                offset.x,
                offset.y + markerRadius / 3,
                numberPaint
            )
            
            // Location name label
            drawContext.canvas.nativeCanvas.drawText(
                dayActivities[index].name,
                offset.x + markerRadius + 8.dp.toPx(),
                offset.y + 4.dp.toPx(),
                textPaint
            )
        }
    }
}


@Composable
fun RouteStat(modifier: Modifier, value: String, unit: String) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Mint)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(text = unit, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MapPreview() {
    val activities = listOf(
        TripActivity("1", "契迪龙寺", 1),
        TripActivity("2", "塔佩门", 1),
        TripActivity("3", "尼曼路", 1)
    )
    MyApplicationTheme {
        MapScreen(
            activities = activities,
            tripTitle = "清迈 3 日行程"
        )
    }
}