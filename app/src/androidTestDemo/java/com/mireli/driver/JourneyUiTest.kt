package com.mireli.driver

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class JourneyUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun reveal(text: String, substring: Boolean = false) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }
    private fun click(text: String) { reveal(text); rule.onNodeWithText(text).performClick() }
    private fun waitFor(text: String) {
        rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitSaved() {
        waitFor("Preview updated")
        rule.waitUntil(15000) { rule.onAllNodesWithText("Preview updated").fetchSemanticsNodes().isEmpty() }
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val dir = File(rule.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, name + ".png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun driverCanCompleteSharedJourney() {
        rule.onNodeWithText("Account", useUnmergedTree = true).performClick()
        click("Reset sample trips")
        waitFor("Preview trips reset")
        rule.waitUntil(15000) { rule.onAllNodesWithText("Preview trips reset").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Today", useUnmergedTree = true).performClick()
        screenshot("today")
        reveal("SGR-1042", substring = true)
        rule.onNodeWithText("SGR-1042", substring = true).performClick()
        click("Accept assignment")
        waitFor("Confirm")
        rule.onNodeWithText("Confirm").performClick()
        awaitSaved()
        click("I have arrived")
        rule.onNodeWithText("Confirm").performClick()
        awaitSaved()
        reveal("Passenger manifest")
        screenshot("manifest")
        listOf("1042", "2042", "3042").forEach { code ->
            reveal("Sample boarding code: " + code)
            rule.onAllNodesWithText("Board party").onFirst().performScrollTo().performClick()
            rule.onNodeWithText("Boarding code").performTextInput(code)
            rule.onNodeWithText("Confirm boarding").let {
                rule.onAllNodesWithText("Confirm boarding").onLast().performClick()
            }
            awaitSaved()
            rule.waitUntil(15000) {
                rule.onAllNodesWithText("Sample boarding code: " + code).fetchSemanticsNodes().isEmpty()
            }
        }
        click("Start journey")
        rule.onNodeWithText("Confirm").performClick()
        awaitSaved()
        reveal("Complete journey")
        screenshot("active-trip")
        click("Complete journey")
        rule.onNodeWithText("Confirm").performClick()
        awaitSaved()
        reveal("Journey complete. Your preview history is saved.")
        rule.onNode(hasScrollAction()).performScrollToIndex(0)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Earnings", useUnmergedTree = true).performClick()
        rule.onAllNodesWithText("KSh 4,200", substring = true).onFirst().assertExists()
        screenshot("earnings")
    }
}
