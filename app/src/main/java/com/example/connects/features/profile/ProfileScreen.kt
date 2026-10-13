package com.example.connects.features.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.connects.R
import com.example.connects.domain.model.ActivityLevel
import com.example.connects.domain.model.BodyStatusSnapshot
import com.example.connects.domain.model.CalorieAdjustment
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val genderOptions = listOf(
    "male" to "男",
    "female" to "女"
)

private val goalOptions = listOf(
    "lose" to "减脂",
    "gain" to "增肌",
    "maintain" to "维持"
)

private val activityOptions = ActivityLevel.entries.map { it.storageKey to it.label }

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showDetailPage by rememberSaveable { mutableStateOf(false) }

    if (showDetailPage) {
        ProfileDetailPage(
            uiState = uiState,
            onBack = { showDetailPage = false },
            onStartEditing = viewModel::startEditing,
            onCancelEditing = viewModel::cancelEditing,
            onSave = { name, gender, height, weight, waist, age, goal, activity ->
                viewModel.updateUserProfile(
                    name = name,
                    gender = gender,
                    height = height,
                    currentWeight = weight,
                    waistCm = waist,
                    age = age,
                    goal = goal,
                    activityLevel = activity
                )
            }
        )
    } else {
        ProfileLandingPage(
            uiState = uiState,
            onOpenDetails = { showDetailPage = true }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProfileLandingPage(
    uiState: ProfileUiState,
    onOpenDetails: () -> Unit
) {
    Scaffold(

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading && uiState.userProfile == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            val profile = uiState.userProfile
            if (profile == null) {
                Text("个人资料为空")
                return@Column
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDetails),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Avatar(initial = profile.name.firstOrNull()?.uppercaseChar()?.toString() ?: "U")

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "点击查看和编辑基本信息与目标",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.bodyStatus?.let { snapshot ->
                BodyStatusCard(snapshot = snapshot)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前目标", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${profile.dailyCaloricTarget} kcal/day",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isAdaptiveTarget) {
                            "已交给自适应引擎：每 15 天按体重与腰围趋势调整"
                        } else {
                            "第一个月按公式计算，满 1 个月后开始按趋势调整"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.adjustment.happened) {
                CalorieAdjustmentCard(adjustment = uiState.adjustment)
            }
        }
    }
}

/** 上一次热量目标调整的原因，回答「为什么目标变了」。 */
@Composable
private fun CalorieAdjustmentCard(adjustment: CalorieAdjustment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "上次目标调整",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${adjustment.beforeCalories} → ${adjustment.afterCalories} kcal " +
                    "（${if (adjustment.delta > 0) "+" else ""}${adjustment.delta}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = adjustment.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatEpochDay(adjustment.day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

/** 身体状态信息栏：状态 + 一句话结论 + 近 15 天的推断依据。 */
@Composable
private fun BodyStatusCard(snapshot: BodyStatusSnapshot) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "身体状态",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = snapshot.status.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = snapshot.status.hint,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bodyStatusEvidence(snapshot),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (snapshot.settledDay != null) {
                    "每 ${snapshot.windowDays} 天结算一次，下次结算前保持不变"
                } else {
                    "每 ${snapshot.windowDays} 天结算一次，已结算才会固定下来"
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f)
            )
        }
    }
}

private fun bodyStatusEvidence(snapshot: BodyStatusSnapshot): String {
    val settled = snapshot.settledDay?.let { day ->
        "上次结算 ${formatEpochDay(day)}"
    } ?: "尚未结算"

    val intake = snapshot.averageIntake?.let {
        "日均摄入 $it kcal（估算消耗 ${snapshot.referenceTdee}）"
    } ?: "饮食记录 ${snapshot.intakeDays} 天，暂不足"

    val weight = snapshot.weightChangeKg?.let { delta ->
        "体重 ${changeArrow(delta)}${magnitude(delta)} kg"
    } ?: "体重记录 ${snapshot.weightSamples} 条，暂不足"

    val waist = snapshot.waistChangeCm?.let { delta ->
        "腰围 ${changeArrow(delta)}${magnitude(delta)} cm"
    }

    return listOfNotNull(
        "近 ${snapshot.windowDays} 天",
        settled,
        intake,
        weight,
        waist
    ).joinToString(" · ")
}

private fun changeArrow(delta: Float): String = when {
    delta > 0f -> "↑"
    delta < 0f -> "↓"
    else -> "→"
}

private fun magnitude(delta: Float): Double {
    return (abs(delta) * 10).roundToInt() / 10.0
}

private fun formatEpochDay(epochDay: Int): String {
    val date = LocalDate.ofEpochDay(epochDay.toLong())
    return "${date.monthValue}月${date.dayOfMonth}日"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDetailPage(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onSave: (String, String, Float, Float, Float?, Int, String, String) -> Unit
) {
    val profile = uiState.userProfile

    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("male") }
    var age by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("maintain") }
    var activity by rememberSaveable { mutableStateOf(ActivityLevel.DEFAULT.storageKey) }

    LaunchedEffect(profile, uiState.isEditing) {
        if (profile != null && !uiState.isEditing) {
            name = profile.name
            gender = profile.gender
            age = profile.age.toString()
            height = profile.heightCm.toString()
            weight = profile.weightKg.toString()
            waist = uiState.latestWaistCm?.toString() ?: ""
            goal = profile.goal
            activity = ActivityLevel.fromStorage(profile.activityLevel).storageKey
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = onStartEditing) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("个人资料为空")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Avatar(initial = (name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("用户名", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (uiState.isEditing) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("基本信息与健康目标", fontWeight = FontWeight.Bold)

                    OptionDropdownField(
                        label = "性别",
                        options = genderOptions,
                        value = gender,
                        enabled = uiState.isEditing,
                        onValueSelected = { gender = it }
                    )

                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("年龄") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("身高 (cm)") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("体重 (kg)") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 引导页填的初始腰围此前没有任何修改入口，只能等 15 天周期弹窗，
                    // 填错了就只能干等，所以这里补一个随时可改的入口。
                    OutlinedTextField(
                        value = waist,
                        onValueChange = { waist = it },
                        label = { Text("腰围 (cm)") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        supportingText = { Text(stringResource(R.string.waist_measure_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OptionDropdownField(
                        label = "目标",
                        options = goalOptions,
                        value = goal,
                        enabled = uiState.isEditing,
                        onValueSelected = { goal = it }
                    )

                    OptionDropdownField(
                        label = "活动水平",
                        options = activityOptions,
                        value = activity,
                        enabled = uiState.isEditing,
                        onValueSelected = { activity = it }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("每日热量目标", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${profile.dailyCaloricTarget} kcal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCancelEditing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            onSave(
                                name,
                                gender,
                                height.toFloatOrNull() ?: profile.heightCm,
                                weight.toFloatOrNull() ?: profile.weightKg,
                                waist.toFloatOrNull(),
                                age.toIntOrNull() ?: profile.age,
                                goal,
                                activity
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }

            uiState.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Avatar(initial: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdownField(
    label: String,
    options: List<Pair<String, String>>,
    value: String,
    enabled: Boolean,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.firstOrNull { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (rawValue, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onValueSelected(rawValue)
                        expanded = false
                    }
                )
            }
        }
    }
}
