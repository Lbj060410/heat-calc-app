package com.example.connects.features.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import com.example.connects.ui.components.NoteBlock
import com.example.connects.ui.components.SectionHead
import com.example.connects.ui.components.UserAvatar
import com.example.connects.ui.components.hairline
import com.example.connects.ui.components.ledgerNumber
import com.example.connects.ui.theme.JournalBig
import com.example.connects.ui.theme.JournalStatement
import com.example.connects.ui.theme.JournalTitle
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

    // 系统返回键：详情页先退回落地页；正在编辑则先退出编辑，避免误触丢掉已填内容。
    BackHandler(enabled = showDetailPage) {
        if (uiState.isEditing) viewModel.cancelEditing() else showDetailPage = false
    }

    if (showDetailPage) {
        ProfileDetailPage(
            uiState = uiState,
            onBack = { showDetailPage = false },
            onStartEditing = viewModel::startEditing,
            onCancelEditing = viewModel::cancelEditing,
            onPickAvatar = viewModel::saveAvatar,
            onRemoveAvatar = viewModel::clearAvatar,
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

/**
 * 「我的」落地页 —— 状态先行的手账版式。
 *
 * 印章头像开页；身体状态 / 每日目标 / 上次调整各是一个左色条信息区：
 * 绿条说身体状态，墨条说每日目标，陶土条说上次调整依据。
 * 没有彩色大区块，靠一条 3px 色线分清层级。
 */
@Composable
private fun ProfileLandingPage(
    uiState: ProfileUiState,
    onOpenDetails: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 外层 MainTabsScreen 的 Scaffold 已经加过状态栏 inset，这里不再叠一遍
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            if (uiState.isLoading && uiState.userProfile == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 80.dp)
                        .align(Alignment.CenterHorizontally)
                )
                return@Column
            }

            val profile = uiState.userProfile
            if (profile == null) {
                Text(
                    text = "个人资料为空",
                    modifier = Modifier.padding(top = 80.dp).align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val genderLabel = genderOptions.firstOrNull { it.first == profile.gender }?.second ?: profile.gender
            ProfileHeadRow(
                initial = profile.name.firstOrNull()?.toString() ?: "我",
                name = profile.name,
                avatarPath = uiState.avatarPath,
                subline = "$genderLabel · ${profile.age} 岁 · ${profile.heightCm.toInt()} cm · ${profile.weightKg} kg",
                onOpenDetails = onOpenDetails
            )

            uiState.bodyStatus?.let { snapshot ->
                BodyStatusNote(snapshot = snapshot)
            }

            DailyTargetNote(
                targetCalories = profile.dailyCaloricTarget,
                isAdaptive = uiState.isAdaptiveTarget
            )

            if (uiState.adjustment.happened) {
                AdjustmentNote(adjustment = uiState.adjustment)
            }

            SectionHead(
                title = "快捷入口",
                latin = "SETTINGS",
                modifier = Modifier.padding(top = 30.dp, bottom = 4.dp)
            )
            LinkRow(
                title = "修改腰围",
                subtitle = "随时可改，覆盖当日记录",
                showDivider = true,
                onClick = onOpenDetails
            )
            LinkRow(
                title = "体重记录",
                subtitle = "每天一次，用于趋势结算",
                showDivider = false,
                onClick = onOpenDetails
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** 页眉：头像 + 姓名 + 一行身体速览，整行可点，进详情页编辑。 */
@Composable
private fun ProfileHeadRow(
    initial: String,
    name: String,
    avatarPath: String?,
    subline: String,
    onOpenDetails: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails)
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarPath = avatarPath, initial = initial, size = 52.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = name,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subline,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text(
            text = "›",
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 身体状态：绿条信息区，一句话结论 + 推断依据。 */
@Composable
private fun BodyStatusNote(snapshot: BodyStatusSnapshot) {
    val settledLine = if (snapshot.settledDay != null) {
        "每 ${snapshot.windowDays} 天结算一次，下次结算前保持不变"
    } else {
        "每 ${snapshot.windowDays} 天结算一次，已结算才会固定下来"
    }

    NoteBlock(
        ruleColor = MaterialTheme.colorScheme.primary,
        tagColor = MaterialTheme.colorScheme.primary,
        tag = "身体状态 · 近 ${snapshot.windowDays} 天",
        statement = {
            Text(
                text = snapshot.status.label,
                style = JournalStatement,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        hint = snapshot.status.hint,
        evidence = bodyStatusEvidence(snapshot) + "\n" + settledLine
    )
}

/** 每日目标：墨条信息区，衬线大数字。 */
@Composable
private fun DailyTargetNote(targetCalories: Int, isAdaptive: Boolean) {
    NoteBlock(
        ruleColor = MaterialTheme.colorScheme.onSurface,
        tagColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tag = "每日目标 · 自适应",
        statement = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = ledgerNumber(targetCalories),
                    style = JournalBig,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "千卡 / 天",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 7.dp)
                )
            }
        },
        hint = if (isAdaptive) {
            "已交给自适应引擎：每 15 天按体重与腰围趋势调整。"
        } else {
            "第一个月按公式计算，满 1 个月后开始按趋势调整。"
        }
    )
}

/** 上次目标调整：陶土条信息区，回答「为什么目标变了」。 */
@Composable
private fun AdjustmentNote(adjustment: CalorieAdjustment) {
    val sign = if (adjustment.delta > 0) "+" else ""
    NoteBlock(
        ruleColor = MaterialTheme.colorScheme.tertiary,
        tagColor = MaterialTheme.colorScheme.tertiary,
        tag = "上次目标调整 · ${formatEpochDay(adjustment.day)}",
        statement = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${ledgerNumber(adjustment.beforeCalories)} → ${ledgerNumber(adjustment.afterCalories)}",
                    style = JournalStatement,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "千卡（$sign${adjustment.delta}）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                )
            }
        },
        hint = adjustment.reason
    )
}

/** 快捷入口行：标题 + 说明 + 右侧箭头，行间发丝线。 */
@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = "›",
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.hairline)
            )
        }
    }
}

private fun bodyStatusEvidence(snapshot: BodyStatusSnapshot): String {
    val settled = snapshot.settledDay?.let { day ->
        "上次结算 ${formatEpochDay(day)}"
    } ?: "尚未结算"

    val intake = snapshot.averageIntake?.let {
        "日均摄入 $it 千卡（估算消耗 ${snapshot.referenceTdee}）"
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

/**
 * 个人资料详情页 —— 手账风：衬线标题栏、去卡片分节排版，
 * 目标区用墨条大数字，保存按钮用墨色。表单字段保留 Material 输入框保证可用性。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDetailPage(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onPickAvatar: (Uri) -> Unit,
    onRemoveAvatar: () -> Unit,
    onSave: (String, String, Float, Float, Float?, Int, String, String) -> Unit
) {
    val profile = uiState.userProfile
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickAvatar) }

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
        containerColor = MaterialTheme.colorScheme.background,
        // 外层 MainTabsScreen 的 Scaffold 已经加过状态栏 inset，这里不再叠一遍
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "个人资料",
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (!uiState.isEditing) {
                    IconButton(onClick = onStartEditing) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
            }
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
                .padding(horizontal = 22.dp)
        ) {
            // 头部：头像 + 用户名；点头像直接唤起系统相册换照片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    avatarPath = uiState.avatarPath,
                    initial = name.firstOrNull()?.toString() ?: "我",
                    size = 52.dp,
                    modifier = Modifier.clickable {
                        pickAvatarLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = "用户名",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.isEditing) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            singleLine = true
                        )
                    } else {
                        Text(
                            text = name,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "点击头像可更换照片",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                if (uiState.avatarPath != null) {
                    TextButton(onClick = onRemoveAvatar) {
                        Text(
                            text = "移除头像",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionHead(
                title = "基本信息与健康目标",
                latin = "PROFILE",
                modifier = Modifier.padding(top = 28.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

            NoteBlock(
                ruleColor = MaterialTheme.colorScheme.onSurface,
                tagColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tag = "每日热量目标",
                statement = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = ledgerNumber(profile.dailyCaloricTarget),
                            style = JournalBig,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "千卡 / 天",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 7.dp)
                        )
                    }
                },
                modifier = Modifier.padding(top = 10.dp)
            )

            if (uiState.isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onCancelEditing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("保存", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            uiState.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
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
