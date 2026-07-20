package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.platform.receiver.DailySummaryReceiver
import com.antgskds.calendarassistant.platform.receiver.KeepAliveReceiver
import com.antgskds.calendarassistant.platform.receiver.ReminderReconcileReceiver
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.platform.clipboard.ClipboardCodeMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RuntimeCenter(
    private val appContext: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val permissionCenter: PermissionCenter,
    private val floatingCenter: FloatingCenter,
    private val capsuleCenter: CapsuleCenter,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "RuntimeCenter"
    }

    private var clipboardCodeMonitorJob: Job? = null

    fun startAppRoutines() {
        restoreSmsNotificationListenerIfNeeded()
        startPeriodicSync()
        scheduleKeepAlive()
        scheduleReminderReconcile()
        startEdgeBarIfNeeded()
        startClipboardCodeMonitoring()
    }

    fun restoreAfterBoot() {
        scheduleDailySummary()
        scheduleKeepAlive()
        scheduleReminderReconcile()
        startPeriodicSync()
        restoreSmsNotificationListenerIfNeeded()
        startEdgeBarIfNeeded()
        startClipboardCodeMonitoring()
    }

    fun startPeriodicSync() {
    }

    fun scheduleDailySummary() {
        DailySummaryReceiver.schedule(appContext)
    }

    fun scheduleKeepAlive() {
        KeepAliveReceiver.schedule(appContext)
    }

    fun scheduleReminderReconcile() {
        ReminderReconcileReceiver.schedule(appContext)
    }

    fun restoreSmsNotificationListenerIfNeeded() {
        try {
            val settings = settingsQueryApi.settings.value
            if (settings.isSmsMonitoringEnabled) {
                SmsNotificationListenerService.rebind(appContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Restore SMS notification listener failed", e)
        }
    }


    fun startEdgeBarIfNeeded() {
        try {
            val settings = settingsQueryApi.settings.value
            if (settings.isFloatingWindowEnabled && settings.edgeBarEnabled && permissionCenter.canDrawOverlays(appContext)) {
                floatingCenter.startEdgeBarServiceIfPermitted()
            }
            if (settings.isFloatingWindowEnabled && settings.floatingBallEnabled && permissionCenter.canDrawOverlays(appContext)) {
                floatingCenter.startFloatingBallServiceIfPermitted()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Start edge bar failed", e)
        }
    }

    fun startClipboardCodeMonitoring() {
        if (clipboardCodeMonitorJob?.isActive == true) return
        clipboardCodeMonitorJob = appScope.launch {
            settingsQueryApi.settings.collectLatest { settings ->
                if (settings.clipboardCodeRecognitionEnabled) {
                    ClipboardCodeMonitorService.startIfNeeded(appContext)
                } else {
                    ClipboardCodeMonitorService.stop(appContext)
                }
            }
        }
    }
}
