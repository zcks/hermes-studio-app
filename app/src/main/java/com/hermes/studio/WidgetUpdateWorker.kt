package com.hermes.studio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val DEFAULT_SERVER_URL = "http://192.168.31.98:8080/api/status"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get all widget IDs
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(applicationContext, ServerMonitorWidget::class.java)
            )

            if (widgetIds.isEmpty()) {
                return@withContext Result.success()
            }

            // Get server URL from first widget's config
            val prefs = applicationContext.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("widget_server_url_${widgetIds[0]}", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

            // Fetch server status
            val statusData = fetchServerStatus(serverUrl)

            // Update cache for all widgets
            val editor = prefs.edit()
            for (widgetId in widgetIds) {
                if (statusData != null) {
                    editor.putString("widget_cpu_$widgetId", statusData.cpu)
                    editor.putString("widget_memory_$widgetId", statusData.memory)
                    editor.putString("widget_disk_$widgetId", statusData.disk)
                    editor.putString("widget_status_$widgetId", "在线")
                    editor.putString("widget_last_update_$widgetId", statusData.lastUpdate)
                } else {
                    editor.putString("widget_status_$widgetId", "离线")
                    editor.putString("widget_last_update_$widgetId", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                }
                editor.putString("widget_server_url_$widgetId", serverUrl)
            }
            editor.apply()

            // Update widgets
            for (widgetId in widgetIds) {
                ServerMonitorWidget.updateAppWidget(applicationContext, appWidgetManager, widgetId)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget update failed", e)
            Result.retry()
        }
    }

    private fun fetchServerStatus(serverUrl: String): ServerStatusData? {
        return try {
            val url = URL(serverUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(response)
                ServerStatusData(
                    cpu = "${json.optDouble("cpu_usage", 0.0).toInt()}%",
                    memory = "${json.optDouble("memory_usage", 0.0).toInt()}%",
                    disk = "${json.optDouble("disk_usage", 0.0).toInt()}%",
                    lastUpdate = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch server status", e)
            null
        }
    }

    private data class ServerStatusData(
        val cpu: String,
        val memory: String,
        val disk: String,
        val lastUpdate: String
    )
}
