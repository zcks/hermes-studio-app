package com.hermes.studio

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val VERSION_URL = "http://192.168.31.15:8649/version.json"
    private const val APK_URL = "http://192.168.31.15:8649/app-debug.apk"

    fun checkOnStart(context: Context, currentVersionCode: Int) {
        if (!isWifiConnected(context)) return

        Thread {
            try {
                val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(body)
                val remoteCode = json.optInt("versionCode", 0)
                val remoteName = json.optString("versionName", "")
                val changelog = json.optString("changelog", "")

                if (remoteCode > currentVersionCode) {
                    Handler(Looper.getMainLooper()).post {
                        showUpdateDialog(context, remoteName, changelog)
                    }
                }
            } catch (_: Exception) {
                // Silent fail - network issues are expected on some networks
            }
        }.start()
    }

    private fun showUpdateDialog(context: Context, versionName: String, changelog: String) {
        val message = buildString {
            append("发现新版本 $versionName")
            if (changelog.isNotEmpty()) {
                append("\n\n更新内容:\n$changelog")
            }
            append("\n\n是否立即下载更新？")
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("应用更新")
            .setMessage(message)
            .setPositiveButton("下载更新") { _, _ -> downloadAndInstall(context) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(context: Context) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(APK_URL))
            .setTitle("下载 Hermes Studio")
            .setDescription("正在下载新版本...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "hermes-studio-update.apk")
            .setAllowedOverMetered(false)

        val downloadId = downloadManager.enqueue(request)

        // Register receiver to install when download completes
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "hermes-studio-update.apk"
                    )
                    val uri = FileProvider.getUriForFile(
                        ctx, "${ctx.packageName}.fileprovider", file
                    )
                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(install)
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }
        context.registerReceiver(
            receiver,
            android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
