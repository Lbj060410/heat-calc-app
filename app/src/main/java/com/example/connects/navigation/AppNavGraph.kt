package com.example.connects.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.connects.features.onboarding.OnboardingScreen
import com.example.connects.features.onboarding.OnboardingViewModel
import com.example.connects.features.weight.WeightRecordScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.navigate(Screen.MainTabs.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.MainTabs.route) {
            MainTabsScreen()
        }

        composable(Screen.WeightRecord.route) {
            WeightRecordScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
