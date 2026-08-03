package com.loomytrip.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPlanningRepositoryTest {

    private val repository = MockPlanningRepository()

    @Test
    fun blankSource_returnsNoPlaces() {
        assertTrue(repository.extractPlaces(" ").isEmpty())
    }

    @Test
    fun travelText_returnsReviewablePlaces() {
        val places = repository.extractPlaces("Three days in Chiang Mai")

        assertEquals(4, places.size)
        assertEquals("Wat Chedi Luang", places.first().name)
        assertTrue(places.all { it.isIncluded })
    }
}
