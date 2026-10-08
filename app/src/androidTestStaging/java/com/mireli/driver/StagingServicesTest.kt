package com.mireli.driver
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.mireli.driver.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.test.espresso.Espresso.closeSoftKeyboard
import java.io.File

class StagingServicesTest {
    @get:Rule(order=0) val cleanup=object:ExternalResource(){override fun before(){DriverSessionStore(ApplicationProvider.getApplicationContext()).clear()}}
    @get:Rule(order=1) val rule=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=2) val failureEvidence=object:TestWatcher(){override fun failed(error:Throwable,description:Description){screenshot("failure-${description.methodName}");println(rule.onRoot().printToString().take(12000))}}
    private fun reveal(text:String){rule.onNode(hasScrollAction()).performScrollToNode(hasText(text,substring=true))}
    private fun screenshot(name:String){rule.waitForIdle();val dir=File(rule.activity.getExternalFilesDir(null),"screenshots").apply{mkdirs()};val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun approvedDriverCanDeliverACharterAndReadQueuedEarnings() {
        val arguments=InstrumentationRegistry.getArguments()
        val email=arguments.getString("fixtureEmail")
        org.junit.Assume.assumeTrue("Requires the guarded local synthetic fixture",email!=null)
        val tripId=arguments.getString("fixtureTripId")!!;val bookingCode=arguments.getString("fixtureCode")!!
        rule.waitUntil(20000){rule.onAllNodes(hasText("Email address") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Email address").performTextInput(email!!)
        rule.onNodeWithTag("driver_send_code").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Sample verification code:",substring=true).fetchSemanticsNodes().isNotEmpty()}
        val sample=rule.onNodeWithText("Sample verification code:",substring=true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ")
        rule.onNodeWithText("6-digit verification code").performTextInput(Regex("[0-9]{6}").find(sample)!!.value)
        closeSoftKeyboard()
        rule.onNodeWithText("Verify and continue").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Ready for your next journey").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Trips",useUnmergedTree=true).performClick()
        reveal("Accept assignment");screenshot("connected-assignment")
        reveal("Accept assignment");rule.onNodeWithText("Accept assignment").performClick();rule.onNodeWithText("Confirm").performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Arrived at pickup").fetchSemanticsNodes().isNotEmpty()}
        reveal("Arrived at pickup");rule.onNodeWithText("Arrived at pickup").performClick();rule.onNodeWithText("Confirm").performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Start journey").fetchSemanticsNodes().isNotEmpty()}
        reveal("View passenger manifest");rule.onNodeWithText("View passenger manifest").performClick()
        reveal("Board this party");rule.onNodeWithText("Board this party").performClick()
        rule.onNodeWithText("Passenger booking code").performTextInput(bookingCode)
        rule.onNodeWithText("Confirm").performClick()
        val sessions=DriverSessionStore(rule.activity);val session=sessions.load()!!;val api=DriverApi()
        rule.waitUntil(30000){rule.onAllNodesWithText("2 boarded · 0 no-show · 0 waiting").fetchSemanticsNodes().isNotEmpty()}
        screenshot("connected-boarding")
        reveal("Start journey");rule.onNodeWithText("Start journey").performClick();rule.onNodeWithText("Confirm").performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Complete delivered journey").fetchSemanticsNodes().isNotEmpty()}
        reveal("Complete delivered journey");rule.onNodeWithText("Complete delivered journey").performClick();rule.onNodeWithText("Confirm").performClick()
        rule.waitUntil(30000){runBlocking{api.request("/trips",token=session.token)}.getJSONArray("trips").let{rows->(0 until rows.length()).any{rows.getJSONObject(it).getString("id")==tripId&&rows.getJSONObject(it).getString("phase")=="completed"}}}
        assertFalse(runBlocking{api.request("/trips",token=session.token)}.getJSONArray("trips").let{rows->(0 until rows.length()).first{rows.getJSONObject(it).getString("id")==tripId}.let{rows.getJSONObject(it).getJSONArray("passengers").length()>0}})
        rule.onNodeWithText("Earnings",useUnmergedTree=true).performClick()
        val line=runBlocking{api.request("/earnings",token=session.token)}.getJSONArray("lines").let{rows->(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.optString("tripId")==tripId}}
        assertEquals("queued",line.getString("status"));assertEquals(170000,line.getInt("netMinor"))
        rule.onNode(hasScrollAction()).performScrollToIndex(0);screenshot("connected-earnings")
        assertNull(sessions.savedAction(runBlocking{api.request("/me",token=session.token)}.getJSONObject("driver").getString("id")))
    }
    @Test fun nativeDriverCanSignInSaveProfileReadStatementAndSignOut() {
        assertTrue(BuildConfig.DRIVER_SERVICE_TEST)
        val api=DriverApi();assertTrue(runBlocking {api.request("/status")}.getBoolean("simulation"))
        DriverSessionStore(rule.activity).clear()
        val phone="+254700"+System.currentTimeMillis().toString().takeLast(6)
        rule.waitUntil(20000){rule.onAllNodes(hasText("Email address") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Email address").performTextInput("driver-${System.currentTimeMillis()}@example.com")
        rule.onNodeWithTag("driver_send_code").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Sample verification code:",substring=true).fetchSemanticsNodes().isNotEmpty()}
        val sampleText=rule.onNodeWithText("Sample verification code:",substring=true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ")
        val code=Regex("[0-9]{6}").find(sampleText)!!.value
        rule.onNodeWithText("6-digit verification code").performTextInput(code)
        closeSoftKeyboard()
        rule.onNodeWithText("Verify and continue").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(30000){rule.onAllNodes(hasText("Sign out") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Account",useUnmergedTree=true).performClick()
        listOf("Kenyan contact phone" to phone,"Driver full name" to "Sample Native Driver","Registration plate" to "DEMO 030","Passenger seats" to "10","Licence class" to "D1","Vehicle type" to "Sample shuttle").forEach{(label,value)->
            reveal(label);rule.onNodeWithText(label).performScrollTo().assertIsEnabled().performTextReplacement(value)
        }
        closeSoftKeyboard();reveal("Save profile to Mireli");rule.onNodeWithText("Save profile to Mireli").performScrollTo().assertIsEnabled().performClick()
        val session=DriverSessionStore(rule.activity).load();assertNotNull(session)
        rule.waitUntil(30000){runBlocking{api.request("/onboarding",token=session!!.token)}.getJSONObject("profile").getString("fullName")=="Sample Native Driver"}
        val saved=runBlocking {api.request("/onboarding",token=session!!.token)}
        assertEquals("Sample Native Driver",saved.getJSONObject("profile").getString("fullName"))
        // The native HTTP importer submits the same bytes/headers as the document picker path.
        val evidence=runBlocking {api.request("/documents/identity","POST",session!!.token,bytes="%PDF-1.4\nSYNTHETIC NATIVE TEST\n%%EOF".toByteArray(),headers=mapOf("Content-Type" to "application/pdf","x-application-version" to saved.getJSONObject("application").getInt("version").toString()))}
        assertEquals("identity",evidence.getJSONObject("application").getJSONArray("documents").getJSONObject(0).getString("type"))
        rule.onNodeWithText("Earnings",useUnmergedTree=true).performClick()
        reveal("No settlement records yet.")
        rule.onNodeWithText("No settlement records yet.",substring=true).assertExists()
        rule.onNodeWithText("Support",useUnmergedTree=true).performClick()
        reveal("How can we help?");rule.onNodeWithText("How can we help?").performTextInput("Synthetic native support request for testing only.")
        closeSoftKeyboard()
        reveal("Send support case");rule.onNodeWithText("Send support case").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(30000){runBlocking{api.request("/support",token=session!!.token)}.getJSONArray("cases").length()>0}
        reveal("Reference:");rule.onNodeWithText("Reference:",substring=true).assertExists()
        assertEquals("Synthetic native support request for testing only.",runBlocking{api.request("/support",token=session!!.token)}.getJSONArray("cases").getJSONObject(0).getString("message"))
        reveal("Sign out");rule.onNodeWithText("Sign out").performClick()
        rule.waitUntil(30000){rule.onAllNodesWithText("Email address").fetchSemanticsNodes().isNotEmpty()}
        assertNull(DriverSessionStore(rule.activity).load())
        val exception=runCatching{runBlocking {api.request("/me",token=session!!.token)}}.exceptionOrNull()
        assertTrue(exception is DriverServiceException && exception.status==401)
    }
}
