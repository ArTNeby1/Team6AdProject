package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@Composable
fun RouteScreen(
    activities: List<TripActivity>,
    tripTitle: String = "行程详情",
    isEditable: Boolean = true,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onMapView: (Int) -> Unit = {},
    onBottomTabClick: (String) -> Unit = {}
) {
    var selectedDay by remember { mutableIntStateOf(1) }

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
                    text = tripTitle,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Mint)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = JadeDeep,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        bottomBar = { FunctionalBottomNavBar(currentRoute = "route", onTabClick = onBottomTabClick) },
        containerColor = Surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            ) {
                item {
                    // AI Badge
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFCEFD6))
                            .border(1.dp, Color(0xFFF3DDAF), RoundedCornerShape(12.dp))
                            .padding(12.dp, 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI 已优化路线 · 相比原顺序省 40 分钟车程",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8A5A10)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    // Day Tabs - Only show days that have activities
                    val validDays = activities.map { it.day }.distinct().sorted()
                    if (validDays.isNotEmpty()) {
                        // Ensure selectedDay is valid
                        if (selectedDay !in validDays) {
                            selectedDay = validDays.first()
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            validDays.forEach { day ->
                                DayTab(
                                    text = "Day $day",
                                    isSelected = selectedDay == day,
                                    onClick = { selectedDay = day }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val dayActivities = activities.filter { it.day == selectedDay }
                
                itemsIndexed(dayActivities) { index, activity ->
                    val isLast = index == dayActivities.size - 1
                    TimelineNode(
                        num = index + 1,
                        time = activity.time,
                        place = activity.name,
                        duration = "游览约 ${activity.duration}",
                        isCurrent = index == 0,
                        showLine = !isLast
                    )
                    if (!isLast) {
                        TravelInfo("🚕 打车 15 分钟")
                    }
                }
            }
            
            // Buttons just above BottomBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onMapView(selectedDay) },
                    modifier = if (isEditable) Modifier.weight(1f).height(44.dp) else Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Jade),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("在地图查看", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                if (isEditable) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("编辑行程", color = JadeDeep, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DayTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Ink else Mint,
        shape = RoundedCornerShape(99.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else Muted,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun TimelineNode(num: Int, time: String, place: String, duration: String, isCurrent: Boolean, showLine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) Amber else Jade),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = num.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp) // Approximate height to fit card + travel info
                        .background(Jade)
                )
            }
        }
        Spacer(modifier = Modifier.width(11.dp))
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(13.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, LineSoft)
        ) {
            Column(modifier = Modifier.padding(11.dp, 9.dp)) {
                Text(
                    text = time,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JadeDeep
                )
                Text(
                    text = place,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = duration,
                    fontSize = 10.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TravelInfo(text: String) {
    Row(
        modifier = Modifier
            .padding(start = 49.dp, top = 3.dp, bottom = 5.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            color = Muted
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoutePreview() {
    MyApplicationTheme {
        RouteScreen(
            activities = listOf(
                TripActivity("1", "契迪龙寺", 1, "09:00", "1.5h"),
                TripActivity("2", "塔佩门", 1, "11:00", "1h")
            ),
            tripTitle = "清迈 3 日行程"
        )
    }
}