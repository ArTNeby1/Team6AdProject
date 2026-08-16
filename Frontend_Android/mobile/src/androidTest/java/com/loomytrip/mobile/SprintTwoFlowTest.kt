package com.loomytrip.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.network.TripRouteDto
import com.loomytrip.mobile.ui.screen.EditTripScreen
import com.loomytrip.mobile.ui.screen.MapScreen
import com.loomytrip.mobile.ui.screen.RouteScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SprintTwoFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun itineraryEditAndMap_completesSprintTwoUiFlow() {
        val stage = mutableIntStateOf(0)
        val activities = mutableStateListOf(
            TripActivity("1", "Merlion Park", "Landmark", 1, "09:00", 60, "One Fullerton", 1.2868, 103.8545),
            TripActivity("2", "Gardens by the Bay", "Garden", 2, "10:00", 90, "Marina Gardens Drive", 1.2816, 103.8636)
        )

        composeRule.setContent {
            MaterialTheme {
                when (stage.intValue) {
                    0 -> RouteScreen(
                        activities = activities,
                        tripName = "Singapore Weekend",
                        startDate = "2026-08-15",
                        tripStatus = "NOT_STARTED",
                        totalDays = 2,
                        onViewMap = { stage.intValue = 2 },
                        onEdit = { stage.intValue = 1 }
                    )
                    1 -> EditTripScreen(
                        activities = activities,
                        initialDay = 2,
                        totalDays = 2,
                        onReorder = { id, targetDay, targetIndex ->
                            val moved = activities.first { it.id == id }
                            activities.remove(moved)
                            val dayActivities = activities.filter { it.day == targetDay }
                            val insertionIndex = if (targetIndex >= dayActivities.size) {
                                activities.size
                            } else {
                                activities.indexOf(dayActivities[targetIndex])
                            }
                            activities.add(insertionIndex, moved.copy(day = targetDay))
                        },
                        onDelete = { id -> activities.removeAll { it.id == id } },
                        onRestore = { activity, index ->
                            activities.add(index.coerceIn(0, activities.size), activity)
                        },
                        onAdd = { day, title, time ->
                            activities.add(
                                TripActivity(
                                    "custom",
                                    title,
                                    "Custom stop",
                                    day,
                                    time.ifBlank { "12:00" },
                                    60,
                                    "Address to be confirmed",
                                    1.30,
                                    103.85
                                )
                            )
                        },
                        onUpdateActivity = { id, startTime ->
                            val index = activities.indexOfFirst { it.id == id }
                            activities[index] = activities[index].copy(startTime = startTime)
                        },
                        onDeleteDay = {},
                        onAddDay = {},
                        onSave = { stage.intValue = 0 }
                    )
                    else -> MapScreen(
                        activities = activities,
                        initialDay = 2,
                        totalDays = 2,
                        routeSummary = TripRouteDto(
                            tripId = 1,
                            day = 2,
                            stopCount = 2,
                            totalDistanceKm = 4.8,
                            totalDurationMinutes = 22,
                            googleMapsUrl = "https://www.google.com/maps/dir/?api=1"
                        ),
                        onEdit = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Day 2").performClick()
        composeRule.onNodeWithText("Gardens by the Bay").assertIsDisplayed()
        composeRule.onNodeWithText("Edit day").performClick()
        composeRule.onNodeWithText("Add a stop to Day 2").performClick()
        composeRule.onNodeWithText("Activity name").performTextInput("Coffee workshop")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("Coffee workshop").assertIsDisplayed()
        composeRule.onNodeWithText("Save itinerary").performClick()
        composeRule.onNodeWithText("Day 2").performClick()
        composeRule.onNodeWithText("Coffee workshop").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("View Day 2 map").performClick()
        composeRule.onNodeWithText("4.8 km").assertIsDisplayed()
        composeRule.onNodeWithText("22 min").assertIsDisplayed()
        composeRule.onNodeWithText("Open Google Maps").assertIsEnabled()
    }

    @Test
    fun editTrip_canDeleteOneDayAfterConfirmation() {
        var deletedDay = 0
        composeRule.setContent {
            MaterialTheme {
                EditTripScreen(
                    activities = listOf(
                        TripActivity("1", "Merlion Park", "Landmark", 1, "09:00", 60, "One Fullerton", 1.2868, 103.8545),
                        TripActivity("2", "Gardens by the Bay", "Garden", 2, "10:00", 90, "Marina Gardens Drive", 1.2816, 103.8636)
                    ),
                    initialDay = 1,
                    totalDays = 2,
                    onReorder = { _, _, _ -> },
                    onDelete = {},
                    onRestore = { _, _ -> },
                    onAdd = { _, _, _ -> },
                    onUpdateActivity = { _, _ -> },
                    onDeleteDay = { deletedDay = it },
                    onAddDay = {},
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Delete Day 1").performClick()
        composeRule.onNodeWithText("Delete Day 1?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete day").performClick()

        assertEquals(1, deletedDay)
    }
}
