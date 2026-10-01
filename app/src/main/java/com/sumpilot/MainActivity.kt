package com.sumpilot

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumpilot.data.repository.AppSettings
import com.sumpilot.ui.SumPilotNavHost
import com.sumpilot.ui.theme.SumPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch screen is shown only until the first frame; no artificial delay.
        installSplashScreen()
        // Light background → dark system-bar icons. Bars stay visible (no immersive mode).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as SumPilotApplication).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            SumPilotTheme(reducedMotion = settings.reducedMotion) {
                SumPilotNavHost()
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) (application as SumPilotApplication).container.soundPlayer.release()
        super.onDestroy()
    }
}
