package com.mireli.driver

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mireli.driver.ui.DepartureSettlement
import com.mireli.driver.ui.DriverDocumentRow
import com.mireli.driver.ui.EarningsOverview
import com.mireli.driver.domain.StatementPeriod
import com.mireli.driver.ui.DocumentSelectionDialog
import com.mireli.driver.ui.DriverIdentity
import com.mireli.driver.ui.DriverHomeDashboard
import com.mireli.driver.ui.DriverHomeTopBar
import com.mireli.driver.ui.DriverSignInScreen
import com.mireli.driver.ui.PayoutDestinationDialog
import com.mireli.driver.ui.driverSupportItems
import com.mireli.driver.data.SupportDraft
import com.mireli.driver.ui.MireliTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeparturePagesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun approvedBeneficiaryCanRequestAReplacementNumber() {
        val statement = JSONObject("""{
            "destination":{"status":"approved","version":7,"accountName":"Pilot Driver"},
            "destinationMasked":"••••1234","payoutDestinationPhoneEditable":true
        }""")
        var submitted: Triple<String, String, Int>? = null
        rule.setContent {
            MireliTheme {
                PayoutDestinationDialog(statement, false, null, { name, phone, version, saved ->
                    submitted = Triple(name, phone, version)
                    saved()
                }, {})
            }
        }
        rule.onNodeWithText("Edit payout details").performScrollTo().performClick()
        rule.onNode(hasText("M-Pesa mobile number")).performScrollTo().performTextInput("0712345678")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Submit for review").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("I own this M-Pesa account.").performScrollTo().performClick()
        rule.onNodeWithText("Submit for review").performScrollTo().assertIsEnabled().performClick()
        assertEquals(Triple("Pilot Driver", "0712345678", 7), submitted)
    }

    @Test fun signInShowsAppearanceBesideSupport() {
        rule.setContent {
            MireliTheme {
                DriverSignInScreen(
                    state = DriverServiceState(loading = false, emailSignInOpen = true),
                    email = "", onEmailChange = {}, code = "", onCodeChange = {},
                    onRequestCode = {}, onVerifyCode = {}, onChangeEmail = {}, onRetry = {},
                    resendInSeconds = 0, onBackToWelcome = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Help and support").assertIsDisplayed()
        rule.onNode(hasContentDescription("Switch to dark mode") or hasContentDescription("Switch to light mode"))
            .assertIsDisplayed()
        rule.onNodeWithText("Appearance").assertDoesNotExist()
    }

    @Test fun supportShowsRetryForUnconfirmedDeliveryAndKeepsTheDraft() {
        var retried = false
        val message = "Please review my synthetic payout."
        val state = DriverServiceState(loading = false, supportDraft = SupportDraft("payout", message, "test-request"),
            supportError = "Support cases could not be refreshed.")
        rule.setContent {
            MireliTheme {
                LazyColumn { driverSupportItems(state, { _, _ -> error("Pending draft must stay fixed") }, { retried = true }, {}) }
            }
        }
        rule.onNode(hasText(message)).assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Retry sending"))
        rule.onNodeWithText("Retry sending").performClick()
        assertEquals(true, retried)
    }

    @Test fun applicantsCanOpenTheLiveMapBeforeApproval() {
        var opened = false
        rule.setContent { MireliTheme { Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DriverHomeTopBar(onSupport = {})
                DriverHomeDashboard(name = "Pilot Driver", eligible = false, verified = false, applicationStatus = "draft",
                    tripsCurrent = true, trips = emptyList(), onRefresh = {}, onOpenTrips = {},
                    onOpenAccount = {}, onOpenTrip = {}, onStartNavigation = { _, _ -> }, onOpenMap = { opened = true })
            }
        } } }
        rule.onNodeWithText("Mireli Driver").assertIsDisplayed()
        rule.onNodeWithContentDescription("Help and support").assertIsDisplayed()
        rule.onNodeWithTag("home_location_preview").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Your location").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Open map").performScrollTo().performClick()
        assertEquals(true, opened)
    }

    @Test fun driverGreetingChangesAtTheDaypartBoundaries() {
        val hour = mutableStateOf(5)
        rule.setContent { MireliTheme { Surface {
            DriverIdentity(name = "James Mwangi", verified = true, hour = hour.value)
        } } }
        listOf(5 to "Good morning", 11 to "Good morning", 12 to "Good afternoon",
            16 to "Good afternoon", 17 to "Good evening", 23 to "Good evening",
            0 to "Good evening", 4 to "Good evening").forEach { (time, greeting) ->
            rule.runOnIdle { hour.value = time }
            rule.onNodeWithText(greeting).assertIsDisplayed()
            rule.onNodeWithText("James Mwangi").assertIsDisplayed()
            rule.onNodeWithText("Let’s start earning!").assertIsDisplayed()
        }
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

    @Test fun removingASelectedFileDoesNotUploadIt() {
        val visible = mutableStateOf(true)
        var uploaded = false
        rule.setContent {
            MireliTheme {
                if (visible.value) DocumentSelectionDialog(
                    title = "Driving licence",
                    uri = Uri.parse("content://com.mireli.test/selection"),
                    expires = "2099-02-28",
                    canUpload = true,
                    onRemove = { visible.value = false },
                    onUpload = { uploaded = true },
                )
            }
        }
        rule.onNodeWithText("Driving licence").assertIsDisplayed()
        rule.onNodeWithText("Remove selected file").performClick()
        rule.onNodeWithText("Review file").assertDoesNotExist()
        assertEquals(false, uploaded)
    }

    @Test fun earningsSummaryHidesAmountAndOpensRealShortcuts() {
        val hidden = mutableStateOf(false)
        var destination = ""
        val period = mutableStateOf(StatementPeriod.All)
        rule.setContent {
            MireliTheme {
                EarningsOverview(
                    paid = if (hidden.value) "KSh ••••" else "KSh 2,450",
                    hidden = hidden.value,
                    period = period.value,
                    onPeriodChange = { period.value = it },
                    onToggleBalance = { hidden.value = !hidden.value },
                    onPayoutDetails = { destination = "payout" },
                    onPayoutSupport = { destination = "help" },
                )
            }
        }
        rule.onNodeWithText("KSh 2,450").assertIsDisplayed()
        rule.onNodeWithContentDescription("Hide balances").performClick()
        rule.onNodeWithText("KSh 2,450").assertDoesNotExist()
        rule.onNodeWithText("KSh ••••").assertIsDisplayed()
        rule.onNodeWithText("Trip history").assertDoesNotExist()
        rule.onNodeWithText("This month").performClick()
        assertEquals(StatementPeriod.Month, period.value)
        for ((label, target) in listOf("M-Pesa" to "payout", "Payout help" to "help")) {
            rule.onNodeWithText(label).performClick()
            assertEquals(target, destination)
        }
    }

    @Test fun hidingBalancesAlsoHidesExpandedSettlementAmounts() {
        val line = JSONObject("""{"routeName":"Nyali transfer","serviceType":"shared","status":"completed",
            "grossMinor":160000,"commissionMinor":32000,"surchargeMinor":0,"netMinor":128000}""")
        val hidden = mutableStateOf(false)
        rule.setContent { MireliTheme { Surface { Column { DepartureSettlement(line, hidden.value) } } } }
        rule.onNodeWithText("Paid").assertIsDisplayed()
        rule.onNodeWithText("M-Pesa settlement").performClick()
        rule.onNodeWithText("Gross fare").assertIsDisplayed()
        rule.runOnIdle { hidden.value = true }
        rule.onAllNodesWithText("KSh ••••").assertCountEquals(5)
    }
}
