package com.mireli.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mireli.driver.ui.MireliApp
import com.mireli.driver.ui.ThemeMode
import com.mireli.driver.ui.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        when(ThemePreference(this).mode){
            ThemeMode.LIGHT->setTheme(R.style.Theme_Mireli_Light)
            ThemeMode.DARK->setTheme(R.style.Theme_Mireli_Dark)
            ThemeMode.SYSTEM->Unit
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MireliApp() }
    }
}
