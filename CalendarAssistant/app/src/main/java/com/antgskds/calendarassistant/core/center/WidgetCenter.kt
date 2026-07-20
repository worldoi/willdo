package com.antgskds.calendarassistant.core.center

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.antgskds.calendarassistant.core.query.CalendarQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.WidgetScheduleQueryApi
import com.antgskds.calendarassistant.platform.widget.ScheduleWidgetProvider
import com.antgskds.calendarassistant.platform.widget.ScheduleWidgetRenderer
import com.antgskds.calendarassistant.platform.widget.WidgetType
import com.antgskds.calendarassistant.platform.widget.WidgetInstanceConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetCenter(
    private val appContext: Context,
    private val calendarQueryApi: CalendarQueryApi,
    private val settingsQueryApi: SettingsQueryApi,
    private val widgetScheduleQueryApi: WidgetScheduleQueryApi,
    private val appScope: CoroutineScope
) {
    private val scheduleRenderer = ScheduleWidgetRenderer(appContext)
    private val configStore = WidgetInstanceConfigStore(appContext)
    private var refreshJob: Job? = null
    private var subscriptionsStarted = false

    fun startSubscriptions() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        appScope.launch {
            settingsQueryApi.settings.collectLatest {
                requestRefresh()
            }
        }
    }

    fun requestRefresh() {
        refreshJob?.cancel()
        refreshJob = appScope.launch(Dispatchers.IO) {
            delay(200)
            refreshAllWidgets()
        }
    }

    fun refreshAllWidgets() {
        refreshWidgetsOfType(WidgetType.SCHEDULE)
    }

    fun refreshWidgetsOfType(type: WidgetType) {
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, providerClass(type))
        val ids = manager.getAppWidgetIds(component)
        refreshWidgets(manager, ids, type)
    }

    fun refreshWidgets(manager: AppWidgetManager, appWidgetIds: IntArray, type: WidgetType = WidgetType.SCHEDULE) {
        if (appWidgetIds.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            val settings = settingsQueryApi.settings.value
            val events by lazy { calendarQueryApi.getEvents() }
            val scheduleSnapshot by lazy { widgetScheduleQueryApi.buildSnapshot(events) }
            appWidgetIds.forEach { widgetId ->
                val config = configStore.ensureConfig(widgetId, type, settings)
                val options = manager.getAppWidgetOptions(widgetId)
                val views = scheduleRenderer.render(widgetId, options, scheduleSnapshot, settings, config)
                withContext(Dispatchers.Main) {
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }
    }

    fun deleteWidgetConfig(appWidgetIds: IntArray) {
        appWidgetIds.forEach(configStore::deleteConfig)
    }

    private fun providerClass(type: WidgetType): Class<*> = when (type) {
        WidgetType.SCHEDULE -> ScheduleWidgetProvider::class.java
    }
}
