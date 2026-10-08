package com.mireli.driver

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mireli.driver.data.RepositoryFactory
import com.mireli.driver.data.DriverSessionStore
import com.mireli.driver.ui.ThemeMode
import com.mireli.driver.ui.ThemePreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

class FreshPilotTest {
    @get:Rule(order=0)val cleanAppearance=object:ExternalResource(){override fun before(){
        DriverSessionStore(ApplicationProvider.getApplicationContext()).clear()
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().clear().commit()
    }}
    @get:Rule(order=1)val rule=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String){rule.waitForIdle()
        val folder=File(rule.activity.getExternalFilesDir(null),"pilot-checks").apply{mkdirs()}
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        File(folder,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun startsWithoutSampleDataAndKeepsAnExplicitThemeChoice(){
        assertFalse(RepositoryFactory.isDemo);assertTrue(RepositoryFactory.create(rule.activity).trips.value.isEmpty())
        assertEquals(ThemeMode.LIGHT,ThemePreference(rule.activity).mode)
        rule.onNodeWithTag("theme_light").assertExists()
        rule.onNodeWithText("Mombasa · SGR transfers").assertExists()
        rule.onNodeWithTag("driver_sign_in_title").assertIsDisplayed()
        rule.onNodeWithTag("driver_email_field").assertIsDisplayed()
        rule.onNodeWithTag("driver_send_code").assertIsDisplayed()
        rule.onAllNodesWithText("Appearance").assertCountEquals(1)
        rule.onAllNodesWithText("PREVIEW",substring=true).assertCountEquals(0)
        capture("fresh-signin-light")
        rule.onNodeWithContentDescription("Dark mode").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithTag("theme_dark").fetchSemanticsNodes().isNotEmpty()}
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("theme_dark").assertExists();assertEquals(ThemeMode.DARK,ThemePreference(rule.activity).mode)
        capture("fresh-signin-dark")
        rule.onNodeWithContentDescription("Dark mode").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithTag("theme_light").fetchSemanticsNodes().isNotEmpty()}
    }
}
