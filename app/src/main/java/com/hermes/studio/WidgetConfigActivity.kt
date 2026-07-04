package com.hermes.studio

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.json.JSONArray

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var serverAddressList: LinearLayout
    private var selectedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        serverAddressList = findViewById(R.id.serverAddressList)

        // Set up widget ID
        setResult(RESULT_CANCELED)
        val intent = intent
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Load server addresses from shared preferences
        loadServerAddresses()

        // Save button
        findViewById<MaterialButton>(R.id.saveConfigBtn).setOnClickListener {
            if (selectedUrl != null) {
                saveConfig()
            } else {
                Toast.makeText(this, "请选择一个服务器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadServerAddresses() {
        val urls = SettingsActivity.getServerUrls(this)
        serverAddressList.removeAllViews()

        if (urls.isEmpty()) {
            val noAddressText = TextView(this).apply {
                text = "没有配置服务器地址，请先在设置中添加"
                textSize = 14f
                setTextColor(Color.parseColor("#9E9E9E"))
                setPadding(16, 32, 16, 32)
            }
            serverAddressList.addView(noAddressText)
            return
        }

        urls.forEach { url ->
            addAddressItem(url)
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

        // Selection indicator
        val selectIcon = TextView(this).apply {
            text = "○"
            textSize = 18f
            setPadding(0, 0, 12, 0)
            setTextColor(Color.parseColor("#9E9E9E"))
        }

        // URL text
        val urlText = TextView(this).apply {
            text = SettingsActivity.maskUrl(url)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.parseColor("#333333"))
            maxLines = 1
        }

        itemLayout.addView(selectIcon)
        itemLayout.addView(urlText)

        itemLayout.setOnClickListener {
            // Update selection
            selectedUrl = url
            // Update all items
            for (i in 0 until serverAddressList.childCount) {
                val child = serverAddressList.getChildAt(i) as LinearLayout
                val icon = child.getChildAt(0) as TextView
                icon.text = "○"
                icon.setTextColor(Color.parseColor("#9E9E9E"))
            }
            selectIcon.text = "●"
            selectIcon.setTextColor(Color.parseColor("#4CAF50"))
        }

        serverAddressList.addView(itemLayout)
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("widget_cache", MODE_PRIVATE)
        prefs.edit().putString("widget_server_url_$appWidgetId", selectedUrl).apply()

        // Update widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        ServerMonitorWidget.updateAppWidget(this, appWidgetManager, appWidgetId)

        // Trigger immediate update
        val intent = Intent(this, ServerMonitorWidget::class.java).apply {
            action = ServerMonitorWidget.ACTION_REFRESH
            putExtra(ServerMonitorWidget.EXTRA_WIDGET_ID, appWidgetId)
        }
        sendBroadcast(intent)

        // Return success
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
