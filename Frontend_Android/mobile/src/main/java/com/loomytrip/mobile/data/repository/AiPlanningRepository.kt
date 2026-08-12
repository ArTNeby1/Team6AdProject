package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.ExtractedPlace
import com.loomytrip.mobile.data.network.ChatRole
import com.loomytrip.mobile.data.network.CreateChatMessageRequest
import com.loomytrip.mobile.data.network.CreatePlanningSessionRequest
import com.loomytrip.mobile.data.network.DraftPlaceDto
import com.loomytrip.mobile.data.network.UpdateDraftPlaceRequest
import com.loomytrip.mobile.data.network.ValidationStatus
import com.loomytrip.mobile.data.network.planningApi

/** Ports Frontend_Web's ImportPage.jsx flow (POST /planning-sessions -> GET detail -> ... -> confirm) to mobile. */
object AiPlanningRepository {

    data class SessionResult(val sessionId: Long, val places: List<ExtractedPlace>)

    suspend fun startSession(sourceText: String): SessionResult {
        val created = planningApi.createSession(
            CreatePlanningSessionRequest(title = "Plan for ${sourceText.take(15)}...", initialBrief = sourceText)
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
}
