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
    private fun click(text: String) { rule.onNodeWithText(text).performScrollTo().performClick() }
    private fun waitFor(text: String) {
        rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
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
        rule.onNodeWithText("Today", useUnmergedTree = true).performClick()
        screenshot("today")
        rule.onNodeWithText("SGR-1042", substring = true).performScrollTo().performClick()
        click("Accept assignment")
        rule.onNodeWithText("Confirm", exact = true).performClick()
        waitFor("I have arrived")
        click("I have arrived")
        rule.onNodeWithText("Confirm", exact = true).performClick()
        waitFor("Passenger manifest")
        screenshot("manifest")
        listOf("1042", "2042", "3042").forEach { code ->
            rule.onAllNodesWithText("Board party").onFirst().performScrollTo().performClick()
            rule.onNodeWithText("Boarding code").performTextInput(code)
            rule.onNodeWithText("Confirm boarding", exact = true).let {
                rule.onAllNodesWithText("Confirm boarding").onLast().performClick()
            }
            rule.waitUntil(15000) {
                rule.onAllNodesWithText("Sample boarding code: " + code).fetchSemanticsNodes().isEmpty()
            }
        }
        click("Start journey")
        rule.onNodeWithText("Confirm", exact = true).performClick()
        waitFor("Complete journey")
        screenshot("active-trip")
        click("Complete journey")
        rule.onNodeWithText("Confirm", exact = true).performClick()
        waitFor("Journey complete. Your preview history is saved.")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Earnings", useUnmergedTree = true).performClick()
        rule.onAllNodesWithText("KSh 4,200", substring = true).onFirst().assertExists()
        screenshot("earnings")
    }
}

