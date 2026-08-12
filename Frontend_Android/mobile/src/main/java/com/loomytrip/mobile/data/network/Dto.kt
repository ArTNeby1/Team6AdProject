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

data class UpdateTripRequest(val startDate: String)

data class AddSchedulesRequest(val day: Int, val locationNames: List<String>)

data class BulkScheduleItem(val id: Long, val day: Int, val sequence: Int, val startTime: String?)

data class BulkUpdateSchedulesRequest(val schedules: List<BulkScheduleItem>)

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

data class ConfirmSessionResponseDto(
    val id: Long,
    val tripName: String?,
    val startDate: String?,
    val durationDays: Int?
)
