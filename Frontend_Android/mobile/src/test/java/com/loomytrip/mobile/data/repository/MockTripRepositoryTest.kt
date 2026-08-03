package com.loomytrip.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockTripRepositoryTest {
    private val repository = MockTripRepository()

    @Test
    fun itinerary_containsThreeOrderedDays() {
        val activities = repository.initialItinerary()

        assertEquals(setOf(1, 2, 3), activities.map { it.day }.toSet())
        assertEquals("Wat Chedi Luang", activities.first().title)
    }

    @Test
    fun savedTrips_containsRoutesForMapSelection() {
        val trips = repository.savedTrips()

        assertEquals(listOf("Chiang Mai", "Bangkok Weekend", "Phuket Coast"), trips.map { it.title })
        assertEquals(2, trips.first { it.id == "bangkok-weekend" }.totalDays)
    }

    @Test
    fun moveActivity_changesOrderOnlyWithinItsDay() {
        val original = repository.initialItinerary()
        val moved = repository.moveActivity(original, "tha-phae-gate", -1)

        assertEquals("Tha Phae Gate", moved.first().title)
        assertEquals(original.filter { it.day == 2 }, moved.filter { it.day == 2 })
    }

    @Test
    fun addAndDeleteActivity_updatesItinerary() {
        val original = repository.initialItinerary()
        val added = repository.addActivity(original, 2, "Coffee workshop", "16:30")
        val custom = added.first { it.title == "Coffee workshop" }

        assertEquals(2, custom.day)
        assertTrue(added.size == original.size + 1)

        val deleted = repository.deleteActivity(added, custom.id)
        assertFalse(deleted.any { it.id == custom.id })
        assertEquals(original.size, deleted.size)
    }

    @Test
    fun reorderActivity_movesStopAcrossDaysAtRequestedPosition() {
        val original = repository.initialItinerary()
        val moved = repository.reorderActivity(
            activities = original,
            id = "sunday-market",
            targetDay = 2,
            targetIndex = 1
        )

        assertFalse(moved.filter { it.day == 1 }.any { it.id == "sunday-market" })
        assertEquals("sunday-market", moved.filter { it.day == 2 }[1].id)
        assertEquals(2, moved.first { it.id == "sunday-market" }.day)
    }

    @Test
    fun restoreActivity_returnsDeletedStopToOriginalDay() {
        val original = repository.initialItinerary()
        val activity = original.first { it.id == "tha-phae-gate" }
        val deleted = repository.deleteActivity(original, activity.id)
        val restored = repository.restoreActivity(deleted, activity, targetIndex = 1)

        assertEquals(activity, restored.filter { it.day == 1 }[1])
        assertEquals(original.size, restored.size)
    }
}
