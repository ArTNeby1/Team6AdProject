package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.ExtractedPlace
import com.loomytrip.mobile.data.network.ChatRole
import com.loomytrip.mobile.data.network.CreateChatMessageRequest
import com.loomytrip.mobile.data.network.CreatePlanningSessionRequest
import com.loomytrip.mobile.data.network.DraftPlaceDto
import com.loomytrip.mobile.data.network.UpdateDraftPlaceRequest
import com.loomytrip.mobile.data.network.ValidationStatus
import com.loomytrip.mobile.data.network.PlanningSessionSummaryDto
import com.loomytrip.mobile.data.network.planningApi

/** Ports Frontend_Web's ImportPage.jsx flow (POST /planning-sessions -> GET detail -> ... -> confirm) to mobile. */
object AiPlanningRepository {

    data class SessionResult(val sessionId: Long, val places: List<ExtractedPlace>)

    suspend fun sessions(): List<PlanningSessionSummaryDto> =
        planningApi.getSessions().sortedByDescending { it.updatedAt.orEmpty() }

    suspend fun resumeSession(sessionId: Long): SessionResult {
        val detail = planningApi.getSession(sessionId)
        return SessionResult(detail.id, toExtractedPlaces(detail.draftPlaces))
    }

    suspend fun startSession(sourceText: String): SessionResult {
        val created = planningApi.createSession(
            CreatePlanningSessionRequest(title = suggestTripTitle(sourceText), initialBrief = sourceText)
        )
        // Mirrors ImportPage.jsx: the backend triggers AI extraction synchronously on create,
        // so re-fetch the session detail right after to read the populated draft places.
        val detail = planningApi.getSession(created.id)
        return SessionResult(created.id, toExtractedPlaces(detail.draftPlaces))
    }

    suspend fun refine(sessionId: Long, instruction: String): List<ExtractedPlace> {
        planningApi.addMessage(sessionId, CreateChatMessageRequest(ChatRole.user, instruction))
        val detail = planningApi.refine(sessionId)
        return toExtractedPlaces(detail.draftPlaces)
    }

    suspend fun renamePlace(placeId: Long, name: String) {
        planningApi.updateDraftPlace(placeId, UpdateDraftPlaceRequest(name = name))
    }

    suspend fun deletePlace(placeId: Long) {
        planningApi.deleteDraftPlace(placeId)
    }

    /** Returns the id of the newly created Trip. */
    suspend fun confirm(sessionId: Long): Long = planningApi.confirm(sessionId).id

    internal fun suggestTripTitle(sourceText: String): String {
        val destination = destinationNames.firstOrNull { (keyword, _) ->
            sourceText.contains(keyword, ignoreCase = true)
        }?.second
        val days = extractDayCount(sourceText)

        return when {
            destination != null && days != null -> "$destination $days-Day Trip"
            destination != null -> "$destination Highlights"
            days != null -> "My $days-Day Itinerary"
            else -> "My AI Itinerary"
        }
    }

    private fun extractDayCount(sourceText: String): Int? {
        val numericPatterns = listOf(
            Regex("(?i)\\b(\\d{1,2})\\s*(?:days?|d)\\b"),
            Regex("(\\d{1,2})\\s*天")
        )
        numericPatterns.forEach { pattern ->
            pattern.find(sourceText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { days ->
                if (days in 1..30) return days
            }
        }

        val normalized = sourceText.lowercase()
        return dayWords.firstNotNullOfOrNull { (word, days) ->
            days.takeIf { Regex("\\b${Regex.escape(word)}\\s+days?\\b").containsMatchIn(normalized) }
        }
    }

    private fun toExtractedPlaces(places: List<DraftPlaceDto>): List<ExtractedPlace> =
        places.map { place ->
            ExtractedPlace(
                id = place.id.toString(),
                name = place.name,
                category = place.category ?: "Place",
                address = place.address ?: "",
                suggestedTime = if (place.validationStatus == ValidationStatus.VALID) "Located" else "Check location",
                isIncluded = true
            )
        }

    private val destinationNames = listOf(
        "Singapore" to "Singapore",
        "新加坡" to "Singapore",
        "Chiang Mai" to "Chiang Mai",
        "清迈" to "Chiang Mai",
        "Kyoto" to "Kyoto",
        "京都" to "Kyoto",
        "Bali" to "Bali",
        "巴厘岛" to "Bali",
        "Tokyo" to "Tokyo",
        "东京" to "Tokyo",
        "Bangkok" to "Bangkok",
        "曼谷" to "Bangkok"
    )

    private val dayWords = listOf(
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7
    )
}
