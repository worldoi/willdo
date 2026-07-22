package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.core.content.EventCapsulePresenter
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.RecognitionLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.SystemLiveDisplay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CapsuleMessageComposer {
    // --- 非事件类胶囊 ---

    fun composeOcrProgress(title: String, content: String): CapsuleDisplayModel {
        return RecognitionLiveDisplay.progress(title, content)
    }

    fun composeOcrResult(title: String, content: String): CapsuleDisplayModel {
        return RecognitionLiveDisplay.statusResult(title, content)
    }

    fun composeModelLoading(title: String, content: String): CapsuleDisplayModel {
        return SystemLiveDisplay.modelLoading(title, content)
    }

    fun composeVoiceTranscription(title: String): CapsuleDisplayModel {
        return SystemLiveDisplay.voiceTranscription(title)
    }

    fun composeTextQuickMemo(title: String, memoId: Long): CapsuleDisplayModel {
        return SystemLiveDisplay.textQuickMemo(title).copy(
            action = CapsuleActionSpec(
                label = "移除",
                receiverAction = EventActionReceiver.ACTION_CLEAR_TEXT_QUICK_MEMO,
                extraLongKey = EventActionReceiver.EXTRA_QUICK_MEMO_ID,
                extraLongValue = memoId
            )
        )
    }

    fun composeQuickMemoRecording(title: String, content: String): CapsuleDisplayModel {
        return SystemLiveDisplay.quickMemoRecording(title, content)
    }

    // --- 事件类胶囊 (委托 EventPresenter) ---

    fun composeSchedule(
        context: Context,
        event: Event,
        isExpired: Boolean,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        val base = EventCapsulePresenter.present(context, event, isExpired, templateMode).displayModel
        // 普通日程（未过期）追加「移至随口记」动作，与「已完成」并列显示
        val moveAction = if (!isExpired) {
            CapsuleActionSpec(label = "移至随口记", receiverAction = EventActionReceiver.ACTION_MOVE_TO_QUICK_MEMO)
        } else null
        return base.copy(actions = listOfNotNull(base.action, moveAction))
    }

    fun composePickup(context: Context, event: Event, isExpired: Boolean): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired).displayModel
    }

    fun composeAggregatePickup(context: Context, pickupEvents: List<Event>): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, pickupEvents).displayModel
    }

    /**
     * 聚合所有待办日程为单条胶囊内容：
     * - 顶部汇总标题「共 X 条待办日程」
     * - 按开始时间先后列出每条标题 + 起止时间（≤2 条完整；>4 条只列前 3 条并补「还有 X 条待办」）
     * - 「已完成」/「移至随口记」两个动作（动作不携带 extraLongKey，由发布器兜底注入胶囊 item.id =
     *   最近一条待办的 eventId，接收器据此精确作用于该条）
     * - 点击主体跳「全部日程」页
     */
    fun composeAggregateSchedule(
        context: Context,
        events: List<Event>
    ): CapsuleDisplayModel {
        val sorted = events.sortedBy { it.startTS }
        val count = sorted.size
        val primaryText = "共 $count 条待办日程"
        val first = sorted.first()
        val secondaryText = "${first.title.ifBlank { "未命名日程" }} · ${formatScheduleTime(first)}"

        val shownCount = if (count > 4) 3 else count
        val shown = sorted.take(shownCount)
        val listText = shown.joinToString("\n") { event ->
            "${event.title.ifBlank { "未命名日程" }}\n${formatScheduleTime(event)}"
        }
        val remaining = count - shownCount
        val expandedText = buildString {
            append(listText)
            if (remaining > 0) append("\n还有 $remaining 条待办")
        }

        val actions = listOf(
            CapsuleActionSpec(label = "已完成", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE),
            CapsuleActionSpec(label = "移至随口记", receiverAction = EventActionReceiver.ACTION_MOVE_TO_QUICK_MEMO)
        )

        return CapsuleDisplayModel(
            shortText = primaryText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            expandedText = expandedText,
            tapOpensAllSchedule = true,
            actions = actions
        )
    }

    private fun formatScheduleTime(event: Event): String {
        val zone = ZoneId.systemDefault()
        val start = runCatching {
            LocalDateTime.ofInstant(Instant.ofEpochSecond(event.startTS), zone)
        }.getOrElse { return "" }
        val end = runCatching {
            LocalDateTime.ofInstant(Instant.ofEpochSecond(event.endTS), zone)
        }.getOrElse { return "" }
        val fmt = DateTimeFormatter.ofPattern("M/d HH:mm")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val startStr = fmt.format(start)
        val endStr = if (start.toLocalDate() == end.toLocalDate()) timeFmt.format(end) else fmt.format(end)
        return "$startStr - $endStr"
    }
}
