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
import com.loomytrip.mobile.data.network.TripTransportDto
import com.loomytrip.mobile.data.network.SuggestedAdditionDto
import com.loomytrip.mobile.data.network.UserProfileDto
import com.loomytrip.mobile.ui.ProfileScreen
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
    fun profile_allowsEditingAndTripsNavigation() {
        var tripsClicked = false
        composeRule.setContent {
            MaterialTheme {
                ProfileScreen(
                    email = "traveler@example.com",
                    profile = UserProfileDto(1, "Traveler", "traveler@example.com", 24, "Female", null, null),
                    tripCount = 3,
                    isLoading = false,
                    isSaving = false,
                    errorMessage = null,
                    onRetry = {},
                    onTripsClick = { tripsClicked = true },
                    onSaveProfile = { _, _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithText("Trips").performClick()
        assertTrue(tripsClicked)
        composeRule.onNodeWithContentDescription("Edit personal information").performClick()
        composeRule.onNodeWithText("Edit personal information").assertIsDisplayed()
        composeRule.onNodeWithText("Email is used for sign-in and cannot be changed here.").assertIsDisplayed()
    }

    @Test
    fun viewAll_opensMyItinerariesAction() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onStartPlanning = {},
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

    @Test
    fun confirmedItinerary_keepsAiNotesCompactAndAddsSuggestion() {
        var addedSuggestion = ""
        var addedDay = 0
        composeRule.setContent {
            MaterialTheme {
                RouteScreen(
                    activities = emptyList(),
                    tripName = "Singapore Highlights",
                    startDate = "2026-08-20",
                    tripStatus = "NOT_STARTED",
                    totalDays = 2,
                    aiWeatherSummary = "Light rain is expected in the afternoon.",
                    suggestedAdditions = listOf(
                        SuggestedAdditionDto(
                            name = "Marina Barrage",
                            distanceKm = 1.4,
                            reason = "Close to the confirmed route"
                        )
                    ),
                    onViewMap = {},
                    onEdit = {},
                    onAddSuggestedPlace = { suggestion, day ->
                        addedSuggestion = suggestion.name
                        addedDay = day
                    }
                )
            }
        }

        composeRule.onNodeWithText("AI trip notes").assertIsDisplayed()
        composeRule.onNodeWithText("Light rain is expected in the afternoon. · 1 nearby idea").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("View AI trip notes").performClick()
        composeRule.onNodeWithText("Light rain is expected in the afternoon.").assertIsDisplayed()
        composeRule.onNodeWithText("Marina Barrage").assertIsDisplayed()
        composeRule.onNodeWithText("1.4 km · Close to the confirmed route").assertIsDisplayed()
        composeRule.onNodeWithText("Add to Day 1").performClick()
        assertEquals("Marina Barrage", addedSuggestion)
        assertEquals(1, addedDay)
    }

    @Test
    fun itinerary_displaysFourBackendTransportOptions() {
        composeRule.setContent {
            MaterialTheme {
                RouteScreen(
                    activities = listOf(
                        TripActivity("10", "Merlion Park", "Landmark", 1, "09:00", 60, "One Fullerton", 1.2868, 103.8545),
                        TripActivity("20", "Gardens by the Bay", "Garden", 1, "11:00", 90, "Marina Gardens Drive", 1.2816, 103.8636)
                    ),
                    tripName = "Singapore Day",
                    startDate = "2026-08-20",
                    tripStatus = "NOT_STARTED",
                    totalDays = 1,
                    routeSummary = TripRouteDto(
                        tripId = 1,
                        day = 1,
                        stopCount = 2,
                        transports = listOf(
                            TripTransportDto(1, 10, 20, transportType = "transit", distanceKm = 2.0, durationMinutes = 12, approximate = true),
                            TripTransportDto(2, 10, 20, transportType = "driving", distanceKm = 2.0, durationMinutes = 7),
                            TripTransportDto(3, 10, 20, transportType = "bicycling", distanceKm = 2.0, durationMinutes = 9),
                            TripTransportDto(4, 10, 20, transportType = "walking", distanceKm = 2.0, durationMinutes = 25)
                        )
                    ),
                    onViewMap = {},
                    onEdit = {}
                )
            }
        }

        composeRule.onNodeWithText("Public transport").assertIsDisplayed()
        composeRule.onNodeWithText("Driving").assertIsDisplayed()
        composeRule.onNodeWithText("Cycling").assertIsDisplayed()
        composeRule.onNodeWithText("Walking").assertIsDisplayed()
        composeRule.onNodeWithText("Approx. 12 min · 2.0 km").assertIsDisplayed()
    }
}
