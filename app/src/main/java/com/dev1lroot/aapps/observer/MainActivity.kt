package com.dev1lroot.aapps.observer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dev1lroot.aapps.observer.ui.theme.ObserverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObserverTheme {
                val navController = rememberNavController()
                val prefs = remember { PreferencesManager(applicationContext) }

                NavHost(navController = navController, startDestination = "streaming") {
                    composable("streaming") {
                        StreamingScreen(
                            preferencesManager = prefs,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            preferencesManager = prefs,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
