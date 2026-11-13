package com.mireli.driver

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mireli.driver.ui.DepartureSettlement
import com.mireli.driver.ui.DriverDocumentRow
import com.mireli.driver.ui.DriverHomeDashboard
import com.mireli.driver.ui.MireliTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeparturePagesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun applicantsCanOpenTheLiveMapBeforeApproval() {
        var opened = false
        rule.setContent { MireliTheme { Surface {
            DriverHomeDashboard(name = "Pilot Driver", eligible = false, verified = false, applicationStatus = "draft",
                tripsCurrent = true, trips = emptyList(), activeNavigation = null, onRefresh = {}, onOpenTrips = {},
                onOpenAccount = {}, onOpenTrip = {}, onStartNavigation = { _, _ -> }, onOpenMap = { opened = true },
                onOpenEarnings = {})
        } } }
        rule.onNodeWithText("Continue onboarding").assertIsDisplayed()
        rule.onNodeWithText("Live map").performClick()
        assertEquals(true, opened)
    }

    @Test fun documentRowRequiresAValidExpiryAndShowsAnInlineError() {
        val requirement = JSONObject("""{"title":"Driving licence","required":true,"expires":true}""")
        val upload = mutableStateOf<DocumentUpload?>(null)
        var chosen: String? = null
        rule.setContent {
            MireliTheme { Surface {
                DriverDocumentRow(requirement, null, null, true, upload.value) { chosen = it }
            } }
        }
        rule.onNodeWithText("Required · Upload needed").assertIsDisplayed()
        rule.onNodeWithText("Upload").performClick()
        rule.onNodeWithText("Choose file").assertIsNotEnabled()
        rule.onNodeWithText("Expiry · YYYY-MM-DD").performTextInput("2099-02-30")
        rule.onNodeWithText("Enter a valid date that has not passed.").assertIsDisplayed()
        rule.onNodeWithText("Expiry · YYYY-MM-DD").performTextReplacement("2099-02-28")
        rule.onNodeWithText("Choose file").assertIsEnabled().performClick()
        assertEquals("2099-02-28", chosen)
        rule.runOnIdle { upload.value = DocumentUpload("licence", message = "Choose a smaller file.") }
        rule.onNodeWithText("Choose a smaller file.").assertIsDisplayed()
    }

    @Test fun hidingBalancesAlsoHidesExpandedSettlementAmounts() {
        val line = JSONObject("""{"routeName":"Nyali transfer","serviceType":"shared","status":"completed",
            "grossMinor":160000,"commissionMinor":32000,"surchargeMinor":0,"netMinor":128000}""")
        val hidden = mutableStateOf(false)
        rule.setContent { MireliTheme { Surface { Column { DepartureSettlement(line, hidden.value) } } } }
        rule.onNodeWithText("Paid").assertIsDisplayed()
        rule.onNodeWithText("Nyali transfer").performClick()
        rule.onNodeWithText("Gross").assertIsDisplayed()
        rule.runOnIdle { hidden.value = true }
        rule.onAllNodesWithText("KSh ••••").assertCountEquals(4)
    }
}
