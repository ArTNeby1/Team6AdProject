package com.loomytrip.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.MapConfigDto
import com.loomytrip.mobile.data.network.TripDto
import com.loomytrip.mobile.data.network.TripRouteDto
import com.loomytrip.mobile.ui.screen.HomeScreen
import com.loomytrip.mobile.ui.screen.MapScreen
import com.loomytrip.mobile.ui.screen.MapTripOption
import com.loomytrip.mobile.ui.screen.RouteScreen
import com.loomytrip.mobile.ui.screen.TripsListScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MobileWebParityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun viewAll_opensMyItinerariesAction() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onStartPlanning = {},
                    onExploreCity = {},
                    onViewTrip = {},
                    onViewAllTrips = { clicked = true },
                    tripName = "Singapore Weekend",
                    tripDayCount = 2,
                    tripStopCount = 4
                )
            }
        }

        composeRule.onNodeWithText("View all").performClick()

        assertTrue(clicked)
    }

    @Test
    fun myItineraries_displaysBackendTrips() {
        composeRule.setContent {
            MaterialTheme {
                TripsListScreen(
                    trips = listOf(
                        TripDto(
                            id = 1,
                            tripName = "Singapore Weekend",
                            startDate = "2026-08-15",
                            durationDays = 2,
                            status = "NOT_STARTED"
                        )
                    ),
                    isLoading = false,
                    errorMessage = null,
                    onRefresh = {},
                    onStartPlanning = {},
                    onTripSelected = {},
                    onDeleteTrip = {}
                )
            }
        }

        composeRule.onNodeWithText("My Itineraries").assertIsDisplayed()
        composeRule.onNodeWithText("Singapore Weekend").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
    }

    @Test
    fun mapScreen_switchesBetweenTrips() {
        var selectedTripId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                MapScreen(
                    activities = listOf(
                        TripActivity(
                            id = "1",
                            title = "Merlion Park",
                            category = "Landmark",
                            day = 1,
                            startTime = "09:00",
                            durationMinutes = 60,
                            address = "One Fullerton",
                            latitude = 1.2868,
                            longitude = 103.8545
                        )
                    ),
                    initialDay = 1,
                    totalDays = 1,
                    tripOptions = listOf(
                        MapTripOption(1, "Singapore Weekend"),
                        MapTripOption(2, "Second trip")
                    ),
                    selectedTripId = 1,
                    onTripSelected = { selectedTripId = it },
                    onEdit = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose itinerary").assertIsDisplayed()
        composeRule.onNodeWithText("Second trip").performClick()

        assertEquals(2L, selectedTripId)
    }

    @Test
    fun mapScreen_displaysBackendRouteAndGoogleMapsAction() {
        composeRule.setContent {
            MaterialTheme {
                MapScreen(
                    activities = listOf(
                        TripActivity(
                            id = "1",
                            title = "Merlion Park",
                            category = "Landmark",
                            day = 1,
                            startTime = "09:00",
                            durationMinutes = 60,
                            address = "One Fullerton",
                            latitude = 1.2868,
                            longitude = 103.8545
                        )
                    ),
                    initialDay = 1,
                    totalDays = 1,
                    mapConfig = MapConfigDto(),
                    routeSummary = TripRouteDto(
                        tripId = 1,
                        day = 1,
                        stopCount = 1,
                        totalDistanceKm = 1.2,
                        totalDurationMinutes = 14,
                        googleMapsUrl = "https://www.google.com/maps/dir/?api=1"
                    ),
                    onEdit = {}
                )
            }
        }

        composeRule.onNodeWithText("1.2 km").assertIsDisplayed()
        composeRule.onNodeWithText("14 min").assertIsDisplayed()
        composeRule.onNodeWithText("Open Google Maps").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Leaflet trip map").assertIsDisplayed()
    }

    @Test
    fun finishedItinerary_showsButLocksStartDate() {
        composeRule.setContent {
            MaterialTheme {
                RouteScreen(
                    activities = emptyList(),
                    tripName = "Completed Singapore Trip",
                    startDate = "2026-07-01",
                    tripStatus = "FINISHED",
                    totalDays = 1,
                    onViewMap = {},
                    onEdit = {}
                )
            }
        }

        composeRule.onNodeWithText("Start date: 2026-07-01").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Change start date").assertIsNotEnabled()
    }
}
