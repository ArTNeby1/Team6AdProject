package com.loomytrip.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalExploreRepositoryTest {

    @Test
    fun cityCatalog_returnsThreeUsefulDestinations() {
        val destinations = LocalExploreRepository.destinationsForCity("Chiang Mai")

        assertEquals(3, destinations.size)
        assertTrue(destinations.all { it.imageUrl.startsWith("https://") })
        assertTrue(destinations.all { it.sampleReviews.size >= 2 })
    }

    @Test
    fun destinationLookup_returnsFullDetail() {
        val destination = LocalExploreRepository.destination("kyoto-fushimi-inari")

        assertNotNull(destination)
        assertEquals("Kyoto", destination?.city)
        assertTrue(destination?.description?.isNotBlank() == true)
        assertTrue(destination?.tags?.isNotEmpty() == true)
    }

    @Test
    fun cityCatalog_ignoresLetterCase() {
        assertEquals(3, LocalExploreRepository.destinationsForCity("bali").size)
    }
}
