package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val sampleStops = listOf(
    "Wat Chedi Luang · 09:00",
    "Tha Phae Gate · 11:00",
    "Nimman Road · 14:00",
    "Sunday Market · 18:00"
)

@Composable
private fun Page(content: @Composable ColumnScope.(Modifier) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content(Modifier.fillMaxWidth())
    }
}

@Composable
fun AttractionScreen(onAddToTrip: () -> Unit) = Page { fullWidth ->
    Text("Wat Chedi Luang", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Temple · Old City, Chiang Mai")
    Text("An ancient Lanna-era temple known for its massive chedi and evening chanting. Open 08:00–17:00.")
    Card(modifier = fullWidth) {
        Column(Modifier.padding(16.dp)) {
            Text("Aiko · ★★★★☆", fontWeight = FontWeight.Bold)
            Text("Shoes off inside; wear long trousers.")
        }
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onAddToTrip, modifier = fullWidth) { Text("Add to trip") }
}

@Composable
fun RouteScreen(onViewMap: () -> Unit, onEdit: () -> Unit) = Page { fullWidth ->
    Text("Chiang Mai · 3 days", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("AI-optimized route · estimated 40 minutes saved")
    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sampleStops) { stop ->
            Card(modifier = fullWidth) { Text(stop, Modifier.padding(16.dp)) }
        }
    }
    Row(modifier = fullWidth, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onViewMap, modifier = Modifier.weight(1f)) { Text("View map") }
        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit trip") }
    }
}

@Composable
fun MapScreen(onEdit: () -> Unit) = Page { fullWidth ->
    Text("Route map", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Card(
        modifier = fullWidth.weight(1f),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Interactive map placeholder", fontWeight = FontWeight.Bold)
            Text("Google Maps or Mapbox will be connected after the navigation prototype.")
        }
    }
    Button(onClick = onEdit, modifier = fullWidth) { Text("Edit itinerary") }
}

@Composable
fun EditTripScreen() = Page { fullWidth ->
    Text("Edit itinerary", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Reorder and review the current mock itinerary.")
    sampleStops.forEachIndexed { index, stop ->
        Card(modifier = fullWidth) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}", fontWeight = FontWeight.Bold)
                Text(stop)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Button(onClick = {}, modifier = fullWidth) { Text("Save trip") }
    OutlinedButton(onClick = {}, modifier = fullWidth) { Text("Share") }
}
