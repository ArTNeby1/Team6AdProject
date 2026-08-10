package com.loomytrip.mobile.data.repository

import android.content.Context
import com.google.gson.Gson
import com.loomytrip.mobile.BuildConfig
import com.loomytrip.mobile.data.model.ExtractedPlace
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.model.TripPlan
import com.loomytrip.mobile.data.remote.AiCoordinates
import com.loomytrip.mobile.data.remote.AiPlace
import com.loomytrip.mobile.data.remote.AddTripSchedulesRequest
import com.loomytrip.mobile.data.remote.AuthResponse
import com.loomytrip.mobile.data.remote.BackendApi
import com.loomytrip.mobile.data.remote.BackendErrorResponse
import com.loomytrip.mobile.data.remote.ConfirmSessionResponse
import com.loomytrip.mobile.data.remote.CreatePlanningSessionRequest
import com.loomytrip.mobile.data.remote.ExtractionResponse
import com.loomytrip.mobile.data.remote.LoginRequest
import com.loomytrip.mobile.data.remote.OrderedStop
import com.loomytrip.mobile.data.remote.PlanningSessionDetailResponse
import com.loomytrip.mobile.data.remote.RecommendationResponse
import com.loomytrip.mobile.data.remote.RegisterRequest
import com.loomytrip.mobile.data.remote.SuggestedAddition
import com.loomytrip.mobile.data.remote.TripResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class BackendSession(
    val email: String,
    val username: String?,
    val userId: Long
)

data class BackendConfirmedPlan(
    val recommendation: RecommendationResponse,
    val trip: TripPlan
)

class BackendException(
    message: String,
    val code: String? = null,
    val statusCode: Int? = null
) : Exception(message)

class BackendRepository(
    context: Context,
    baseUrl: String = BuildConfig.BACKEND_BASE_URL
) {
    private val sessionStore = BackendSessionStore(context.applicationContext)
    private val gson = Gson()
    private val api: BackendApi

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = sessionStore.accessToken()
                val request = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }.build()
                chain.proceed(request)
            }
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BackendApi::class.java)
    }

    fun hasSession(): Boolean = !sessionStore.accessToken().isNullOrBlank()

    fun savedEmail(): String? = sessionStore.email()

    suspend fun login(email: String, password: String): BackendSession = call {
        api.login(LoginRequest(email.trim(), password)).also(sessionStore::save).toSession()
    }

    suspend fun register(name: String, email: String, password: String): BackendSession = call {
        api.register(
            RegisterRequest(
                username = name.trim(),
                email = email.trim(),
                password = password
            )
        ).also(sessionStore::save).toSession()
    }

    fun signOut() = sessionStore.clear()

    suspend fun savedTrips(): List<TripPlan> = call {
        api.trips().map(TripResponse::toTripPlan)
    }

    suspend fun createPlanningSession(text: String): PlanningSessionDetailResponse = call {
        api.createPlanningSession(
            CreatePlanningSessionRequest(
                title = planningTitle(text),
                initialBrief = text
            )
        )
    }

    suspend fun deleteDraftPlace(placeId: Long) = call {
        api.deleteDraftPlace(placeId)
    }

    suspend fun confirmPlanningSession(sessionId: Long): BackendConfirmedPlan = call {
        val confirmation = api.confirmPlanningSession(sessionId)
        val trip = api.trip(confirmation.id)
        val recommendation = confirmation.toRecommendation(trip)
        if (recommendation.orderedStops.isEmpty()) {
            throw BackendException(
                message = "Backend created a trip but returned no planned stops.",
                code = "EMPTY_ITINERARY"
            )
        }
        BackendConfirmedPlan(recommendation, trip.toTripPlan())
    }

    suspend fun addSuggestedPlaces(tripId: Long, placeNames: Set<String>): TripPlan = call {
        if (placeNames.isEmpty()) {
            api.trip(tripId).toTripPlan()
        } else {
            api.addTripSchedules(
                tripId = tripId,
                request = AddTripSchedulesRequest(day = 1, locationNames = placeNames.toList())
            ).toTripPlan()
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val parsed = runCatching {
            gson.fromJson(error.response()?.errorBody()?.string(), BackendErrorResponse::class.java)
        }.getOrNull()
        val details = parsed?.details.orEmpty().joinToString("\n")
        val message = parsed?.message
            ?.takeIf { it.isNotBlank() }
            ?: "Backend request failed (${error.code()})."
        throw BackendException(
            message = if (details.isBlank()) message else "$message\n$details",
            code = parsed?.code,
            statusCode = error.code()
        )
    }

    private fun planningTitle(text: String): String {
        val firstLine = text.lineSequence().firstOrNull().orEmpty().trim()
        return firstLine.take(48).ifBlank { "New trip" }
    }
}

fun PlanningSessionDetailResponse.toExtractionResponse(): ExtractionResponse = ExtractionResponse(
    destination = "Singapore",
    dates = emptyList(),
    places = draftPlaces.map { place ->
        AiPlace(
            name = place.name,
            type = place.category ?: "other",
            coords = if (place.latitude != null && place.longitude != null) {
                AiCoordinates(place.latitude, place.longitude)
            } else {
                null
            },
            activities = place.activities.map { it.title }
        )
    }
)

fun PlanningSessionDetailResponse.toExtractedPlaces(): List<ExtractedPlace> = draftPlaces.map { place ->
    ExtractedPlace(
        id = "backend-${place.id}",
        name = place.name,
        category = (place.category ?: "other").replaceFirstChar { it.uppercase() },
        address = place.address.orEmpty(),
        activities = place.activities.map { it.title },
        latitude = place.latitude,
        longitude = place.longitude
    )
}

private fun AuthResponse.toSession() = BackendSession(
    email = email,
    username = username,
    userId = userId
)

private fun TripResponse.toTripPlan(): TripPlan {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull()
    val days = durationDays.coerceAtLeast(1)
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    val shortFormatter = DateTimeFormatter.ofPattern("d MMM")
    val dateLabel = when {
        start == null -> startDate
        days == 1 -> start.format(formatter)
        else -> "${start.format(shortFormatter)} – ${start.plusDays((days - 1).toLong()).format(formatter)}"
    }
    val dayLabels = if (start == null) {
        emptyList()
    } else {
        (0 until days).map { start.plusDays(it.toLong()).format(shortFormatter) }
    }

    return TripPlan(
        id = "backend-$id",
        title = tripName,
        dateLabel = dateLabel,
        totalDays = days,
        activities = schedules
            .sortedWith(compareBy({ it.tripDay.daySequence }, { it.sequence }))
            .map { schedule ->
                val fallbackHour = (9 + (schedule.sequence - 1) * 2).coerceAtMost(20)
                TripActivity(
                    id = "backend-schedule-${schedule.id}",
                    title = schedule.destination.name,
                    category = schedule.destination.category ?: "Trip stop",
                    day = schedule.tripDay.daySequence,
                    startTime = schedule.startTime?.take(5) ?: "%02d:00".format(fallbackHour),
                    durationMinutes = schedule.plannedDurationMinutes ?: 90,
                    address = schedule.destination.address ?: schedule.note ?: "Address to be confirmed",
                    latitude = schedule.destination.latitude ?: 1.290270,
                    longitude = schedule.destination.longitude ?: 103.851959
                )
            },
        dayLabels = dayLabels
    )
}

private fun ConfirmSessionResponse.toRecommendation(trip: TripResponse) = RecommendationResponse(
    status = "OK",
    weatherSummary = weatherSummary,
    orderedStops = trip.schedules
        .sortedWith(compareBy({ it.tripDay.daySequence }, { it.sequence }))
        .mapIndexed { index, schedule ->
            OrderedStop(
                name = schedule.destination.name,
                type = schedule.destination.category ?: "other",
                lat = schedule.destination.latitude,
                lng = schedule.destination.longitude,
                order = index + 1,
                reason = schedule.note ?: "Saved by LoomyTrip Backend."
            )
        },
    suggestedAdditions = suggestedAdditions.map { suggestion ->
        SuggestedAddition(
            name = suggestion.name,
            type = suggestion.type,
            lat = suggestion.latitude,
            lng = suggestion.longitude,
            distanceKm = suggestion.distanceKm,
            reason = suggestion.reason,
            activities = suggestion.activities
        )
    }
)

private class BackendSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("loomytrip_backend_session", Context.MODE_PRIVATE)

    fun accessToken(): String? = preferences.getString("access_token", null)

    fun email(): String? = preferences.getString("email", null)

    fun save(response: AuthResponse) {
        preferences.edit()
            .putString("access_token", response.accessToken)
            .putString("email", response.email)
            .putString("username", response.username)
            .putLong("user_id", response.userId)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
