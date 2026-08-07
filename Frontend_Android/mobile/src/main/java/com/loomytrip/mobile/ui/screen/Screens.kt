package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AttractionScreen(onAddToTrip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Wat Chedi Luang", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Temple · Old City, Chiang Mai")
        Text("An ancient Lanna-era temple known for its massive chedi and evening chanting. Open 08:00–17:00.")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Aiko · ★★★★☆", fontWeight = FontWeight.Bold)
                Text("Shoes off inside; wear long trousers.")
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onAddToTrip, modifier = Modifier.fillMaxWidth()) {
            Text("Add to trip")
        }
    }
}
