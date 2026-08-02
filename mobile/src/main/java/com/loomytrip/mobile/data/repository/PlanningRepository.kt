package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.ExtractedPlace

interface PlanningRepository {
    fun extractPlaces(sourceText: String): List<ExtractedPlace>
}

class MockPlanningRepository : PlanningRepository {
    override fun extractPlaces(sourceText: String): List<ExtractedPlace> {
        if (sourceText.isBlank()) return emptyList()

        return listOf(
            ExtractedPlace(
                id = "wat-chedi-luang",
                name = "Wat Chedi Luang",
                category = "Temple",
                address = "103 Prapokkloa Road, Chiang Mai",
                suggestedTime = "09:00"
            ),
            ExtractedPlace(
                id = "tha-phae-gate",
                name = "Tha Phae Gate",
                category = "Landmark",
                address = "Moon Muang Road, Chiang Mai",
                suggestedTime = "11:00"
            ),
            ExtractedPlace(
                id = "nimman-road",
                name = "Nimman Road",
                category = "Food & shopping",
                address = "Nimmanahaeminda Road, Chiang Mai",
                suggestedTime = "14:00"
            ),
            ExtractedPlace(
                id = "sunday-market",
                name = "Sunday Walking Street",
                category = "Market",
                address = "Ratchadamnoen Road, Chiang Mai",
                suggestedTime = "18:00"
            )
        )
    }
}
