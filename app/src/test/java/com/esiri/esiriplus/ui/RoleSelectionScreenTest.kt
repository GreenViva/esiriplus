package com.esiri.esiriplus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.esiri.esiriplus.feature.auth.screen.RoleSelectionScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoleSelectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays welcome title and subtitle`() {
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("Welcome to eSIRI Plus").assertIsDisplayed()
        composeTestRule.onNodeWithText("How would you like to proceed?").assertIsDisplayed()
    }

    @Test
    fun `displays patient section`() {
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("FOR PATIENTS").assertIsDisplayed()
        composeTestRule.onNodeWithText("New to the platform?").assertIsDisplayed()
        composeTestRule.onNodeWithText("I have my ID").assertIsDisplayed()
    }

    @Test
    fun `displays doctor portal section`() {
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("DOCTOR PORTAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `clicking new patient triggers onPatientSelected`() {
        var clicked = false
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = { clicked = true },
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("New to the platform?").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `clicking I have my ID triggers onHaveMyId`() {
        var clicked = false
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("I have my ID").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `clicking Sign In triggers onDoctorSelected`() {
        var clicked = false
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = { clicked = true },
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("Sign In").performScrollTo().performClick()
        assertTrue(clicked)
    }

    @Test
    fun `clicking Sign Up triggers onDoctorRegister`() {
        var clicked = false
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = { clicked = true },
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("Sign Up").performScrollTo().performClick()
        assertTrue(clicked)
    }

    @Test
    fun `clicking forgot patient ID triggers onRecoverPatientId`() {
        var clicked = false
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = { clicked = true },
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("Forgot your Patient ID?").performScrollTo().performClick()
        assertTrue(clicked)
    }

    @Test
    fun `displays copyright footer`() {
        composeTestRule.setContent {
            RoleSelectionScreen(
                onPatientSelected = {},
                onDoctorSelected = {},
                onDoctorRegister = {},
                onRecoverPatientId = {},
                onHaveMyId = {},
            )
        }

        composeTestRule.onNodeWithText("\u00A9 2026 eSIRI Plus. All rights reserved.")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
