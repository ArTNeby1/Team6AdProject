package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loomytrip.mobile.data.repository.LocalDestination
import com.loomytrip.mobile.data.repository.LocalExploreRepository
import com.loomytrip.mobile.data.repository.LocalReview

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DestinationDetailScreen(
    destination: LocalDestination,
    onPlanDestination: () -> Unit
) {
    val context = LocalContext.current
    val reviews = remember(destination.id) {
        mutableStateListOf<LocalReview>().apply {
            addAll(LocalExploreRepository.reviewsFor(context, destination))
        }
    }
    var showReviewDialog by remember { mutableStateOf(false) }
    val rating = reviews.map { it.rating }.average().takeIf { !it.isNaN() } ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = destination.imageUrl,
                    contentDescription = destination.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(destination.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${destination.category} · ${destination.city}",
                        color = Color.White.copy(alpha = 0.84f)
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFE19A22))
                        Spacer(Modifier.size(5.dp))
                        Text("${"%.1f".format(rating)}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "${reviews.size} reviews",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Popular choice",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(destination.description, lineHeight = 22.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destination.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            DestinationInformation(destination)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Traveler reviews", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Recent traveler reviews",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(onClick = { showReviewDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("Write")
                }
            }
        }

        items(reviews, key = { it.id }) { review ->
            ReviewCard(review)
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Button(onClick = onPlanDestination, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("Plan a trip with this place", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showReviewDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Write a review")
                }
            }
        }
    }

    if (showReviewDialog) {
        AddLocalReviewDialog(
            onDismiss = { showReviewDialog = false },
            onAdd = { stars, content ->
                val review = LocalExploreRepository.addReview(context, destination.id, stars, content)
                reviews.add(0, review)
                showReviewDialog = false
            }
        )
    }
}

@Composable
private fun DestinationInformation(destination: LocalDestination) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Visitor information", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            InfoLine(Icons.Default.Schedule, "Opening hours", destination.openingHours)
            InfoLine(Icons.Default.Schedule, "Suggested time", destination.recommendedDuration)
            InfoLine(Icons.Default.LocationOn, "Address", destination.address)
            Text("Entry: ${destination.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ReviewCard(review: LocalReview) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(review.author.take(1).uppercase(), fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.padding(start = 9.dp)) {
                        Text(review.author, fontWeight = FontWeight.Bold)
                        Text(
                            if (review.isUserReview) "Your review · ${review.date}" else "Traveler review",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
                Text("★".repeat(review.rating), color = Color(0xFFE19A22), fontWeight = FontWeight.Bold)
            }
            Text(review.content, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AddLocalReviewDialog(
    onDismiss: () -> Unit,
    onAdd: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write a review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("Add your own rating and a short travel note.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { stars ->
                        FilterChip(
                            selected = rating == stars,
                            onClick = { rating = stars },
                            label = { Text("$stars★") }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your experience") },
                    supportingText = { Text("${content.length}/300") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(rating, content) }, enabled = content.isNotBlank()) {
                Text("Save review")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
