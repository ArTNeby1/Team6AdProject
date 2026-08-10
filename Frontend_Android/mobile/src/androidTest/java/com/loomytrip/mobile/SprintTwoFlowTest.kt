package com.loomytrip.mobile

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SprintTwoFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun allowTripNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant com.loomytrip.mobile android.permission.POST_NOTIFICATIONS")
                .close()
        }
    }

    @Test
    fun itineraryEditAndMap_completesSprintTwoFlow() {
        composeRule.onNodeWithText("Continue with offline demo").performClick()
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
        composeRule.onNodeWithText("Day 2 · 16 Jul").assertIsDisplayed()
        composeRule.onNodeWithText("4 stops · 6 hr · 45 min travel").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("View Day 2 map").performClick()
        composeRule.onNodeWithText("Chiang Mai · Day 2 · 4 stops").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Route preview from saved coordinates. Navigation opens your map app."
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
