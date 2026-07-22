package com.antgskds.calendarassistant.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.App

/**
 * 日程提醒通知被移除（用户滑动清除 / 代码 cancel）时由系统回调。
 * 用于在「折叠分组」模式下从活动提醒登记表移除对应条目并重建汇总通知，
 * 保持折叠组标题的「X条待办日程」计数与预览准确。
 */
class ScheduleReminderDeleteReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.antgskds.calendarassistant.action.SCHEDULE_REMINDER_DISMISSED"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val TAG = "ScheduleReminderDelete"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (id <= 0) return
        val app = context.applicationContext as? App ?: run {
            Log.w(TAG, "applicationContext 不是 App 实例，忽略 id=$id")
            return
        }
        app.notificationCenter.onScheduleReminderDismissed(id)
    }
}
