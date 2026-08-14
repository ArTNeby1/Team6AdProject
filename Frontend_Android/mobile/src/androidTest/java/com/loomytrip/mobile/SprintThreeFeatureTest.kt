package com.loomytrip.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.PlanningSessionSummaryDto
import com.loomytrip.mobile.data.repository.LocalExploreRepository
import com.loomytrip.mobile.ui.screen.DestinationDetailScreen
import com.loomytrip.mobile.ui.screen.ImportGuideScreen
import com.loomytrip.mobile.ui.screen.RouteScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SprintThreeFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun destinationDetail_showsLocalPhotosInformationAndReviews() {
        val destination = checkNotNull(
            LocalExploreRepository.destination("chiang-mai-wat-chedi-luang")
        )

        composeRule.setContent {
            MaterialTheme {
                DestinationDetailScreen(destination = destination, onPlanDestination = {})
            }
        }

        composeRule.onNodeWithText("Wat Chedi Luang").assertIsDisplayed()
        composeRule.onNodeWithText("Popular choice").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(3)
        composeRule.onNodeWithText("Traveler reviews").assertIsDisplayed()
        composeRule.onNodeWithText("Write").assertIsDisplayed()
    }

    @Test
    fun tripPreferences_areSavedForTheOpenTripOnly() {
        var savedStyle = ""
        var savedTransport = ""

        composeRule.setContent {
            MaterialTheme {
                RouteScreen(
                    activities = emptyList(),
                    tripName = "Kyoto Test",
                    startDate = "2026-08-20",
                    tripStatus = "NOT_STARTED",
                    totalDays = 1,
                    travelStyle = "Balanced",
                    preferTransport = "Public transport",
                    onViewMap = {},
                    onEdit = {},
                    onSavePreferences = { style, transport ->
                        savedStyle = style
                        savedTransport = transport
                    }
                )
            }
        }

        composeRule.onNodeWithText("Preferences for this trip").performClick()
        composeRule.onNodeWithText("Relaxed").performClick()
        composeRule.onNodeWithText("Save").performClick()

        assertEquals("Relaxed", savedStyle)
        assertEquals("Public transport", savedTransport)
    }

    @Test
    fun itineraryName_canBeChangedAfterPlanning() {
        var savedName = ""
        composeRule.setContent {
            MaterialTheme {
                RouteScreen(
                    activities = emptyList(),
                    tripName = "Singapore Draft",
                    startDate = "2026-08-20",
                    tripStatus = "NOT_STARTED",
                    totalDays = 1,
                    onViewMap = {},
                    onEdit = {},
                    onTripNameChange = { savedName = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rename itinerary").performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Singapore Graduation Trip")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals("Singapore Graduation Trip", savedName)
    }

    @Test
    fun smartReorder_requiresConfirmationBeforeCallingBackendAction() {
        var generated = false
        composeRule.setContent {
            MaterialTheme {
                RouteScreen(
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
                    tripName = "Singapore Test",
                    startDate = "2026-08-20",
                    tripStatus = "NOT_STARTED",
                    totalDays = 1,
                    onViewMap = {},
                    onEdit = {},
                    onSmartReorder = { generated = true }
                )
            }
        }

        composeRule.onNodeWithText("Smart reorder with AI").performClick()
        assertTrue(!generated)
        composeRule.onNodeWithText("Reorganize").performClick()
        assertTrue(generated)
    }

    @Test
    fun planningHistory_opensActiveDraftAndLabelsDeletedTrip() {
        var selectedId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                ImportGuideScreen(
                    onExtract = {},
                    history = listOf(
                        PlanningSessionSummaryDto(
                            id = 9,
                            title = "Previous Kyoto notes",
                            initialBrief = "Temples and markets",
                            status = "EXTRACTED",
                            updatedAt = "2026-08-14T09:00:00"
                        ),
                        PlanningSessionSummaryDto(
                            id = 8,
                            title = "Old Singapore plan",
                            initialBrief = "A completed import",
                            status = "CONFIRMED",
                            confirmedTripId = null,
                            updatedAt = "2026-08-13T09:00:00"
                        )
                    ),
                    onHistorySelected = { selectedId = it.id }
                )
            }
        }

        composeRule.onNodeWithText("Previous AI imports (2)").performClick()
        composeRule.onNodeWithText("Trip deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Previous Kyoto notes").performClick()
        assertEquals(9L, selectedId)
    }
}
