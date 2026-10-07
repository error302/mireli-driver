package com.mireli.driver.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
class ThemePreference(context:Context) {
    private val preferences=context.applicationContext.getSharedPreferences("appearance",Context.MODE_PRIVATE)
    val mode:ThemeMode get()=ThemeMode.from(preferences.getString("theme_mode",null))
    suspend fun save(mode:ThemeMode)=withContext(Dispatchers.IO){check(preferences.edit().putString("theme_mode",mode.name).commit())}
}
private val LightColors=lightColorScheme(
    primary=Color(0xFF007F79),onPrimary=Color.White,primaryContainer=Color(0xFFE5F2EF),onPrimaryContainer=Color(0xFF063555),
    secondary=Color(0xFF063555),onSecondary=Color.White,secondaryContainer=Color(0xFFDDF2EE),onSecondaryContainer=Color(0xFF063555),
    background=Color(0xFFF8FAFB),onBackground=Color(0xFF142D40),surface=Color.White,onSurface=Color(0xFF142D40),
    surfaceVariant=Color(0xFFEAF0F3),onSurfaceVariant=Color(0xFF5A6E7C),surfaceContainer=Color.White,surfaceContainerHighest=Color.White,
    outline=Color(0xFF617584),outlineVariant=Color(0xFFD5E0E7))
private val DarkColors=darkColorScheme(
    primary=Color(0xFF70DDD0),onPrimary=Color(0xFF003F3B),primaryContainer=Color(0xFF153E3B),onPrimaryContainer=Color(0xFFBCF4EB),
    secondary=Color(0xFF9ACBF0),onSecondary=Color(0xFF063555),secondaryContainer=Color(0xFF183B42),onSecondaryContainer=Color(0xFFC2F0E9),
    background=Color(0xFF0B1722),onBackground=Color(0xFFE4EFF6),surface=Color(0xFF132532),onSurface=Color(0xFFE4EFF6),
    surfaceVariant=Color(0xFF233B49),onSurfaceVariant=Color(0xFFB0C4D1),surfaceContainer=Color(0xFF132532),surfaceContainerHighest=Color(0xFF1D3341),
    outline=Color(0xFF7E98A8),outlineVariant=Color(0xFF36515F))
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
            navigationBarStyle=SystemBarStyle.auto(0xE6F8FAFB.toInt(),0xE60B1722.toInt()){dark})
    }
    val appearance=Appearance(mode,saving,failed){choice->
        if(!saving && choice!=mode){saving=true;failed=false;scope.launch{
            try{preference.save(choice);mode=choice}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){failed=true}finally{saving=false}
        }}
    }
    MaterialTheme(colorScheme=if(dark)DarkColors else LightColors,typography=Typography(
        headlineMedium=androidx.compose.ui.text.TextStyle(fontSize=30.sp,lineHeight=36.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,letterSpacing=(-0.8).sp),
        titleLarge=androidx.compose.ui.text.TextStyle(fontSize=22.sp,lineHeight=28.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleMedium=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)),shapes=Shapes(
        extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),small=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(28.dp))) {
        CompositionLocalProvider(LocalAppearance provides appearance){Box(Modifier.fillMaxSize().testTag(if(dark)"theme_dark" else "theme_light")){content()}}
    }
}

/** Shared control is available before sign-in, during onboarding and in the driver account. */
@Composable fun AppearanceToggle(showSettings:Boolean=true) {
    val appearance=LocalAppearance.current
    val dark=when(appearance.mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.DARK->true;ThemeMode.LIGHT->false}
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Dark mode",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Switch(checked=dark,onCheckedChange={appearance.select(if(it)ThemeMode.DARK else ThemeMode.LIGHT)},enabled=!appearance.saving,
            modifier=Modifier.semantics{contentDescription="Dark mode"})
        if(showSettings)AppearanceButton()
    }
}

@Composable fun AppearanceButton() {
    var open by rememberSaveable{mutableStateOf(false)}
    val appearance=LocalAppearance.current
    TextButton(onClick={open=true}){Text(stringResource(R.string.appearance))}
    if(open)AlertDialog(onDismissRequest={open=false},title={Text(stringResource(R.string.appearance))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(stringResource(R.string.theme_explanation))
        ThemeMode.entries.forEach{choice->Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=appearance.mode==choice,enabled=!appearance.saving,role=Role.RadioButton,onClick={appearance.select(choice)}),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            RadioButton(selected=appearance.mode==choice,onClick=null,enabled=!appearance.saving);Text(stringResource(choice.label),Modifier.padding(start=12.dp))
        }}
        if(appearance.saving)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(appearance.failed)Text(stringResource(R.string.theme_save_error),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={open=false}){Text(stringResource(R.string.done))}})
}
