package com.loomytrip.mobile.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

interface AiPlanningApi {
    @POST("extract")
    suspend fun extract(@Body request: ExtractRequest): ExtractionResponse

    @POST("recommend")
    suspend fun recommend(@Body request: RecommendRequest): RecommendationResponse
}

data class ExtractRequest(
    val text: String,
    @SerializedName("source_name") val sourceName: String = "android_mobile"
)

data class AiCoordinates(
    val lat: Double,
    val lng: Double
)

data class AiPlace(
    val name: String,
    val type: String = "other",
    val coords: AiCoordinates? = null,
    val activities: List<String> = emptyList()
)

data class ExtractionResponse(
    val destination: String,
    val dates: List<String> = emptyList(),
    val places: List<AiPlace> = emptyList()
)

data class RecommendPlace(
    val name: String,
    val type: String = "other",
    val lat: Double? = null,
    val lng: Double? = null,
    val activities: List<String> = emptyList()
)

data class RecommendRequest(
    val places: List<RecommendPlace>,
    val destination: String = "Singapore",
    val date: String? = null,
    @SerializedName("preference_text") val preferenceText: String? = null,
    @SerializedName("top_n") val topN: Int = 3,
    val mode: String = "hybrid",
    @SerializedName("max_distance_km") val maxDistanceKm: Double? = null
)

data class OrderedStop(
    val name: String,
    val type: String = "other",
    val lat: Double? = null,
    val lng: Double? = null,
    val activities: List<String> = emptyList(),
    val order: Int,
    @SerializedName("time_of_day") val timeOfDay: String? = null,
    @SerializedName("is_outdoor") val isOutdoor: Boolean? = null,
    val reason: String = ""
)

data class SuggestedAddition(
    val name: String,
    val type: String = "attraction",
    val lat: Double? = null,
    val lng: Double? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    val similarity: Double? = null,
    val reason: String = "",
    val activities: List<String> = emptyList()
)

data class RecommendationResponse(
    val status: String,
    @SerializedName("weather_summary") val weatherSummary: String? = null,
    @SerializedName("ordered_stops") val orderedStops: List<OrderedStop> = emptyList(),
    @SerializedName("suggested_additions") val suggestedAdditions: List<SuggestedAddition> = emptyList()
)
