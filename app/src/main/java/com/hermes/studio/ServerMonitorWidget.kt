package com.hermes.studio

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ServerMonitorWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.hermes.studio.REFRESH_WIDGET"
        const val EXTRA_WIDGET_ID = "appWidgetId"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_server)

            // Set up refresh button click
            val refreshIntent = Intent(context, ServerMonitorWidget::class.java).apply {
                action = ACTION_REFRESH
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
            }
            views.setOnClickPendingIntent(
                R.id.widgetRefreshBtn,
                android.app.PendingIntent.getBroadcast(
                    context, appWidgetId, refreshIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Try to read cached data from SharedPreferences
            val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("widget_server_url_$appWidgetId", null)
            val cpu = prefs.getString("widget_cpu_$appWidgetId", null)
            val memory = prefs.getString("widget_memory_$appWidgetId", null)
            val disk = prefs.getString("widget_disk_$appWidgetId", null)
            val status = prefs.getString("widget_status_$appWidgetId", null)
            val lastUpdate = prefs.getString("widget_last_update_$appWidgetId", null)

            if (cpu != null) views.setTextViewText(R.id.widgetCpu, cpu)
            if (memory != null) views.setTextViewText(R.id.widgetMemory, memory)
            if (disk != null) views.setTextViewText(R.id.widgetDisk, disk)

            if (status != null) {
                views.setTextViewText(R.id.widgetStatus, status)
                if (status == "在线") {
                    views.setTextColor(R.id.widgetStatus, 0xFF4CAF50.toInt())
                } else {
                    views.setTextColor(R.id.widgetStatus, 0xFFF44336.toInt())
                }
            }

            if (lastUpdate != null) {
                views.setTextViewText(R.id.widgetLastUpdate, lastUpdate)
            }

            if (serverUrl != null) {
                views.setTextViewText(R.id.widgetTitle, "服务器监控")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun startPeriodicUpdate(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                5, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "server_monitor_widget_update",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        startPeriodicUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, widgetId)

                // Trigger immediate work
                val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                    5, TimeUnit.MINUTES
                ).build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startPeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork("server_monitor_widget_update")
    }
}
