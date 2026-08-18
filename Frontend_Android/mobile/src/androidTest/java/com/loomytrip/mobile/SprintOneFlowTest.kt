package com.loomytrip.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
        var submittedAge = 0
        var submittedGender = ""
        composeRule.setContent {
            MaterialTheme {
                RegisterScreen(
                    onRegister = { name, email, password, age, gender ->
                        submittedName = name
                        submittedEmail = email
                        submittedPassword = password
                        submittedAge = age
                        submittedGender = gender
                    },
                    onBackToLogin = {}
                )
            }
        }

        composeRule.onNodeWithText("Display name").performTextInput("Bevis")
        composeRule.onNodeWithText("Email").performTextInput("bevis@example.com")
        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Age").performTextInput("24")
        composeRule.onNodeWithText("Female").performClick()
        composeRule.onNodeWithText("Create account").performClick()

        assertEquals("Bevis", submittedName)
        assertEquals("bevis@example.com", submittedEmail)
        assertEquals("secret123", submittedPassword)
        assertEquals(24, submittedAge)
        assertEquals("Female", submittedGender)
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

    @Test
    fun reviewScreen_submitsRefinementAndPlaceRename() {
        val refinementText = mutableStateOf("")
        var submittedRefinement = ""
        var renamedPlace = ""
        val places = listOf(
            ExtractedPlace("7", "Merlion Park", "Landmark", "One Fullerton", "Located")
        )

        composeRule.setContent {
            MaterialTheme {
                ReviewExtractedScreen(
                    places = places,
                    refinementText = refinementText.value,
                    onRefinementTextChange = { refinementText.value = it },
                    onRefine = { submittedRefinement = it },
                    onRenamePlace = { _, name -> renamedPlace = name },
                    onIncludedChange = { _, _ -> },
                    onConfirm = {},
                    onImportAgain = {}
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Located").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("Add another instruction").performTextInput("Add a garden")
        composeRule.onNodeWithText("Update places with AI").performClick()
        assertEquals("Add a garden", submittedRefinement)

        composeRule.onNodeWithText("Merlion Park").performTextReplacement("Merlion Waterfront")
        composeRule.onNodeWithText("Save name").performClick()
        assertEquals("Merlion Waterfront", renamedPlace)
    }

    @Test
    fun invalidTravelInput_showsDedicatedDialog() {
        composeRule.setContent {
            MaterialTheme {
                ImportGuideScreen(
                    onExtract = {},
                    invalidInputMessage = "Please include a destination or attraction."
                )
            }
        }

        composeRule.onNodeWithText("No travel information found").assertIsDisplayed()
        composeRule.onNodeWithText("Please include a destination or attraction.").assertIsDisplayed()
        composeRule.onNodeWithText("Edit my input").assertIsDisplayed()
    }

    @Test
    fun reviewScreen_selectsDurationAndAssignsPlaceToDay() {
        val duration = mutableIntStateOf(3)
        val showDuration = mutableStateOf(true)
        var assignedDay = 0

        composeRule.setContent {
            MaterialTheme {
                ReviewExtractedScreen(
                    places = listOf(
                        ExtractedPlace("9", "Merlion Park", "Landmark", "One Fullerton", "Located")
                    ),
                    durationDays = duration.intValue,
                    showDurationDialog = showDuration.value,
                    onDurationConfirmed = {
                        duration.intValue = it
                        showDuration.value = false
                    },
                    onDismissDurationDialog = { showDuration.value = false },
                    onDayChange = { _, day -> assignedDay = day },
                    onIncludedChange = { _, _ -> },
                    onConfirm = {},
                    onImportAgain = {}
                )
            }
        }

        composeRule.onNodeWithText("How many days is this trip?").assertIsDisplayed()
        composeRule.onAllNodesWithText("+")[1].performClick()
        composeRule.onNodeWithText("Use 4 days").performClick()
        assertEquals(4, duration.intValue)

        composeRule.onNodeWithText("Choose day").performClick()
        composeRule.onNodeWithText("Day 2").performClick()
        assertEquals(2, assignedDay)
    }
}
