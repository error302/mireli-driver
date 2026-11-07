package com.mireli.driver

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mireli.driver.ui.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DriverDesignTest {
    @get:Rule val rule = createComposeRule()
    // These inputs exist only in the test process; no drivers or trips are seeded.
    @Test fun freshOverviewRequiresApprovalAndRoutesToOnboarding() {
        var opened = -1
        rule.setContent { MireliTheme { Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                DriverBrandHeader("Driver account")
                DriverHomeOverview(null, null, false, emptyList(), false) { opened = it }
            }
        } } }
        rule.onNodeWithContentDescription("Mireli original logo").assertExists()
        rule.onNodeWithText("Driver approval needed").assertExists()
        rule.onNodeWithText("Continue onboarding").performScrollTo().performClick()
        assertEquals(0, opened)
        rule.onNodeWithText("Earnings & payouts").performScrollTo().performClick()
        assertEquals(1, opened)
        rule.onNodeWithText("Help & support").performScrollTo().performClick()
        assertEquals(2, opened)
        rule.onAllNodesWithText("Go online").assertCountEquals(0)
    }

    @Test fun approvedOverviewUsesReturnedAssignmentsAndKeepsAccountAccessInDarkMode() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("appearance", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", "DARK").commit()
        var opened = -1
        rule.setContent { MireliTheme { Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                DriverHomeOverview(JSONObject().put("fullName", "Test Driver"), JSONObject().put("status", "approved"), true,
                    listOf(JSONObject("""{
                        "id":"synthetic-assignment",
                        "phase":"accepted",
                        "routeName":"South Coast transfer",
                        "serviceType":"shared",
                        "direction":"TO_TERMINUS",
                        "departureAt":"2026-11-07T05:15:00Z",
                        "stages":[{"name":"Mombasa SGR Terminus"},{"name":"Ferry"},{"name":"Fayaz"},{"name":"Shika Adabu"}]
                    }"""), JSONObject().put("phase", "completed")), false) { opened = it }
            }
        } } }
        rule.onNodeWithTag("theme_dark").assertExists()
        rule.onNodeWithText("Welcome, Test").assertExists()
        rule.onNodeWithText("Pickup").assertExists()
        rule.onNodeWithText("Shika Adabu").assertExists()
        rule.onNodeWithText("Drop-off").assertExists()
        rule.onNodeWithText("Mombasa SGR Terminus").assertExists()
        rule.onNodeWithText("08:15 EAT").assertExists()
        rule.onNodeWithText("View assignments").performScrollTo().performClick()
        assertEquals(3, opened)
        rule.onAllNodesWithText("1").assertCountEquals(2)
        rule.onNodeWithText("Driver account").performScrollTo().performClick()
        assertEquals(0, opened)
    }
}
