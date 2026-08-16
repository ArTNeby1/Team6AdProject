package com.loomytrip.mobile.data.model

data class ExtractedPlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val suggestedTime: String,
    val isIncluded: Boolean = true,
    val suggestedDay: Int? = null
)
