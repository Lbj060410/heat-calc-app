package com.example.connects.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.connects.features.dailyrecord.DailyRecordDialog
import com.example.connects.features.dailyrecord.DailyRecordViewModel

private data class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: @Composable () -> Unit
)

private val bottomTabs = listOf(
    BottomTab(Screen.Home, "\u9996\u9875", { Icon(Icons.Filled.Home, contentDescription = "\u9996\u9875") }),
    BottomTab(Screen.Diary, "\u65e5\u5fd7", { Icon(Icons.Filled.DateRange, contentDescription = "\u65e5\u5fd7") }),
    BottomTab(Screen.Profile, "\u6211\u7684", { Icon(Icons.Filled.Person, contentDescription = "\u6211\u7684") })
)

@Composable
fun MainTabsScreen() {
    val tabNavController = rememberNavController()
    val backStackEntry = tabNavController.currentBackStackEntryAsState().value
    val currentDestination = backStackEntry?.destination

    // 进入应用先补齐必填记录：体重每天一次，腰围每 15 天一次。
    // 有任一项到期就一直显示不可关闭的弹窗；用户点「跳过」只跳过本次会话。
    val dailyRecordViewModel: DailyRecordViewModel = hiltViewModel()
    val needsWeightInput by dailyRecordViewModel.needsWeightInput.collectAsState()
    val needsWaistInput by dailyRecordViewModel.needsWaistInput.collectAsState()
    val isRecordSkipped by dailyRecordViewModel.isSkipped.collectAsState()

    if (!isRecordSkipped && (needsWeightInput == true || needsWaistInput == true)) {
        DailyRecordDialog(viewModel = dailyRecordViewModel)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true

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
                PlaceholderPage(
                    title = "\u0041\u0049 \u529f\u80fd\u6682\u672a\u5f00\u653e",
                    subtitle = "\u5f53\u524d\u7248\u672c\u5148\u4fdd\u7559\u5165\u53e3\uff0c\u540e\u7eed\u518d\u63a5\u5165\u667a\u80fd\u5efa\u8bae\u3002"
                )
            }

            composable(Screen.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(viewModel = profileViewModel)
            }
        }
    }
}

@Composable
private fun PlaceholderPage(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Filled.Build, contentDescription = null)
            Text(text = title)
            Text(text = subtitle)
        }
    }
}
