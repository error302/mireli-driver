package com.mireli.driver

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mireli.driver.ui.MireliTheme
import com.mireli.driver.ui.TripNavigation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {
    @get:Rule val rule=createComposeRule()
    // Test-package-only in-memory input: no API, database or account writes.
    private fun trip()=JSONObject("""{"id":"navigation-test","direction":"TO_TERMINUS","phase":"in_progress","stages":[{"id":"t","name":"SGR Terminus","latitude":-4.01,"longitude":39.60},{"id":"c","name":"Coast stop","latitude":-4.04,"longitude":39.70}]}""")
    @Test fun destinationAndStopSelectionSendOnlyTheExpectedMapsUrl(){
        var opened:String?=null
        rule.setContent{MireliTheme{Surface{Column{TripNavigation(trip(),onNavigate={opened=it;true})}}}}
        rule.onNodeWithText("Open destination in Google Maps").performClick()
        assertTrue(opened!!.contains("destination=-4.01%2C39.6"))
        rule.onNodeWithText("Coast stop").performClick()
        rule.onNodeWithText("Open destination in Google Maps").performClick()
        assertTrue(opened!!.contains("destination=-4.04%2C39.7"))
    }
    @Test fun missingMapsHandlerShowsAnActionableError(){
        rule.setContent{MireliTheme{Surface{Column{TripNavigation(trip(),onNavigate={false})}}}}
        rule.onNodeWithText("Open destination in Google Maps").performClick()
        rule.onNodeWithText("No maps app or browser could open. Install one and try again.").assertExists()
    }
}
