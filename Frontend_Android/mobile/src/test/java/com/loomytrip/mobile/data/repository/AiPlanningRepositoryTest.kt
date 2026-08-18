package com.loomytrip.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiPlanningRepositoryTest {

    @Test
    fun planningErrorParser_readsNoUsefulContentCode() {
        val error = parsePlanningApiError(
            """{"code":"NO_USEFUL_CONTENT","message":"No destination was found","details":[]}"""
        )

        assertEquals("NO_USEFUL_CONTENT", error?.code)
        assertEquals("No destination was found", error?.message)
    }

    @Test
    fun planningErrorParser_ignoresMalformedResponse() {
        assertNull(parsePlanningApiError("gateway timeout"))
    }

    @Test
    fun distributedDay_splitsPlacesAcrossRequestedDays() {
        val assignments = (0 until 6).map { index ->
            AiPlanningRepository.distributedDay(index, placeCount = 6, durationDays = 3)
        }

        assertEquals(listOf(1, 1, 2, 2, 3, 3), assignments)
    }
}
