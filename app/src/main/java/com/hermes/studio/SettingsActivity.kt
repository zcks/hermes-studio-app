package com.hermes.studio

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "hermes_settings"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_URLS = "server_urls"
        const val KEY_AUTO_SELECT = "auto_select"
        const val KEY_CLIPBOARD_SYNC = "clipboard_sync"
        const val KEY_NOTIFICATION = "notification_push"
        const val KEY_AUTO_UPDATE = "auto_update_check"
        const val KEY_LANDSCAPE_LOCK = "landscape_lock"

        fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        fun getServerUrl(context: Context): String {
            val urls = getServerUrls(context)
            return if (urls.isNotEmpty()) urls[0] else ""
        }

        fun getServerUrls(context: Context): List<String> {
            val prefs = getPrefs(context)
            val json = prefs.getString(KEY_SERVER_URLS, null)
            if (json != null) {
                try {
                    val array = JSONArray(json)
                    return (0 until array.length()).map { array.getString(it) }
                } catch (_: Exception) {
                    // Fall back to single URL
                }
            }
            // Legacy support: migrate single URL to list
            val singleUrl = prefs.getString(KEY_SERVER_URL, "") ?: ""
            if (singleUrl.isNotEmpty()) {
                saveServerUrls(context, listOf(singleUrl))
                return listOf(singleUrl)
            }
            return emptyList()
        }

        fun saveServerUrls(context: Context, urls: List<String>) {
            val array = JSONArray()
            urls.forEach { array.put(it) }
            getPrefs(context).edit().putString(KEY_SERVER_URLS, array.toString()).apply()
        }

        fun isAutoSelectEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_AUTO_SELECT, false)
        }

        fun isClipboardSyncEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_CLIPBOARD_SYNC, true)
        }

        fun isAutoUpdateEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_AUTO_UPDATE, true)
        }

        fun isLandscapeLockEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_LANDSCAPE_LOCK, false)
        }

        fun isNotificationEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_NOTIFICATION, true)
        }

        /**
         * Mask server URL for privacy display.
         * http://192.168.31.99:8648 → http://192.168.***.***:8648
         * https://server.lifang.asia:8648 → https://server.***.***:8648
         */
        fun maskUrl(url: String): String {
            try {
                val uri = URI(url)
                val host = uri.host ?: return url
                val parts = host.split(".")
                if (parts.size >= 2) {
                    val masked = parts.first() + ".***" + ".***" + parts.drop(2).joinToString("") { ".$it" }
                    return url.replace(host, masked)
                }
            } catch (_: Exception) {}
            return url
        }
    }

    private lateinit var serverAddressList: LinearLayout
    private lateinit var autoSelectSwitch: SwitchMaterial
    private lateinit var clipboardSyncSwitch: SwitchMaterial
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var autoUpdateSwitch: SwitchMaterial
    private lateinit var landscapeLockSwitch: SwitchMaterial

    private val addressStatusMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        serverAddressList = findViewById(R.id.serverAddressList)
        autoSelectSwitch = findViewById(R.id.autoSelectSwitch)
        clipboardSyncSwitch = findViewById(R.id.clipboardSyncSwitch)
        notificationSwitch = findViewById(R.id.notificationSwitch)
        autoUpdateSwitch = findViewById(R.id.autoUpdateSwitch)
        landscapeLockSwitch = findViewById(R.id.landscapeLockSwitch)

        loadSettings()

        // Add address button
        findViewById<MaterialButton>(R.id.addAddressBtn).setOnClickListener {
            showAddAddressDialog()
        }

        // Save settings button
        findViewById<MaterialButton>(R.id.saveSettingsBtn).setOnClickListener {
            saveSettings()
            finish()
        }

        // Clear cache
        findViewById<MaterialButton>(R.id.clearCacheBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除缓存")
                .setMessage("确定要清除缓存吗？")
                .setPositiveButton("确定") { _, _ ->
                    deleteDatabase("webviewCache.db")
                    cacheDir.deleteRecursively()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // Clear cookies
        findViewById<MaterialButton>(R.id.clearCookiesBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除 Cookies")
                .setMessage("确定要清除所有 Cookies 吗？")
                .setPositiveButton("确定") { _, _ ->
                    CookieManager.getInstance().removeAllCookies(null)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // Clear all data
        findViewById<MaterialButton>(R.id.clearAllDataBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除所有数据")
                .setMessage("此操作将清除所有应用数据，包括设置、缓存和 Cookies。此操作不可撤销！")
                .setPositiveButton("确定清除") { _, _ ->
                    CookieManager.getInstance().removeAllCookies(null)
                    cacheDir.deleteRecursively()
                    deleteDatabase("webviewCache.db")
                    getPrefs(this).edit().clear().apply()
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadSettings() {
        val prefs = getPrefs(this)
        autoSelectSwitch.isChecked = prefs.getBoolean(KEY_AUTO_SELECT, false)
        clipboardSyncSwitch.isChecked = prefs.getBoolean(KEY_CLIPBOARD_SYNC, true)
        notificationSwitch.isChecked = prefs.getBoolean(KEY_NOTIFICATION, true)
        autoUpdateSwitch.isChecked = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        landscapeLockSwitch.isChecked = prefs.getBoolean(KEY_LANDSCAPE_LOCK, false)

        // Load server addresses
        val urls = getServerUrls(this)
        serverAddressList.removeAllViews()
        urls.forEach { url -> addAddressItem(url) }
    }

    private fun showAddAddressDialog() {
        val input = EditText(this).apply {
            hint = "例如: http://192.168.1.100:8080"
            setPadding(64, 32, 64, 32)
        }

        val statusText = TextView(this).apply {
            textSize = 14f
            setPadding(64, 16, 64, 0)
        }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(statusText)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("添加服务器地址")
            .setView(dialogLayout)
            .setPositiveButton("添加", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false

        val handler = Handler(Looper.getMainLooper())
        var detectionRunnable: Runnable? = null
        var currentTestUrl: String = ""

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                detectionRunnable?.let { handler.removeCallbacks(it) }
                val url = s?.toString()?.trim() ?: ""
                if (url.isEmpty()) {
                    statusText.text = ""
                    positiveButton.isEnabled = false
                    currentTestUrl = ""
                    return
                }
                statusText.text = "⏳ 正在检测..."
                statusText.setTextColor(Color.parseColor("#FF9800"))
                positiveButton.isEnabled = false
                currentTestUrl = url

                detectionRunnable = Runnable {
                    if (url != currentTestUrl) return@Runnable
                    Thread {
                        val reachable = try {
                            val conn = URL(url).openConnection() as HttpURLConnection
                            conn.connectTimeout = 3000
                            conn.readTimeout = 3000
                            conn.requestMethod = "HEAD"
                            conn.connect()
                            val code = conn.responseCode
                            conn.disconnect()
                            code in 200..399
                        } catch (_: Exception) {
                            false
                        }
                        runOnUiThread {
                            if (!dialog.isShowing) return@runOnUiThread
                            if (url != currentTestUrl) return@runOnUiThread
                            if (reachable) {
                                statusText.text = "✅ 连接成功，已自动加入"
                                statusText.setTextColor(Color.parseColor("#4CAF50"))
                                addAddressItem(url)
                                dialog.dismiss()
                            } else {
                                statusText.text = "❌ 连接失败，仍可手动添加"
                                statusText.setTextColor(Color.parseColor("#F44336"))
                                positiveButton.isEnabled = true
                            }
                        }
                    }.start()
                }
                handler.postDelayed(detectionRunnable!!, 800)
            }
        })

        positiveButton.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) {
                addAddressItem(url)
            }
            dialog.dismiss()
        }
    }

    private fun addAddressItem(url: String) {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            layoutParams = params
        }

        // Status indicator
        val statusIcon = TextView(this).apply {
            text = "⏳"
            textSize = 16f
            setPadding(0, 0, 12, 0)
        }

        // URL text - display masked URL by default, store full URL in tag
        val urlText = TextView(this).apply {
            text = maskUrl(url)
            tag = url  // Store full URL for saving
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.parseColor("#333333"))
            maxLines = 1
            // Toggle mask on click
            setOnClickListener {
                val currentFullUrl = tag as? String ?: return@setOnClickListener
                if (text.toString().contains("***")) {
                    text = currentFullUrl
                } else {
                    text = maskUrl(currentFullUrl)
                }
            }
        }

        // Test button
        val testBtn = TextView(this).apply {
            text = "测试"
            textSize = 12f
            setPadding(16, 0, 16, 0)
            setTextColor(Color.parseColor("#1976D2"))
            setOnClickListener {
                val fullUrl = urlText.tag as? String ?: urlText.text.toString()
                testConnection(fullUrl, statusIcon)
            }
        }

        // Delete button
        val deleteBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setPadding(12, 0, 0, 0)
            setTextColor(Color.parseColor("#9E9E9E"))
            setOnClickListener {
                removeAddressItem(itemLayout, url)
            }
        }

        itemLayout.addView(statusIcon)
        itemLayout.addView(urlText)
        itemLayout.addView(testBtn)
        itemLayout.addView(deleteBtn)
        serverAddressList.addView(itemLayout)

        // Auto test on add
        testConnection(url, statusIcon)
    }

    private fun removeAddressItem(itemView: View, url: String) {
        serverAddressList.removeView(itemView)
    }

    private fun testConnection(url: String, statusIcon: TextView) {
        statusIcon.text = "⏳"
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "HEAD"
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()
                runOnUiThread {
                    statusIcon.text = if (code in 200..399) "✅" else "❌"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    statusIcon.text = "❌"
                }
            }
        }.start()
    }

    private fun saveSettings() {
        val urls = mutableListOf<String>()
        for (i in 0 until serverAddressList.childCount) {
            val itemLayout = serverAddressList.getChildAt(i) as LinearLayout
            val urlText = itemLayout.getChildAt(1) as TextView
            // Read full URL from tag (text may be masked)
            val fullUrl = urlText.tag as? String ?: urlText.text.toString()
            urls.add(fullUrl)
        }

        getPrefs(this).edit().apply {
            val ja = JSONArray()
            urls.forEach { ja.put(it) }
            putString(KEY_SERVER_URLS, ja.toString())
            putBoolean(KEY_AUTO_SELECT, autoSelectSwitch.isChecked)
            putBoolean(KEY_CLIPBOARD_SYNC, clipboardSyncSwitch.isChecked)
            putBoolean(KEY_NOTIFICATION, notificationSwitch.isChecked)
            putBoolean(KEY_AUTO_UPDATE, autoUpdateSwitch.isChecked)
            putBoolean(KEY_LANDSCAPE_LOCK, landscapeLockSwitch.isChecked)
            apply()
        }
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
