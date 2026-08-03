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
    fun loginImportAndBuildTrip_completesPlanningFlow() {
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.onNodeWithText("AI TRIP PLANNER").assertIsDisplayed()

        composeRule.onNodeWithText("Start planning")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Turn notes into a trip").assertIsDisplayed()

        composeRule.onNodeWithText("Sample").performClick()
        composeRule.onNodeWithText("Extract places and build trip").performClick()
        composeRule.onNodeWithText("Route ready").assertIsDisplayed()
        composeRule.onNodeWithText("Wat Chedi Luang").assertIsDisplayed()
    }
}
