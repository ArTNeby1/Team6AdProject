package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.BuildConfig
import com.loomytrip.mobile.data.remote.AiPlanningApi
import com.loomytrip.mobile.data.remote.AiPlace
import com.loomytrip.mobile.data.remote.ExtractRequest
import com.loomytrip.mobile.data.remote.ExtractionResponse
import com.loomytrip.mobile.data.remote.OrderedStop
import com.loomytrip.mobile.data.remote.RecommendPlace
import com.loomytrip.mobile.data.remote.RecommendRequest
import com.loomytrip.mobile.data.remote.RecommendationResponse
import com.loomytrip.mobile.data.remote.SuggestedAddition
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface AiPlanningRepository {
    suspend fun extract(text: String): ExtractionResponse
    suspend fun recommend(
        extraction: ExtractionResponse,
        includedNames: Set<String>,
        preference: String
    ): RecommendationResponse
}

class RemoteAiPlanningRepository(
    baseUrl: String = BuildConfig.AI_BASE_URL
) : AiPlanningRepository {
    private val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AiPlanningApi::class.java)

    override suspend fun extract(text: String): ExtractionResponse =
        api.extract(ExtractRequest(text = text))

    override suspend fun recommend(
        extraction: ExtractionResponse,
        includedNames: Set<String>,
        preference: String
    ): RecommendationResponse {
        val places = extraction.places
            .filter { it.name in includedNames }
            .map { place ->
                RecommendPlace(
                    name = place.name,
                    type = place.type,
                    lat = place.coords?.lat,
                    lng = place.coords?.lng,
                    activities = place.activities
                )
            }
        return api.recommend(
            RecommendRequest(
                places = places,
                destination = extraction.destination,
                date = extraction.dates.firstOrNull(),
                preferenceText = "travel_style=${preference.lowercase()}"
            )
        )
    }
}

class OfflineAiPlanningRepository : AiPlanningRepository {
    override suspend fun extract(text: String) = ExtractionResponse(
        destination = "Singapore",
        dates = emptyList(),
        places = listOf(
            AiPlace("Gardens by the Bay", "attraction", activities = listOf("Photos", "Cloud Forest")),
            AiPlace("National Museum of Singapore", "attraction", activities = listOf("Exhibition"))
        )
    )

    override suspend fun recommend(
        extraction: ExtractionResponse,
        includedNames: Set<String>,
        preference: String
    ) = RecommendationResponse(
        status = "OK",
        weatherSummary = "Weather service unavailable in offline demo mode.",
        orderedStops = extraction.places
            .filter { it.name in includedNames }
            .mapIndexed { index, place ->
                OrderedStop(
                    name = place.name,
                    type = place.type,
                    activities = place.activities,
                    order = index + 1,
                    reason = if (index == 0) "Starting point of the route." else "Kept close to the previous stop."
                )
            },
        suggestedAdditions = listOf(
            SuggestedAddition(
                name = "Marina Bay Sands SkyPark",
                reason = "Nearby attraction that fits the current route."
            )
        )
    )
}
