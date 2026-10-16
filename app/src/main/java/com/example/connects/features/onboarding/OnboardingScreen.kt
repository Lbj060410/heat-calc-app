package com.example.connects.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connects.R
import com.example.connects.domain.model.ActivityAudience
import com.example.connects.domain.model.ActivityLevel
import com.example.connects.ui.components.JournalUnderlineField
import com.example.connects.ui.components.RulerMeter
import com.example.connects.ui.components.hairline
import com.example.connects.ui.theme.JournalTitle
import com.example.connects.ui.theme.SerifFamily
import kotlinx.coroutines.launch

/**
 * 引导页 —— 手账风：刻度尺进度 + 衬线大标题 + 下划线居中输入 + 印章式选项块。
 * 六步流程与每一步的校验逻辑不变。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val coroutineScope = rememberCoroutineScope()

    // 系统返回键：回到上一步；第 0 步不拦截，允许直接退出。
    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 刻度尺进度：第几步走到第几个刻度
            RulerMeter(
                progress = (pagerState.currentPage + 1) / 6f,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> HeightWaistStepPage(
                        height = viewModel.heightInput,
                        onHeightChange = { viewModel.updateHeight(it) },
                        waist = viewModel.waistInput,
                        onWaistChange = { viewModel.updateWaist(it) }
                    )
                    1 -> InputStepPage(
                        title = "请填写体重",
                        unit = "kg",
                        value = viewModel.weightInput,
                        onValueChange = { viewModel.updateWeight(it) },
                        placeholder = "例如 65.5"
                    )
                    2 -> InputStepPage(
                        title = "请填写年龄",
                        unit = "岁",
                        value = viewModel.ageInput,
                        onValueChange = { viewModel.updateAge(it) },
                        placeholder = "例如 25"
                    )
                    3 -> GenderStepPage(
                        selectedValue = viewModel.genderInput,
                        onValueSelected = { viewModel.updateGender(it) }
                    )
                    4 -> ActivityLevelStepPage(
                        selectedValue = viewModel.selectedActivityLevel,
                        onValueSelected = { viewModel.updateActivityLevel(it) }
                    )
                    5 -> TargetStepPage(
                        selectedValue = viewModel.selectedGoal,
                        onValueSelected = { viewModel.updateGoal(it) }
                    )
                }
            }

            val isValid = viewModel.isInputValid()

            Button(
                onClick = {
                    if (pagerState.currentPage < 5) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        viewModel.submitProfile(onComplete = onFinished)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = if (pagerState.currentPage < 3) isValid else true,
                shape = RoundedCornerShape(14.dp),
                // 墨色主按钮：进入应用这个动作不需要喊叫
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = if (pagerState.currentPage == 5) "完成并进入应用" else "下一步",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 身高 + 腰围放在同一页。
 *
 * 腰围是身体状态判断里区分「长脂肪还是长肌肉」的关键信号，必须在引导阶段就拿到基线，
 * 否则第一个 15 天周期结算时没有可对比的腰围。
 */
@Composable
fun HeightWaistStepPage(
    height: String,
    onHeightChange: (String) -> Unit,
    waist: String,
    onWaistChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请填写身高和腰围",
            style = JournalTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(36.dp))

        JournalUnderlineField(
            label = "身高",
            value = height,
            onValueChange = onHeightChange,
            unit = "cm",
            placeholder = "例如 175",
            centered = true,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(0.72f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        JournalUnderlineField(
            label = "腰围",
            value = waist,
            onValueChange = onWaistChange,
            unit = "cm",
            placeholder = "例如 80",
            centered = true,
            supportingText = stringResource(R.string.waist_measure_hint),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(0.72f)
        )
    }
}

@Composable
fun InputStepPage(
    title: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = JournalTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(36.dp))

        JournalUnderlineField(
            label = title.removePrefix("请填写"),
            value = value,
            onValueChange = onValueChange,
            unit = unit,
            placeholder = placeholder,
            centered = true,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(0.72f)
        )
    }
}

@Composable
fun GenderStepPage(
    selectedValue: String,
    onValueSelected: (String) -> Unit
) {
    // 性别直接决定 BMR 公式（男 +5 / 女 -161），必须让用户选，不能默认成男性。
    val options = listOf("male" to "男", "female" to "女")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请选择你的性别",
            style = JournalTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(32.dp))

        options.forEach { (value, label) ->
            JournalOptionBlock(
                text = label,
                selected = selectedValue == value,
                onClick = { onValueSelected(value) }
            )
        }
    }
}

@Composable
fun ActivityLevelStepPage(
    selectedValue: ActivityLevel,
    onValueSelected: (ActivityLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "请选择你的活动水平",
            style = JournalTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "分健身人群和大众两类，选最接近真实情况的一项；这个选项会直接影响每日热量目标。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        ActivityAudience.entries.forEach { audience ->
            Text(
                text = audience.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
            )
            ActivityLevel.forAudience(audience).forEachIndexed { index, level ->
                val isSelected = selectedValue == level
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueSelected(level) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .height(30.dp)
                                .width(3.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.hairline
                                )
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = level.label,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                fontFamily = if (isSelected) SerifFamily else null,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = level.description,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { onValueSelected(level) }
                        )
                    }
                    if (index < ActivityLevel.forAudience(audience).lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.hairline)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TargetStepPage(
    selectedValue: String,
    onValueSelected: (String) -> Unit
) {
    val targets = listOf("lose", "gain", "maintain")
    val targetLabels = mapOf(
        "lose" to "减脂",
        "gain" to "增肌",
        "maintain" to "维持体重"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请选择你的目标",
            style = JournalTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(32.dp))

        targets.forEach { target ->
            JournalOptionBlock(
                text = targetLabels[target] ?: target,
                selected = selectedValue == target,
                onClick = { onValueSelected(target) }
            )
        }
    }
}

/** 印章式选项块：选中 = 绿底白衬线字，像盖了个章；未选中只有发丝框。 */
@Composable
private fun JournalOptionBlock(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .padding(vertical = 7.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.hairline
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontFamily = if (selected) SerifFamily else null,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
