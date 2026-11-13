package com.mireli.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
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
    registering: Boolean = false,
    onBackToWelcome: (() -> Unit)? = null,
) {
    val verifying = state.challengeId != null
    val signInOpen = state.emailSignInOpen == true
    val canEnterEmail = signInOpen && (!registering || state.registrationOpen == true)
    val canRequest = canEnterEmail && !state.loading && email.isNotBlank()
    val canVerify = !state.loading && code.length == 6
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val emailFocus = remember { FocusRequester() }
    val links = LocalUriHandler.current
    var policyError by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
        state = list,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBackToWelcome != null) IconButton(onClick = onBackToWelcome, enabled = !state.loading) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to welcome", tint = MaterialTheme.colorScheme.secondary)
            }
            Surface(shape = MaterialTheme.shapes.small, color = Color.White) {
                Image(painterResource(R.drawable.mireli_driver_logo), null, Modifier.size(34.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Mireli Driver", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onSupport) {
                Icon(MireliIcons.HeadsetMic, "Help and support", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
    item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 44.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (verifying) "Check your email" else if (registering) "Register to drive" else "Sign in to drive",
                    Modifier.testTag("driver_sign_in_title"), style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.secondary)
                if (!verifying) Text(if (registering) "Verify your email to start your driver application."
                    else "Enter your email to receive a sign-in code.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        TextField(
                            email,
                            { onEmailChange(it.take(254)) },
                            label = { Text("Email address") },
                            placeholder = { Text("you@example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (canRequest) {
                                    focus.clearFocus(); keyboard?.hide(); onRequestCode()
                                }
                            }),
                            singleLine = true,
                            enabled = canEnterEmail && !state.loading,
                            colors = departureFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).focusRequester(emailFocus)
                                .testTag("driver_email_field")
                        )
                    }
                    DepartureButton(
                        onClick = { focus.clearFocus(); keyboard?.hide(); onRequestCode() },
                        enabled = canRequest,
                        modifier = Modifier.fillMaxWidth().testTag("driver_send_code")
                    ) {
                        Text(when {
                            state.loading -> "Checking sign-in…"
                            canEnterEmail && registering -> "Send verification code"
                            canEnterEmail -> "Send sign-in code"
                            registering -> "Registration unavailable"
                            else -> "Sign-in unavailable"
                        },
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
                        TextField(
                            code,
                            { onCodeChange(it.filter(Char::isDigit).take(6)) },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 8.sp),
                            label = { Text("6-digit verification code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (canVerify) { focus.clearFocus(); keyboard?.hide(); onVerifyCode() } }),
                            singleLine = true,
                            enabled = !state.loading,
                            colors = departureFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        )
                    }
                    DepartureButton(onClick = { focus.clearFocus(); keyboard?.hide(); onVerifyCode() },
                        enabled = canVerify, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.loading) "Verifying…" else "Verify and continue", style = MaterialTheme.typography.titleMedium)
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

                if (onBackToWelcome == null && !registering && signInOpen && state.registrationOpen == true && !verifying) {
                    TextButton(onClick = {
                        scope.launch {
                            list.animateScrollToItem(1)
                            emailFocus.requestFocus()
                            keyboard?.show()
                        }
                    }, enabled = !state.loading, modifier = Modifier.testTag("driver_sign_in_promo")) {
                        Text("New driver? Start application", color = MaterialTheme.colorScheme.secondary)
                    }
                }
                AppearanceToggle(showSettings = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf("Privacy" to "privacy", "Terms" to "terms").forEach { (label, path) ->
                        TextButton(onClick = {
                            policyError = runCatching { links.openUri("https://mireli.online/$path") }.isFailure
                        }) {
                            Text(label, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (policyError) Text("Open mireli.online in a browser to read the policies.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (supportAddressFailed) Text("Email driver support at ${state.supportEmail}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (onClose != null) TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Back to preview") }
            }
    }
    }
}

@Composable
internal fun DepartureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick = onClick, modifier = modifier.heightIn(min = 50.dp).pressScale(interaction),
        enabled = enabled, shape = CircleShape, interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary), content = content)
}

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
