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
}
