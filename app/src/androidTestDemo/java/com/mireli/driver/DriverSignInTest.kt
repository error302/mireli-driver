package com.mireli.driver

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mireli.driver.ui.DriverSignInScreen
import com.mireli.driver.ui.MireliTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The enabled sign-in screen cannot be reached from a build pointed at a closed service,
 * so it is rendered directly here. This covers the state a driver sees on launch once
 * the backend gates are open.
 */
@RunWith(AndroidJUnit4::class)
class DriverSignInTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "signin").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun openSignInEnablesEmailEntryAndShowsTheCodeStep() {
        var state by mutableStateOf(DriverServiceState(loading = false, emailSignInOpen = true, registrationOpen = true))
        rule.setContent {
            MireliTheme {
                var email by remember { mutableStateOf("") }
                var code by remember { mutableStateOf("") }
                DriverSignInScreen(
                    state = state,
                    email = email,
                    onEmailChange = { email = it },
                    code = code,
                    onCodeChange = { code = it },
                    onRequestCode = {},
                    onVerifyCode = {},
                    onChangeEmail = {},
                    onRetry = {},
                    resendInSeconds = 0
                )
            }
        }

        rule.onNodeWithTag("driver_sign_in_title").assertIsDisplayed()
        rule.onNodeWithText("Sign in to drive").assertIsDisplayed()
        rule.onNodeWithTag("driver_email_field").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("driver_send_code").assertIsNotEnabled()
        rule.onNodeWithTag("driver_email_field").performTextInput("driver@mireli.test")
        rule.onNodeWithTag("driver_send_code").assertIsEnabled().assertTextContains("Continue")
        capture("signin-open-light")

        // The challenge step replaces the email field with a six-digit code.
        state = state.copy(challengeId = "challenge-1", challengeEmail = "driver@mireli.test")
        rule.onNodeWithTag("driver_sign_in_title").assertIsDisplayed()
        rule.onNodeWithText("Check your email").assertIsDisplayed()
        rule.onNodeWithText("6-digit verification code").assertExists()
        rule.onNodeWithText("Verify and continue").assertIsNotEnabled()
        rule.onNodeWithText("6-digit verification code").performTextInput("123456")
        rule.onNodeWithText("Verify and continue").assertIsEnabled()
        capture("signin-code-light")
    }
}
