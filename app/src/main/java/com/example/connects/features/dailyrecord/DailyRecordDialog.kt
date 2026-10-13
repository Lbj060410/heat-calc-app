package com.example.connects.features.dailyrecord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.connects.R

/**
 * 进入应用时的记录弹窗：体重（每天）+ 腰围（每 15 天）。
 *
 * 刻意不可随手关闭：不许点背景、不许按返回键，避免误触丢失记录；
 * 只有把到期的字段全部记录成功（ViewModel 观察到当天有体重、周期内有腰围）之后弹窗才会消失。
 * 想跳过只能点「跳过」按钮——只跳过本次会话，不记录数据，下次进入应用会重新弹出。
 */
@Composable
fun DailyRecordDialog(viewModel: DailyRecordViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val needsWeight by viewModel.needsWeightInput.collectAsState()
    val needsWaist by viewModel.needsWaistInput.collectAsState()

    val showWeight = needsWeight == true
    val showWaist = needsWaist == true

    Dialog(
        onDismissRequest = { /* 必须记录完才能进入应用，这里不做任何事 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "记录身体数据",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        if (showWeight) append("每天记录一次体重，才能看到体重变化趋势。")
                        if (showWeight && showWaist) append("\n")
                        if (showWaist) append("每 15 天量一次腰围，用来判断长的是脂肪还是肌肉。")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showWeight) {
                    OutlinedTextField(
                        value = uiState.weightInput,
                        onValueChange = viewModel::updateWeightInput,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("体重 (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = uiState.error != null
                    )
                }

                if (showWaist) {
                    OutlinedTextField(
                        value = uiState.waistInput,
                        onValueChange = viewModel::updateWaistInput,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("腰围 (cm)") },
                        supportingText = { Text(stringResource(R.string.waist_measure_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = uiState.error != null
                    )
                }

                uiState.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存并进入应用")
                    }
                }

                // 只跳过本次会话，不记录数据；下次进入应用会重新弹出提醒。
                TextButton(
                    onClick = viewModel::skip,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text("跳过，下次再提醒")
                }
            }
        }
    }
}
