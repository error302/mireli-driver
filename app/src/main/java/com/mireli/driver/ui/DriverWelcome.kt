package com.mireli.driver.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import com.mireli.driver.BuildConfig
import com.mireli.driver.R

private val WelcomeMint = Color(0xFF69DFB4)
private val WelcomeBlue = Color(0xFF223B51)
private val WelcomeText = Color(0xFFD5E0E9)

@Composable
private fun LaunchSystemBars() {
    val window = (LocalContext.current as? ComponentActivity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
    DisposableEffect(controller) {
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}

@Composable
internal fun DriverLaunchLoading() {
    LaunchSystemBars()
    Box(Modifier.fillMaxSize().background(colorResource(R.color.mireli_launch_navy)).testTag("driver_startup_loading")
        .safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.mireli_driver_logo), null,
                Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(6.dp))
            Text("Mireli Driver", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            CircularProgressIndicator(Modifier.size(23.dp), color = WelcomeMint, trackColor = WelcomeBlue, strokeWidth = 2.dp)
            Text("Loading your account…", color = WelcomeText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun DriverWelcome(onSignIn: () -> Unit, onRegister: () -> Unit, message: String?, onClose: (() -> Unit)?) {
    LaunchSystemBars()
    val navy = colorResource(R.color.mireli_launch_navy)
    BoxWithConstraints(Modifier.fillMaxSize().background(navy).testTag("driver_welcome"), contentAlignment = Alignment.TopCenter) {
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        var panelHeight by remember { mutableStateOf(Dp.Unspecified) }
        val overlap = 24.dp
        val heroHeight = if (panelHeight == Dp.Unspecified) (viewportHeight * .55f).coerceAtLeast(170.dp)
            else (viewportHeight - panelHeight + overlap).coerceAtLeast(170.dp)
        Box(Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                Image(painterResource(R.drawable.mireli_welcome_hero), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
                Box(Modifier.fillMaxWidth().height(84.dp).background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = .44f), Color.Transparent))))
            }
            Surface(Modifier.fillMaxWidth().padding(top = heroHeight - overlap)
                .onSizeChanged { with(density) { panelHeight = it.height.toDp() } },
                color = navy, contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = WelcomeMint)) { append("Mireli") }
                        append(" Driver")
                    }, style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Manage your trips, navigation and earnings.", color = WelcomeText,
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    if (BuildConfig.DRIVER_SERVICE_TEST) {
                        Text("Staging · simulated sign-in and payouts", color = WelcomeText, style = MaterialTheme.typography.bodySmall)
                    }
                    message?.let { Text(it, color = WelcomeText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(2.dp))
                    WelcomeButton("Sign in", "driver_welcome_sign_in", WelcomeMint, navy, onSignIn)
                    WelcomeButton("Register", "driver_welcome_register", WelcomeBlue, Color.White, onRegister)
                    if (onClose != null) TextButton(onClick = onClose) { Text("Back to preview", color = WelcomeText) }
                }
            }
        }
    }
}

@Composable
private fun WelcomeButton(label: String, tag: String, background: Color, foreground: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick = onClick, shape = CircleShape, interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(interaction).testTag(tag)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
