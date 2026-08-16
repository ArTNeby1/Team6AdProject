package com.loomytrip.mobile.data.repository

import com.google.gson.Gson
import com.loomytrip.mobile.data.model.ExtractedPlace
import com.loomytrip.mobile.data.network.ChatRole
import com.loomytrip.mobile.data.network.ConfirmSessionRequest
import com.loomytrip.mobile.data.network.CreateChatMessageRequest
import com.loomytrip.mobile.data.network.CreatePlanningSessionRequest
import com.loomytrip.mobile.data.network.DraftPlaceDto
import com.loomytrip.mobile.data.network.PlanningSessionDetailDto
import com.loomytrip.mobile.data.network.PlanningApi
import com.loomytrip.mobile.data.network.UpdateDraftPlaceRequest
import com.loomytrip.mobile.data.network.ValidationStatus
import com.loomytrip.mobile.data.network.PlanningSessionSummaryDto
import com.loomytrip.mobile.data.network.SuggestedAdditionDto
import com.loomytrip.mobile.data.network.planningApi
import kotlinx.coroutines.delay
import retrofit2.HttpException

class InvalidPlanningInputException(message: String) : Exception(message)
class PlanningImportException(message: String) : Exception(message)
class PlanningImportTimeoutException : Exception("Import timed out. Please try again.")
class DurationRequiredException : Exception("Choose how many days this trip lasts before confirming.")

internal data class PlanningApiError(val code: String?, val message: String?)

internal fun parsePlanningApiError(rawBody: String?): PlanningApiError? = runCatching {
    Gson().fromJson(rawBody, PlanningApiError::class.java)
}.getOrNull()

/** Ports Frontend_Web's ImportPage.jsx flow (POST /planning-sessions -> GET detail -> ... -> confirm) to mobile. */
object AiPlanningRepository {

    private const val PROCESSING = "PROCESSING"
    private const val DRAFT_READY = "DRAFT_READY"
    private const val FAILED = "FAILED"
    private const val POLL_ATTEMPTS = 30
    private const val POLL_INTERVAL_MS = 2_000L

    data class SessionResult(
        val sessionId: Long,
        val durationDays: Int?,
        val places: List<ExtractedPlace>
    )

    data class ConfirmationResult(
        val tripId: Long,
        val weatherSummary: String?,
        val suggestedAdditions: List<SuggestedAdditionDto>
    )

    suspend fun sessions(): List<PlanningSessionSummaryDto> =
        planningApi.getSessions().sortedByDescending { it.updatedAt.orEmpty() }

    suspend fun resumeSession(sessionId: Long): SessionResult {
        val current = planningApi.getSession(sessionId)
        val detail = when (current.status?.uppercase()) {
            PROCESSING -> waitForInitialImport(sessionId)
            FAILED -> throwImportFailure(current)
            else -> current
        }
        return detail.toSessionResult()
    }

    suspend fun startSession(sourceText: String): SessionResult =
        startSession(sourceText, planningApi, POLL_ATTEMPTS, POLL_INTERVAL_MS)

    internal suspend fun startSession(
        sourceText: String,
        api: PlanningApi,
        pollAttempts: Int = POLL_ATTEMPTS,
        pollIntervalMs: Long = POLL_INTERVAL_MS
    ): SessionResult {
        return withPlanningErrorMapping {
            val created = api.createSession(
                CreatePlanningSessionRequest(title = suggestTripTitle(sourceText), initialBrief = sourceText)
            )
            val ready = when (created.status?.uppercase()) {
                PROCESSING -> waitForInitialImport(created.id, api, pollAttempts, pollIntervalMs)
                FAILED -> throwImportFailure(created)
                else -> api.getSession(created.id)
            }
            val validated = if (ready.status?.uppercase() == DRAFT_READY) {
                api.validatePlaces(ready.id)
            } else {
                ready
            }
            validated.toSessionResult()
        }
    }

    suspend fun refine(sessionId: Long, instruction: String): SessionResult =
        refine(sessionId, instruction, planningApi)

    internal suspend fun refine(sessionId: Long, instruction: String, api: PlanningApi): SessionResult {
        return withPlanningErrorMapping {
            api.addMessage(sessionId, CreateChatMessageRequest(ChatRole.user, instruction))
            api.refine(sessionId)
            api.validatePlaces(sessionId).toSessionResult()
        }
    }

    suspend fun renamePlace(placeId: Long, name: String) = renamePlace(placeId, name, planningApi)

    internal suspend fun renamePlace(placeId: Long, name: String, api: PlanningApi) {
        api.updateDraftPlace(placeId, UpdateDraftPlaceRequest(name = name))
    }

    suspend fun assignDay(placeId: Long, day: Int) = assignDay(placeId, day, planningApi)

    internal suspend fun assignDay(placeId: Long, day: Int, api: PlanningApi) {
        require(day in 1..30) { "Day must be between 1 and 30." }
        api.updateDraftPlace(placeId, UpdateDraftPlaceRequest(suggestedDay = day))
    }

    suspend fun distributePlaces(places: List<ExtractedPlace>, durationDays: Int): List<ExtractedPlace> {
        require(durationDays in 1..30) { "Trip duration must be between 1 and 30 days." }
        if (places.isEmpty()) return places

        return places.mapIndexed { index, place ->
            val day = distributedDay(index, places.size, durationDays)
            place.id.toLongOrNull()?.let { assignDay(it, day) }
            place.copy(suggestedDay = day)
        }
    }

    suspend fun deletePlace(placeId: Long) = deletePlace(placeId, planningApi)

    internal suspend fun deletePlace(placeId: Long, api: PlanningApi) {
        api.deleteDraftPlace(placeId)
    }

    suspend fun validatePlaces(sessionId: Long): SessionResult = validatePlaces(sessionId, planningApi)

    internal suspend fun validatePlaces(sessionId: Long, api: PlanningApi): SessionResult =
        api.validatePlaces(sessionId).toSessionResult()

    suspend fun confirm(sessionId: Long, durationDays: Int): ConfirmationResult =
        confirm(sessionId, durationDays, planningApi)

    internal suspend fun confirm(
        sessionId: Long,
        durationDays: Int,
        api: PlanningApi
    ): ConfirmationResult {
        require(durationDays in 1..30) { "Trip duration must be between 1 and 30 days." }
        return withPlanningErrorMapping {
            val response = api.confirm(sessionId, ConfirmSessionRequest(durationDays))
            ConfirmationResult(
                tripId = response.id,
                weatherSummary = response.weatherSummary,
                suggestedAdditions = response.suggestedAdditions
            )
        }
    }

    private suspend fun waitForInitialImport(
        sessionId: Long,
        api: PlanningApi = planningApi,
        pollAttempts: Int = POLL_ATTEMPTS,
        pollIntervalMs: Long = POLL_INTERVAL_MS
    ): PlanningSessionDetailDto {
        repeat(pollAttempts) { attempt ->
            val detail = api.getSession(sessionId)
            when (detail.status?.uppercase()) {
                DRAFT_READY -> return detail
                FAILED -> throwImportFailure(detail)
                PROCESSING -> Unit
                else -> if (detail.draftPlaces.isNotEmpty()) return detail
            }
            if (attempt < pollAttempts - 1 && pollIntervalMs > 0) delay(pollIntervalMs)
        }
        throw PlanningImportTimeoutException()
    }

    private fun throwImportFailure(detail: PlanningSessionDetailDto): Nothing {
        val reason = detail.failureReason?.takeIf { it.isNotBlank() }
            ?: "We could not import these travel notes."
        if (detail.failureCode == "NO_USEFUL_CONTENT") {
            throw InvalidPlanningInputException(reason)
        }
        throw PlanningImportException(reason)
    }

    private suspend fun <T> withPlanningErrorMapping(block: suspend () -> T): T {
        try {
            return block()
        } catch (error: HttpException) {
            val apiError = parsePlanningApiError(error.response()?.errorBody()?.string())
            if (error.code() == 422 && apiError?.code == "NO_USEFUL_CONTENT") {
                throw InvalidPlanningInputException(
                    apiError.message?.takeIf { it.isNotBlank() }
                        ?: "No useful travel information was found."
                )
            }
            if (error.code() == 422 && apiError?.code == "DAYS_REQUIRED") {
                throw DurationRequiredException()
            }
            throw error
        }
    }

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

    private fun PlanningSessionDetailDto.toSessionResult(): SessionResult = SessionResult(
        sessionId = id,
        durationDays = durationDays,
        places = toExtractedPlaces(draftPlaces)
    )

    private fun toExtractedPlaces(places: List<DraftPlaceDto>): List<ExtractedPlace> =
        places.map { place ->
            ExtractedPlace(
                id = place.id.toString(),
                name = place.name,
                category = place.category ?: "Place",
                address = place.address ?: "",
                suggestedTime = if (place.validationStatus == ValidationStatus.VALID) "Located" else "Check location",
                isIncluded = true,
                suggestedDay = place.suggestedDay
                    ?: place.activities.firstNotNullOfOrNull { it.suggestedDay }
            )
        }

    internal fun distributedDay(index: Int, placeCount: Int, durationDays: Int): Int {
        require(placeCount > 0)
        require(index in 0 until placeCount)
        require(durationDays in 1..30)
        return minOf(durationDays, (index * durationDays) / placeCount + 1)
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
