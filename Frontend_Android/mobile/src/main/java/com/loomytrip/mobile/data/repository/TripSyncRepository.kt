package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.AddSchedulesRequest
import com.loomytrip.mobile.data.network.BulkScheduleItem
import com.loomytrip.mobile.data.network.BulkUpdateSchedulesRequest
import com.loomytrip.mobile.data.network.ScheduleDto
import com.loomytrip.mobile.data.network.TripDto
import com.loomytrip.mobile.data.network.UpdateTripRequest
import com.loomytrip.mobile.data.network.GenerateItineraryResponseDto
import com.loomytrip.mobile.data.network.tripApi
import java.time.LocalDate

/** Loads the signed-in account's real trips/itinerary from the backend and syncs local edits back. */
object TripSyncRepository {

    suspend fun fetchTrips(): List<TripDto> = sortForDisplay(tripApi.getTrips())

    suspend fun fetchTrip(tripId: Long): TripDto = tripApi.getTrip(tripId)

    suspend fun deleteTrip(tripId: Long) = tripApi.deleteTrip(tripId)

    suspend fun updateStartDate(tripId: Long, startDate: LocalDate): TripDto =
        tripApi.updateTrip(tripId, UpdateTripRequest(startDate = startDate.toString()))

    suspend fun updatePreferences(tripId: Long, travelStyle: String, preferTransport: String): TripDto =
        tripApi.updateTrip(
            tripId,
            UpdateTripRequest(travelStyle = travelStyle, preferTransport = preferTransport)
        )

    suspend fun generateItinerary(tripId: Long): Pair<GenerateItineraryResponseDto, TripDto> {
        val result = tripApi.generateItinerary(tripId)
        return result to tripApi.getTrip(tripId)
    }

    fun sortForDisplay(trips: List<TripDto>): List<TripDto> = trips.sortedWith { left, right ->
        val statusComparison = statusOrder(left.status).compareTo(statusOrder(right.status))
        if (statusComparison != 0) {
            statusComparison
        } else {
            val leftDate = parseDate(left.startDate)
            val rightDate = parseDate(right.startDate)
            when {
                leftDate == null && rightDate == null -> 0
                leftDate == null -> 1
                rightDate == null -> -1
                left.status == "FINISHED" -> rightDate.compareTo(leftDate)
                else -> leftDate.compareTo(rightDate)
            }
        }
    }

    private fun statusOrder(status: String?): Int = when (status) {
        "ACTIVE" -> 0
        "NOT_STARTED" -> 1
        "FINISHED" -> 2
        else -> 3
    }

    private fun parseDate(value: String?): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    fun toActivities(trip: TripDto): List<TripActivity> =
        trip.schedules
            .sortedWith(compareBy({ it.tripDay?.daySequence ?: 1 }, { it.sequence ?: Int.MAX_VALUE }))
            .map { schedule ->
            TripActivity(
                id = schedule.id.toString(),
                title = schedule.destination.name,
                category = schedule.destination.category ?: schedule.activityType ?: "Visit",
                day = schedule.tripDay?.daySequence ?: 1,
                startTime = schedule.startTime?.take(5) ?: "09:00",
                durationMinutes = schedule.plannedDurationMinutes ?: 90,
                address = schedule.destination.address ?: schedule.note ?: "Address to be confirmed",
                latitude = schedule.destination.latitude ?: 0.0,
                longitude = schedule.destination.longitude ?: 0.0
            )
        }

    /**
     * Ports Frontend_Web's TripContext.saveTripEdits diffing to mobile: deletes schedules that
     * were removed locally, creates the ones added via "Add activity" (they only exist as local
     * "custom-<id>" items until now), then persists the final order/day/time for everything else.
     */
    suspend fun syncEdits(
        tripId: Long,
        activities: List<TripActivity>,
        baseline: List<ScheduleDto>,
        durationDays: Int
    ): TripDto {
        tripApi.updateTrip(tripId, UpdateTripRequest(durationDays = durationDays.coerceAtLeast(1)))
        val keptIds = activities.mapNotNull { it.id.toLongOrNull() }.toSet()
        baseline.map { it.id }.filterNot { it in keptIds }.forEach { removedId ->
            tripApi.deleteSchedule(tripId, removedId)
        }

        val manualItems = activities.filter { it.id.startsWith("custom-") }
        var resolved = activities
        if (manualItems.isNotEmpty()) {
            val createdIdByTempId = mutableMapOf<String, String>()
            manualItems.groupBy { it.day }.forEach { (day, items) ->
                val response = tripApi.addSchedules(tripId, AddSchedulesRequest(day, items.map { it.title }))
                val daySchedules = response.schedules
                    .filter { it.tripDay?.daySequence == day }
                val created = daySchedules.takeLast(items.size)
                items.forEachIndexed { index, item ->
                    created.getOrNull(index)?.let { createdIdByTempId[item.id] = it.id.toString() }
                }
            }
            resolved = activities.map { activity ->
                createdIdByTempId[activity.id]?.let { activity.copy(id = it) } ?: activity
            }
        }

        val bulkItems = resolved
            .filterNot { it.id.startsWith("custom-") }
            .groupBy { it.day }
            .flatMap { (day, items) ->
                items.mapIndexed { index, item ->
                    BulkScheduleItem(id = item.id.toLong(), day = day, sequence = index + 1, startTime = item.startTime)
                }
            }
        if (bulkItems.isNotEmpty()) {
            tripApi.bulkUpdateSchedules(tripId, BulkUpdateSchedulesRequest(bulkItems))
        }
        return tripApi.getTrip(tripId)
    }
}
