package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.network.DestinationDto
import com.loomytrip.mobile.data.network.ScheduleDto
import com.loomytrip.mobile.data.network.TripDayDto
import com.loomytrip.mobile.data.network.TripDto
import org.junit.Assert.assertEquals
import org.junit.Test

class TripSyncRepositoryTest {

    @Test
    fun toActivities_usesBackendDayOrderAndScheduleDetails() {
        val trip = TripDto(
            id = 7,
            tripName = "Singapore Weekend",
            durationDays = 2,
            schedules = listOf(
                schedule(id = 22, name = "Chinatown", day = 2, sequence = 1, time = "10:30:00"),
                schedule(id = 11, name = "Marina Bay", day = 1, sequence = 2, time = "13:00:00"),
                schedule(id = 10, name = "Gardens by the Bay", day = 1, sequence = 1, time = "09:00:00")
            )
        )

        val activities = TripSyncRepository.toActivities(trip)

        assertEquals(listOf("Gardens by the Bay", "Marina Bay", "Chinatown"), activities.map { it.title })
        assertEquals(listOf(1, 1, 2), activities.map { it.day })
        assertEquals("09:00", activities.first().startTime)
        assertEquals("Singapore", activities.first().address)
    }

    @Test
    fun sortForDisplay_matchesWebStatusAndDateOrder() {
        val trips = listOf(
            trip(id = 1, status = "FINISHED", date = "2026-07-01"),
            trip(id = 2, status = "NOT_STARTED", date = "2026-09-10"),
            trip(id = 3, status = "ACTIVE", date = "2026-08-12"),
            trip(id = 4, status = "FINISHED", date = "2026-08-01"),
            trip(id = 5, status = "NOT_STARTED", date = "2026-08-20")
        )

        val sorted = TripSyncRepository.sortForDisplay(trips)

        assertEquals(listOf(3L, 5L, 2L, 4L, 1L), sorted.map { it.id })
    }

    private fun trip(id: Long, status: String, date: String) = TripDto(
        id = id,
        tripName = "Trip $id",
        startDate = date,
        status = status,
        durationDays = 1
    )

    private fun schedule(
        id: Long,
        name: String,
        day: Int,
        sequence: Int,
        time: String
    ) = ScheduleDto(
        id = id,
        destination = DestinationDto(
            id = id,
            name = name,
            address = "Singapore",
            latitude = 1.28,
            longitude = 103.85,
            category = "Attraction"
        ),
        tripDay = TripDayDto(daySequence = day),
        sequence = sequence,
        startTime = time,
        plannedDurationMinutes = 90
    )
}
