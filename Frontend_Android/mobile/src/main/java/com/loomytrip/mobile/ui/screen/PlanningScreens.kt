package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomytrip.mobile.data.model.ExtractedPlace

@Composable
fun ImportGuideScreen(
    onExtract: (String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var guide by remember {
        mutableStateOf("Singapore for 2 days. I want Gardens by the Bay, Chinatown, Marina Bay and local food.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Smart AI Import", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "Paste travel notes and LoomyTrip will extract places for you to review before creating an itinerary.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            lineHeight = 21.sp
        )
        OutlinedTextField(
            value = guide,
            onValueChange = { guide = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 8,
            label = { Text("Travel text") },
            supportingText = { Text("${guide.length} characters") },
            shape = RoundedCornerShape(18.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Text(
                    "The AI reads this text and proposes real places for your itinerary.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Button(
            onClick = { onExtract(guide) },
            modifier = Modifier.fillMaxWidth(),
            enabled = guide.isNotBlank() && !isLoading
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (isLoading) "Analyzing…" else "Start Parsing", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReviewExtractedScreen(
    places: List<ExtractedPlace>,
    onIncludedChange: (String, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onImportAgain: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val includedCount = places.count { it.isIncluded }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Review extracted places", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "$includedCount of ${places.size} places selected. Remove anything that should not enter the itinerary.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(places, key = { it.id }) { place ->
                ExtractedPlaceCard(
                    place = place,
                    onIncludedChange = { onIncludedChange(place.id, it) }
                )
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = includedCount > 0 && !isLoading
        ) {
            Text(if (isLoading) "Creating itinerary…" else "Confirm itinerary", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onImportAgain,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Edit source text")
        }
    }
}

@Composable
private fun ExtractedPlaceCard(
    place: ExtractedPlace,
    onIncludedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (place.isIncluded) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(place.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${place.category} · ${place.suggestedTime}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    place.address,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            Checkbox(
                checked = place.isIncluded,
                onCheckedChange = onIncludedChange
            )
        }
    }
}
