package com.antgskds.calendarassistant.ui.page_display.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.ui.components.AppCard as Card
import com.antgskds.calendarassistant.ui.components.AppModalBottomSheet
import com.antgskds.calendarassistant.ui.components.CenteredDialogTitle
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private typealias CalendarInfo = com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager.CalendarInfo

@Composable
fun ScheduleSettingsPage(
    viewModel: SettingsViewModel,
    onNavigateTo: (SettingsDestination) -> Unit,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val availableSyncCalendars by viewModel.availableSyncCalendars.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as? App
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSourceCalendarSheet by remember { mutableStateOf(false) }
    var showEventDurationPicker by remember { mutableStateOf(false) }

    val selectedSourceCalendars by remember(syncStatus.sourceCalendarIds, availableSyncCalendars) {
        derivedStateOf {
            availableSyncCalendars.filter { syncStatus.sourceCalendarIds.contains(it.id) }
        }
    }
    val selectedSourceSummary by remember(syncStatus.sourceCalendarIds, selectedSourceCalendars) {
        derivedStateOf {
            formatSelectedCalendarSummary(syncStatus.sourceCalendarIds, selectedSourceCalendars)
        }
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.enableCalendarSyncAndSyncNow { result ->
                viewModel.refreshSyncCalendars()
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("日历同步已开启，并已立即同步")
                } else {
                    snackbarHostState.showSnackbar("日历同步开启失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
                }
            }
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "需要日历权限才能使用同步功能",
                    actionLabel = "去设置",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
    }

    val requestCalendarPermission = {
        showPermissionDialog = false
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            emptyArray()
        }
        calendarPermissionLauncher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSyncStatus()
        viewModel.refreshSyncCalendars()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSyncStatus()
                viewModel.refreshSyncCalendars()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardValueStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(bottom = 80.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("日程", style = sectionTitleStyle)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SwitchSettingItem(
                            title = "日历同步",
                            subtitle = "将课程和日程同步到系统日历",
                            checked = syncStatus.isEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (app?.permissionCenter?.hasCalendarPermissions(context) == true) {
                                        viewModel.enableCalendarSyncAndSyncNow { result ->
                                            if (result.isSuccess) {
                                                showToast("日历同步已开启，并已立即同步")
                                            } else {
                                                showToast("日历同步开启失败", ToastType.ERROR)
                                            }
                                        }
                                    } else {
                                        showPermissionDialog = true
                                    }
                                } else {
                                    viewModel.toggleCalendarSync(false)
                                    showToast("日历同步已关闭")
                                }
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AnimatedVisibility(
                            visible = syncStatus.isEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                ActionSettingItem(
                                    title = "同步来源日历",
                                    subtitle = selectedSourceSummary,
                                    value = if (syncStatus.sourceCalendarIds.isEmpty()) {
                                        "未选择"
                                    } else {
                                        "${syncStatus.sourceCalendarIds.size} 个"
                                    },
                                    enabled = syncStatus.hasPermission,
                                    onClick = {
                                        if (syncStatus.hasPermission) {
                                            showSourceCalendarSheet = true
                                        } else {
                                            showPermissionDialog = true
                                        }
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                SliderSettingItem(
                                    title = "兜底同步频率",
                                    subtitle = "仅作兜底轮询，优先即时监听",
                                    value = syncStatus.syncIntervalSeconds.toFloat(),
                                    onValueChange = { seconds ->
                                        viewModel.updateSyncIntervalSeconds(seconds.toInt())
                                    },
                                    valueRange = 1f..300f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "s"
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SwitchSettingItem(
                            title = "自动归档",
                            subtitle = "日程过期后立即自动归档",
                            checked = settings.autoArchiveEnabled,
                            onCheckedChange = { isChecked ->
                                viewModel.updatePreference(autoArchive = isChecked)
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        EventDurationSettingItem(
                            title = "日程默认持续时间",
                            subtitle = "所有识别日程都按该时长生成结束时间",
                            durationMinutes = settings.defaultEventDurationMinutes,
                            onClick = { showEventDurationPicker = true },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle,
                            cardValueStyle = cardValueStyle
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        ActionSettingItem(
                            title = "日程颜色",
                            subtitle = "自定义新建和识别日程使用的色盘",
                            value = "${settings.eventColorPaletteHex.size} 个",
                            enabled = true,
                            onClick = { onNavigateTo(SettingsDestination.ScheduleColors) },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle,
                            cardValueStyle = cardValueStyle
                        )
                    }
                }
            }

            if (showEventDurationPicker) {
                EventDurationPickerDialog(
                    selectedDuration = settings.defaultEventDurationMinutes,
                    onDismiss = { showEventDurationPicker = false },
                    onConfirm = { minutes ->
                        viewModel.updatePreference(defaultEventDurationMinutes = minutes)
                        showEventDurationPicker = false
                    }
                )
            }

            PredictiveFloatingActionCard(
                visible = showPermissionDialog,
                title = "需要日历权限",
                content = "为了让您在系统日历中查看和管理课程与日程，需要授予应用读取和写入日历的权限。",
                confirmText = "授予权限",
                dismissText = "取消",
                isDestructive = false,
                isLoading = false,
                predictiveBackEnabled = settings.predictiveBackEnabled,
                onConfirm = requestCalendarPermission,
                onDismiss = { showPermissionDialog = false }
            )

            if (showSourceCalendarSheet) {
                SourceCalendarPickerSheet(
                    calendars = availableSyncCalendars,
                    initialSelection = syncStatus.sourceCalendarIds.toSet(),
                    onDismiss = { showSourceCalendarSheet = false },
                    onConfirm = { selectedIds ->
                        viewModel.updateSourceCalendars(selectedIds) { result ->
                            if (result.isSuccess) {
                                showToast("同步来源日历已更新")
                                showSourceCalendarSheet = false
                            } else {
                                showToast("更新同步来源失败", ToastType.ERROR)
                            }
                        }
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp + bottomInset),
                snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCalendarPickerSheet(
    calendars: List<CalendarInfo>,
    initialSelection: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedIds by remember(initialSelection, calendars) {
        mutableStateOf(initialSelection.intersect(calendars.map { it.id }.toSet()))
    }
    val haptics = rememberAppHaptics()
    val groupedCalendars = remember(calendars) {
        calendars.groupBy { calendar ->
            buildAccountGroupTitle(calendar)
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text("同步来源日历", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "勾选后，这些系统日历中的日程会同步到 APP；在 APP 中修改导入日程时，会回写到原日历。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (calendars.isEmpty()) {
                Text(
                    text = "当前没有可读取的系统日历。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(onClick = { haptics.selection(); selectedIds = calendars.map { it.id }.toSet() }) {
                    Text("全选")
                }

                groupedCalendars.forEach { (groupTitle, groupCalendars) ->
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    groupCalendars.forEach { calendar ->
                        val checked = selectedIds.contains(calendar.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.selection()
                                    selectedIds = if (checked) {
                                        selectedIds - calendar.id
                                    } else {
                                        selectedIds + calendar.id
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    haptics.selection()
                                    selectedIds = if (isChecked) {
                                        selectedIds + calendar.id
                                    } else {
                                        selectedIds - calendar.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = buildCalendarMetaLine(calendar),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { haptics.confirm(); onConfirm(calendars.filter { selectedIds.contains(it.id) }.map { it.id }) },
                enabled = calendars.isNotEmpty() && selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}

private fun formatSelectedCalendarSummary(
    selectedIds: List<Long>,
    selectedCalendars: List<CalendarInfo>
): String {
    if (selectedIds.isEmpty()) {
        return "请选择需要从系统同步进 APP 的日历"
    }

    if (selectedCalendars.isEmpty()) {
        return "已选择 ${selectedIds.size} 个日历"
    }

    val names = selectedCalendars.map { it.name }.distinct()
    return if (names.size <= 2) {
        names.joinToString("、")
    } else {
        names.take(2).joinToString("、") + " 等 ${names.size} 个日历"
    }
}

private fun buildAccountGroupTitle(
    calendar: CalendarInfo
): String {
    val accountName = calendar.accountName?.takeIf { it.isNotBlank() } ?: "本地账户"
    val accountType = calendar.accountType?.takeIf { it.isNotBlank() }
    return if (accountType != null) {
        "$accountName  ($accountType)"
    } else {
        accountName
    }
}

private fun buildCalendarMetaLine(
    calendar: CalendarInfo
): String {
    val tags = mutableListOf<String>()
    if (!calendar.isVisible) tags += "已隐藏"
    if (!calendar.syncEvents) tags += "未启用系统同步"
    if (!calendar.isWritable) tags += "只读"
    return if (tags.isEmpty()) {
        "ID: ${calendar.id}"
    } else {
        "ID: ${calendar.id}  ${tags.joinToString(" · ")}"
    }
}
