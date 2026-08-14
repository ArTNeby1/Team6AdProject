package com.loomytrip.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.loomytrip.mobile.data.model.ExtractedPlace
import com.loomytrip.mobile.ui.screen.ImportGuideScreen
import com.loomytrip.mobile.ui.screen.RegisterScreen
import com.loomytrip.mobile.ui.screen.ReviewExtractedScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SprintOneFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountRegistration_submitsEnteredAccount() {
        var submittedName = ""
        var submittedEmail = ""
        var submittedPassword = ""
        composeRule.setContent {
            MaterialTheme {
                RegisterScreen(
                    onRegister = { name, email, password ->
                        submittedName = name
                        submittedEmail = email
                        submittedPassword = password
                    },
                    onBackToLogin = {}
                )
            }
        }

        composeRule.onNodeWithText("Display name").performTextInput("Bevis")
        composeRule.onNodeWithText("Email").performTextInput("bevis@example.com")
        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Create account").performClick()

        assertEquals("Bevis", submittedName)
        assertEquals("bevis@example.com", submittedEmail)
        assertEquals("secret123", submittedPassword)
    }

    @Test
    fun importAndReview_completesSprintOneUiFlow() {
        val stage = mutableIntStateOf(0)
        var parsedText = ""
        var confirmed = false
        val places = listOf(
            ExtractedPlace("1", "Gardens by the Bay", "Garden", "Marina Gardens Drive", "09:00"),
            ExtractedPlace("2", "Chinatown", "District", "Singapore", "13:00")
        )

        composeRule.setContent {
            MaterialTheme {
                if (stage.intValue == 0) {
                    ImportGuideScreen(
                        onExtract = {
                            parsedText = it
                            stage.intValue = 1
                        }
                    )
                } else {
                    ReviewExtractedScreen(
                        places = places,
                        onIncludedChange = { _, _ -> },
                        onConfirm = { confirmed = true },
                        onImportAgain = { stage.intValue = 0 }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Start Parsing").performClick()
        composeRule.onNodeWithText("Review extracted places").assertIsDisplayed()
        composeRule.onNodeWithText("Gardens by the Bay").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm itinerary").performClick()

        assertTrue(parsedText.isNotBlank())
        assertTrue(confirmed)
    }
}
