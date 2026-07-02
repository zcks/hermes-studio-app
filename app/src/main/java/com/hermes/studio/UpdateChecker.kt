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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/zcks/hermes-studio-app/releases/latest"

    fun checkOnStart(context: Context, currentVersionCode: Int) {
        if (!isWifiConnected(context)) return

        Thread {
            try {
                val conn = URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val changelog = json.optString("body", "")

                // Extract version code from tag (v1.3.0 -> 5, need to parse)
                val versionName = tagName.removePrefix("v")
                val remoteCode = parseVersionCode(versionName)

                // Find debug APK download URL
                val assets = json.optJSONArray("assets") ?: JSONArray()
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith("-debug.apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }

                if (remoteCode > currentVersionCode && apkUrl.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        showUpdateDialog(context, versionName, changelog, apkUrl)
                    }
                }
            } catch (_: Exception) {
                // Silent fail - network issues are expected
            }
        }.start()
    }

    private fun parseVersionCode(versionName: String): Int {
        // Convert "1.3.0" to version code (major*10000 + minor*100 + patch)
        val parts = versionName.split(".")
        var code = 0
        for (part in parts) {
            code = code * 100 + (part.toIntOrNull() ?: 0)
        }
        return code
    }

    private fun showUpdateDialog(context: Context, versionName: String, changelog: String, apkUrl: String) {
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
            .setPositiveButton("下载更新") { _, _ -> downloadAndInstall(context, apkUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, apkUrl: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("下载 Hermes Studio")
            .setDescription("正在下载新版本...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "hermes-studio-update.apk")
            .setAllowedOverMetered(true)

        val downloadId = downloadManager.enqueue(request)

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
