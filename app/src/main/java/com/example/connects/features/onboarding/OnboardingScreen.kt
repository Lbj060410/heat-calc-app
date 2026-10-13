package com.example.connects.features.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connects.R
import com.example.connects.domain.model.ActivityAudience
import com.example.connects.domain.model.ActivityLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1) / 6f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
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
                    .height(56.dp),
                enabled = if (pagerState.currentPage < 3) isValid else true,
                shape = RoundedCornerShape(16.dp)
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
        Text(text = "请填写身高和腰围", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = height,
            onValueChange = onHeightChange,
            modifier = Modifier.fillMaxWidth(0.7f),
            label = { Text("身高") },
            trailingIcon = { Text("cm", fontWeight = FontWeight.Bold) },
            placeholder = { Text("例如 175", color = Color.Gray) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = waist,
            onValueChange = onWaistChange,
            modifier = Modifier.fillMaxWidth(0.7f),
            label = { Text("腰围") },
            trailingIcon = { Text("cm", fontWeight = FontWeight.Bold) },
            placeholder = { Text("例如 80", color = Color.Gray) },
            supportingText = { Text(stringResource(R.string.waist_measure_hint)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
            shape = RoundedCornerShape(12.dp)
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
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(0.7f),
            trailingIcon = { Text(unit, fontWeight = FontWeight.Bold) },
            placeholder = { Text(placeholder, color = Color.Gray) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
            shape = RoundedCornerShape(12.dp)
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
        Text(text = "请选择你的性别", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        options.forEach { (value, label) ->
            val isSelected = selectedValue == value
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 8.dp)
                    .clickable { onValueSelected(value) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
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
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "请选择你的活动水平", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "分健身人群和大众两类，选最接近真实情况的一项；这个选项会直接影响每日热量目标。",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        ActivityAudience.entries.forEach { audience ->
            Text(
                text = audience.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp)
            )
            ActivityLevel.forAudience(audience).forEach { level ->
                val isSelected = selectedValue == level
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onValueSelected(level) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = level.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = level.description,
                                fontSize = 12.sp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    Color.Gray
                                }
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { onValueSelected(level) }
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
        Text(text = "请选择你的目标", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        targets.forEach { target ->
            val isSelected = selectedValue == target
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 8.dp)
                    .clickable { onValueSelected(target) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(
                    text = targetLabels[target] ?: target,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
