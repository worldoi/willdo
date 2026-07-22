package com.antgskds.calendarassistant.service.capsule

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 胶囊发布站 —— 胶囊「一条线」里的发布分支。
 *
 * 职责（从 CapsuleStateManager 搬出，行为不变）：接收算好的 [CapsuleUiState]，
 * 决定走哪条 vendor 通道（小米超级岛 Xposed transport / 原生 / 魅族），并执行真正的
 * notify/cancel、stale 清理、聚合监控。CapsuleStateManager 从此只算状态、不发布。
 *
 * 小米超级岛走 Xposed/SystemUI 跨进程，是底层 transport 例外，此处仅作为入口分流，
 * 不把它塞进 app 进程内的 publisher 模型。
 *
 * @param uiStateProvider 读取当前 uiState（stale 清理需要核对实时状态）。
 */
class CapsuleDispatcher(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val settingsQueryApi: SettingsQueryApi,
    private val uiStateProvider: () -> CapsuleUiState
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val provider: ICapsuleProvider = NativeCapsuleProvider()
    private val activeNotifIds = ConcurrentHashMap.newKeySet<Int>()
    private var monitorJob: Job? = null
    private var isAggregateMode = false

    /** 胶囊状态变化时的总分发入口：原生通道发布/取消。 */
    fun dispatch(state: CapsuleUiState) {
        when (state) {
            is CapsuleUiState.Active -> updateCapsules(state.capsules)
            is CapsuleUiState.None -> {
                monitorJob?.cancel()
                isAggregateMode = false
                cancelAllCapsuleNotifications()
            }
        }
    }

    private fun updateCapsules(newCapsules: List<CapsuleUiState.Active.CapsuleItem>) {
        val validIds = newCapsules.map { it.notifId }.toSet()

        val newAggregateMode = newCapsules.any { it.id == AGGREGATE_PICKUP_ID }
        if (newAggregateMode && !isAggregateMode) {
            isAggregateMode = true
            startMonitoring()
        } else if (!newAggregateMode && isAggregateMode) {
            isAggregateMode = false
            monitorJob?.cancel()
        }

        newCapsules.forEach { item ->
            val iconResId = IconUtils.getSmallIconForCapsule(context, item)
            val notification = provider.buildNotification(context, item, iconResId)
            notificationManager.notify(item.notifId, notification)
            activeNotifIds.add(item.notifId)
            cancelLegacyCapsuleNotification(item)
        }

        // 维护日程胶囊的折叠汇总通知（X条待办日程 + 最新预览）
        updateScheduleReminderSummary(newCapsules)

        // 清理不再需要的通知
        val staleIds = activeNotifIds.toMutableSet()
        staleIds.removeAll(validIds)
        staleIds.forEach { id ->
            notificationManager.cancel(id)
            activeNotifIds.remove(id)
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000)
                if (isAggregateMode) {
                    cleanupStaleNotifications()
                }
            }
        }
    }

    private fun cleanupStaleNotifications() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.forEach { sb ->
                val notificationId = sb.id
                if (notificationId !in activeNotifIds) return@forEach
                val channelId = sb.notification.channelId
                val channelMatch = channelId != null && channelId.contains("live", ignoreCase = true)
                if (channelMatch) {
                    val state = uiStateProvider()
                    if (state is CapsuleUiState.Active) {
                        val stillValid = state.capsules.any { it.notifId == notificationId }
                        if (!stillValid) {
                            notificationManager.cancel(notificationId)
                            activeNotifIds.remove(notificationId)
                            Log.d(TAG, "清除过期胶囊通知: id=$notificationId")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理过期通知失败", e)
        }
    }

    private fun cancelAllCapsuleNotifications() {
        activeNotifIds.forEach { id ->
            notificationManager.cancel(id)
        }
        activeNotifIds.clear()
    }

    private fun cancelLegacyCapsuleNotification(item: CapsuleUiState.Active.CapsuleItem) {
        if (item.type != CapsuleType.SCHEDULE &&
            item.type != CapsuleType.PICKUP &&
            item.type != CapsuleType.PICKUP_EXPIRED
        ) return
        NotificationIds.legacyKeyIds(item.id)
            .filter { it != item.notifId }
            .forEach(notificationManager::cancel)
    }

    /**
     * 维护日程胶囊的折叠汇总通知：所有 SCHEDULE 胶囊归入同组，默认折叠，
     * 汇总标题显示「X条待办日程」+ 最新1条预览。数量归零时取消汇总。
     * 子胶囊本身保持 HIGH + 震动（实时提醒优先级不变），汇总静默、不弹窗。
     */
    private fun updateScheduleReminderSummary(capsules: List<CapsuleUiState.Active.CapsuleItem>) {
        val scheduleCaps = capsules.filter { it.type == CapsuleType.SCHEDULE }
        if (scheduleCaps.isEmpty()) {
            notificationManager.cancel(NotificationIds.SCHEDULE_REMINDER_GROUP)
            return
        }
        val latest = scheduleCaps.maxByOrNull { it.startMillis }
        val preview = latest?.display?.primaryText ?: ""
        val summaryText = if (scheduleCaps.size == 1) preview else "最新：$preview"
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NotificationIds.SCHEDULE_REMINDER_GROUP,
            summaryIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val summary = Notification.Builder(context, App.CHANNEL_ID_LIVE)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("${scheduleCaps.size}条待办日程")
            .setContentText(summaryText)
            .setGroup(NativeCapsuleProvider.GROUP_SCHEDULE_REMINDERS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setShowWhen(false)
            .setColor(latest?.color ?: 0)
            .build()
        notificationManager.notify(NotificationIds.SCHEDULE_REMINDER_GROUP, summary)
    }

    companion object {
        private const val TAG = "CapsuleDispatcher"
        private const val AGGREGATE_PICKUP_ID = "AGGREGATE_PICKUP"
    }
}
