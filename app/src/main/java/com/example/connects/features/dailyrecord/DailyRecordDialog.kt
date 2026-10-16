package com.example.connects.features.dailyrecord

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.connects.R
import com.example.connects.domain.model.WeightMeasurementContext
import com.example.connects.ui.components.JournalUnderlineField
import com.example.connects.ui.theme.JournalTitle

/**
 * 进入应用时的记录弹窗：体重（每天）+ 腰围（每 15 天）。
 *
 * 刻意不可随手关闭：不许点背景、不许按返回键，避免误触丢失记录；
 * 只有把到期的字段全部记录成功（ViewModel 观察到当天有体重、周期内有腰围）之后弹窗才会消失。
 * 想跳过只能点「跳过」按钮——只跳过本次会话，不记录数据，下次进入应用会重新弹出。
 *
 * 版式沿用批准稿：纸感卡片、下划线输入配衬线大数字、主按钮用墨色而非彩色。
 */
@Composable
fun DailyRecordDialog(viewModel: DailyRecordViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val needsWeight by viewModel.needsWeightInput.collectAsState()
    val needsWaist by viewModel.needsWaistInput.collectAsState()

    val showWeight = needsWeight == true
    val showWaist = needsWaist == true

    val title = when {
        showWeight && showWaist -> "记录身体数据"
        showWaist -> "记录腰围"
        else -> "记录今日体重"
    }
    val caption = buildString {
        if (showWeight) append("每天一次，用于校准热量目标与身体状态结算。")
        if (showWeight && showWaist) append("\n")
        if (showWaist) append("每 15 天量一次，用来判断长的是脂肪还是肌肉。")
    }

    Dialog(
        onDismissRequest = { /* 必须记录完才能进入应用，这里不做任何事 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = caption,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (showWeight) {
                    JournalUnderlineField(
                        label = "今日体重 · KG",
                        value = uiState.weightInput,
                        isError = uiState.error != null,
                        onValueChange = viewModel::updateWeightInput,
                        modifier = Modifier.padding(top = 22.dp)
                    )
                    WeighContextSelector(
                        selected = uiState.measurementContext,
                        onSelect = viewModel::updateWeightContext
                    )
                }

                if (showWaist) {
                    JournalUnderlineField(
                        label = "今日腰围 · CM",
                        value = uiState.waistInput,
                        isError = uiState.error != null,
                        onValueChange = viewModel::updateWaistInput,
                        supportingText = stringResource(R.string.waist_measure_hint),
                        modifier = Modifier.padding(top = 22.dp)
                    )
                }

                uiState.error?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp)
                        .height(50.dp),
                    enabled = !uiState.isSaving,
                    // 墨色主按钮：不用彩色，保存这个动作不需要喊叫
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        )
                    } else {
                        Text(
                            text = "保存记录",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 只跳过本次会话，不记录数据；下次进入应用会重新弹出提醒。
                TextButton(
                    onClick = viewModel::skip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    enabled = !uiState.isSaving
                ) {
                    Text(
                        text = "跳过，下次再说",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (showWaist) {
                        "跳过仅本次有效 · 腰围每 15 天提醒一次"
                    } else {
                        "跳过仅本次有效，下次进入应用会重新提醒"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 本次称重的测量时段选择。并非人人都能早上空腹称，餐后/练后也可记录，
 * 打上时段标签便于自查，趋势计算不因时段过滤样本。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeighContextSelector(
    selected: WeightMeasurementContext,
    onSelect: (WeightMeasurementContext) -> Unit
) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Text(
            text = "这次称重是什么时段？",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeightMeasurementContext.entries.forEach { context ->
                val isSelected = context == selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = context.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelect(context) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
