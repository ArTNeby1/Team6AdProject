package com.loomytrip.mobile.data.network

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val username: String?,
    val email: String,
    val password: String,
    val age: Int? = null,
    val gender: String? = null
)

data class AuthResponseDto(
    val accessToken: String,
    val tokenType: String?,
    val userId: Long,
    val username: String?,
    val email: String,
    val travelStyle: String?,
    val preferTransport: String?
)

data class UserProfileDto(
    val id: Long,
    val username: String?,
    val email: String,
    val age: Int?,
    val gender: String?,
    val travelStyle: String?,
    val preferTransport: String?
)

data class DestinationDto(
    val id: Long? = null,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null
)

data class TripDayDto(
    val id: Long? = null,
    val daySequence: Int? = null
)

data class ScheduleDto(
    val id: Long,
    val destination: DestinationDto,
    val tripDay: TripDayDto? = null,
    val sequence: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val activityType: String? = null,
    val plannedDurationMinutes: Int? = null,
    val note: String? = null
)

data class TripDto(
    val id: Long,
    val tripName: String,
    val startDate: String? = null,
    val status: String? = null,
    val durationDays: Int? = null,
    val updatedAt: String? = null,
    val travelStyle: String? = null,
    val preferTransport: String? = null,
    val coverImage: String? = null,
    val schedules: List<ScheduleDto> = emptyList()
)

data class UpdateTripRequest(
    val tripName: String? = null,
    val startDate: String? = null,
    val durationDays: Int? = null,
    val travelStyle: String? = null,
    val preferTransport: String? = null
)

data class AddSchedulesRequest(val day: Int, val locationNames: List<String>)

data class BulkScheduleItem(val id: Long, val day: Int, val sequence: Int, val startTime: String?)

data class BulkUpdateSchedulesRequest(val schedules: List<BulkScheduleItem>)

data class TripRouteDto(
    val tripId: Long,
    val day: Int,
    val stopCount: Int,
    val totalDistanceKm: Double? = null,
    val totalDurationMinutes: Int? = null,
    val googleMapsUrl: String? = null,
    val legs: List<RouteLegDto> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class GenerateItineraryResponseDto(
    val tripId: Long,
    val status: String? = null,
    val days: List<GeneratedDayDto> = emptyList()
)

data class GeneratedDayDto(
    val day: Int,
    val date: String? = null,
    val weatherSummary: String? = null,
    val stops: List<GeneratedStopDto> = emptyList()
)

data class GeneratedStopDto(
    val scheduleId: Long? = null,
    val name: String,
    val order: Int? = null,
    val timeOfDay: String? = null,
    val reason: String? = null
)

data class RouteLegDto(
    val fromScheduleId: Long?,
    val toScheduleId: Long?,
    val fromName: String,
    val toName: String,
    val distanceKm: Double? = null,
    val durationMinutes: Int? = null,
    val googleMapLink: String? = null
)

data class NearbyRecommendationDto(
    val id: Long? = null,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null,
    val distanceKm: Double? = null,
    val reason: String? = null,
    val activities: List<String> = emptyList()
)

data class RecommendationResponseDto(
    val items: List<NearbyRecommendationDto> = emptyList()
)

data class CrowdHintDto(
    val quarter: Int,
    val seasonalIndex: Double,
    val level: String,
    val note: String
)

data class MapConfigDto(
    val tileUrlTemplate: String = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    val attribution: String = "© OpenStreetMap contributors",
    val defaultLatitude: Double = 1.3521,
    val defaultLongitude: Double = 103.8198,
    val defaultZoom: Int = 12
)

enum class ChatRole { user, assistant, system }

enum class ValidationStatus { UNVALIDATED, VALID, AMBIGUOUS, INVALID }

data class CreatePlanningSessionRequest(val title: String, val initialBrief: String)

data class CreateChatMessageRequest(val role: ChatRole, val content: String)

data class UpdateDraftPlaceRequest(val name: String? = null)

data class DraftPlaceDto(
    val id: Long,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null,
    val validationStatus: ValidationStatus? = null,
    val note: String? = null
)

data class PlanningSessionDetailDto(
    val id: Long,
    val title: String? = null,
    val initialBrief: String? = null,
    val status: String? = null,
    val confirmedTripId: Long? = null,
    val draftPlaces: List<DraftPlaceDto> = emptyList()
)

data class PlanningSessionSummaryDto(
    val id: Long,
    val title: String? = null,
    val initialBrief: String? = null,
    val status: String? = null,
    val confirmedTripId: Long? = null,
    val updatedAt: String? = null
)

data class ConfirmSessionResponseDto(
    val id: Long,
    val tripName: String?,
    val startDate: String?,
    val durationDays: Int?
)
