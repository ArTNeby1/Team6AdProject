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
    fun moveActivity_changesOrderOnlyWithinItsDay() {
        val original = repository.initialItinerary()
        val moved = repository.moveActivity(original, "tha-phae-gate", -1)

        assertEquals("Tha Phae Gate", moved.first().title)
        assertEquals("11:00", moved.first().startTime)
        assertEquals("09:00", moved[1].startTime)
        assertEquals(original.filter { it.day == 2 }, moved.filter { it.day == 2 })
    }

    @Test
    fun reorderActivity_movesAStopToAnotherDayAndKeepsItsTime() {
        val original = repository.initialItinerary()
        val moved = repository.reorderActivity(original, "tha-phae-gate", 2, 1)
        val activity = moved.first { it.id == "tha-phae-gate" }

        assertEquals(2, activity.day)
        assertEquals("11:00", activity.startTime)
        assertEquals("tha-phae-gate", moved.filter { it.day == 2 }[1].id)
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
    fun addActivity_movesConflictingTimeAfterTheLastStop() {
        val original = repository.initialItinerary()
        val added = repository.addActivity(original, 1, "Late snack", "10:00")

        assertEquals("20:15", added.first { it.title == "Late snack" }.startTime)
    }

    @Test
    fun addActivity_choosesTheNextAvailableTimeWhenTimeIsBlank() {
        val original = repository.initialItinerary()
        val added = repository.addActivity(original, 2, "Coffee workshop", "")

        assertEquals("16:15", added.first { it.title == "Coffee workshop" }.startTime)
    }

    @Test
    fun deleteDay_removesItsStopsAndMovesLaterDaysForward() {
        val original = repository.initialItinerary()
        val deleted = repository.deleteDay(original, 2)

        assertFalse(deleted.any { it.id == "wat-phra-that" || it.id == "bhubing-palace" })
        assertEquals(2, deleted.first { it.id == "elephant-nature-park" }.day)
        assertEquals(setOf(1, 2), deleted.map { it.day }.toSet())
    }
}
