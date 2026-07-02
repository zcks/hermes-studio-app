package com.hermes.studio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MessageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val conn = URL(NOTIFICATION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val hasNewMessage = json.optBoolean("hasNewMessage", false)
            val title = json.optString("title", "Hermes Studio")
            val message = json.optString("message", "你有新的消息")

            if (hasNewMessage) {
                showNotification(title, message)
            }
        } catch (_: Exception) {
            // Silent fail
        }
        Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Hermes Studio",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Hermes Studio 消息通知"
            }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_URL = "http://192.168.31.15:8649/notification.json"
        private const val CHANNEL_ID = "hermes_messages"
        private const val NOTIFICATION_ID = 2001
    }
}
