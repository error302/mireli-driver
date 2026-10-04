package com.mireli.driver

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PreviewTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun previewIsClearlyLabelledAndNavigationWorks() {
        rule.onNodeWithText("PREVIEW", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Trips", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Your trips").assertIsDisplayed()
        rule.onNodeWithText("Earnings", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Payouts are not connected").assertIsDisplayed()
    }
}
