package com.hermes.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineNotice: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var statusIndicator: View
    private lateinit var connectionStatusText: TextView
    private lateinit var navConnectionStatus: TextView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isServerReachable = false

    // Network type tracking for WiFi↔mobile switch detection
    private var currentNetworkType: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private var networkSwitchRunnable: Runnable? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                offlineNotice.visibility = View.GONE
                updateConnectionStatus("connecting")
                checkAllServersAndLoad()
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                isServerReachable = false
                offlineNotice.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                updateConnectionStatus("error")
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val newType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                else -> "other"
            }

            if (currentNetworkType.isEmpty()) {
                // First time, just record the type
                currentNetworkType = newType
                return
            }

            if (newType != currentNetworkType) {
                // Network type changed (WiFi↔mobile), debounce 2s
                networkSwitchRunnable?.let { mainHandler.removeCallbacks(it) }
                networkSwitchRunnable = Runnable {
                    currentNetworkType = newType
                    updateConnectionStatus("connecting")
                    checkAllServersAndLoad()
                }
                mainHandler.postDelayed(networkSwitchRunnable!!, 2000)
            }
        }
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(this)
        CrashLogger.log(this, "MainActivity", "onCreate started")

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        offlineNotice = findViewById(R.id.offlineNotice)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        drawerLayout = findViewById(R.id.drawerLayout)
        statusIndicator = findViewById(R.id.statusIndicator)
        connectionStatusText = findViewById(R.id.connectionStatusText)

        // Toolbar setup
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Navigation Drawer item clicks
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val navHeader = navView.getHeaderView(0)
        navConnectionStatus = navHeader.findViewById(R.id.connectionStatus)
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.nav_refresh -> {
                    webView.reload()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // SwipeRefreshLayout
        swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
        swipeRefresh.isEnabled = false

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

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = false
                updateConnectionStatus("connecting")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                isServerReachable = true
                updateConnectionStatus("connected")
                CrashLogger.log(this@MainActivity, "WebView", "Page loaded: $url")
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                isServerReachable = false
                updateConnectionStatus("error")
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

        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            swipeRefresh.isEnabled = scrollY == 0
        }

        // Initialize current network type before registering callback
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null) {
                currentNetworkType = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                    else -> "other"
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)

        if (!isNetworkAvailable()) {
            isServerReachable = false
            offlineNotice.visibility = View.VISIBLE
        }

        offlineNotice.setOnLongClickListener {
            CrashLogger.shareLogs(this)
            true
        }

        webView.loadUrl(getBaseUrl())

        if (SettingsActivity.isClipboardSyncEnabled(this)) {
            ClipboardSync.start(this) { webView }
        }

        scheduleNotificationCheck()

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
        if (SettingsActivity.isAutoUpdateEnabled(this)) {
            UpdateChecker.checkOnStart(this, versionCode)
        }

        CrashLogger.log(this, "MainActivity", "onCreate finished")
    }

    /**
     * Check all server addresses and load the first reachable one.
     * Runs on background thread to avoid blocking UI.
     */
    private fun checkAllServersAndLoad() {
        Thread {
            val urls = SettingsActivity.getServerUrls(this@MainActivity)
            if (urls.isEmpty()) {
                runOnUiThread {
                    isServerReachable = false
                    updateConnectionStatus("error")
                }
                return@Thread
            }

            var foundUrl: String? = null
            if (SettingsActivity.isAutoSelectEnabled(this@MainActivity)) {
                for (url in urls) {
                    if (testUrlReachable(url)) {
                        foundUrl = url
                        break
                    }
                }
            } else {
                // Not auto-select: just test the first URL
                if (testUrlReachable(urls[0])) {
                    foundUrl = urls[0]
                }
            }

            runOnUiThread {
                if (foundUrl != null) {
                    isServerReachable = true
                    val currentUrl = webView.url ?: ""
                    if (currentUrl != foundUrl) {
                        webView.loadUrl(foundUrl)
                    } else {
                        // Already on the right URL, just update status
                        updateConnectionStatus("connected")
                    }
                } else {
                    isServerReachable = false
                    offlineNotice.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    updateConnectionStatus("error")
                }
            }
        }.start()
    }

    private fun updateConnectionStatus(status: String = when {
        isServerReachable -> "connected"
        else -> "offline"
    }) {
        val indicator = statusIndicator.background as? GradientDrawable
        val color: Int
        val text: String

        // 获取当前连接的服务器地址，判断是局域网还是公网
        val networkType = if (status == "connected") {
            val currentUrl = webView.url ?: ""
            if (isLocalAddress(currentUrl)) "(局域网)" else "(公网)"
        } else ""

        when (status) {
            "connected" -> {
                color = android.graphics.Color.parseColor("#4CAF50")
                text = "🟢 在线$networkType"
            }
            "connecting" -> {
                color = android.graphics.Color.parseColor("#FFC107")
                text = "🟡 正在连接"
            }
            "error" -> {
                color = android.graphics.Color.parseColor("#F44336")
                text = "🔴 离线"
            }
            else -> {
                color = android.graphics.Color.parseColor("#F44336")
                text = "🔴 离线"
            }
        }

        indicator?.setColor(color)
        connectionStatusText.text = text
        connectionStatusText.setTextColor(color)

        // Update nav header status
        when (status) {
            "connected" -> {
                navConnectionStatus.text = "🟢 在线$networkType"
                navConnectionStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            }
            "connecting" -> {
                navConnectionStatus.text = "🟡 正在连接"
                navConnectionStatus.setTextColor(android.graphics.Color.parseColor("#FFC107"))
            }
            else -> {
                navConnectionStatus.text = "🔴 离线"
                navConnectionStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
            }
        }
    }

    /**
     * 判断URL是否是局域网地址
     * 支持IPv4私有地址段和IPv6链路本地地址
     */
    private fun isLocalAddress(url: String): Boolean {
        try {
            val host = java.net.URI(url).host ?: return false

            // IPv4 私有地址段
            if (host.startsWith("192.168.")) return true
            if (host.startsWith("10.")) return true
            if (host.startsWith("172.")) {
                val secondOctet = host.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                if (secondOctet in 16..31) return true
            }
            if (host.startsWith("127.")) return true  // localhost

            // IPv6 链路本地地址
            if (host.startsWith("fe80:")) return true
            if (host.startsWith("fc") || host.startsWith("fd")) return true  // ULA

            return false
        } catch (_: Exception) {
            return false
        }
    }

    private fun scheduleNotificationCheck() {
        if (!SettingsActivity.isNotificationEnabled(this)) return

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
        val urls = SettingsActivity.getServerUrls(this)
        if (urls.isEmpty()) return "about:blank"

        // If auto-select is enabled, try each URL and return the first reachable one
        if (SettingsActivity.isAutoSelectEnabled(this)) {
            for (url in urls) {
                if (testUrlReachable(url)) {
                    return url
                }
            }
            // If none reachable, return first URL
            return urls[0]
        }

        // Otherwise return the first URL
        return urls[0]
    }

    private fun testUrlReachable(url: String): Boolean {
        return try {
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
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawers()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        updateConnectionStatus()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        ClipboardSync.stop()
        networkSwitchRunnable?.let { mainHandler.removeCallbacks(it) }
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
        webView.destroy()
        super.onDestroy()
    }
}
