package com.loomytrip.mobile

import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SprintOneFlowTest {

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
    fun accountRegistrationScreen_acceptsValidInput() {
        composeRule.onNodeWithText("No account? Create one").performClick()
        composeRule.onNodeWithText("Create your account").assertIsDisplayed()

        composeRule.onNodeWithText("Display name").performTextInput("Bevis")
        composeRule.onNodeWithText("Email").performTextInput("bevis@example.com")
        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
        composeRule.onNodeWithText("Back to sign in").performClick()
        composeRule.onNodeWithText("Continue with offline demo").performClick()
        composeRule.onNodeWithText("AI TRIP PLANNER").assertIsDisplayed()
    }

    @Test
    fun loginImportAndBuildTrip_completesPlanningFlow() {
        composeRule.onNodeWithText("Continue with offline demo").performClick()
        composeRule.onNodeWithText("AI TRIP PLANNER").assertIsDisplayed()

        composeRule.onNodeWithText("Start planning")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Turn notes into a trip").assertIsDisplayed()

        composeRule.onNodeWithText("Sample").performClick()
        composeRule.onNodeWithText("Extract places").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Review extracted places"), 20_000)
        composeRule.onNodeWithText("Gardens by the Bay").assertIsDisplayed()

        composeRule.onNodeWithText("Confirm and optimise").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("AI route ready"), 20_000)
        composeRule.onNodeWithText("Weather outlook").assertIsDisplayed()
    }
}
