package com.loomytrip.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class SprintTwoFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun itineraryEditAndMap_completesSprintTwoFlow() {
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.onNodeWithText("Trips").performClick()

        composeRule.onNodeWithText("Chiang Mai").assertIsDisplayed()
        composeRule.onNodeWithText("Wat Chedi Luang").assertIsDisplayed()

        composeRule.onNodeWithText("Day 2").performClick()
        composeRule.onNodeWithText("Wat Phra That Doi Suthep").assertIsDisplayed()
        composeRule.onNodeWithText("Edit day").performClick()

        composeRule.onNodeWithText("Add a stop to Day 2").performClick()
        composeRule.onNodeWithText("Activity name").performTextInput("Coffee workshop")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("Coffee workshop").assertIsDisplayed()

        composeRule.onNodeWithText("Save itinerary").performClick()
        composeRule.onNodeWithText("Day 2").performClick()
        composeRule.onNodeWithText("Day 2 plan").assertIsDisplayed()
        composeRule.onNodeWithText("4 stops  •  6 hr").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("View Day 2 map").performClick()
        composeRule.onNodeWithText("Chiang Mai · Day 2 · 4 stops").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Drag to move. Pinch to zoom. Tap a stop for details."
        ).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Choose trip. Currently Chiang Mai").performClick()
        composeRule.onNodeWithText("Bangkok Weekend").performClick()
        composeRule.onNodeWithText("Bangkok Weekend · Day 1 · 3 stops").assertIsDisplayed()
        composeRule.onNodeWithText("The Grand Palace").assertIsDisplayed()

        composeRule.onNodeWithText("Open itinerary").performClick()
        composeRule.onNodeWithText("Bangkok Weekend").assertIsDisplayed()
        composeRule.onNodeWithText("The Grand Palace").assertIsDisplayed()
    }
}
