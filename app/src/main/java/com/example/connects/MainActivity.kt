package com.example.connects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.navigation.Screen
import com.example.connects.navigation.AppNavGraph
import com.example.connects.ui.theme.ConnectsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConnectsTheme {
                val navController = rememberNavController()
                val isOnboardingComplete by userPreferences.isOnboardingComplete.collectAsState(initial = false)
                val startDestination = if (isOnboardingComplete) {
                    Screen.MainTabs.route
                } else {
                    Screen.Onboarding.route
                }
                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
