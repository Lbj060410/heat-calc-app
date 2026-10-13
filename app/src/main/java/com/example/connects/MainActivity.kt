package com.example.connects

import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.rememberNavController
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.navigation.AppNavGraph
import com.example.connects.navigation.Screen
import com.example.connects.ui.theme.ConnectsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * res/raw 下开机视频的文件名（不带扩展名）。
 * 把素材放到 app/src/main/res/raw/splash.mp4 即可生效，无需改代码。
 */
private const val SPLASH_VIDEO_NAME = "splash"

/**
 * 开机视频的最短展示时长。数据读得很快，不兜个下限的话素材只会闪一帧。
 * 设成 0 表示只等数据读完就进应用、不额外停留。
 */
private const val SPLASH_MIN_DURATION_MS = 1500L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConnectsTheme {
                val navController = rememberNavController()
                // 第一帧还不知道是否完成引导，先不建导航图，避免老用户闪一下引导页。
                val isOnboardingComplete by userPreferences.isOnboardingComplete
                    .collectAsState(initial = null)

                // 让开机视频至少完整展示一段时间，否则数据读得快就只剩一帧。
                var minDurationElapsed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MIN_DURATION_MS)
                    minDurationElapsed = true
                }

                // 两条都满足（读到了引导状态、且视频展示够久）才进应用。
                if (isOnboardingComplete == null || !minDurationElapsed) {
                    SplashVideo(modifier = Modifier.fillMaxSize())
                } else {
                    AppNavGraph(
                        navController = navController,
                        startDestination = if (isOnboardingComplete == true) {
                            Screen.MainTabs.route
                        } else {
                            Screen.Onboarding.route
                        }
                    )
                }
            }
        }
    }
}

/**
 * 开机视频。
 *
 * 找不到素材时退化成纯背景色：既能保证启动不受影响，也避免因为缺文件而编译失败
 * （素材由使用者自己放进 res/raw，代码这边不做强引用）。
 *
 * 建议素材用竖屏比例（如 1080x1920），VideoView 默认按原始比例居中显示，
 * 比例和屏幕差太多时上下/左右会有黑边。
 */
@Composable
private fun SplashVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val videoResId = remember(context) {
        context.resources.getIdentifier(SPLASH_VIDEO_NAME, "raw", context.packageName)
    }

    if (videoResId == 0) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {}
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$videoResId"))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f) // 开机静音；想保留伴音就删掉这一行
                    start()
                }
                // 素材损坏或解码失败时静默跳过，别弹系统错误框。
                setOnErrorListener { _, _, _ -> true }
            }
        },
        onRelease = { view -> view.stopPlayback() }
    )
}