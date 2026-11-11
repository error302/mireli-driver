package com.mireli.driver.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mireli.driver.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode(val label:Int) {
    SYSTEM(R.string.theme_system), LIGHT(R.string.theme_light), DARK(R.string.theme_dark);
    companion object {fun from(value:String?)=entries.firstOrNull{it.name==value}?:LIGHT}
}

/** Design tokens. Spacing follows an 8-point grid, with 4dp allowed for labels and hairlines. */
internal object MireliDesign {
    val ControlRadius = 12.dp
    val PanelRadius = 20.dp
    val PanelPadding = 16.dp
    const val DetailDurationMs = 180
    const val NumberFeatures = "tnum"

    object Space {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
    }

    object Radius {
        val chip = 100.dp
        val tile = 12.dp
        val card = 20.dp
        val hero = 24.dp
    }

    /** Emerald brand. */
    val Emerald = Color(0xFF0B826E)

    /** Ink used for hero surfaces and primary text. */
    val Ink = Color(0xFF101820)

    /** Warm orange, reserved for attention states such as a train arriving now. */
    val Signal = Color(0xFFEF6C1A)
}

class ThemePreference(context:Context) {
    private val preferences=context.applicationContext.getSharedPreferences("appearance",Context.MODE_PRIVATE)
    val mode:ThemeMode get()=ThemeMode.from(preferences.getString("theme_mode",null))
    suspend fun save(mode:ThemeMode)=withContext(Dispatchers.IO){check(preferences.edit().putString("theme_mode",mode.name).commit())}
}

private val LightColors=lightColorScheme(
    primary=Color(0xFF0B826E),onPrimary=Color.White,primaryContainer=Color(0xFFE0F3EC),onPrimaryContainer=Color(0xFF06302A),
    secondary=Color(0xFF101820),onSecondary=Color.White,secondaryContainer=Color(0xFFE9EFF1),onSecondaryContainer=Color(0xFF101820),
    tertiary=Color(0xFFEF6C1A),onTertiary=Color.White,tertiaryContainer=Color(0xFFFFEBDD),onTertiaryContainer=Color(0xFF7A2C00),
    background=Color(0xFFF7F9F8),onBackground=Color(0xFF101820),surface=Color.White,onSurface=Color(0xFF101820),
    surfaceVariant=Color(0xFFEDF2EF),onSurfaceVariant=Color(0xFF5A6B66),
    surfaceContainer=Color.White,surfaceContainerHigh=Color(0xFFF2F6F4),surfaceContainerHighest=Color(0xFFE8EFEB),
    outline=Color(0xFF7F908B),outlineVariant=Color(0xFFD9E4DE))

private val DarkColors=darkColorScheme(
    primary=Color(0xFF4FD8B8),onPrimary=Color(0xFF00261F),primaryContainer=Color(0xFF0C3A31),onPrimaryContainer=Color(0xFFA9F2DF),
    secondary=Color(0xFFCFE0E8),onSecondary=Color(0xFF0B1620),secondaryContainer=Color(0xFF1B2A33),onSecondaryContainer=Color(0xFFDCEBF2),
    tertiary=Color(0xFFFFB27A),onTertiary=Color(0xFF4A1E00),tertiaryContainer=Color(0xFF4A2A12),onTertiaryContainer=Color(0xFFFFD9BC),
    background=Color(0xFF0B1116),onBackground=Color(0xFFE8F0F2),surface=Color(0xFF101820),onSurface=Color(0xFFE8F0F2),
    surfaceVariant=Color(0xFF1B2A31),onSurfaceVariant=Color(0xFFA9BCC4),
    surfaceContainer=Color(0xFF101820),surfaceContainerHigh=Color(0xFF16222A),surfaceContainerHighest=Color(0xFF1E2C35),
    outline=Color(0xFF7A8E97),outlineVariant=Color(0xFF2C3F49))

private val MireliTypography=Typography(
    displaySmall=TextStyle(fontSize=36.sp,lineHeight=42.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.2).sp,fontFeatureSettings=MireliDesign.NumberFeatures),
    headlineLarge=TextStyle(fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp,fontFeatureSettings=MireliDesign.NumberFeatures),
    headlineMedium=TextStyle(fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp,fontFeatureSettings=MireliDesign.NumberFeatures),
    headlineSmall=TextStyle(fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.4).sp),
    titleLarge=TextStyle(fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),
    titleSmall=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=17.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,fontFeatureSettings=MireliDesign.NumberFeatures),
    labelMedium=TextStyle(fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium,letterSpacing=(.2).sp,fontFeatureSettings=MireliDesign.NumberFeatures),
    labelSmall=TextStyle(fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.Medium,letterSpacing=(.4).sp))

private val MireliShapes=Shapes(
    extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(MireliDesign.Radius.tile),
    medium=RoundedCornerShape(MireliDesign.Radius.card),large=RoundedCornerShape(MireliDesign.Radius.hero))

private data class Appearance(val mode:ThemeMode,val saving:Boolean,val failed:Boolean,val select:(ThemeMode)->Unit)
private val LocalAppearance=staticCompositionLocalOf<Appearance>{error("MireliTheme is required")}

@Composable fun MireliTheme(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val preference=remember(context.applicationContext){ThemePreference(context)}
    var mode by remember {mutableStateOf(preference.mode)}
    var saving by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val dark=when(mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.LIGHT->false;ThemeMode.DARK->true}
    SideEffect {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark},
            navigationBarStyle=SystemBarStyle.auto(0xE6F7F9F8.toInt(),0xE60B1116.toInt()){dark})
    }
    val appearance=Appearance(mode,saving,failed){choice->
        if(!saving && choice!=mode){saving=true;failed=false;scope.launch{
            try{preference.save(choice);mode=choice}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){failed=true}finally{saving=false}
        }}
    }
    MaterialTheme(colorScheme=if(dark)DarkColors else LightColors,typography=MireliTypography,shapes=MireliShapes) {
        CompositionLocalProvider(LocalAppearance provides appearance){Box(Modifier.fillMaxSize().testTag(if(dark)"theme_dark" else "theme_light")){content()}}
    }
}

/**
 * The theme control is one icon: a moon to darken, a sun to go back to light. It is the only
 * appearance affordance on Home and on sign-in, so the screen stays about driving.
 */
@Composable fun ThemeIconButton(tint: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    val appearance=LocalAppearance.current
    val dark=when(appearance.mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.DARK->true;ThemeMode.LIGHT->false}
    IconButton(onClick={appearance.select(if(dark)ThemeMode.LIGHT else ThemeMode.DARK)},enabled=!appearance.saving,modifier=modifier) {
        Icon(if(dark)MireliIcons.Sun else MireliIcons.Moon,"Dark mode",tint=if(appearance.saving)tint.copy(alpha=.4f) else tint)
    }
}

/**
 * Compact appearance row for settings screens: what the app follows, plus the same single icon.
 * The label opens the full System / Light / Dark choice.
 */
@Composable fun AppearanceToggle(showSettings:Boolean=true) {
    val appearance=LocalAppearance.current
    var open by rememberSaveable{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(R.string.appearance),style=MaterialTheme.typography.bodyMedium,
            modifier=Modifier.weight(1f).clickable(enabled=showSettings,role=Role.Button){open=true}.padding(vertical=12.dp))
        ThemeIconButton()
    }
    if(open&&showSettings)ThemeDialog(appearance){open=false}
}

@Composable fun AppearanceButton() {
    var open by rememberSaveable{mutableStateOf(false)}
    val appearance=LocalAppearance.current
    TextButton(onClick={open=true}){Text(stringResource(R.string.appearance))}
    if(open)ThemeDialog(appearance){open=false}
}

@Composable private fun ThemeDialog(appearance:Appearance,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.appearance))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(stringResource(R.string.theme_explanation))
        ThemeMode.entries.forEach{choice->Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=appearance.mode==choice,enabled=!appearance.saving,role=Role.RadioButton,onClick={appearance.select(choice)}),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            RadioButton(selected=appearance.mode==choice,onClick=null,enabled=!appearance.saving);Text(stringResource(choice.label),Modifier.padding(start=12.dp))
        }}
        if(appearance.saving)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(appearance.failed)Text(stringResource(R.string.theme_save_error),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.done))}})
}
