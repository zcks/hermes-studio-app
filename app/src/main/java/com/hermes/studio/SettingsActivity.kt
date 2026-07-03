package com.hermes.studio

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "hermes_settings"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_STATUS_BAR = "status_bar_follow_theme"
        const val KEY_CLIPBOARD_SYNC = "clipboard_sync"
        const val KEY_NOTIFICATION = "notification_push"
        const val KEY_AUTO_UPDATE = "auto_update_check"
        const val KEY_LANDSCAPE_LOCK = "landscape_lock"
        const val DEFAULT_SERVER_URL = "http://192.168.31.98:8648"

        fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        fun getServerUrl(context: Context): String {
            return getPrefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
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

        fun isDarkModeEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_DARK_MODE, false)
        }

        fun isStatusBarFollowTheme(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_STATUS_BAR, true)
        }

        fun isNotificationEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_NOTIFICATION, true)
        }
    }

    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var statusBarSwitch: SwitchMaterial
    private lateinit var clipboardSyncSwitch: SwitchMaterial
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var autoUpdateSwitch: SwitchMaterial
    private lateinit var landscapeLockSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        serverUrlInput = findViewById(R.id.serverUrl)
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        statusBarSwitch = findViewById(R.id.statusBarSwitch)
        clipboardSyncSwitch = findViewById(R.id.clipboardSyncSwitch)
        notificationSwitch = findViewById(R.id.notificationSwitch)
        autoUpdateSwitch = findViewById(R.id.autoUpdateSwitch)
        landscapeLockSwitch = findViewById(R.id.landscapeLockSwitch)

        loadSettings()

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
        serverUrlInput.setText(prefs.getString(KEY_SERVER_URL, ""))
        darkModeSwitch.isChecked = prefs.getBoolean(KEY_DARK_MODE, false)
        statusBarSwitch.isChecked = prefs.getBoolean(KEY_STATUS_BAR, true)
        clipboardSyncSwitch.isChecked = prefs.getBoolean(KEY_CLIPBOARD_SYNC, true)
        notificationSwitch.isChecked = prefs.getBoolean(KEY_NOTIFICATION, true)
        autoUpdateSwitch.isChecked = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        landscapeLockSwitch.isChecked = prefs.getBoolean(KEY_LANDSCAPE_LOCK, false)
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    private fun saveSettings() {
        val url = serverUrlInput.text?.toString()?.trim() ?: ""
        getPrefs(this).edit().apply {
            putString(KEY_SERVER_URL, url)
            putBoolean(KEY_DARK_MODE, darkModeSwitch.isChecked)
            putBoolean(KEY_STATUS_BAR, statusBarSwitch.isChecked)
            putBoolean(KEY_CLIPBOARD_SYNC, clipboardSyncSwitch.isChecked)
            putBoolean(KEY_NOTIFICATION, notificationSwitch.isChecked)
            putBoolean(KEY_AUTO_UPDATE, autoUpdateSwitch.isChecked)
            putBoolean(KEY_LANDSCAPE_LOCK, landscapeLockSwitch.isChecked)
            apply()
        }
    }
}
