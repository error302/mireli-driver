package com.mireli.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mireli.driver.BuildConfig
import com.mireli.driver.DriverServiceState
import com.mireli.driver.R

@Composable
fun DriverSignInScreen(
    state: DriverServiceState,
    email: String,
    onEmailChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onChangeEmail: () -> Unit,
    onRetry: () -> Unit,
    resendInSeconds: Long,
    onClose: (() -> Unit)? = null,
    supportAddressFailed: Boolean = false,
    onSupport: () -> Unit = {},
) {
    val verifying = state.challengeId != null
    val signInOpen = state.emailSignInOpen == true
    val canRequest = signInOpen && !state.loading && email.isNotBlank()
    val canVerify = !state.loading && code.length == 6
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
    item {
        Surface(Modifier.statusBarsPadding(), color = StationNavy) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.small, color = Color.White) {
                        Image(painterResource(R.drawable.mireli_driver_logo), null, Modifier.size(44.dp))
                    }
                    Text("Mireli Driver", color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (!verifying) {
                    Spacer(Modifier.height(24.dp))
                    Text("Drive Mombasa’s\nSGR transfers", color = Color.White,
                        style = MaterialTheme.typography.displaySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mombasa Terminus", Modifier.weight(1f), color = StationMint,
                            style = MaterialTheme.typography.labelLarge)
                        StationLines(Modifier.width(70.dp).height(32.dp))
                        Text("Local stops", color = StationMint, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    item {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(MireliDesign.Radius.hero),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (verifying) "Check your email" else "Sign in to drive",
                    Modifier.testTag("driver_sign_in_title"), style = MaterialTheme.typography.headlineSmall)
                if (BuildConfig.DRIVER_SERVICE_TEST) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("STAGING · Simulated sign-in and payouts", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (!verifying && (!signInOpen || state.registrationOpen != true)) SignInServiceNotice(state)
                state.error?.takeIf { signInOpen }?.let { message ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                if (!verifying) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            email,
                            { onEmailChange(it.take(254)) },
                            label = { Text("Email address") },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = { Icon(MireliIcons.Mail, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (canRequest) {
                                    focus.clearFocus(); keyboard?.hide(); onRequestCode()
                                }
                            }),
                            singleLine = true,
                            enabled = signInOpen && !state.loading,
                            shape = MaterialTheme.shapes.medium,
                            colors = signInFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("driver_email_field")
                        )
                        Text("We email you a 6-digit code. No password.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MireliPrimaryButton(
                        onClick = { focus.clearFocus(); keyboard?.hide(); onRequestCode() },
                        enabled = canRequest,
                        modifier = Modifier.fillMaxWidth().testTag("driver_send_code")
                    ) {
                        Text(if (signInOpen) "Send sign-in code" else if (state.loading) "Checking sign-in…" else "Sign-in unavailable",
                            style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("We sent a verification code to", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.challengeEmail ?: email, Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { onChangeEmail() }, enabled = !state.loading) { Text("Change") }
                        }
                        state.demoCode?.let { sample ->
                            Text("Sample verification code: $sample", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            code,
                            { onCodeChange(it.filter(Char::isDigit).take(6)) },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 8.sp),
                            label = { Text("6-digit verification code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (canVerify) { focus.clearFocus(); keyboard?.hide(); onVerifyCode() } }),
                            singleLine = true,
                            enabled = !state.loading,
                            shape = MaterialTheme.shapes.medium,
                            colors = signInFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        )
                    }
                    MireliPrimaryButton(onClick = { focus.clearFocus(); keyboard?.hide(); onVerifyCode() },
                        enabled = canVerify, modifier = Modifier.fillMaxWidth()) {
                        Text("Verify and continue", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { onRequestCode() }, enabled = !state.loading && resendInSeconds <= 0L,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (resendInSeconds > 0L) "Resend code in ${resendInSeconds}s" else "Resend verification code")
                    }
                }

                if (!verifying && (!signInOpen || state.registrationOpen != true)) {
                    OutlinedButton(onClick = onRetry, enabled = !state.loading,
                        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Icon(MireliIcons.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry connection")
                    }
                }

                if (signInOpen && state.registrationOpen == true && !verifying) {
                    Text(
                        "New to Mireli? Use your email to start a driver application. Have your licence and vehicle documents ready.",
                        Modifier.testTag("driver_sign_in_promo"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSupport) {
                        Icon(MireliIcons.HeadsetMic, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Support")
                    }
                    Spacer(Modifier.weight(1f))
                    AppearanceToggle(showSettings = true)
                }
                if (supportAddressFailed) Text("Email driver support at ${state.supportEmail}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (onClose != null) TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Back to preview") }
            }
        }
    }
    }
}

@Composable
private fun signInFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
internal fun SignInServiceNotice(state: DriverServiceState) {
    val open = state.emailSignInOpen == true
    val title = when {
        open -> "New driver applications are paused"
        state.emailSignInOpen == false -> "Driver sign-in is closed"
        state.loading -> "Checking driver sign-in"
        else -> "Driver service unavailable"
    }
    val detail = state.error?.takeIf { !open } ?: when {
        open -> "Use the email linked to your existing Mireli driver account."
        state.emailSignInOpen == false -> state.serviceMessage ?: "Check again later or contact driver support."
        state.loading -> "Checking whether email sign-in is available."
        else -> "Check your connection and try again."
    }
    DriverNotice(if (open) MireliIcons.Info else MireliIcons.Lock, title, detail)
}
