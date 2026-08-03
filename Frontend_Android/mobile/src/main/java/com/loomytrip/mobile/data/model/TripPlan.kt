package com.loomytrip.mobile.data.model

data class TripPlan(
    val id: String,
    val title: String,
    val dateLabel: String,
    val totalDays: Int,
    val activities: List<TripActivity>
)
