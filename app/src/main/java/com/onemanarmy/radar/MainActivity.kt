package com.onemanarmy.radar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.onemanarmy.radar.platform.AndroidDiagnosticsAndActivityBridge
import com.onemanarmy.radar.platform.AndroidLocaleHelper
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val diagnosticsBridge: AndroidDiagnosticsAndActivityBridge by inject()

    override fun attachBaseContext(newBase: Context) {
        val language = AndroidLocaleHelper.getSavedLanguage(newBase)
        AndroidLocaleHelper.applyDefaultLocale(newBase, language)
        super.attachBaseContext(AndroidLocaleHelper.wrapContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        diagnosticsBridge.attachActivity(this)
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        diagnosticsBridge.detachActivity(this)
        super.onDestroy()
    }
}
