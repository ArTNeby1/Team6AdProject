package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.AddSchedulesRequest
import com.loomytrip.mobile.data.network.BulkScheduleItem
import com.loomytrip.mobile.data.network.BulkUpdateSchedulesRequest
import com.loomytrip.mobile.data.network.ScheduleDto
import com.loomytrip.mobile.data.network.TripDto
import com.loomytrip.mobile.data.network.tripApi

/** Loads the signed-in account's real trips/itinerary from the backend and syncs local edits back. */
object TripSyncRepository {

    /** Most recently created trip for the account, or null if it has none yet. */
    suspend fun fetchLatestTrip(): TripDto? =
        tripApi.getTrips().maxByOrNull { it.id }

    fun toActivities(trip: TripDto): List<TripActivity> =
        (trip.schedules ?: emptyList()).map { schedule ->
            TripActivity(
                id = schedule.id.toString(),
                title = schedule.destination.name,
                category = schedule.activityType ?: "Visit",
                day = schedule.tripDay?.daySequence ?: 1,
                startTime = schedule.startTime?.take(5) ?: "09:00",
                durationMinutes = schedule.plannedDurationMinutes ?: 90,
                address = schedule.destination.name,
                latitude = schedule.destination.latitude ?: 0.0,
                longitude = schedule.destination.longitude ?: 0.0
            )
        }

    /**
     * Ports Frontend_Web's TripContext.saveTripEdits diffing to mobile: deletes schedules that
     * were removed locally, creates the ones added via "Add activity" (they only exist as local
     * "custom-<id>" items until now), then persists the final order/day/time for everything else.
     */
    suspend fun syncEdits(tripId: Long, activities: List<TripActivity>, baseline: List<ScheduleDto>) {
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
                val daySchedules = (response.schedules ?: emptyList())
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
    }
}
