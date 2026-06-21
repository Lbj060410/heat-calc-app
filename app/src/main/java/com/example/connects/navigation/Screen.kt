package com.example.connects.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object MainTabs : Screen("main_tabs")

    data object Home : Screen("home")
    data object WeightRecord : Screen("weight_record")
    data object Diary : Screen("diary")
    data object AI : Screen("ai")
    data object Profile : Screen("profile")
}
