package com.example.connects.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.example.connects.ui.components.SealAvatar
import com.example.connects.ui.components.hairline
import com.example.connects.ui.theme.JournalTitle

private data class BottomTab(
    val screen: Screen,
    val label: String
)

private val bottomTabs = listOf(
    BottomTab(Screen.Home, "首页"),
    BottomTab(Screen.Diary, "日志"),
    BottomTab(Screen.Profile, "我的")
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            JournalBottomBar(
                selectedRoute = currentDestination?.route,
                onSelect = { route ->
                    tabNavController.navigate(route) {
                        popUpTo(tabNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
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
                    title = "AI 功能暂未开放",
                    subtitle = "当前版本先保留入口，后续再接入智能建议。"
                )
            }

            composable(Screen.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(viewModel = profileViewModel)
            }
        }
    }
}

/**
 * 手账式底部导航：纯文字 + 选中项顶上的小圆点，纸底 + 发丝上边线。
 */
@Composable
private fun JournalBottomBar(
    selectedRoute: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.hairline)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bottomTabs.forEach { tab ->
                val selected = currentRouteMatches(selectedRoute, tab)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab.screen.route) }
                        .padding(top = 10.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(5.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private fun currentRouteMatches(selectedRoute: String?, tab: BottomTab): Boolean =
    selectedRoute == tab.screen.route

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
            SealAvatar(initial = "AI", size = 56.dp)
            Text(
                text = title,
                style = JournalTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
