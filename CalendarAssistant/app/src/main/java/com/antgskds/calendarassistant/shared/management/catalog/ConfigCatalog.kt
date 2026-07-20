package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.data.model.FloatingEntryStyle
import com.antgskds.calendarassistant.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.data.model.RecognitionMode

/**
 * 配置目录（catalog）—— 可调配置项的唯一声明清单。
 *
 * 设计要点（对应架构纪律「注册要承重」「唯一事实源不动」）：
 * - 值仍存 [MySettings]（SharedPreferences JSON，唯一事实源），这里**只登记元信息**
 *   （域 / 键 / 标签 / 说明 / 暴露级别 / 控件类型）+ 与 MySettings 字段的**绑定**（get/set）。
 * - 加一条配置 = 在 [ConfigCatalog.items] 里 add 一条；配置编辑页自动按域分组渲染、读写走 get/set，
 *   **不需要改任何导航枚举 / 编辑页代码**。
 * - 本文件只放声明（management 纪律：不放流程/发布/网络/DB）；真正写回由编辑页调
 *   settingsOperationApi.updateSettings 完成。
 *
 * 控件以 Int 为主，开关以 0/1 与 [MySettings] 字段桥接。
 */

/** 配置分组（域）。落地页按出现的域分组；加新域时在此加一个值。 */
enum class ConfigDomain(val label: String) {
    APPEARANCE("主题"),
    RECOGNITION("识别"),
    NOTIFICATION("通知"),
    VOICE("语音"),
}

/** 暴露级别（§5.5）。编辑页按级别决定是否渲染；SYSTEM_INTERNAL 不渲染。 */
enum class ConfigExposure {
    USER_EDITABLE,    // 普通用户可改（将来可在普通设置页也开入口）
    DEVELOPER_ONLY,   // 仅开发者页可见
    SYSTEM_INTERNAL,  // 系统内部，不在编辑页出现
}

/** 配置层级：用户显式设置 vs 系统底层策略。开发者编辑器先服务 POLICY，用户页将来服务 USER_SETTING。 */
enum class ConfigKind {
    USER_SETTING,   // 用户显式设置（天气开关、刷新间隔…），偏产品功能
    POLICY,         // 系统底层策略（阈值、时长、模板…），偏规则常量；防止散写裸常量
}

/** 控件类型：决定子页怎么渲染、怎么取值/写值。 */
sealed interface ConfigControl {
    /** 离散整数选项（如天气阈值的 1/2/3）。 */
    data class IntOptions(val options: List<Option>) : ConfigControl {
        data class Option(val value: Int, val label: String)
    }
    /** 整数步进输入（带上下限/步长/单位）。用于天气阈值等任意数值。 */
    data class IntInput(val min: Int, val max: Int, val step: Int = 1, val unitLabel: String = "") : ConfigControl
    /** 开关（Boolean）。get/set 以 0/1 与 Int 字段桥接。 */
    object Toggle : ConfigControl
    // 以后扩展：EnumOptions / DoubleInput(降水阈值) …
}

/**
 * 一条配置的登记。get/set 把控件值绑定到具体 MySettings 字段（首版仅 Int）。
 */
class ConfigItem(
    val domain: ConfigDomain,
    val kind: ConfigKind,                     // 用户设置(USER_SETTING) / 系统策略(POLICY)
    val key: String,                          // 稳定标识，如 "recognition.user.mode"
    val label: String,
    val description: String,
    val exposure: ConfigExposure,
    val control: ConfigControl,
    val get: (MySettings) -> Int,             // 读当前值
    val set: (MySettings, Int) -> MySettings, // 产出更新后的 settings
    val getText: (MySettings) -> String = { "" },
    val setText: (MySettings, String) -> MySettings = { s, _ -> s },
    val visible: (MySettings) -> Boolean = { true },
)

object ConfigCatalog {

    val items: List<ConfigItem> = listOf(
        ConfigItem(
            domain = ConfigDomain.RECOGNITION,
            kind = ConfigKind.USER_SETTING,
            key = "recognition.user.mode",
            label = "识别模式",
            description = "选择文本/语音转写识别时走 AI、正则，或正则未匹配后再走 AI。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntOptions(
                RecognitionMode.ALL.map { mode ->
                    ConfigControl.IntOptions.Option(mode, RecognitionMode.label(mode))
                }
            ),
            get = { it.recognitionMode },
            set = { s, v -> s.copy(recognitionMode = MySettings.normalizeRecognitionMode(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.result.timeout_ms",
            label = "结果通知停留时长",
            description = "OCR/识别结果类通知自动消失前停留的时长。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(5000, "5 秒"),
                    ConfigControl.IntOptions.Option(8000, "8 秒（默认）"),
                    ConfigControl.IntOptions.Option(15000, "15 秒"),
                    ConfigControl.IntOptions.Option(30000, "30 秒"),
                )
            ),
            get = { it.resultNotificationTimeoutMs },
            set = { s, v -> s.copy(resultNotificationTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.quick_memo_suggestion.timeout_ms",
            label = "快速备忘建议停留时长",
            description = "快速备忘建议通知自动消失前停留的时长。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(30000, "30 秒"),
                    ConfigControl.IntOptions.Option(60000, "60 秒（默认）"),
                    ConfigControl.IntOptions.Option(120000, "120 秒"),
                )
            ),
            get = { it.quickMemoSuggestionTimeoutMs },
            set = { s, v -> s.copy(quickMemoSuggestionTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.daily_summary.timeout_ms",
            label = "每日汇总停留时长",
            description = "每日日程汇总通知自动消失前停留的时长。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(30000, "30 秒"),
                    ConfigControl.IntOptions.Option(60000, "60 秒（默认）"),
                    ConfigControl.IntOptions.Option(120000, "120 秒"),
                )
            ),
            get = { it.dailySummaryTimeoutMs },
            set = { s, v -> s.copy(dailySummaryTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.ocr_progress.timeout_ms",
            label = "识别进度胶囊时长",
            description = "OCR 识别进行中的进度胶囊在自动消失前的最长停留时长（仅灵动形态，无普通对应）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(60000, "1 分钟"),
                    ConfigControl.IntOptions.Option(120000, "2 分钟（默认）"),
                    ConfigControl.IntOptions.Option(300000, "5 分钟"),
                )
            ),
            get = { it.ocrProgressTimeoutMs },
            set = { s, v -> s.copy(ocrProgressTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.model_loading.timeout_ms",
            label = "模型加载胶囊时长",
            description = "模型加载中的胶囊在自动消失前的最长停留时长（仅灵动形态，无普通对应）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(300000, "5 分钟"),
                    ConfigControl.IntOptions.Option(600000, "10 分钟（默认）"),
                    ConfigControl.IntOptions.Option(900000, "15 分钟"),
                )
            ),
            get = { it.modelLoadingTimeoutMs },
            set = { s, v -> s.copy(modelLoadingTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.input.enabled",
            label = "随口记",
            description = "随口记总开关；关闭后长按音量+和悬浮窗入口都不能启动随口记录音。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.voiceInputEnabled) 1 else 0 },
            set = { s, v -> s.copy(voiceInputEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_long_press.enabled",
            label = "悬浮窗长按随口记",
            description = "控制悬浮窗已呼出后再次长按音量+是否进入随口记录音；默认开启以保留旧行为。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingVoiceLongPressEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingVoiceLongPressEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_text_quick_memo.auto_pin_enabled",
            label = "悬浮窗文本随口记同步挂起",
            description = "悬浮窗随口记模式保存文本后，同步挂到实况通知；需开启实况通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingTextQuickMemoAutoPinEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingTextQuickMemoAutoPinEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.quick_memo.auto_pin_enabled",
            label = "语音随口记同步挂起",
            description = "语音随口记转写完成后，自动挂到实况通知；需开启实况通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.voiceQuickMemoAutoPinEnabled) 1 else 0 },
            set = { s, v -> s.copy(voiceQuickMemoAutoPinEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.quick_memo.recording_display_mode",
            label = "随口记录音展示",
            description = "控制所有随口记录音入口在录音时使用实况通知还是悬浮窗。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(QuickMemoRecordingDisplayMode.LIVE_CAPSULE, "实况通知"),
                    ConfigControl.IntOptions.Option(QuickMemoRecordingDisplayMode.FLOATING_WINDOW, "悬浮窗"),
                )
            ),
            get = { it.quickMemoRecordingDisplayMode },
            set = { s, v -> s.copy(quickMemoRecordingDisplayMode = QuickMemoRecordingDisplayMode.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating.entry_style",
            label = "悬浮入口样式",
            description = "旧兼容字段；当前侧边栏和悬浮球使用独立开关。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(FloatingEntryStyle.EDGE_BAR, "侧边栏"),
                    ConfigControl.IntOptions.Option(FloatingEntryStyle.FLOATING_BALL, "悬浮球"),
                )
            ),
            get = { it.floatingEntryStyle },
            set = { s, v -> s.copy(floatingEntryStyle = FloatingEntryStyle.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.enabled",
            label = "悬浮球入口",
            description = "独立显示可拖动悬浮球，可与侧边栏同时开启。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingBallEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingBallEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.single_tap_action",
            label = "悬浮球单击动作",
            description = "配置悬浮球单击触发的操作。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.floatingBallSingleTapAction },
            set = { s, v -> s.copy(floatingBallSingleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.double_tap_action",
            label = "悬浮球双击动作",
            description = "配置悬浮球双击触发的操作。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.floatingBallDoubleTapAction },
            set = { s, v -> s.copy(floatingBallDoubleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.long_press_action",
            label = "悬浮球长按动作",
            description = "配置悬浮球长按触发的操作。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.floatingBallLongPressAction },
            set = { s, v -> s.copy(floatingBallLongPressAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.edge_bar.single_tap_action",
            label = "侧边栏单击动作",
            description = "配置侧边栏单击触发的操作；滑动呼出日程仍保留。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.edgeBarSingleTapAction },
            set = { s, v -> s.copy(edgeBarSingleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.edge_bar.double_tap_action",
            label = "侧边栏双击动作",
            description = "配置侧边栏双击触发的操作；滑动呼出日程仍保留。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.edgeBarDoubleTapAction },
            set = { s, v -> s.copy(edgeBarDoubleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.edge_bar.long_press_action",
            label = "侧边栏长按动作",
            description = "配置侧边栏长按触发的操作；滑动呼出日程仍保留。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.edgeBarLongPressAction },
            set = { s, v -> s.copy(edgeBarLongPressAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_title",
            label = "拖拽文本包含标题",
            description = "从悬浮窗拖拽日程到输入框时，输出第一行标题。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeTitle) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeTitle = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_time",
            label = "拖拽文本包含时间",
            description = "从悬浮窗拖拽日程到输入框时，附加开始和结束时间。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeTime) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeTime = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_location",
            label = "拖拽文本包含地点",
            description = "从悬浮窗拖拽日程到输入框时，附加地点。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeLocation) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeLocation = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_description",
            label = "拖拽文本包含详情",
            description = "从悬浮窗拖拽日程到输入框时，附加备注或结构化详情。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeDescription) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeDescription = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.POLICY,
            key = "voice.floating_drag.hot_zone_percent",
            label = "拖拽热区范围",
            description = "控制悬浮卡片拖出和拖回取消使用的呼出侧热区宽度百分比。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(
                min = MySettings.FLOATING_DRAG_HOT_ZONE_MIN_PERCENT,
                max = MySettings.FLOATING_DRAG_HOT_ZONE_MAX_PERCENT,
                step = 5,
                unitLabel = " %"
            ),
            get = { it.floatingDragHotZonePercent },
            set = { s, v -> s.copy(floatingDragHotZonePercent = MySettings.normalizeFloatingDragHotZonePercent(v)) },
        ),
        // —— 通知·用户开关（USER_EDITABLE；将来普通设置页也照此渲染）——
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.daily_summary_enabled",
            label = "每日汇总通知",
            description = "开启后每天定时推送当日（及次日）日程汇总通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isDailySummaryEnabled) 1 else 0 },
            set = { s, v -> s.copy(isDailySummaryEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.daily_summary_morning_minute_of_day",
            label = "今日提醒时间",
            description = "每天推送今日日程汇总的时间，按当天 00:00 起的分钟数保存。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntInput(
                min = MySettings.DAILY_SUMMARY_MIN_MINUTE_OF_DAY,
                max = MySettings.DAILY_SUMMARY_MAX_MINUTE_OF_DAY,
                unitLabel = " 分钟"
            ),
            get = { it.dailySummaryMorningMinuteOfDay },
            set = { s, v -> s.copy(dailySummaryMorningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.daily_summary_evening_minute_of_day",
            label = "明日预告时间",
            description = "每天推送明日日程预告的时间，按当天 00:00 起的分钟数保存。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntInput(
                min = MySettings.DAILY_SUMMARY_MIN_MINUTE_OF_DAY,
                max = MySettings.DAILY_SUMMARY_MAX_MINUTE_OF_DAY,
                unitLabel = " 分钟"
            ),
            get = { it.dailySummaryEveningMinuteOfDay },
            set = { s, v -> s.copy(dailySummaryEveningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.advance_reminder_enabled",
            label = "日程提前提醒",
            description = "日程提前提醒总开关；关闭后不再为日程发送提前提醒通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isAdvanceReminderEnabled) 1 else 0 },
            set = { s, v -> s.copy(isAdvanceReminderEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.live_capsule_enabled",
            label = "实况胶囊通知",
            description = "开启后识别进度/结果、天气等以实况胶囊（灵动）形态展示。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isLiveCapsuleEnabled) 1 else 0 },
            set = { s, v -> s.copy(isLiveCapsuleEnabled = v != 0) },
        ),
    )

    /** 编辑页可见的配置项（滤掉系统内部项）。 */
    fun editableItems(): List<ConfigItem> =
        items.filter { it.exposure != ConfigExposure.SYSTEM_INTERNAL }

    /** 可见项里出现的域，保持声明顺序、去重。 */
    fun visibleDomains(): List<ConfigDomain> =
        editableItems().map { it.domain }.distinct()

    /** 某个域下的可见配置项。 */
    fun itemsInDomain(domain: ConfigDomain): List<ConfigItem> =
        editableItems().filter { it.domain == domain }

    private fun floatingGestureActionControl(): ConfigControl.IntOptions {
        return ConfigControl.IntOptions(
            FloatingBallGestureAction.ALL.map { action ->
                ConfigControl.IntOptions.Option(action, FloatingBallGestureAction.label(action))
            }
        )
    }
}
