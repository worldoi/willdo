package com.antgskds.calendarassistant.ui.page_display.settings

import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.ai.AiPrompts
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.data.model.AppBackupOptions
import com.antgskds.calendarassistant.ui.components.AppCard
import com.antgskds.calendarassistant.ui.components.AppModalBottomSheet
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsPage(viewModel: SettingsViewModel, mainViewModel: MainViewModel, uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentSettings by viewModel.settings.collectAsState()
    val haptics = rememberAppHaptics(currentSettings.hapticFeedbackEnabled)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var showBackupExportSheet by remember { mutableStateOf(false) }
    var showBackupImportSheet by remember { mutableStateOf(false) }
    var exportOptions by remember { mutableStateOf(AppBackupOptions(includeEvents = true)) }
    var importOptions by remember { mutableStateOf(AppBackupOptions(includeEvents = true, includeSettings = true, includeAttachments = true, includePrompts = true)) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportIsZip by remember { mutableStateOf(false) }
    val promptLocalVersion by mainViewModel.promptLocalVersion.collectAsState()
    val promptSource by mainViewModel.promptSource.collectAsState()
    val promptCheckInProgress by mainViewModel.promptCheckInProgress.collectAsState()
    var attachmentCount by remember { mutableIntStateOf(0) }
    var attachmentSizeText by remember { mutableStateOf(EventAttachmentManager.formatSize(0L)) }

    LaunchedEffect(showBackupExportSheet, showBackupImportSheet) {
        withContext(Dispatchers.IO) {
            val count = viewModel.getAttachmentCount()
            val size = EventAttachmentManager.formatSize(viewModel.estimateAttachmentBytes())
            withContext(Dispatchers.Main) {
                attachmentCount = count
                attachmentSizeText = size
            }
        }
    }

    fun showToast(message: String, type: ToastType) {
        when (type) {
            ToastType.ERROR -> haptics.error()
            ToastType.SUCCESS -> haptics.confirm()
            else -> Unit
        }
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // --- 字体样式优化 ---
    // 板块标题：Primary + ExtraBold
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    // 卡片标题：OnSurface + Medium
    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 说明文字：Grey + Transparent
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val contentBodyStyle = MaterialTheme.typography.bodyMedium

    // 监听 Prompt 检查反馈
    LaunchedEffect(mainViewModel) {
        mainViewModel.promptCheckFeedback.collect { feedback ->
            showToast(feedback.message, feedback.type)
        }
    }

    // 数据备份导出 JSON
    val exportBackupJsonLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = viewModel.exportBackupData(exportOptions)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonData.toByteArray())
                    } ?: throw IOException("无法打开导出文件")
                    withContext(Dispatchers.Main) { showToast("数据备份导出成功", ToastType.SUCCESS) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 数据备份导出 ZIP
    val exportBackupZipLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    viewModel.exportBackupZip(uri, exportOptions)
                    withContext(Dispatchers.Main) { showToast("数据备份导出成功", ToastType.SUCCESS) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 数据备份导入
    val importBackupLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri).orEmpty()
            val name = uri.lastPathSegment.orEmpty()
            pendingImportUri = uri
            pendingImportIsZip = type.contains("zip", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)
            importOptions = AppBackupOptions(
                includeEvents = true,
                includeSettings = true,
                includeAttachments = pendingImportIsZip,
                includePrompts = true,
                includeQuickMemos = pendingImportIsZip
            )
            showBackupImportSheet = true
        }
    }

    // 提示词导出
    val exportPromptsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = AiPrompts.exportToJson(context)
                    if (jsonData.isNotBlank()) {
                        context.contentResolver.openOutputStream(uri)?.use { output -> output.write(jsonData.toByteArray()) }
                        withContext(Dispatchers.Main) { showToast("提示词导出成功", ToastType.SUCCESS) }
                    } else {
                        withContext(Dispatchers.Main) { showToast("导出失败：无法获取提示词", ToastType.ERROR) }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) }
                }
            }
        }
    }

    // 提示词导入
    val importPromptsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val success = AiPrompts.importFromJson(context, content)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                showToast("提示词导入成功", ToastType.SUCCESS)
                                mainViewModel.refreshPromptInfo()
                            } else {
                                showToast("导入失败：格式无效", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导入失败: ${e.message}", ToastType.ERROR) }
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides currentSettings.hapticFeedbackEnabled) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("数据管理", style = sectionTitleStyle)

            BackupCard(
                title = "数据备份",
                desc = "备份/恢复日程、随口记、设置、附件和提示词",
                onExport = {
                    haptics.click()
                    exportOptions = AppBackupOptions(includeEvents = true)
                    showBackupExportSheet = true
                },
                onImport = { haptics.click(); importBackupLauncher.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )

            Text("提示词管理", style = sectionTitleStyle)

            val promptSourceText = if (promptSource == AiPrompts.PromptSource.CLOUD) {
                "云端 v$promptLocalVersion"
            } else {
                "本地"
            }
            BackupCard(
                title = "提示词来源：$promptSourceText",
                desc = "导入/导出提示词，或检查云端更新",
                onExport = {
                    haptics.click()
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportPromptsLauncher.launch("ai_prompts_$timestamp.json")
                },
                onImport = { haptics.click(); importPromptsLauncher.launch(arrayOf("application/json")) },
                swapButtons = true, // 导出在左，导入在右
                extraButton = {
                    OutlinedButton(
                        onClick = { haptics.confirm(); mainViewModel.checkPromptUpdatesManually() },
                        enabled = !promptCheckInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (promptCheckInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查中...")
                        } else {
                            Text("检查更新")
                        }
                    }
                },
                extraButtonText = "检查更新",
                extraButtonOnTop = false,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )


        if (showBackupExportSheet) {
            BackupOptionsSheet(
                title = "导出数据备份",
                description = "选择要写入备份文件的数据。包含附件或随口记时会导出 ZIP，否则导出 JSON。",
                options = exportOptions,
                attachmentCount = attachmentCount,
                attachmentSizeText = attachmentSizeText,
                confirmText = "导出",
                onOptionsChange = { exportOptions = it },
                onDismiss = { showBackupExportSheet = false },
                onConfirm = {
                    haptics.confirm()
                    showBackupExportSheet = false
                    val normalized = if (exportOptions.includeAttachments) exportOptions.copy(includeEvents = true) else exportOptions
                    exportOptions = normalized
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    if (normalized.includeAttachments || normalized.includeQuickMemos) {
                        exportBackupZipLauncher.launch("willdo_backup_$timestamp.zip")
                    } else {
                        exportBackupJsonLauncher.launch("willdo_backup_$timestamp.json")
                    }
                }
            )
        }

        if (showBackupImportSheet && pendingImportUri != null) {
            BackupOptionsSheet(
                title = "导入数据备份",
                description = if (pendingImportIsZip) "选择要从 ZIP 备份恢复的数据。" else "选择要从 JSON 备份恢复的数据。",
                options = importOptions,
                attachmentCount = if (pendingImportIsZip) attachmentCount else 0,
                attachmentSizeText = attachmentSizeText,
                confirmText = "导入",
                onOptionsChange = { importOptions = it },
                onDismiss = {
                    showBackupImportSheet = false
                    pendingImportUri = null
                },
                onConfirm = {
                    haptics.confirm()
                    val uri = pendingImportUri ?: return@BackupOptionsSheet
                    showBackupImportSheet = false
                    scope.launch(Dispatchers.IO) {
                        val result = if (pendingImportIsZip) {
                            viewModel.importBackupZip(uri, importOptions)
                        } else {
                            val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                            viewModel.importBackupJson(jsonString, importOptions)
                        }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) showToast(formatBackupImportResult(result.getOrThrow()), ToastType.SUCCESS)
                            else showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                        }
                    }
                    pendingImportUri = null
                }
            )
        }
    }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupOptionsSheet(
    title: String,
    description: String,
    options: AppBackupOptions,
    attachmentCount: Int,
    attachmentSizeText: String,
    confirmText: String,
    onOptionsChange: (AppBackupOptions) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val normalizedOptions = if (options.includeAttachments && !options.includeEvents) {
        options.copy(includeEvents = true)
    } else {
        options
    }
    LaunchedEffect(normalizedOptions) {
        if (normalizedOptions != options) onOptionsChange(normalizedOptions)
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
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BackupOptionRow(
                    title = "日程数据",
                    subtitle = "日程、提醒、归档状态和重复规则",
                    checked = normalizedOptions.includeEvents,
                    onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeEvents = it, includeAttachments = normalizedOptions.includeAttachments && it)) }
                )
                BackupOptionRow(
                    title = "设置数据",
                    subtitle = "包含模型、天气、主题等完整设置",
                    checked = normalizedOptions.includeSettings,
                    onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeSettings = it)) }
                )
                BackupOptionRow(
                    title = "附件",
                    subtitle = if (attachmentCount > 0) "$attachmentCount 个附件，约 $attachmentSizeText；需要同时导出日程" else "当前没有附件；需要同时导出日程",
                    checked = normalizedOptions.includeAttachments,
                    enabled = normalizedOptions.includeEvents,
                    onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeAttachments = it, includeEvents = normalizedOptions.includeEvents || it)) }
                )
                BackupOptionRow(
                    title = "随口记",
                    subtitle = "包含随口记、状态和录音",
                    checked = normalizedOptions.includeQuickMemos,
                    onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeQuickMemos = it)) }
                )
                BackupOptionRow(
                    title = "提示词",
                    subtitle = "当前本地提示词配置",
                    checked = normalizedOptions.includePrompts,
                    onCheckedChange = { onOptionsChange(normalizedOptions.copy(includePrompts = it)) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "设置备份会包含 API Key 等私密配置，请妥善保存备份文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                enabled = normalizedOptions.includeEvents || normalizedOptions.includeSettings || normalizedOptions.includePrompts || normalizedOptions.includeQuickMemos,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmText)
            }
        }
    }
}

@Composable
private fun BackupOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { haptics.selection(); onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = { haptics.selection(); onCheckedChange(it) })
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBackupImportResult(result: AppBackupImportResult): String {
    val parts = mutableListOf<String>()
    result.eventsResult?.let { importResult ->
        parts += "新增 ${importResult.successCount} 条日程"
        if (importResult.skippedCount > 0) parts += "跳过 ${importResult.skippedCount} 条"
        if (importResult.archiveStatusUpdateCount > 0) parts += "更新 ${importResult.archiveStatusUpdateCount} 条"
    }
    if (result.settingsImported) parts += "设置"
    if (result.promptsImported) parts += "提示词"
    if (result.attachmentsImported > 0) parts += "附件 ${result.attachmentsImported} 个"
    if (result.quickMemosImported > 0) parts += "随口记 ${result.quickMemosImported} 条"
    return if (parts.isEmpty()) "导入完成" else "导入成功：${parts.joinToString("，")}"
}

@Composable
fun BackupCard(
    title: String,
    desc: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    showExport: Boolean = true,
    importLabel: String = "导入",
    extraButton: @Composable (() -> Unit)? = null,
    extraButtonText: String = "检查更新",
    extraButtonOnTop: Boolean = false,
    swapButtons: Boolean = false, // 是否交换导出/导入按钮顺序（导出在左，导入在右）
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = cardTitleStyle)
            Text(desc, style = cardSubtitleStyle)
            Spacer(modifier = Modifier.height(16.dp))
            if (extraButton != null && extraButtonOnTop) {
                extraButton()
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(importLabel)
                    }
                    if (showExport) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                }
            } else if (extraButton != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (swapButtons) {
                        // 导出在左，导入在右
                        if (showExport) {
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(importLabel)
                        }
                    } else {
                        // 导入在左，导出在右
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(importLabel)
                        }
                        if (showExport) {
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                extraButton()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showExport) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(if (showExport) 1f else 1f)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(importLabel)
                    }
                }
            }
        }
    }
}
