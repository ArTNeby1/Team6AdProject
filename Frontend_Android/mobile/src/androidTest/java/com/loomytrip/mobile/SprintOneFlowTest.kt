package com.loomytrip.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class SprintOneFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun accountRegistration_opensHome() {
        composeRule.onNodeWithText("Create an account").performClick()
        composeRule.onNodeWithText("Create your account").assertIsDisplayed()

        composeRule.onNodeWithText("Display name").performTextInput("Bevis")
        composeRule.onNodeWithText("Email").performTextInput("bevis@example.com")
        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Create account").performClick()

        composeRule.onNodeWithText("AI TRIP PLANNER").assertIsDisplayed()
    }

    @Test
    fun loginImportAndReview_completesSprintOneFlow() {
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.onNodeWithText("AI TRIP PLANNER").assertIsDisplayed()

        composeRule.onNodeWithText("Start planning")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Import a travel guide").assertIsDisplayed()

        composeRule.onNodeWithText("Extract places with AI").performClick()
        composeRule.onNodeWithText("Review extracted places").assertIsDisplayed()
        composeRule.onNodeWithText("Wat Chedi Luang").assertIsDisplayed()
        composeRule.onNodeWithText("4 of 4 places selected. Remove anything that should not enter the itinerary.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Confirm itinerary").performClick()
        composeRule.onNodeWithText("Chiang Mai · 3 days").assertIsDisplayed()
    }
}
