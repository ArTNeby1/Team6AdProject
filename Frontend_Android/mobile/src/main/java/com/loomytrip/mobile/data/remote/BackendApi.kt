package com.loomytrip.mobile.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BackendApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @GET("api/v1/trips")
    suspend fun trips(): List<TripResponse>

    @GET("api/v1/trips/{tripId}")
    suspend fun trip(@Path("tripId") tripId: Long): TripResponse

    @POST("api/v1/trips/{tripId}/schedules")
    suspend fun addTripSchedules(
        @Path("tripId") tripId: Long,
        @Body request: AddTripSchedulesRequest
    ): TripResponse

    @POST("api/v1/planning-sessions")
    suspend fun createPlanningSession(
        @Body request: CreatePlanningSessionRequest
    ): PlanningSessionDetailResponse

    @DELETE("api/v1/planning-sessions/draft-places/{placeId}")
    suspend fun deleteDraftPlace(@Path("placeId") placeId: Long)

    @POST("api/v1/planning-sessions/{sessionId}/confirm")
    suspend fun confirmPlanningSession(
        @Path("sessionId") sessionId: Long
    ): ConfirmSessionResponse
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val age: Int? = null,
    val gender: String? = null
)

data class AuthResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val userId: Long,
    val username: String? = null,
    val email: String,
    val travelStyle: String? = null,
    val preferTransport: String? = null
)

data class CreatePlanningSessionRequest(
    val title: String,
    val initialBrief: String
)

data class PlanningSessionDetailResponse(
    val id: Long,
    val title: String? = null,
    val initialBrief: String? = null,
    val status: String,
    val confirmedTripId: Long? = null,
    val draftPlaces: List<DraftPlaceResponse> = emptyList(),
    val updatedAt: String? = null
)

data class DraftPlaceResponse(
    val id: Long,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null,
    val validationStatus: String? = null,
    val note: String? = null,
    val activities: List<DraftActivityResponse> = emptyList()
)

data class DraftActivityResponse(
    val id: Long,
    val title: String
)

data class ConfirmSessionResponse(
    val id: Long,
    val tripName: String,
    val startDate: String,
    val durationDays: Int,
    val updatedAt: String? = null,
    val weatherSummary: String? = null,
    val suggestedAdditions: List<BackendSuggestedAddition> = emptyList()
)

data class BackendSuggestedAddition(
    val name: String,
    val type: String = "attraction",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double? = null,
    val reason: String = "",
    val activities: List<String> = emptyList()
)

data class TripResponse(
    val id: Long,
    val tripName: String,
    val startDate: String,
    val durationDays: Int,
    val updatedAt: String? = null,
    val status: String? = null,
    val schedules: List<TripScheduleResponse> = emptyList()
)

data class AddTripSchedulesRequest(
    val day: Int,
    val locationNames: List<String>
)

data class TripScheduleResponse(
    val id: Long,
    val destination: BackendDestination,
    val tripDay: BackendTripDay,
    val sequence: Int,
    val startTime: String? = null,
    val endTime: String? = null,
    val plannedDurationMinutes: Int? = null,
    val note: String? = null
)

data class BackendDestination(
    val id: Long,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null
)

data class BackendTripDay(
    val id: Long,
    @SerializedName("daySequence") val daySequence: Int
)

data class BackendErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val details: List<String> = emptyList()
)
