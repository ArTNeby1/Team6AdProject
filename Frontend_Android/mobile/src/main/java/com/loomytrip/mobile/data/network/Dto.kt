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
    val name: String,
    val latitude: Double?,
    val longitude: Double?
)

data class TripDayDto(val daySequence: Int?)

data class ScheduleDto(
    val id: Long,
    val destination: DestinationDto,
    val tripDay: TripDayDto?,
    val startTime: String?,
    val activityType: String?,
    val plannedDurationMinutes: Int?
)

data class TripDto(
    val id: Long,
    val tripName: String,
    val startDate: String?,
    val status: String?,
    val durationDays: Int?,
    val coverImage: String?,
    val schedules: List<ScheduleDto>?
)

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
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val category: String?,
    val validationStatus: ValidationStatus?,
    val note: String?
)

data class PlanningSessionDetailDto(
    val id: Long,
    val title: String?,
    val initialBrief: String?,
    val status: String?,
    val confirmedTripId: Long?,
    val draftPlaces: List<DraftPlaceDto>?
)

data class ConfirmSessionResponseDto(
    val id: Long,
    val tripName: String?,
    val startDate: String?,
    val durationDays: Int?
)
