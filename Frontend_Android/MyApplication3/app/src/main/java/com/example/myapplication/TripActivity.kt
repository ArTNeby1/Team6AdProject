package com.example.myapplication

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

data class TripActivity(
    val id: String,
    val name: String,
    val day: Int,
    val time: String = "09:00",
    val duration: String = "1.5h"
)

data class TripData(
    val id: String,
    val title: String,
    val activities: SnapshotStateList<TripActivity>,
    val status: TripStatus = TripStatus.ACTIVE,
    val date: String = "2026.08.01",
    val description: String = "1 天 0 站"
)

enum class TripStatus {
    ACTIVE, NOT_STARTED, FINISHED
}
