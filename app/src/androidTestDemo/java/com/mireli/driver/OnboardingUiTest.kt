package com.mireli.driver

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.mireli.driver.data.OnboardingStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun clearDraft() { OnboardingStore(ApplicationProvider.getApplicationContext()).clear() }
    private fun reveal(text: String) { rule.onNode(hasScrollAction()).performScrollToNode(hasText(text)) }
    private fun fill(label: String, text: String) { reveal(label); rule.onNodeWithText(label).performTextInput(text) }
    @Test fun driverCanSaveProfileSeeDocumentsAndReviewMissingEvidence() {
        reveal("Start driver onboarding")
        rule.onNodeWithText("Start driver onboarding").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Full name").fetchSemanticsNodes().isNotEmpty() }
        fill("Full name", "Sample Driver")
        fill("Kenyan mobile number", "0712345678")
        fill("Vehicle registration", "KAA123A")
        fill("Passenger capacity", "10")
        fill("Driving licence class", "D1")
        val notice = "I understand this is a local test draft. I will use sample documents; nothing is submitted or approved."
        reveal(notice); rule.onNodeWithText(notice).performClick()
        reveal("Save driver & vehicle"); rule.onNodeWithText("Save driver & vehicle").performClick()
        rule.waitUntil(15000) { rule.onAllNodes(hasText("Documents") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Documents").performClick()
        reveal("National ID or passport")
        rule.onNodeWithText("National ID or passport").assertExists()
        rule.onNodeWithText("Review application").performClick()
        rule.onNodeWithText("Check application").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Needs attention").fetchSemanticsNodes().isNotEmpty() }
        reveal("Needs attention")
        rule.onNodeWithText("Needs attention").assertIsDisplayed()
        val saved = OnboardingStore(rule.activity).load()
        org.junit.Assert.assertEquals("Sample Driver", saved.fullName)
        org.junit.Assert.assertNull(saved.checkedAt)
    }
}
