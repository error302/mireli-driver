package com.mireli.driver

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mireli.driver.ui.ThemeMode
import com.mireli.driver.ui.ThemePreference
import com.mireli.driver.data.OnboardingStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.rules.ExternalResource
import java.io.File

class AppearanceTest {
    @get:Rule(order=0)val preference=object:ExternalResource(){override fun before(){runBlocking{ThemePreference(ApplicationProvider.getApplicationContext()).save(ThemeMode.SYSTEM)}};override fun after(){runBlocking{ThemePreference(ApplicationProvider.getApplicationContext()).save(ThemeMode.SYSTEM)}}}
    @get:Rule(order=1)val rule=createAndroidComposeRule<MainActivity>()
    @Before fun prepareSyntheticDraft(){OnboardingStore(rule.activity).clear()}
    private fun choose(label:String,mode:ThemeMode){
        rule.onNodeWithText("Appearance").performClick();rule.onNodeWithText(label).performClick()
        rule.waitUntil(10000){ThemePreference(rule.activity).mode==mode}
        rule.waitUntil(10000){rule.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(label).assertIsSelected();rule.onNodeWithText("Done").performClick();rule.onNodeWithText("Done").assertDoesNotExist()
    }
    private fun waitForTheme(dark:Boolean){
        rule.waitUntil(10000){rule.onAllNodesWithTag(if(dark)"theme_dark" else "theme_light").fetchSemanticsNodes().isNotEmpty()}
        rule.runOnIdle{val bars=androidx.core.view.WindowCompat.getInsetsController(rule.activity.window,rule.activity.window.decorView);assertEquals(!dark,bars.isAppearanceLightStatusBars);assertEquals(!dark,bars.isAppearanceLightNavigationBars)}
    }
    private fun systemNight(value:String){InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd uimode night $value").use{descriptor->android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{it.readBytes()}}}
    private fun screenshot(name:String){rule.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(300,5000);val dir=File(rule.activity.getExternalFilesDir(null),"appearance").apply{mkdirs()};val bitmap=automation.takeScreenshot();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun themePersistsAndAppliesToTripsOnboardingAndConnectedSignIn(){
        choose("Dark",ThemeMode.DARK);rule.onNodeWithTag("theme_dark").assertExists();screenshot("dark-today")
        rule.activityRule.scenario.recreate();rule.onNodeWithTag("theme_dark").assertExists();assertEquals(ThemeMode.DARK,ThemePreference(rule.activity).mode)
        rule.onNodeWithText("Trips",useUnmergedTree=true).performClick();rule.onNodeWithText("Your trips").assertExists();screenshot("dark-trips")
        rule.onNodeWithText("Earnings",useUnmergedTree=true).performClick();screenshot("dark-earnings")
        rule.onNodeWithText("Account",useUnmergedTree=true).performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Driver onboarding & documents"));rule.onNodeWithText("Driver onboarding & documents").performClick()
        rule.onNodeWithTag("theme_dark").assertExists();rule.onNodeWithText("Become a Mireli driver").assertExists()
        rule.waitUntil(15000){rule.onAllNodes(hasText("Full name") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Full name").performTextInput("Appearance Test Driver");androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Appearance"))
        choose("Light",ThemeMode.LIGHT);rule.onNodeWithText("Appearance Test Driver").assertExists()
        choose("Dark",ThemeMode.DARK);rule.onNodeWithText("Appearance Test Driver").assertExists();screenshot("dark-onboarding")
        rule.onNodeWithText("Back").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Connect driver account & payouts"));rule.onNodeWithText("Connect driver account & payouts").performClick()
        rule.onNodeWithTag("theme_dark").assertExists();rule.onNodeWithText("Sign in or apply to drive").assertExists();screenshot("dark-sign-in")
        choose("Light",ThemeMode.LIGHT);rule.onNodeWithTag("theme_light").assertExists();screenshot("light-sign-in")
        rule.activityRule.scenario.recreate();rule.onNodeWithTag("theme_light").assertExists()
        choose("System",ThemeMode.SYSTEM)
        val systemDark=(rule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES
        rule.onNodeWithTag(if(systemDark)"theme_dark" else "theme_light").assertExists()
    }
    @Test fun systemModeFollowsDeviceAndManualChoiceOverridesIt(){
        val originalDark=(rule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES
        try{
            systemNight("yes");waitForTheme(true)
            choose("Light",ThemeMode.LIGHT);waitForTheme(false)
            systemNight("no");waitForTheme(false)
            choose("Dark",ThemeMode.DARK);waitForTheme(true)
            rule.activityRule.scenario.recreate();waitForTheme(true)
            choose("System",ThemeMode.SYSTEM);waitForTheme(false)
        }finally{systemNight(if(originalDark)"yes" else "no")}
    }
}
