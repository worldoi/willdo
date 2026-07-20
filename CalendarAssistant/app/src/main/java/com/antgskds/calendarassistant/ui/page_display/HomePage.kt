package com.antgskds.calendarassistant.ui.page_display

import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.core.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import com.antgskds.calendarassistant.core.util.LunarCalendarUtils
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.ui.components.AppCard
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.theme.SectionTitleTextStyle
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarVisualHeight
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    viewModel: MainViewModel,
    currentPageKey: String,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    isActionExpanded: Boolean = false,
    onActionExpandedChange: (Boolean) -> Unit = {},
    searchRequestId: Int = 0,
    imageRequestId: Int = 0,
    isSidebarOpen: Boolean = false,
    onPageChange: (String) -> Unit = {},
    onAddEventClick: () -> Unit = {},
    onEditItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    onEditNote: (NoteEntity) -> Unit = {},
    onCreateNote: () -> Unit = {},
    onRequestDeleteNote: (NoteEntity) -> Unit = {},
    onRequestDeleteQuickMemo: (QuickMemoEntity) -> Unit = {},
    onRequestClearQuickMemos: () -> Unit = {},
    quickMemoCount: Int = 0,
    onOpenQuickMemoDetail: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberAppHaptics(uiState.settings.hapticFeedbackEnabled)


    var todaySearchQuery by rememberSaveable { mutableStateOf("") }
    var allSearchQuery by rememberSaveable { mutableStateOf("") }
    var noteSearchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var isLegacyNoteMode by rememberSaveable { mutableStateOf(false) }

    val isTodayPage = currentPageKey == HomeEntryKey.TODAY
    val isAllPage = currentPageKey == HomeEntryKey.ALL
    val isNotePage = currentPageKey == HomeEntryKey.NOTE

    var isImageImporting by remember { mutableStateOf(false) }
    var imageImportJob by remember { mutableStateOf<Job?>(null) }

    val cancelImageImport = {
        imageImportJob?.cancel()
        imageImportJob = null
        isImageImporting = false
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || isImageImporting) return@rememberLauncherForActivityResult

        imageImportJob?.cancel()
        imageImportJob = scope.launch {
            isImageImporting = true
            try {
                val settings = uiState.settings
                if (!settings.isRecognitionConfigReady()) {
                    Toast.makeText(context, settings.recognitionConfigMissingMessage(), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val imageFile = ImageImportUtils.createImportedImageFile(context)
                val copied = withContext(Dispatchers.IO) {
                    ImageImportUtils.copyUriToFile(context, uri, imageFile)
                }
                if (!copied) {
                    Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
                }
                if (bitmap == null) {
                    Toast.makeText(context, "图片解码失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val analysisResult = withContext(Dispatchers.IO) {
                    (context.applicationContext as App)
                        .recognitionCenter
                        .analyzeImage(
                            bitmap = bitmap,
                            settings = settings,
                            context = context.applicationContext,
                            sourceType = RecognitionFeedbackSource.HOME_SOURCE_TYPE,
                            sourceId = RecognitionFeedbackSource.HOME_SOURCE_ID,
                            sourceImagePath = imageFile.absolutePath,
                            ingestRequested = true
                        )
                }
                bitmap.recycle()

                when (analysisResult) {
                    is AnalysisResult.Success -> {
                        Toast.makeText(context, "识别完成，正在保存...", Toast.LENGTH_SHORT).show()
                    }
                    is AnalysisResult.Empty -> return@launch
                    is AnalysisResult.Failure -> return@launch
                }
            } catch (_: CancellationException) {
                // 取消识别时不提示错误
            } catch (e: Exception) {
                Toast.makeText(context, "分析失败：${e.message ?: "unknown"}", Toast.LENGTH_SHORT).show()
            } finally {
                isImageImporting = false
                imageImportJob = null
            }
        }
    }

    val topBarIconSize = when (uiSize) {
        1 -> 24.dp
        2 -> 28.dp
        else -> 32.dp
    }


    // 提升 listState，用于精确判断列表是否到达顶部
    val listState = rememberLazyListState()


    LaunchedEffect(searchRequestId) {
        if (searchRequestId > 0) {
            isSearchMode = true
        }
    }

    LaunchedEffect(currentPageKey) {
        if (!isNotePage) {
            isLegacyNoteMode = false
        }
    }

    LaunchedEffect(imageRequestId) {
        if (imageRequestId > 0 && !isImageImporting) {
            imagePickerLauncher.launch("image/*")
        }
    }

    var serviceEnabled by remember {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        mutableStateOf(enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == TextAccessibilityService::class.java.name
        })
    }
    var notificationEnabled by remember {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        mutableStateOf(notificationManager.areNotificationsEnabled())
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarOffset = IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset
    val floatingBarContentPadding = IntegratedFloatingBarVisualHeight + IntegratedFloatingBarBottomSpacing + bottomInset + 16.dp

    LifecycleResumeEffect(context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        serviceEnabled = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == TextAccessibilityService::class.java.name
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationEnabled = notificationManager.areNotificationsEnabled()
        onPauseOrDispose { }
    }

    // --- 3. 根布局 ---
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isActionExpanded, isSearchMode) {
                    detectTapGestures(onTap = {
                        when {
                            isActionExpanded -> onActionExpandedChange(false)
                            isSearchMode -> {
                                isSearchMode = false
                                when {
                                    isAllPage -> allSearchQuery = ""
                                    isNotePage -> noteSearchQuery = ""
                                    else -> todaySearchQuery = ""
                                }
                            }
                            else -> viewModel.onRevealItem(null)
                        }
                    })
                }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            /*设置为background后会导致深浅模式切换时topbar不同步;此注释严禁删除
                            containerColor = MaterialTheme.colorScheme.background,*/
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            val title = when {
                                isTodayPage -> "今日日程"
                                isNotePage -> if (isLegacyNoteMode) "普通便签" else "随口记"
                                else -> "全部日程"
                            }
                            Text(title)
                        },
                        actions = {
                            if (isNotePage && !isLegacyNoteMode && quickMemoCount > 0) {
                                IconButton(onClick = { haptics.click(); onRequestClearQuickMemos() }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "清空随口记",
                                        modifier = Modifier.size(topBarIconSize)
                                    )
                                }
                            }
                        }
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val showSearchBar = isSearchMode && !isSidebarOpen && (isTodayPage || isAllPage || isNotePage)
                    val searchBarHeight = 64.dp
                    val searchBarOffset = searchBarHeight + 12.dp
                    val contentBottomPadding = if (showSearchBar) {
                        floatingBarContentPadding + searchBarOffset
                    } else {
                        floatingBarContentPadding
                    }

                    val pageOrder = remember { listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE) }
                    AnimatedContent(
                        targetState = currentPageKey,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val from = pageOrder.indexOf(initialState).coerceAtLeast(0)
                            val to = pageOrder.indexOf(targetState).coerceAtLeast(0)
                            val direction = if (to >= from) 1 else -1
                            (slideInHorizontally(animationSpec = tween(220)) { width -> width * direction } +
                                fadeIn(animationSpec = tween(160))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(220)) { width -> -width * direction } +
                                    fadeOut(animationSpec = tween(140))) using SizeTransform(clip = false)
                        },
                        label = "home_page_switch"
                    ) { animatedPageKey ->
                    val animatedIsTodayPage = animatedPageKey == HomeEntryKey.TODAY
                    val animatedIsAllPage = animatedPageKey == HomeEntryKey.ALL
                    val animatedIsNotePage = animatedPageKey == HomeEntryKey.NOTE

                    if (animatedIsTodayPage) {
                        // === 今日视图内容 ===
                        val todayEvents = remember(uiState.currentDateEvents, todaySearchQuery) {
                            if (todaySearchQuery.isBlank()) {
                                uiState.currentDateEvents
                            } else {
                                uiState.currentDateEvents.filter { event ->
                                    event.title.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.description.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.location.contains(todaySearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        val tomorrowEvents = remember(uiState.tomorrowEvents, todaySearchQuery) {
                            if (todaySearchQuery.isBlank()) {
                                uiState.tomorrowEvents
                            } else {
                                uiState.tomorrowEvents.filter { event ->
                                    event.title.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.description.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.location.contains(todaySearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        LazyColumn(
                            // 绑定 listState
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = contentBottomPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item { Spacer(modifier = Modifier.height(0.dp)) }

                            // 日期卡片
                            item {
                                val themePrimary = MaterialTheme.colorScheme.primary
                                val themeOnPrimary = MaterialTheme.colorScheme.onPrimary
                                val isToday = uiState.selectedDate == uiState.today
                                val topBaseColor = when {
                                    isToday -> themePrimary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val topContentColor = when {
                                    isToday -> themeOnPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val topBarColor = topBaseColor
                                val dateCardShape = RoundedCornerShape(16.dp)
                                AppCard(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(0.95f)
                                        .then(
                                            Modifier
                                        )
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    if (totalDrag < -50) viewModel.updateSelectedDate(uiState.selectedDate.plusDays(1))
                                                    else if (totalDrag > 50) viewModel.updateSelectedDate(uiState.selectedDate.minusDays(1))
                                                    totalDrag = 0f
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    totalDrag += dragAmount
                                                }
                                            )
                                        },
                                    shape = dateCardShape,
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(0.2f)
                                                .fillMaxWidth()
                                                .background(topBarColor)
                                                .clickable { viewModel.updateSelectedDate(uiState.today) }
                                        ) {

                                        }
                                        Column(
                                            modifier = Modifier.weight(0.8f).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                                Text(
                                                    uiState.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    LunarCalendarUtils.getLunarDate(uiState.selectedDate),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = uiState.selectedDate.dayOfMonth.toString(),
                                                fontSize = 140.sp,
                                                fontWeight = FontWeight.Black,
                                                lineHeight = 140.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptics.selection()
                                                    viewModel.updateSelectedDate(uiState.today)
                                                }
                                            )
                                            Text(
                                                "${uiState.selectedDate.year}年${uiState.selectedDate.monthValue}月",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }

                            if (!serviceEnabled) item { PermissionWarningCard(Icons.Default.Warning, "无障碍服务未开启", { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }
                            if (!notificationEnabled) item { PermissionWarningCard(Icons.Default.NotificationsOff, "通知权限未开启", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }

                            item { SectionHeader(if (uiState.selectedDate == uiState.today) "今日安排" else "${uiState.selectedDate.monthValue}月${uiState.selectedDate.dayOfMonth}日 安排", MaterialTheme.colorScheme.primary) }

                            if (todayEvents.isEmpty()) {
                                val emptyText = if (todaySearchQuery.isBlank()) "今日暂无日程" else "未找到相关日程"
                                item { Text(emptyText, modifier = Modifier.padding(vertical = 40.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)) }
                            } else {
                                items(todayEvents, key = { "today_${it.stableKey}" }) { item ->
                                    SwipeableEventItem(
                                        item = item,
                                        isRevealed = uiState.revealedItemKey == item.stableKey,
                                        timeRefreshToken = uiState.timeRefreshToken,
                                        onExpand = { viewModel.onRevealItem(item.stableKey) },
                                        onCollapse = { viewModel.onRevealItem(null) },
                                        onDelete = { item.eventId?.let { id -> viewModel.deleteEvent(id) } },
                                        onEdit = { onEditItem(item) },
                                        onLongPress = { onRequestDeleteItem(item) },
                                        uiSize = uiSize,
                                        isArchivePage = false,
                                        onArchive = { viewModel.archiveItem(item.action) },
                                        hapticEnabled = uiState.settings.hapticFeedbackEnabled
                                    )
                                }
                            }

                            if (uiState.selectedDate == uiState.today && tomorrowEvents.isNotEmpty()) {
                                item { SectionHeader("明日安排", MaterialTheme.colorScheme.tertiary) }
                                items(tomorrowEvents, key = { "tomorrow_${it.stableKey}" }) { item ->
                                    SwipeableEventItem(
                                        item = item,
                                        isRevealed = uiState.revealedItemKey == item.stableKey,
                                        timeRefreshToken = uiState.timeRefreshToken,
                                        onExpand = { viewModel.onRevealItem(item.stableKey) },
                                        onCollapse = { viewModel.onRevealItem(null) },
                                        onDelete = { item.eventId?.let { id -> viewModel.deleteEvent(id) } },
                                        onEdit = { onEditItem(item) },
                                        onLongPress = { onRequestDeleteItem(item) },
                                        uiSize = uiSize,
                                        isArchivePage = false,
                                        onArchive = { viewModel.archiveItem(item.action) },
                                        hapticEnabled = uiState.settings.hapticFeedbackEnabled
                                    )
                                }
                            }
                        }
                    } else if (animatedIsAllPage) {
                        AllEventsPage(
                            viewModel = viewModel,
                            onEditItem = { onEditItem(it) },
                            uiSize = uiSize,
                            // 【修改 2】透传给 AllEventsPage
                            pickupTimestamp = pickupTimestamp,
                            searchQuery = allSearchQuery,
                            extraBottomPadding = if (showSearchBar) searchBarOffset else 0.dp,
                            onRequestDeleteItem = onRequestDeleteItem,
                            hapticEnabled = uiState.settings.hapticFeedbackEnabled
                        )
                    } else if (animatedIsNotePage && isLegacyNoteMode) {
                        NotePage(
                            viewModel = viewModel,
                            searchQuery = noteSearchQuery,
                            extraBottomPadding = if (showSearchBar) searchBarOffset else 0.dp,
                            onEditNote = onEditNote,
                            onPendingDeleteChange = { note -> note?.let(onRequestDeleteNote) },
                            hapticEnabled = uiState.settings.hapticFeedbackEnabled
                        )
                    } else {
                        QuickMemoPage(
                            viewModel = viewModel,
                            searchQuery = noteSearchQuery,
                            uiSize = uiSize,
                            extraBottomPadding = if (showSearchBar) searchBarOffset else 0.dp,
                            onOpenDetail = onOpenQuickMemoDetail,
                            onPendingDeleteChange = { memo -> memo?.let(onRequestDeleteQuickMemo) },
                            hapticEnabled = uiState.settings.hapticFeedbackEnabled
                        )
                    }
                    }

                    if (showSearchBar) {
                        val searchFocusRequester = remember { FocusRequester() }
                        val keyboardController = LocalSoftwareKeyboardController.current

                        LaunchedEffect(isSearchMode) {
                            if (isSearchMode) {
                                searchFocusRequester.requestFocus()
                            }
                        }

                        // 实时获取键盘高度
                        val imePaddingDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

                        // 悬浮栏基础高度（键盘完全收起时，搜索框应该待的最低位置）
                        val baseBottomPadding = floatingBarOffset + 36.dp

                        // 动态计算：coerceAtLeast 保证搜索框永远不会低于基础高度
                        val searchBarBottomPadding = (imePaddingDp + 12.dp).coerceAtLeast(baseBottomPadding)

                        // 触摸屏障：防止点击穿透到后面的列表
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = searchBarBottomPadding)
                                .height(searchBarHeight + 36.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* 消费触摸，防止穿透 */ }
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            OutlinedTextField(
                                value = when {
                                    isAllPage -> allSearchQuery
                                    isNotePage -> noteSearchQuery
                                    else -> todaySearchQuery
                                },
                                onValueChange = {
                                    when {
                                        isAllPage -> allSearchQuery = it
                                        isNotePage -> noteSearchQuery = it
                                        else -> todaySearchQuery = it
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(searchBarHeight)
                                    .focusRequester(searchFocusRequester)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            searchFocusRequester.requestFocus()
                                            keyboardController?.show()
                                        }
                                    },
                                placeholder = {
                                    Text(
                                        when {
                                            isAllPage -> "搜索标题、备注或地点..."
                                            isNotePage -> if (isLegacyNoteMode) "搜索便签标题或正文..." else "搜索随口记正文..."
                                            else -> "搜索标题、备注或地点..."
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "搜索",
                                        modifier = Modifier.size(topBarIconSize)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isSearchMode = false },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "关闭",
                                            modifier = Modifier.size(topBarIconSize)
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }
            }

        }

        PredictiveFloatingActionCard(
            visible = isImageImporting,
            title = "正在识别",
            content = "OCR + AI 分析中...",
            confirmText = "处理中",
            dismissText = "取消",
            isDestructive = false,
            isLoading = true,
            allowDismissWhileLoading = true,
            dismissOnClickOutside = false,
            predictiveBackEnabled = uiState.settings.predictiveBackEnabled,
            onConfirm = {},
            onDismiss = cancelImageImport,
            modifier = Modifier
                .padding(bottom = floatingBarOffset + 16.dp)
        )
    }
}

@Composable
private fun PermissionWarningCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text = title, style = SectionTitleTextStyle.copy(color = MaterialTheme.colorScheme.onBackground))
    }
}
