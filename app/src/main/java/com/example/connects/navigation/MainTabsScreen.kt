package com.example.connects.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.connects.features.diary.DiaryScreen
import com.example.connects.features.diary.DiaryViewModel
import com.example.connects.features.home.HomeScreen
import com.example.connects.features.home.HomeViewModel
import com.example.connects.features.profile.ProfileScreen
import com.example.connects.features.profile.ProfileViewModel

private data class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: @Composable () -> Unit
)

private val bottomTabs = listOf(
    BottomTab(
        screen = Screen.Home,
        label = "首页",
        icon = { Icon(Icons.Filled.Home, contentDescription = "首页") }
    ),
    BottomTab(
        screen = Screen.Diary,
        label = "日记",
        icon = { Icon(Icons.Filled.DateRange, contentDescription = "日记") }
    ),
    BottomTab(
        screen = Screen.Profile,
        label = "我的",
        icon = { Icon(Icons.Filled.Person, contentDescription = "我的") }
    )
)

@Composable
fun MainTabsScreen() {
    val tabNavController = rememberNavController()
    val backStackEntry = tabNavController.currentBackStackEntryAsState().value
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == tab.screen.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabNavController.navigate(tab.screen.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = tab.icon,
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = hiltViewModel()
                HomeScreen(viewModel = homeViewModel)
            }

            composable(Screen.Diary.route) {
                val diaryViewModel: DiaryViewModel = hiltViewModel()
                DiaryScreen(viewModel = diaryViewModel)
            }

            composable(Screen.AI.route) {
                PlaceholderPage("AI 功能开发中")
            }

            composable(Screen.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(viewModel = profileViewModel)
            }
        }
    }
}

@Composable
private fun PlaceholderPage(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$title page is ready for implementation")
    }
}
