package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomytrip.mobile.data.model.ExtractedPlace

@Composable
fun ImportGuideScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onExtract: (String) -> Unit
) {
    val sampleGuide = "Singapore on 12 August: Gardens by the Bay for photos, then the National Museum for an exhibition."
    var guide by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Turn notes into a trip", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Paste a travel post or your own notes. The AI service will extract places and activities for you to review.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            lineHeight = 21.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {
                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { guide = it }
                },
                label = { Text("Paste") },
                leadingIcon = {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            AssistChip(
                onClick = { guide = sampleGuide },
                label = { Text("Sample") },
                leadingIcon = {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(17.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            AssistChip(
                onClick = { guide = "" },
                enabled = guide.isNotEmpty(),
                label = { Text("Clear") },
                leadingIcon = {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(17.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        OutlinedTextField(
            value = guide,
            onValueChange = { guide = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            minLines = 6,
            label = { Text("Travel text") },
            placeholder = { Text("Paste a post, list of places, or rough travel notes here…") },
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
                    "Live AI mock over HTTPS. You will review the extracted places before route optimisation starts.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
        Button(
            onClick = { onExtract(guide) },
            modifier = Modifier.fillMaxWidth(),
            enabled = guide.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
            }
            Spacer(Modifier.size(8.dp))
            Text(if (isLoading) "Contacting AI service..." else "Extract places", fontWeight = FontWeight.Bold)
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

@Composable
fun ReviewExtractedScreen(
    places: List<ExtractedPlace>,
    onIncludedChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit = {},
    isConfirming: Boolean = false,
    errorMessage: String? = null,
    onConfirm: () -> Unit,
    onImportAgain: () -> Unit
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
                    onIncludedChange = { onIncludedChange(place.id, it) },
                    onRemove = { onRemove(place.id) }
                )
            }
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = includedCount > 0 && !isConfirming
        ) {
            if (isConfirming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(if (isConfirming) "Optimising route..." else "Confirm and optimise", fontWeight = FontWeight.Bold)
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        OutlinedButton(
            onClick = onImportAgain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Edit source text")
        }
    }
}

@Composable
private fun ExtractedPlaceCard(
    place: ExtractedPlace,
    onIncludedChange: (Boolean) -> Unit,
    onRemove: () -> Unit
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
                    listOfNotNull(
                        place.category.takeIf { it.isNotBlank() },
                        place.activities.takeIf { it.isNotEmpty() }?.joinToString()
                            ?: place.suggestedTime.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (place.address.isNotBlank()) {
                    Text(
                        place.address,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Checkbox(
                checked = place.isIncluded,
                onCheckedChange = onIncludedChange
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${place.name}")
            }
        }
    }
}
