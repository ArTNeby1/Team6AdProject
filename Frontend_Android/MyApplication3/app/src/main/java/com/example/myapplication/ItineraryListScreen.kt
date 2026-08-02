package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@Composable
fun ItineraryListScreen(
    trips: List<TripData>,
    onTripClick: (String) -> Unit = {}, 
    onBottomTabClick: (String) -> Unit = {}
) {
    Scaffold(
        bottomBar = { FunctionalBottomNavBar(currentRoute = "route", onTabClick = onBottomTabClick) },
        containerColor = Paper
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "我的行程",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(20.dp, 24.dp, 20.dp, 12.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: ACTIVE
                val activeTrips = trips.filter { it.status == TripStatus.ACTIVE }
                if (activeTrips.isNotEmpty()) {
                    item {
                        Text(
                            text = "进行中",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = JadeDeep,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(activeTrips) { trip ->
                        ItineraryListItem(
                            title = trip.title,
                            date = trip.date,
                            status = "进行中", // Simplified for now
                            progress = 0.6f,
                            onClick = { onTripClick(trip.id) }
                        )
                    }
                }

                // Section: NOT_STARTED
                val notStartedTrips = trips.filter { it.status == TripStatus.NOT_STARTED }
                if (notStartedTrips.isNotEmpty()) {
                    item {
                        Text(
                            text = "未开始",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink, // Using Ink for high contrast
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(notStartedTrips) { trip ->
                        HistoryTripItem(
                            trip = TripSummary(trip.title, trip.date, trip.description, Jade), 
                            onClick = { onTripClick(trip.id) }
                        )
                    }
                }

                // Section: FINISHED
                val finishedTrips = trips.filter { it.status == TripStatus.FINISHED }
                if (finishedTrips.isNotEmpty()) {
                    item {
                        Text(
                            text = "已结束",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Muted,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(finishedTrips) { trip ->
                        HistoryTripItem(
                            trip = TripSummary(trip.title, trip.date, trip.description, Muted), 
                            onClick = { onTripClick(trip.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryListItem(title: String, date: String, status: String, progress: Float, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(colors = listOf(Jade, JadeDeep))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "CHIANG\nMAI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(text = date, fontSize = 11.sp, color = Muted)
                Text(text = status, fontSize = 12.sp, color = JadeDeep, modifier = Modifier.padding(top = 4.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp)),
                    color = Amber,
                    trackColor = Mint
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTripItem(trip: TripSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(trip.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = trip.title.take(1), color = trip.color, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = trip.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(text = trip.desc, fontSize = 11.sp, color = Muted)
            }
            Text(text = trip.date, fontSize = 11.sp, color = Muted)
        }
    }
}

data class TripSummary(val title: String, val date: String, val desc: String, val color: Color)

@Preview(showBackground = true)
@Composable
fun ItineraryListPreview() {
    MyApplicationTheme {
        ItineraryListScreen(trips = emptyList())
    }
}