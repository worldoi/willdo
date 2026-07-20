package com.antgskds.calendarassistant.core.developer

import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationBehavior
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay
import java.time.LocalDate
import com.antgskds.calendarassistant.core.ai.RecognitionFailureDisplay
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.management.catalog.NotificationKindCatalog
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import com.antgskds.calendarassistant.shared.management.catalog.PipelineCatalog
import com.antgskds.calendarassistant.shared.management.catalog.HelperCatalog
import com.antgskds.calendarassistant.shared.management.catalog.PolicyCatalog
import com.antgskds.calendarassistant.shared.management.catalog.WorkerCatalog
import com.antgskds.calendarassistant.core.rule.RecognitionRuleCatalog
import com.antgskds.calendarassistant.core.content.ContentRegistry
import com.antgskds.calendarassistant.shared.management.catalog.FeatureCatalog
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 一个调试动作的声明。
 *
 * - [id]：稳定标识。adb 通过 `debug:<id>` 触发；未来开发者页也用它作为 key。
 * - [label]：给（未来）开发者页 UI 显示的人类可读名称。
 * - [category]：分组（通知 / 胶囊 / 事件 / 元…），供 UI 按组渲染。
 * - [dangerous]：高风险动作（如写入持久化数据），UI 可据此加二次确认或显著标记。
 * - [execute]：动作本体。**只接收 [App]、只调用其暴露的公开 api**
 *   （notificationCenter / scheduleCenter / reminderCenter / settings），
 *   不反射、不直接钻内部实现——这是调试动作的硬纪律。
 */
class DebugAction(
    val id: String,
    val label: String,
    val category: String,
    val dangerous: Boolean = false,
    val execute: suspend (App) -> Unit,
)

/**
 * 调试动作注册中心：ADB 后门与（未来）开发者页**共用的唯一清单**。
 *
 * 设计要点（对应架构纪律「注册要承重」）：
 * - 加一个调试动作 = 在 [actions] 里登记一条，adb 与 UI 都照这张表走，
 *   不再在各处各写各的分发逻辑（散落清单一定会烂、维护者也看不出）。
 * - 接收器只负责「按 id 查表 → 执行」，不再持有任何具体动作实现。
 * - 所有动作只调 [App] 的公开 api，保证「不在注册表里就接不进去」且不触碰内部实现。
 */
object DebugActionRegistry {

    val actions: List<DebugAction> = listOf(
        // —— 通知 ——
        DebugAction("dump", "导出通知快照", CATEGORY_NOTIFICATION) { app -> dump(app) },
        DebugAction("resync", "重排所有系统闹钟", CATEGORY_NOTIFICATION) { app ->
            Log.d(DEBUG_TAG, "resync requested")
            app.notificationCenter.rescheduleAllAlarms()
        },
        DebugAction("reconcile", "强制全量重排（含重复窗口）", CATEGORY_NOTIFICATION) { app ->
            Log.d(DEBUG_TAG, "reconcile requested")
            app.reminderCenter.reconcileAllNow()
        },
    ) + listOf(
        // —— 通知测试（迁自实验室：普通通知 / 每日提醒 / 新链路预览；与旧 chip 同实现）——
        DebugAction("test-plain-schedule", "测试普通通知·日程", CATEGORY_NOTIFICATION) { app ->
            firePlainSample(app, "schedule", "调试·日程", "15 分钟后开始：检查普通日程通知样式。", R.drawable.ic_stat_event)
        },
        DebugAction("test-plain-pickup", "测试普通通知·取件", CATEGORY_NOTIFICATION) { app ->
            firePlainSample(app, "pickup", "调试·取件", "菜鸟驿站 3-2-101，取件码 8-2333。", R.drawable.ic_stat_package)
        },
        DebugAction("test-normal-double-action", "测试普通通知·双按钮", CATEGORY_NOTIFICATION) { app ->
            fireNormalDoubleAction(app)
        },
        DebugAction("test-daily-today", "测试每日提醒·今日", CATEGORY_NOTIFICATION) { app ->
            fireDailySummary(app, isMorning = true)
        },
        DebugAction("test-daily-tomorrow", "测试每日提醒·明日", CATEGORY_NOTIFICATION) { app ->
            fireDailySummary(app, isMorning = false)
        },
        DebugAction("test-new-chain-preview", "新链路预览·普通提醒", CATEGORY_NOTIFICATION) { app ->
            previewNewChain(app)
        },
        // —— 胶囊 ——
        DebugAction("capsule:on", "实况胶囊：开", CATEGORY_CAPSULE) { app -> setCapsule(app, true) },
        DebugAction("capsule:off", "实况胶囊：关", CATEGORY_CAPSULE) { app -> setCapsule(app, false) },
        // —— 胶囊测试（迁自实验室「实况胶囊」段；与旧 chip 同实现，gate 于实况胶囊开关）——
        DebugAction("test-capsule-ocr", "测试胶囊·OCR 进度", CATEGORY_CAPSULE) { app -> fireOcrProgress(app) },
        DebugAction("test-capsule-recognition-1", "测试胶囊·识别成功", CATEGORY_CAPSULE) { app -> fireRecognitionSuccess(app, 1) },
        DebugAction("test-capsule-recognition-2", "测试胶囊·识别成功 x2", CATEGORY_CAPSULE) { app -> fireRecognitionSuccess(app, 2) },
        DebugAction("test-capsule-recognition-fail", "测试胶囊·识别失败", CATEGORY_CAPSULE) { app -> fireRecognitionFailure(app) },
        DebugAction("test-capsule-model-loading", "测试胶囊·模型加载", CATEGORY_CAPSULE) { app -> fireModelLoading(app) },
        DebugAction("test-live-double-action", "测试实况通知·双按钮", CATEGORY_CAPSULE) { app -> fireLiveDoubleAction(app) },
        DebugAction("test-live-daily-today", "测试实况通知·每日提醒今日", CATEGORY_CAPSULE) { app ->
            fireDailySummary(app, isMorning = true, requireLive = true)
        },
        DebugAction("test-live-daily-tomorrow", "测试实况通知·每日提醒明日", CATEGORY_CAPSULE) { app ->
            fireDailySummary(app, isMorning = false, requireLive = true)
        },
        DebugAction("test-capsule-clear", "测试胶囊·清除", CATEGORY_CAPSULE) { app -> clearTestCapsules(app) },
        // —— 事件（写入持久化数据，标 dangerous）——
        DebugAction("create-recurring", "建测试事件：每日重复", CATEGORY_EVENT, dangerous = true) { app ->
            createEvent(app, recurring = true)
        },
        DebugAction("create-single", "建测试事件：单次", CATEGORY_EVENT, dangerous = true) { app ->
            createEvent(app, recurring = false)
        },
        DebugAction("create-soon", "建测试事件：约70秒后提醒（验双弹）", CATEGORY_EVENT, dangerous = true) { app ->
            createSoonEvent(app)
        },
        DebugAction("create-missed", "建测试事件：开始已过30秒（验错过补发）", CATEGORY_EVENT, dangerous = true) { app ->
            createMissedEvent(app)
        },
        DebugAction("test-imported-restore", "测试导入归档日程恢复", CATEGORY_EVENT, dangerous = true) { app ->
            testImportedRestore(app)
        },
    ) + listOf(
        DebugAction("delete-test-events", "删除所有 DEBUG 测试事件", CATEGORY_EVENT, dangerous = true) { app ->
            deleteTestEvents(app)
        },
        // —— 元 ——
        DebugAction("actions", "列出已注册调试动作", CATEGORY_META) { _ -> listActions() },
        DebugAction("verify-sort", "验证列表排序方向（正序/倒序对比）", CATEGORY_META) { app -> verifySort(app) },
        DebugAction("registry-check", "自检所有注册台账（页面/通知类型/事件类型/配置/调试动作/流程/工具/策略/后台任务）", CATEGORY_META) { _ -> registryCheck() },
    )

    /** 按 id 查动作；接收器据此分发，未找到返回 null。 */
    fun find(id: String): DebugAction? = actions.firstOrNull { it.id == id }

    // ============================ 动作实现（只调公开 api） ============================

    private suspend fun dump(app: App) {
        val all = app.notificationCenter.list(NotificationQuery(includeCancelled = true))
        Log.d(DEBUG_TAG, "dump: ${all.size} snapshots")
        all.forEach { s ->
            Log.d(
                DEBUG_TAG,
                "  key=${s.key.value} state=${s.state} triggerAt=${s.behavior.triggerAtEpochMillis} " +
                    "kind=${s.kind} offset=${s.offsetMinutes}"
            )
        }
    }

    private suspend fun createEvent(app: App, recurring: Boolean) {
        val nowSec = System.currentTimeMillis() / 1000L
        val event = Event(
            id = null,
            startTS = nowSec + 600L,   // 10 分钟后开始
            endTS = nowSec + 3600L,    // 1 小时
            title = if (recurring) "DEBUG 每日重复" else "DEBUG 单次",
            reminder1Minutes = 5,      // 提前 5 分钟
            rrule = if (recurring) "FREQ=DAILY" else ""
        )
        val id = app.scheduleCenter.addEvent(event)
        Log.d(
            DEBUG_TAG,
            "created ${if (recurring) "recurring" else "single"} test event " +
                "id=$id startTS=${event.startTS} reminder=5m"
        )
    }

    private suspend fun createSoonEvent(app: App) {
        val nowSec = System.currentTimeMillis() / 1000L
        val event = Event(
            id = null,
            startTS = nowSec + 70L,    // 约 70 秒后开始
            endTS = nowSec + 3670L,    // 1 小时
            title = "DEBUG 即将开始",
            reminder1Minutes = 0,      // 0 偏移：开始时提醒
            rrule = ""
        )
        val id = app.scheduleCenter.addEvent(event)
        Log.d(DEBUG_TAG, "created soon test event id=$id startTS=${event.startTS} reminder=0m (~70s)")
    }

    private suspend fun createMissedEvent(app: App) {
        val nowSec = System.currentTimeMillis() / 1000L
        val event = Event(
            id = null,
            startTS = nowSec - 30L,    // 开始时间已过去 30 秒
            endTS = nowSec + 3600L,    // 仍在进行中（1 小时后结束）
            title = "DEBUG 错过补发",
            reminder1Minutes = 0,      // 0 偏移：开始时提醒，已错过 → 应即时补发
            rrule = ""
        )
        val id = app.scheduleCenter.addEvent(event)
        Log.d(DEBUG_TAG, "created missed test event id=$id startTS=${event.startTS}(已过30s) reminder=0m，预期 MISSED_IMMEDIATE 补发")
    }

    private suspend fun deleteTestEvents(app: App) {
        // 匹配调试入口造的事件，旧版工厂事件以 "[DEV] " 开头，新链路专项事件以 "DEBUG " 开头。
        val targets = (app.calendarCenter.getEvents() + app.calendarCenter.getArchivedEvents())
            .filter { it.title.startsWith("DEBUG ") || it.title.startsWith("[DEV] ") }
            .distinctBy { it.id }
        Log.d(DEBUG_TAG, "delete-test-events: found ${targets.size} DEBUG events")
        targets.forEach { e ->
            val id = e.id ?: return@forEach
            app.scheduleCenter.deleteEvent(id)
            Log.d(DEBUG_TAG, "  deleted id=$id title=${e.title}")
        }
        // 删后强制全量重排，清掉重复事件窗口外残留的 rec: 提醒键。
        app.reminderCenter.reconcileAllNow()
        Log.d(DEBUG_TAG, "delete-test-events: reconciled after deletion")
    }

    private suspend fun testImportedRestore(app: App) {
        val nowSec = System.currentTimeMillis() / 1000L
        val original = Event(
            id = null,
            startTS = nowSec - 21 * 24 * 3600L,
            endTS = nowSec - 20 * 24 * 3600L,
            title = "DEBUG 导入归档恢复",
            location = "debug-imported-restore",
            description = "模拟备份导入后的归档/异常状态日程",
            reminder1Minutes = 0,
            state = 99,
            archivedAt = nowSec - 3600L
        )
        val id = app.scheduleCenter.addEvent(original)
        Log.d(
            DEBUG_TAG,
            "imported-restore created id=$id start=${original.startTS} end=${original.endTS} " +
                "state=${original.state} archived=${original.archivedAt}"
        )
        app.scheduleCenter.updateSingleFromPatch(
            id,
            EventPatch(
                title = original.title,
                startTS = nowSec - 30L,
                endTS = nowSec + 3600L,
                location = original.location,
                description = original.description,
                tag = original.tag,
                color = original.color,
                rrule = original.rrule,
                reminder1Minutes = 0,
                reminder2Minutes = original.reminder2Minutes,
                reminder3Minutes = original.reminder3Minutes
            )
        )
        val restored = app.calendarCenter.getEvent(id)
        Log.d(
            DEBUG_TAG,
            "imported-restore after-edit id=$id start=${restored?.startTS} end=${restored?.endTS} " +
                "state=${restored?.state} archived=${restored?.archivedAt}"
        )
    }

    private fun setCapsule(app: App, enabled: Boolean) {
        val current = app.settingsQueryApi.settings.value
        app.settingsOperationApi.updateSettings(current.copy(isLiveCapsuleEnabled = enabled))
        // 回读同步内存 flow（saveSettings 同步更新它），给一个不依赖 XML 落盘的确认信号。
        Log.d(
            DEBUG_TAG,
            "capsule set to $enabled; readback=${app.settingsQueryApi.settings.value.isLiveCapsuleEnabled}"
        )
    }


    private fun firePlainSample(app: App, key: String, title: String, content: String, smallIcon: Int) {
        app.notificationCenter.showPlainNotification(
            notificationId = testNotificationId("plain_$key"),
            title = title,
            content = content,
            channelId = App.CHANNEL_ID_POPUP,
            smallIcon = smallIcon
        )
        Log.d(DEBUG_TAG, "plain test fired: $title")
    }

    private fun fireNormalDoubleAction(app: App) {
        val result = app.notificationCenter.publishPlainNotification(
            NotificationRequest(
                key = NotificationKey.debug("normal-double-action"),
                kind = NotificationKind.DEBUG,
                display = NotificationDisplaySnapshot(
                    shortText = "双按钮测试",
                    primaryText = "调试·普通通知双按钮",
                    secondaryText = "用于验证普通通知是否同时展示两个操作按钮。",
                    expandedText = "这条通知通过新通知链路发布，包含两个调试动作按钮。点击按钮只记录日志，不修改数据。"
                ),
                route = NotificationRoute.NORMAL,
                notificationId = testNotificationId("normal_double_action"),
                smallIconResId = R.drawable.ic_notification_small,
                channelKey = App.CHANNEL_ID_POPUP,
                category = "debug",
                behavior = NotificationBehavior(timeoutAfterMillis = 60_000L),
                tapTarget = NotificationTapTarget(NotificationTapTargetType.APP_HOME),
                actions = debugNotificationActions("normal"),
                source = "debug_normal_double_action"
            )
        )
        Log.d(DEBUG_TAG, "normal double-action result=$result")
    }

    private fun fireLiveDoubleAction(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.showOcrResult(
            title = "调试·实况双按钮",
            content = "用于验证实况通知是否同时展示两个操作按钮。",
            durationMs = 60_000L,
            actions = debugCapsuleActions()
        )
        Log.d(DEBUG_TAG, "live double-action fired")
    }

    private fun debugNotificationActions(source: String): List<NotificationAction> {
        return listOf(
            NotificationAction(
                key = EventActionReceiver.ACTION_DEBUG_PRIMARY,
                label = "按钮一",
                payload = mapOf("debug_source" to source, "debug_button" to "primary")
            ),
            NotificationAction(
                key = EventActionReceiver.ACTION_DEBUG_SECONDARY,
                label = "按钮二",
                payload = mapOf("debug_source" to source, "debug_button" to "secondary")
            )
        )
    }

    private fun debugCapsuleActions(): List<CapsuleActionSpec> {
        return listOf(
            CapsuleActionSpec(
                label = "按钮一",
                receiverAction = EventActionReceiver.ACTION_DEBUG_PRIMARY
            ),
            CapsuleActionSpec(
                label = "按钮二",
                receiverAction = EventActionReceiver.ACTION_DEBUG_SECONDARY
            )
        )
    }

    private fun fireDailySummary(app: App, isMorning: Boolean, requireLive: Boolean = false) {
        if (requireLive && !liveCapsuleReady(app)) return
        val settings = app.settingsQueryApi.settings.value
        val payload = app.dailySummaryQueryApi.buildPayload(
            isMorning = isMorning,
            settings = settings.copy(isDailySummaryEnabled = true),
            events = app.scheduleCenter.events.value
        ) ?: fallbackDailySummaryPayload(isMorning)
        app.notificationCenter.showDailySummaryNotification(payload, isMorning)
        Log.d(DEBUG_TAG, "daily-summary fired isMorning=$isMorning shortTitle=${payload.shortTitle}")
    }

    private fun fallbackDailySummaryPayload(isMorning: Boolean): DailySummaryPayload {
        val shortTitle = ScheduleNormalDisplay.dailySummaryShortTitle(isMorning)
        val titles = if (isMorning) {
            listOf("调试·晨会", "调试·取件", "调试·复盘")
        } else {
            listOf("调试·早课", "调试·航班", "调试·晚间复盘")
        }
        return DailySummaryPayload(
            targetDate = if (isMorning) LocalDate.now() else LocalDate.now().plusDays(1),
            title = ScheduleNormalDisplay.dailySummaryTitle(shortTitle, "24°C 阴"),
            shortTitle = shortTitle,
            content = ScheduleNormalDisplay.dailySummaryContent(titles.size, titles),
            eventCount = titles.size,
            fullLines = titles,
            compactLines = listOf(titles.first(), ScheduleNormalDisplay.dailySummaryMoreLine(titles.size - 1))
        )
    }

    private suspend fun previewNewChain(app: App) {
        val result = NotificationDebugActions.previewScheduleReminder(app.notificationCenter)
        Log.d(DEBUG_TAG, "new-chain preview result=$result")
    }

    private fun testNotificationId(key: String): Int =
        ("debug_notification_test:$key".hashCode() and Int.MAX_VALUE)

    private fun liveCapsuleReady(app: App): Boolean {
        val ready = app.settingsQueryApi.settings.value.isLiveCapsuleEnabled
        if (!ready) Log.w(DEBUG_TAG, "实况胶囊开关未开启，胶囊测试不会显示")
        return ready
    }

    private fun fireOcrProgress(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.showOcrProgress(title = "正在分析截图", content = "调试：OCR 识别中，预计数秒后完成。")
        Log.d(DEBUG_TAG, "capsule ocr-progress fired")
    }

    private fun fireRecognitionSuccess(app: App, count: Int) {
        if (!liveCapsuleReady(app)) return
        app.notificationCenter.showCreatedEventResultNotifications(sourceType = "developer", events = recognitionSampleEvents(count))
        Log.d(DEBUG_TAG, "capsule recognition-success fired count=$count")
    }

    private fun fireRecognitionFailure(app: App) {
        if (!liveCapsuleReady(app)) return
        app.notificationCenter.showRecognitionFailureResultNotification(
            RecognitionFailureDisplay(reason = "模型返回格式异常", suggestion = "请重试，或切换到更稳定的模型")
        )
        Log.d(DEBUG_TAG, "capsule recognition-failure fired")
    }

    private fun fireModelLoading(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.showModelLoading(title = "本地模型加载中", content = "调试：正在准备本地语义模型。")
        Log.d(DEBUG_TAG, "capsule model-loading fired")
    }

    private fun clearTestCapsules(app: App) {
        app.capsuleCenter.clearOcrCapsule()
        app.capsuleCenter.clearModelLoading()
        Log.d(DEBUG_TAG, "test capsules cleared")
    }

    /**
     * 自检所有注册台账：统计项数 + 检查每条登记是否健康（关键字段非空）。
     * 给（不读代码的）维护者和 agent 一条命令验证「注册的东西都没坏」。结果打到 WillDoNotify。
     */
    private fun registryCheck() {
        var problems = 0
        fun warn(msg: String) { problems++; Log.w(DEBUG_TAG, "registry-check 问题: $msg") }

        Log.d(DEBUG_TAG, "===== registry-check 开始 =====")

        // 功能模块
        Log.d(DEBUG_TAG, "功能模块台账(FeatureCatalog): ${FeatureCatalog.features.size} 项")
        FeatureCatalog.features.forEach { fea ->
            if (fea.name.isBlank() || fea.entry.isBlank()) warn("功能模块 ${fea.name} 含空字段")
        }

        // 页面
        Log.d(DEBUG_TAG, "页面台账(PageCatalog): ${PageCatalog.pages.size} 项")
        PageCatalog.pages.forEach { p ->
            if (p.title.isBlank()) warn("页面 ${p.destination} 标题为空")
            if (p.visibility != PageCatalog.PageVisibility.ACTION && p.route.isNullOrBlank()) {
                warn("页面 ${p.destination} 非操作类却没有路由")
            }
        }

        // 通知类型
        Log.d(DEBUG_TAG, "通知类型台账(NotificationKindCatalog): ${NotificationKindCatalog.kinds.size} 项")
        NotificationKindCatalog.kinds.forEach { k ->
            if (k.label.isBlank()) warn("通知类型 ${k.kind} 标签为空")
        }

        // 事件类型
        val eventTypes = RecognitionRuleCatalog.registeredTypes()
        Log.d(DEBUG_TAG, "事件类型台账(RecognitionRuleCatalog): ${eventTypes.size} 项")
        eventTypes.forEach { (tag, name) ->
            if (tag.isBlank() || name.isBlank()) warn("事件类型 tag=$tag name=$name 含空字段")
        }

        // 配置
        Log.d(DEBUG_TAG, "配置台账(ConfigCatalog): ${ConfigCatalog.items.size} 项")
        ConfigCatalog.items.forEach { c ->
            if (c.label.isBlank()) warn("配置项 ${c.key} 标签为空")
        }

        // 内容源（运行时由 App 注册）
        val contentSources = ContentRegistry.getDefinitions()
        Log.d(DEBUG_TAG, "内容源台账(ContentRegistry): ${contentSources.size} 项")
        contentSources.forEach { d ->
            if (d.displayName.isBlank()) warn("内容源 ${d.sourceType} 显示名为空")
        }

        // 调试动作
        Log.d(DEBUG_TAG, "调试动作台账(DebugActionRegistry): ${actions.size} 项")
        val dupIds = actions.groupBy { it.id }.filter { it.value.size > 1 }.keys
        if (dupIds.isNotEmpty()) warn("调试动作存在重复 id: $dupIds")
        actions.forEach { a ->
            if (a.label.isBlank()) warn("调试动作 ${a.id} 标签为空")
        }

        // 流程主线
        Log.d(DEBUG_TAG, "流程主线台账(PipelineCatalog): ${PipelineCatalog.pipelines.size} 项")
        PipelineCatalog.pipelines.forEach { p ->
            if (p.name.isBlank() || p.entry.isBlank()) warn("流程主线 ${p.name} 名称或入口为空")
        }

        // 辅助工具
        Log.d(DEBUG_TAG, "辅助工具台账(HelperCatalog): ${HelperCatalog.helpers.size} 项")
        HelperCatalog.helpers.forEach { h ->
            if (h.name.isBlank() || h.entry.isBlank()) warn("辅助工具 ${h.name} 名称或入口为空")
        }

        // 策略
        Log.d(DEBUG_TAG, "策略台账(PolicyCatalog): ${PolicyCatalog.policies.size} 项")
        PolicyCatalog.policies.forEach { p ->
            if (p.name.isBlank() || p.entry.isBlank()) warn("策略 ${p.name} 名称或入口为空")
        }

        // 后台任务
        Log.d(DEBUG_TAG, "后台任务台账(WorkerCatalog): ${WorkerCatalog.workers.size} 项")
        WorkerCatalog.workers.forEach { w ->
            if (w.name.isBlank() || w.entry.isBlank()) warn("后台任务 ${w.name} 名称或入口为空")
        }

        if (problems == 0) {
            Log.d(DEBUG_TAG, "===== registry-check 通过：所有注册台账健康 =====")
        } else {
            Log.w(DEBUG_TAG, "===== registry-check 完成：发现 $problems 个问题（见上方 W 日志）=====")
        }
    }

    private fun recognitionSampleEvents(count: Int): List<Event> {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        return (0 until count).map { index ->
            val start = now.plusMinutes(30L + index * 90L)
            val end = start.plusMinutes(45L)
            val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()
            val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
            Event(
                id = -(System.currentTimeMillis() % 100_000L) - index,
                startTS = startSeconds,
                endTS = endSeconds,
                title = if (index == 0) "调试·测试会议" else "调试·测试复盘",
                location = if (index == 0) "会议室 A203" else "线上会议",
                description = if (index == 0) "确认识别结果胶囊的两行内容" else "验证多个日程各发一条结果通知"
            )
        }
    }

    private fun listActions() {
        Log.d(DEBUG_TAG, "registered debug actions: ${actions.size}")
        actions.forEach { a ->
            Log.d(
                DEBUG_TAG,
                "  debug:${a.id} [${a.category}]${if (a.dangerous) " (dangerous)" else ""} - ${a.label}"
            )
        }
    }

    /**
     * 验证「列表排序方向」：自构造今天三条不同时间的样本事件（纯内存，不写库），
     * 分别以正序 / 倒序 settings 跑首页查询，打印两种结果的标题+开始时间顺序，
     * 直接对比即可确认翻转生效且分组结构不乱。
     */
    private fun verifySort(app: App) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun ts(hour: Int): Long = today.atTime(hour, 0).atZone(zone).toEpochSecond()
        val sample = listOf(
            Event(id = -90101, startTS = ts(9), endTS = ts(10), title = "样本·09点"),
            Event(id = -90102, startTS = ts(12), endTS = ts(13), title = "样本·12点"),
            Event(id = -90103, startTS = ts(15), endTS = ts(16), title = "样本·15点"),
        )
        val base = app.settingsQueryApi.settings.value

        val asc = app.homeQueryApi.buildSnapshot(
            today, sample, base.copy(homeListReverseOrder = false, showTomorrowEvents = false)
        ).currentDateEvents
        val desc = app.homeQueryApi.buildSnapshot(
            today, sample, base.copy(homeListReverseOrder = true, showTomorrowEvents = false)
        ).currentDateEvents

        Log.d(DEBUG_TAG, "verify-sort: today=$today sampleCount=${sample.size} homeToday=${asc.size}")
        Log.d(DEBUG_TAG, "verify-sort [正序] ↓")
        asc.forEachIndexed { i, it -> Log.d(DEBUG_TAG, "  $i. ts=${it.startTS} ${it.title}") }
        Log.d(DEBUG_TAG, "verify-sort [倒序] ↓")
        desc.forEachIndexed { i, it -> Log.d(DEBUG_TAG, "  $i. ts=${it.startTS} ${it.title}") }
        val reversedMatches = asc.map { it.stableKey } == desc.map { it.stableKey }.asReversed()
        Log.d(DEBUG_TAG, "verify-sort: 倒序 == 正序完全翻转 ? $reversedMatches")
    }

    const val DEBUG_TAG = "WillDoNotify"
    private const val CATEGORY_NOTIFICATION = "通知"
    private const val CATEGORY_CAPSULE = "胶囊"
    private const val CATEGORY_EVENT = "事件"
    private const val CATEGORY_META = "元"
}
