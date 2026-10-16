package com.example.connects.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.connects.ui.theme.JournalInput
import com.example.connects.ui.theme.JournalSeal
import java.io.File
import java.time.LocalDate

/** 发丝分隔线：点线色打底、降透明度，光暗两种主题都成立。 */
val androidx.compose.material3.ColorScheme.hairline: Color
    get() = outlineVariant.copy(alpha = 0.55f)

/** 千位分隔的账目数字：1,214。 */
fun ledgerNumber(value: Int): String = String.format("%,d", value)

/** 克数账目：120 / 210 g。 */
fun ledgerAmount(consumed: Float, target: Float): String =
    "${String.format("%.0f", consumed)} / ${String.format("%.0f", target)} g"

/** 毫克账目（钠）：1,240 / 2,000 mg。 */
fun ledgerMilligrams(consumed: Float, target: Float): String =
    "${String.format("%,.0f", consumed)} / ${String.format("%,.0f", target)} mg"

/** 「10月13日 · 周二」式的日期行。 */
fun formatDateLine(date: LocalDate): String {
    val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return "${date.monthValue}月${date.dayOfMonth}日 · ${weekNames[date.dayOfWeek.value - 1]}"
}

/** HH:mm。 */
fun formatClockTime(epochMillis: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = epochMillis
    return String.format(
        "%02d:%02d",
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE)
    )
}

/**
 * 点线引导：账单菜单式的「名字 ······ 金额」中间那段。
 * 用 weight(1f) 撑开，画一排小圆点。
 */
@Composable
fun DottedLeader(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .height(10.dp)
            .drawBehind {
                val dot = 2.4.dp.toPx()
                val gap = 4.6.dp.toPx()
                val y = size.height / 2f
                var x = 0f
                while (x + dot <= size.width) {
                    drawCircle(
                        color = dotColor,
                        radius = dot / 2f,
                        center = Offset(x + dot / 2f, y)
                    )
                    x += dot + gap
                }
            }
    )
}

/** 区块标题：衬线中文 + 极小号拉丁注记。 */
@Composable
fun SectionHead(title: String, latin: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = com.example.connects.ui.theme.SerifFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.padding(start = 10.dp))
        Text(
            text = latin,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 印章式头像：深绿方块 + 衬线单字，微微倾斜，像盖上去的章。 */
@Composable
fun SealAvatar(initial: String, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = JournalSeal,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * 用户头像：设置过照片就显示圆角照片（印章字垫底，图加载失败也不空），
 * 没设置退回印章。
 */
@Composable
fun UserAvatar(
    avatarPath: String?,
    initial: String,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    if (avatarPath == null) {
        SealAvatar(initial = initial, size = size, modifier = modifier)
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = JournalSeal,
            color = MaterialTheme.colorScheme.onPrimary
        )
        AsyncImage(
            model = File(avatarPath),
            contentDescription = "头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 手账贴纸式虚线按钮：「＋ 添加食物」。 */
@Composable
fun DashedAddButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 刻度尺进度：一条底线 + 十分位刻度 + 填充 + 圆形游标，
 * 替代通用 ProgressIndicator，呼应「手账量尺」的表达。
 *
 * @param progress 0..1，超出按 1 封顶（超支状态由外部文案表达）
 */
@Composable
fun RulerMeter(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val p = progress.coerceIn(0f, 1f)
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val fillColor = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val paperColor = MaterialTheme.colorScheme.background

    Canvas(modifier = modifier.fillMaxWidth().height(30.dp)) {
        val lineWidth = size.width
        val lineY = size.height - 10.dp.toPx() // 底线中心
        val stroke = 2.dp.toPx()

        // 底线
        drawLine(lineColor, Offset(0f, lineY), Offset(lineWidth, lineY), stroke, StrokeCap.Round)
        // 填充
        drawLine(fillColor, Offset(0f, lineY), Offset(lineWidth * p, lineY), stroke, StrokeCap.Round)

        // 十分位刻度：两端高、50% 更高
        for (i in 0..10) {
            val x = lineWidth * i / 10f
            val h = when {
                i == 0 || i == 10 -> 13.dp.toPx()
                i == 5 -> 9.dp.toPx()
                else -> 5.dp.toPx()
            }
            drawLine(tickColor, Offset(x, lineY - h), Offset(x, lineY), 1.5.dp.toPx(), StrokeCap.Round)
        }

        // 游标：绿环 + 纸芯
        if (p > 0f) {
            val cx = lineWidth * p
            drawCircle(fillColor, radius = 7.dp.toPx(), center = Offset(cx, lineY))
            drawCircle(paperColor, radius = 3.dp.toPx(), center = Offset(cx, lineY))
        }
    }
}

/**
 * 通用下划线输入：小号标签 + 衬线数字 + 一条底线。
 * 弹窗（每日记录 / 分量 / 自定义食物）与引导页共用，替代描边输入框。
 */
@Composable
fun JournalUnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    centered: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    textStyle: TextStyle = JournalInput
) {
    val underlineColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    val alignment = if (centered) TextAlign.Center else TextAlign.Start

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = textStyle.copy(
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = alignment
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            textAlign = alignment
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (unit != null) {
                Text(
                    text = unit,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(1.5.dp)
                .background(underlineColor)
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** 左色条信息区：「我的」页的状态 / 目标 / 调整依据都用这一种排版。 */
@Composable
fun NoteBlock(
    ruleColor: Color,
    tagColor: Color,
    tag: String,
    statement: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    evidence: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(ruleColor)
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = tagColor
            )
            Box(modifier = Modifier.padding(top = 6.dp)) { statement() }
            if (hint != null) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (evidence != null) {
                Text(
                    text = evidence,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }
    }
}
