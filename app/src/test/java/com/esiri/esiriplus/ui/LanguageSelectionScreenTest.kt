package com.esiri.esiriplus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.esiri.esiriplus.feature.auth.screen.LanguageSelectionScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LanguageSelectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays Swahili and English buttons`() {
        composeTestRule.setContent {
            LanguageSelectionScreen(onContinue = {})
        }

        composeTestRule.onNodeWithText("Chagua lugha >").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose your Language >").assertIsDisplayed()
    }

    @Test
    fun `clicking Swahili button triggers onContinue`() {
        var clickCount = 0
        composeTestRule.setContent {
            LanguageSelectionScreen(onContinue = { clickCount++ })
        }

        composeTestRule.onNodeWithText("Chagua lugha >").performClick()
        assertEquals(1, clickCount)
    }

    @Test
    fun `clicking English button triggers onContinue`() {
        var clickCount = 0
        composeTestRule.setContent {
            LanguageSelectionScreen(onContinue = { clickCount++ })
        }

        composeTestRule.onNodeWithText("Choose your Language >").performClick()
        assertEquals(1, clickCount)
    }
}
