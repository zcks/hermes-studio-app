package com.hermes.studio

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineNotice: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isOnline = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                if (!isOnline) {
                    isOnline = true
                    offlineNotice.visibility = View.GONE
                    webView.loadUrl(getBaseUrl())
                }
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                if (isOnline) {
                    isOnline = false
                    offlineNotice.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 100
        private const val LAN_URL = "http://192.168.31.98:8648"
        private const val PUBLIC_URL = "http://server.lifang.asia:8648"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize crash logger FIRST
        CrashLogger.init(this)
        CrashLogger.log(this, "MainActivity", "onCreate started")

        setContentView(R.layout.activity_main)

        // Status bar matches Hermes Studio color
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkMode
        window.statusBarColor = if (isDarkMode) android.graphics.Color.parseColor("#1a1a1a") else android.graphics.Color.parseColor("#f7f7f4")

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        offlineNotice = findViewById(R.id.offlineNotice)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        // SwipeRefreshLayout - only refresh when WebView is at top
        swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
        swipeRefresh.isEnabled = false  // Disable by default, enable only when at top

        // Cookie persistence
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Enable third-party cookies
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                CrashLogger.log(this@MainActivity, "WebView", "Page loaded: $url")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(
                    Intent.createChooser(intent, "选择文件"),
                    FILE_CHOOSER_REQUEST_CODE
                )
                return true
            }
        }

        // Enable SwipeRefreshLayout only when WebView is at top
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            swipeRefresh.isEnabled = scrollY == 0
        }

        // Register network callback
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Check initial connectivity
        if (!isNetworkAvailable()) {
            isOnline = false
            offlineNotice.visibility = View.VISIBLE
        }

        // Long-press offline notice to export logs
        offlineNotice.setOnLongClickListener {
            CrashLogger.shareLogs(this)
            true
        }

        webView.loadUrl(getBaseUrl())

        // === Feature 1: Clipboard Sync ===
        ClipboardSync.start(this) { webView }

        // === Feature 2: Notification Check (WorkManager) ===
        scheduleNotificationCheck()

        // === Feature 5: NAS Update Check ===
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
        UpdateChecker.checkOnStart(this, versionCode)

        CrashLogger.log(this, "MainActivity", "onCreate finished")
    }

    private fun scheduleNotificationCheck() {
        val workRequest = PeriodicWorkRequestBuilder<MessageCheckWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "hermes_message_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getBaseUrl(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return PUBLIC_URL
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return PUBLIC_URL
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            LAN_URL
        } else {
            PUBLIC_URL
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val result = if (resultCode == Activity.RESULT_OK && data != null) {
                data.data?.let { uri ->
                    arrayOf(uri)
                }
            } else null
            fileUploadCallback?.onReceiveValue(result)
            fileUploadCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        ClipboardSync.stop()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
        webView.destroy()
        super.onDestroy()
    }
}
