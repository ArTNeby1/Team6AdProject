package com.loomytrip.mobile.data.model

data class TripActivity(
    val id: String,
    val title: String,
    val category: String,
    val day: Int,
    val startTime: String,
    val durationMinutes: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val travelMinutesToNext: Int? = null,
    val transportModeToNext: String? = null
) {
    val durationLabel: String
        get() = when {
            durationMinutes < 60 -> "${durationMinutes} min"
            durationMinutes % 60 == 0 -> "${durationMinutes / 60} hr"
            else -> "${durationMinutes / 60} hr ${durationMinutes % 60} min"
        }
}
