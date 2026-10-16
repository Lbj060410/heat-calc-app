package com.example.connects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.navigation.AppNavGraph
import com.example.connects.navigation.Screen
import com.example.connects.ui.theme.ConnectsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * 开机动画的最短展示时长。数据读得很快，不兜个下限的话动画只会闪一帧。
 * 设成 0 表示只等数据读完就进应用、不额外停留。
 */
private const val SPLASH_MIN_DURATION_MS = 1500L

/**
 * 开屏动画开关（代码级，不暴露到界面）：
 * - true：播放粉色圆环 + 白色跳动爱心动画。
 * - false：不播动画，短暂白屏等引导状态读完直接进入应用。
 * 关闭时动画不渲染，但数据读取的空白占位仍会保留，避免老用户闪一下引导页。
 */
private const val SPLASH_ANIMATION_ENABLED = true

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

                var minDurationElapsed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MIN_DURATION_MS)
                    minDurationElapsed = true
                }

                // 开屏动画开关关闭时不渲染动画；但仍保留数据读取占位，避免闪引导页。
                val showSplashAnimation =
                    SPLASH_ANIMATION_ENABLED && (isOnboardingComplete == null || !minDurationElapsed)
                val waitingForData = isOnboardingComplete == null

                when {
                    showSplashAnimation -> {
                        // 开机加载层：白底 + 粉色催眠圆环 + 中心白色跳动的心，
                        // 配色与参考仓库硬编码保持一致。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            // 圆环画在心的下面，和参考仓库保持一致。
                            RingAnimation(color = ringPink)
                            BeatingHeart()
                        }
                    }
                    waitingForData -> {
                        // 动画关闭时的空白占位：等引导状态读完，不播动画，避免闪引导页。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundWhite)
                        )
                    }
                    else -> {
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
}

// ---------------------------------------------------------------------------
// 以下两个 Composable 按参考仓库（Stand404/HypnosisAPP）逐字复刻，
// 参数（圈数、扩散时长、生成间隔、线宽）与配色保持一致。
// ---------------------------------------------------------------------------

/** 从屏幕中心持续向外扩散的粉色圆环，实心不透明，10 圈起步、每 200ms 补一圈。 */
@Composable
private fun RingAnimation(color: Color, isActive: Boolean = true) {
    val density = LocalDensity.current

    var centerX by remember { mutableFloatStateOf(0f) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var maxRadius by remember { mutableFloatStateOf(0f) }
    val baseStroke = 50f

    var rings by remember { mutableStateOf<List<RingData>>(emptyList()) }
    var animationFrame by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(maxWidth, maxHeight) {
            with(density) {
                centerX = maxWidth.toPx() / 2f
                centerY = maxHeight.toPx() / 2f
                maxRadius = maxOf(maxHeight.toPx(), maxWidth.toPx()) / 2f + 200f
            }
        }

        LaunchedEffect(isActive, maxRadius, color) {
            if (!isActive || maxRadius <= 0f) return@LaunchedEffect

            // 预创建 10 个有进度的圆环
            val initialRings = mutableListOf<RingData>()
            val totalCircles = 10
            repeat(totalCircles) { i ->
                val progress = i.toFloat() / totalCircles
                val initialRadius = maxRadius * progress
                val remainingDuration = (2000 * (1 - progress)).toLong()
                initialRings.add(
                    RingData(
                        id = i.toLong(),
                        radius = initialRadius,
                        strokeWidth = baseStroke * (1 + initialRadius / maxRadius),
                        startTime = System.currentTimeMillis() - (2000 - remainingDuration)
                    )
                )
            }

            rings = initialRings
            var nextId = totalCircles.toLong()
            var lastSpawnTime = System.currentTimeMillis()

            while (isActive) {
                val currentTime = System.currentTimeMillis()

                // 每 200ms 生成一个新圆环
                if (currentTime - lastSpawnTime >= 200) {
                    rings = rings + RingData(
                        id = nextId++,
                        radius = 0f,
                        strokeWidth = baseStroke,
                        startTime = currentTime
                    )
                    lastSpawnTime = currentTime
                }

                // 更新所有圆环状态，完成后移除
                rings = rings.mapNotNull { ring ->
                    val progress = ((currentTime - ring.startTime) / 2000f).coerceIn(0f, 1f)
                    if (progress >= 1f) {
                        null
                    } else {
                        ring.copy(
                            radius = maxRadius * progress,
                            strokeWidth = baseStroke * (1 + (maxRadius * progress) / maxRadius)
                        )
                    }
                }

                animationFrame++ // 触发重组
                delay(16) // 约 60fps
            }

            if (!isActive) rings = emptyList()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            rings.forEach { ring ->
                drawCircle(
                    color = color,
                    radius = ring.radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = ring.strokeWidth)
                )
            }
        }
    }
}

private data class RingData(
    val id: Long,
    val radius: Float,
    val strokeWidth: Float,
    val startTime: Long
)

/** 中心白色跳动的心。按参考仓库 BeatingHeart 原样用 Coil 加载 SVG 渲染，
 * 保证样式一致（stroke-linecap/linejoin 等由 SVG 引擎处理，Canvas 手画无法像素对齐）。 */
@Composable
private fun BeatingHeart() {
    val infinite = rememberInfiniteTransition(label = "heart")
    val beatingScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.8f at 500
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // 参考仓库 ConfigManager 的默认配色：白填充，外层深紫红描边，内层亮粉描边。
    val svgData = remember {
        """
        <svg width="500" height="500" viewBox="0 0 500 500" xmlns="http://www.w3.org/2000/svg">
            <!-- 外层心形 -->
            <path d="$OUTER_HEART_D"
                  fill="$HEART_FILL"
                  stroke="$HEART_OUTER_STROKE"
                  stroke-width="80"
                  stroke-linecap="round"
                  stroke-linejoin="round"/>
            <!-- 内层心形 -->
            <path d="$INNER_HEART_D"
                  fill="$HEART_FILL"
                  stroke="$HEART_INNER_STROKE"
                  stroke-width="40"
                  stroke-linecap="round"
                  stroke-linejoin="round"/>
        </svg>
        """.trimIndent().toByteArray(Charsets.UTF_8)
    }

    Box(
        modifier = Modifier
            .fillMaxSize(0.6f)
            .scale(beatingScale),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(svgData).build(),
            contentDescription = "Heart icon",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            imageLoader = imageLoader
        )
    }
}

/** 外层心形 path（原样取自参考仓库 BeatingHeart 的 SVG）。 */
private const val OUTER_HEART_D = "M250.23,148.82c-2.85,-4.23 -35.06,-50.39 -84.46,-48.78c-46.47,1.51 -78.73,44.33 -87.63,73.69C58.28,239.24 125.75,333.15 251.29,400c123.34,-66.36 189.73,-159.66 169.98,-225.23c-8.61,-28.59 -39.46,-71.35 -85.52,-73.69C286,98.55 252.92,144.93 250.23,148.82z"

/** 内层心形 path（原样取自参考仓库 BeatingHeart 的 SVG）。 */
private const val INNER_HEART_D = "M250.52,148.82c-2.85,-4.23 -35.06,-50.39 -84.46,-48.78c-46.47,1.51 -78.73,44.33 -87.63,73.69C58.57,239.24 126.04,333.15 251.58,400c123.34,-66.36 189.73,-159.66 169.98,-225.23c-8.61,-28.59 -39.46,-71.35 -85.52,-73.69C286.29,98.55 253.21,144.93 250.52,148.82z"

private const val HEART_FILL = "#FFFFFF"
private const val HEART_OUTER_STROKE = "#A500A1"
private const val HEART_INNER_STROKE = "#EF36CD"
private val backgroundWhite = Color(0xFFFFFFFF)
private val ringPink = Color(0xFFEF36CD)