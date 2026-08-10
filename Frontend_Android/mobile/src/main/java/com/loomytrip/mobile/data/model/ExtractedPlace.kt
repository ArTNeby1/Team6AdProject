package com.loomytrip.mobile.data.model

data class ExtractedPlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String = "",
    val suggestedTime: String = "",
    val activities: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isIncluded: Boolean = true
)
