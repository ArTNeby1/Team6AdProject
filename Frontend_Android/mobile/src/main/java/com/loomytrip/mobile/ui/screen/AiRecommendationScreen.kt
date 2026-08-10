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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomytrip.mobile.data.remote.OrderedStop
import com.loomytrip.mobile.data.remote.RecommendationResponse
import com.loomytrip.mobile.data.remote.SuggestedAddition

@Composable
fun AiRecommendationScreen(
    result: RecommendationResponse,
    isLiveResult: Boolean,
    sourceLabel: String = if (isLiveResult) "Live AI mock" else "Offline fallback",
    onUseItinerary: (Set<String>) -> Unit
) {
    val selectedSuggestions = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI route ready", style = MaterialTheme.typography.headlineMedium)
            AssistChip(
                onClick = {},
                label = { Text(sourceLabel) },
                leadingIcon = {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Column {
                            val hasWeatherSummary = !result.weatherSummary.isNullOrBlank()
                            Text(
                                text = if (hasWeatherSummary) "Weather outlook" else "Route optimisation",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = result.weatherSummary.orEmpty().ifBlank {
                                    "Weather forecast unavailable for this date. " +
                                        "The route was optimised by distance instead."
                                },
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item {
                Text("Optimised order", style = MaterialTheme.typography.titleLarge)
            }
            items(result.orderedStops.sortedBy { it.order }, key = { "ordered-${it.order}-${it.name}" }) { stop ->
                OrderedStopCard(stop)
            }

            if (result.suggestedAdditions.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Recommended nearby", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Optional places from the Singapore attractions dataset.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
                items(result.suggestedAdditions, key = { "suggested-${it.name}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        selected = suggestion.name in selectedSuggestions,
                        onToggle = {
                            if (suggestion.name in selectedSuggestions) {
                                selectedSuggestions.remove(suggestion.name)
                            } else {
                                selectedSuggestions.add(suggestion.name)
                            }
                        }
                    )
                }
            }
        }

        Button(
            onClick = { onUseItinerary(selectedSuggestions.toSet()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Use this itinerary", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OrderedStopCard(stop: OrderedStop) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    stop.order.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stop.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    listOfNotNull(
                        stop.timeOfDay?.replaceFirstChar { it.uppercase() },
                        stop.type.replaceFirstChar { it.uppercase() },
                        stop.activities.takeIf { it.isNotEmpty() }?.joinToString()
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
                Text(
                    stop.reason.ifBlank { "AI kept this stop in the route." },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SuggestedAddition,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(suggestion.name, fontWeight = FontWeight.Bold)
                suggestion.distanceKm?.let { Text("${"%.1f".format(it)} km away", fontSize = 12.sp) }
                Text(
                    suggestion.reason.removePrefix("[MOCK] "),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    fontSize = 12.sp
                )
            }
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (selected) "Remove ${suggestion.name}" else "Add ${suggestion.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
