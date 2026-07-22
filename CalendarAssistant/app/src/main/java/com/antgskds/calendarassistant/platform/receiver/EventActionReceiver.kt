package com.antgskds.calendarassistant.platform.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.isCompleted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DateTimeFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 事件动作接收器：处理通知上的「完成」「签到」按钮。
 * 统一通过 ActionTarget 路由到 ScheduleCenter 新 API。
 */
class EventActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COMPLETE = "com.antgskds.calendarassistant.action.COMPLETE"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_CHECKIN = "com.antgskds.calendarassistant.action.CHECKIN"
        const val ACTION_MOVE_TO_QUICK_MEMO = "com.antgskds.calendarassistant.action.MOVE_TO_QUICK_MEMO"
        const val ACTION_CREATE_QUICK_MEMO_SUGGESTION = "com.antgskds.calendarassistant.action.CREATE_QUICK_MEMO_SUGGESTION"
        const val ACTION_CLEAR_TEXT_QUICK_MEMO = "com.antgskds.calendarassistant.action.CLEAR_TEXT_QUICK_MEMO"
        const val ACTION_DEBUG_PRIMARY = "com.antgskds.calendarassistant.action.DEBUG_PRIMARY"
        const val ACTION_DEBUG_SECONDARY = "com.antgskds.calendarassistant.action.DEBUG_SECONDARY"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_SUGGESTION_ID = "suggestion_id"
        const val EXTRA_QUICK_MEMO_ID = "quick_memo_id"
        private const val RECURRING_INSTANCE_PREFIX = "rec:"
        private const val TAG = "EventActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val scheduleCenter = app.scheduleCenter
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.d(TAG, "receive action=${intent.action} eventId=${intent.getStringExtra(EXTRA_EVENT_ID)}")

        when (intent.action) {
            ACTION_DEBUG_PRIMARY, ACTION_DEBUG_SECONDARY -> {
                Log.d(TAG, "debug notification action clicked action=${intent.action}")
            }
            ACTION_CLEAR_TEXT_QUICK_MEMO -> {
                val memoId = intent.getLongExtra(EXTRA_QUICK_MEMO_ID, -1L).takeIf { it > 0L }
                    ?: run {
                        Log.w(TAG, "ignore quick memo clear action: missing memo id")
                        return
                    }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        app.quickMemoCenter.clearPinnedTextQuickMemo(memoId)
                        Log.d(TAG, "text quick memo capsule cleared memoId=$memoId")
                    } catch (t: Throwable) {
                        Log.e(TAG, "text quick memo clear failed memoId=$memoId", t)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_CREATE_QUICK_MEMO_SUGGESTION -> {
                val suggestionId = intent.getLongExtra(EXTRA_SUGGESTION_ID, -1L).takeIf { it > 0L }
                    ?: run {
                        Log.w(TAG, "ignore quick memo action: missing suggestion id")
                        return
                    }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val suggestion = app.quickMemoCenter.getSuggestion(suggestionId) ?: run {
                            Log.w(TAG, "quick memo suggestion not found: $suggestionId")
                            return@launch
                        }
                        if (suggestion.status != QuickMemoSuggestionStatus.PENDING) {
                            Log.d(TAG, "quick memo suggestion ignored: id=$suggestionId status=${suggestion.status}")
                            return@launch
                        }
                        val draft = QuickMemoSuggestionCodec.decode(suggestion.candidateJson) ?: run {
                            Log.w(TAG, "quick memo suggestion decode failed: $suggestionId")
                            return@launch
                        }
                        val settings = app.settingsQueryApi.settings.value
                        val event = convertDraftToEvent(
                            draft = draft,
                            defaultDurationMinutes = settings.defaultEventDurationMinutes,
                            forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow,
                            eventColorPaletteHex = settings.eventColorPaletteHex
                        )
                        val eventId = scheduleCenter.addEvent(event)
                        app.quickMemoCenter.markSuggestionCreated(suggestionId, eventId)
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.cancel(NotificationIds.quickMemoSuggestion(suggestionId))
                        Log.d(TAG, "quick memo suggestion created eventId=$eventId suggestionId=$suggestionId")
                    } catch (t: Throwable) {
                        Log.e(TAG, "quick memo action failed: suggestionId=$suggestionId", t)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_MOVE_TO_QUICK_MEMO -> {
                handleMoveToQuickMemo(context, intent, app, scope)
            }
            ACTION_COMPLETE, ACTION_COMPLETE_SCHEDULE, ACTION_CHECKIN -> {
                val eventIdStr = intent.getStringExtra(EXTRA_EVENT_ID) ?: run {
                    Log.w(TAG, "ignore event action: missing event id action=${intent.action}")
                    return
                }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (eventIdStr == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            // 聚合取件完成：完成所有活跃的取件事件
                            val pickups = scheduleCenter.events.value.filter {
                                it.tag in setOf(EventTags.PICKUP, EventTags.FOOD, EventTags.TICKET, EventTags.SENDER) && !it.isCompleted
                            }
                            pickups.forEach { event ->
                                val id = event.id ?: return@forEach
                                scheduleCenter.completeItem(ActionTarget.Single(id))
                            }
                            Log.d(TAG, "aggregate pickup action completed count=${pickups.size}")
                        } else if (eventIdStr.startsWith(RECURRING_INSTANCE_PREFIX)) {
                            val target = parseRecurringTarget(eventIdStr) ?: run {
                                Log.w(TAG, "ignore event action: invalid recurring id=$eventIdStr")
                                return@launch
                            }
                            when (intent.action) {
                                ACTION_CHECKIN -> scheduleCenter.checkInItem(target)
                                else -> scheduleCenter.completeItem(target)
                            }
                            Log.d(TAG, "event action applied recurring=$eventIdStr action=${intent.action}")
                        } else {
                            val targetEventId = eventIdStr.toLongOrNull() ?: run {
                                Log.w(TAG, "ignore event action: invalid event id=$eventIdStr")
                                return@launch
                            }
                            val event = scheduleCenter.events.value.find { it.id == targetEventId } ?: run {
                                Log.w(TAG, "ignore event action: event not found id=$targetEventId")
                                return@launch
                            }
                            val target = ActionTarget.Single(targetEventId)

                            when (intent.action) {
                                ACTION_CHECKIN -> scheduleCenter.checkInItem(target)
                                else -> scheduleCenter.completeItem(target)
                            }
                            Log.d(TAG, "event action applied id=$targetEventId title=${event.title} action=${intent.action}")
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "event action failed action=${intent.action} eventId=$eventIdStr", t)
                    } finally {
                        // 点击「已完成/签到」后移除对应提醒通知，并触发折叠分组更新
                        // （聚合取件走独立通知，不在此取消）
                        if (eventIdStr != CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            if (eventIdStr.startsWith(RECURRING_INSTANCE_PREFIX)) {
                                mgr.cancel(NotificationIds.standardReminder(eventIdStr))
                            } else {
                                eventIdStr.toLongOrNull()?.let { mgr.cancel(NotificationIds.standardReminder(it)) }
                            }
                        }
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun parseRecurringTarget(eventId: String): ActionTarget.RecurringOccurrence? {
        val parts = eventId.split(':')
        val parentId = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val occurrenceTs = parts.getOrNull(2)?.toLongOrNull() ?: return null
        return ActionTarget.RecurringOccurrence(parentId, occurrenceTs)
    }

    /**
     * Scheme 2：将单条日程的完整内容（标题 / 时间 / 备注）迁移到随口记模块，
     * 并清除该条系统提醒通知（触发折叠分组更新）。不标记日程为「已完成」，
     * 默认不跳转 APP，仅以轻量 Toast 提示结果。
     */
    private fun handleMoveToQuickMemo(context: Context, intent: Intent, app: App, scope: CoroutineScope) {
        val eventIdStr = intent.getStringExtra(EXTRA_EVENT_ID) ?: run {
            Log.w(TAG, "ignore move-to-quick-memo: missing event id")
            return
        }
        // 兼容普通事件（数字 id）与重复实例（rec:parentId:ts）两种格式
        val parentId = if (eventIdStr.startsWith(RECURRING_INSTANCE_PREFIX)) {
            eventIdStr.split(":").getOrNull(1)?.toLongOrNull()
        } else {
            eventIdStr.toLongOrNull()
        }
        if (parentId == null) {
            Log.w(TAG, "ignore move-to-quick-memo: invalid event id=$eventIdStr")
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                val event = app.scheduleCenter.events.value.find { it.id == parentId }
                if (event == null) {
                    showToast(context, "日程不存在")
                    return@launch
                }
                val timeText = buildTimeTextForMemo(event)
                val memoText = buildString {
                    append(event.title.ifBlank { "未命名日程" })
                    if (timeText.isNotBlank()) append("\n$timeText")
                    val desc = event.description?.let { stripSourceImageMarkers(it) }?.takeIf { it.isNotBlank() }
                    if (desc != null) append("\n$desc")
                }
                app.quickMemoCenter.createTextMemo(memoText, asTodo = true)
                // 清除该条通知（不标记已完成），deleteIntent 会更新折叠分组
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NotificationIds.standardReminder(eventIdStr))
                showToast(context, "已移至随口记")
                Log.d(TAG, "moved event to quick memo id=$eventIdStr parentId=$parentId")
            } catch (t: Throwable) {
                Log.e(TAG, "move to quick memo failed id=$eventIdStr", t)
                showToast(context, "迁移失败")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildTimeTextForMemo(event: com.antgskds.calendarassistant.calendar.models.Event): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
        val zone = ZoneId.systemDefault()
        val start = runCatching {
            fmt.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTS * 1000L), zone))
        }.getOrElse { "" }
        // 永久日程（endTS ≈ startTS + 100年）等超大跨度只显示开始时间
        val spanYears = (event.endTS - event.startTS) / (365L * 24 * 3600)
        val end = if (spanYears > 10L) "" else runCatching {
            fmt.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTS * 1000L), zone))
        }.getOrElse { "" }
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start - $end"
            start.isNotBlank() -> start
            else -> ""
        }
    }

    private fun showToast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
