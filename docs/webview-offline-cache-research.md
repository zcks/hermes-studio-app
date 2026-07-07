# WebView Offline Cache Strategy Research

**Target**: Hermes Studio Android App (Kotlin, minSdk 24, targetSdk 34)
**Current State**: `cacheMode = WebSettings.LOAD_DEFAULT` — shows white screen when server unreachable
**Goal**: Show last-loaded content when offline, refresh when online

---

## 1. Cache Mode Reference (`WebSettings.setCacheMode`)

### All Cache Modes

| Mode | Behavior |
|------|----------|
| `LOAD_DEFAULT` | Uses HTTP cache headers. If no network, fails. **(current mode)** |
| `LOAD_CACHE_ELSE_NETWORK` | Load from cache even if expired. Only hits network if cache is completely empty. **BEST FIT for offline-first** |
| `LOAD_NO_CACHE` | Always fetch from network. No cache. |
| `LOAD_CACHE_ONLY` | Never hit network. Only from cache. |

### `LOAD_CACHE_ELSE_NETWORK` — The Right Choice

**How it works:**
1. **Has cache?** → Use it immediately, even if stale/expired. No network request.
2. **No cache?** → Falls back to network request.
3. **Network fails AND no cache?** → `onReceivedError` fires.

**Why this is the correct mode for offline-first:**
- The user sees the last cached page instantly on launch (no white screen)
- When online, the page is served from cache (fast load)
- When the network comes back, you can force a fresh load in the background

**⚠️ Critical caveat:** `LOAD_CACHE_ELSE_NETWORK` ignores `Cache-Control` headers entirely. If your server sends `no-store` or `no-cache` headers, this mode will still use the cached version. This is actually desirable for offline-first, but be aware.

---

## 2. Recommended Implementation

### 2a. Smart Cache Mode Switching

The best strategy is **NOT** to use a single cache mode forever, but to switch modes based on connectivity:

```kotlin
/**
 * Configure cache mode based on current network state.
 * - Offline: LOAD_CACHE_ELSE_NETWORK (show last cached content)
 * - Online:  LOAD_DEFAULT (respect HTTP cache headers for freshness)
 *
 * This gives us:
 * - Fast offline launch with last-viewed content
 * - Proper cache freshness when online
 * - No white screens ever
 */
private fun updateCacheMode() {
    webView.settings.cacheMode = if (isNetworkAvailable()) {
        WebSettings.LOAD_DEFAULT
    } else {
        WebSettings.LOAD_CACHE_ELSE_NETWORK
    }
}
```

### 2b. Complete WebView Setup for Offline-First

```kotlin
private fun setupWebView() {
    webView.settings.apply {
        // --- Core features ---
        javaScriptEnabled = true
        domStorageEnabled = true          // Required for localStorage/sessionStorage
        databaseEnabled = true            // Required for Web SQL

        // --- Viewport ---
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false

        // --- Security ---
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // --- Cache strategy ---
        // Start with LOAD_CACHE_ELSE_NETWORK so the first load never shows white screen
        // Switch to LOAD_DEFAULT after successful load for proper cache freshness
        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        // --- App cache (deprecated but still useful for Web Workers) ---
        // Note: AppCache is deprecated in API 30+, but enabling it doesn't hurt
        // and some frameworks still reference it
        @Suppress("DEPRECATION")
        setAppCacheEnabled(true)
        @Suppress("DEPRECATION")
        setAppCachePath(cacheDir.resolve("webview_cache").absolutePath)
        @Suppress("DEPRECATION")
        setAppCacheMaxSize(10L * 1024 * 1024) // 10MB
    }

    // Enable cookies (required for session persistence)
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
}
```

### 2c. Smart Loading Strategy (Network-Aware)

```kotlin
/**
 * Load URL with smart strategy:
 * 1. If online: load directly with LOAD_DEFAULT
 * 2. If offline: switch to LOAD_CACHE_ELSE_NETWORK first, then load
 *    This ensures the cached version appears instantly
 */
private fun smartLoad(url: String) {
    if (isNetworkAvailable()) {
        // Online: use default cache (respects HTTP headers)
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.loadUrl(url)
    } else {
        // Offline: use cache fallback
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.loadUrl(url)
        // Show offline notice but DON'T hide the webview
        // The cached content should still display
        offlineNotice.visibility = View.GONE  // Let cached content show
    }
}
```

### 2d. Network Callback Integration

```kotlin
private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        runOnUiThread {
            // Network is back — refresh content
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

            // Clear the offline banner
            offlineNotice.visibility = View.GONE
            updateConnectionStatus("connecting")

            // Reload to get fresh content
            webView.reload()
        }
    }

    override fun onLost(network: Network) {
        runOnUiThread {
            // Network lost — switch to cache mode so next load works
            webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

            isServerReachable = false
            // DON'T show offline notice if we have cached content
            // The WebView should still be displaying the cached page
            progressBar.visibility = View.GONE
            updateConnectionStatus("offline_cached")
        }
    }
}
```

### 2e. Enhanced Error Handling (Don't Show White Screen)

```kotlin
webView.webViewClient = object : WebViewClient() {

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)

        // Only handle main frame errors (not subresource failures)
        if (request?.isForMainFrame != true) return

        val errorCode = error?.errorCode ?: return
        val failingUrl = request.url?.toString() ?: return

        // For ERR_INTERNET_DISCONNECTED (=-2) and ERR_CONNECTION_REFUSED (=-111)
        // the cache mode should have handled this. If we still get here,
        // it means there's truly no cache available.
        when (errorCode) {
            ERROR_CONNECT, ERROR_TIMEOUT, ERROR_HOST_LOOKUP, ERROR_IO -> {
                // These are retryable — don't show error page
                // If cache showed content, we're fine
                // If not, we need a fallback
                showOfflineFallback(failingUrl)
            }
            else -> {
                showOfflineFallback(failingUrl)
            }
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: android.webkit.WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame != true) return

        val statusCode = errorResponse?.statusCode ?: return
        if (statusCode == 502 || statusCode == 503 || statusCode == 504) {
            // Server error — try cache fallback
            webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            webView.reload()
        }
    }
}

/**
 * Show a native offline fallback page when WebView cache has nothing.
 * This prevents the white screen even on first launch without network.
 */
private fun showOfflineFallback(url: String) {
    val offlineHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body {
                    font-family: -apple-system, sans-serif;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    margin: 0;
                    background: #1a1a2e;
                    color: #e0e0e0;
                    text-align: center;
                }
                .container { padding: 2rem; }
                h1 { font-size: 3rem; margin-bottom: 0.5rem; }
                p { font-size: 1.2rem; opacity: 0.8; }
                .retry-btn {
                    margin-top: 1.5rem;
                    padding: 0.8rem 2rem;
                    background: #4CAF50;
                    color: white;
                    border: none;
                    border-radius: 8px;
                    font-size: 1rem;
                    cursor: pointer;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>📡</h1>
                <h2>离线模式</h2>
                <p>无法连接到服务器</p>
                <p>请检查网络连接后重试</p>
                <button class="retry-btn" onclick="window.location.reload()">重试</button>
            </div>
        </body>
        </html>
    """.trimIndent()
    webView.loadDataWithBaseURL(url, offlineHtml, "text/html", "UTF-8", null)
}
```

---

## 3. Common Pitfalls

### Pitfall 1: `LOAD_CACHE_ELSE_NETWORK` is too aggressive
**Problem**: If you set this permanently, you never get fresh content when online.
**Solution**: Switch to `LOAD_DEFAULT` when network is available. Use the smart switching pattern above.

### Pitfall 2: Cache never populates
**Problem**: If the server returns `Cache-Control: no-store, no-cache` or `Pragma: no-cache`, the HTTP cache may never store the response.
**Solution**:
```kotlin
// Option A: Override headers via WebViewClient (API 21+)
override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
): WebResourceResponse? {
    // Let the WebView handle normal requests
    return null
}
```
Or better — configure the server to send proper cache headers for the main page:
```
Cache-Control: max-age=300, stale-while-revalidate=3600
```

### Pitfall 3: DOM storage not enabled
**Problem**: Many modern web apps (React, Vue) use `localStorage` for state. Without DOM storage, the app may not render correctly from cache.
**Solution**: Always enable both:
```kotlin
domStorageEnabled = true
databaseEnabled = true
```

### Pitfall 4: WebView doesn't cache XHR/fetch responses
**Problem**: Standard WebView caching only works for navigation requests (page loads). AJAX/fetch calls for data are NOT cached by the WebView's HTTP cache.
**Solution**: This is why Hermes Studio uses WebSocket — but WebSocket connections are also not cached. The solution is **Service Worker caching** (see section 5 below) or **app-level data persistence**.

### Pitfall 5: `onReceivedError` fires even when cache works
**Problem**: Subresource loading errors (fonts, images from CDN) fire `onReceivedError` even when the main page loaded from cache fine.
**Solution**: Always check `request?.isForMainFrame == true` before showing error UI.

### Pitfall 6: App restart clears WebView state
**Problem**: When the app process is killed and restarted, the in-memory WebView state is gone. The HTTP cache persists, but DOM state doesn't.
**Solution**: Use `WebView.saveState(outState)` / `WebView.restoreState(savedInstanceState)`:
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    webView.saveState(outState)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... setup code ...
    if (savedInstanceState != null) {
        webView.restoreState(savedInstanceState)
    } else {
        webView.loadUrl(getBaseUrl())
    }
}
```

### Pitfall 7: `LOAD_CACHE_ELSE_NETWORK` + WebSocket = broken reconnection
**Problem**: When loading from cache, WebSocket connections inside the page may attempt to reconnect to a server that's unreachable, causing the page to show a "disconnected" state.
**Solution**: See section 4 below.

### Pitfall 8: Cache size limits
**Problem**: WebView HTTP cache has no explicit size limit configuration (unlike desktop browsers). On low-storage devices, old cached content may be evicted.
**Solution**: Use `LOAD_CACHE_ELSE_NETWORK` which falls back to network if cache is empty. Also consider explicit cache management via `WebStorage.deleteAllData()` only when you intentionally want to clear.

### Pitfall 9: Multiple `loadUrl` calls race
**Problem**: Calling `loadUrl` rapidly (e.g., on network switch) can cause race conditions where the wrong URL loads.
**Solution**: Debounce network-change callbacks (the app already does this with 2s debounce — good).

### Pitfall 10: `LOAD_CACHE_ELSE_NETWORK` ignores `no-store`
**Problem**: You might want to respect `no-store` for sensitive content.
**Solution**: Use `LOAD_DEFAULT` for sensitive pages; use `LOAD_CACHE_ELSE_NETWORK` only for public content like the Hermes Studio UI.

---

## 4. WebSocket Reconnection After Cache

### The Problem
When the WebView loads from cache, the JavaScript inside runs immediately and tries to establish WebSocket connections. If the server is offline, these connections fail. The web app (Hermes Studio) likely shows a "disconnected" indicator.

### The Solution: Hybrid Approach

**Strategy**: Let the WebView cache handle the page display. Use the native Android side to detect connectivity and tell the web app when to attempt reconnection.

#### 4a. Pass connectivity state to JavaScript

```kotlin
/**
 * Inject a global flag the web app can read to know if it should
 * attempt WebSocket connections or stay in offline mode.
 */
private fun updateJsConnectivityState() {
    val isConnected = isNetworkAvailable() && isServerReachable
    val script = """
        window.__ANDROID_ONLINE__ = $isConnected;
        // Dispatch a custom event so the web app can listen
        window.dispatchEvent(new CustomEvent('android-connectivity', {
            detail: { online: $isConnected }
        }));
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}
```

#### 4b. Listen for the web app's reconnection attempt

```kotlin
// In WebViewClient or via JavaScriptInterface:
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun onWebSocketDisconnected() {
        // Web app lost its WebSocket connection
        // Don't immediately reload — the page content is still valid
        runOnUiThread {
            // Show a subtle "reconnecting" indicator (not an error)
            updateConnectionStatus("reconnecting")
        }
    }

    @JavascriptInterface
    fun onWebSocketConnected() {
        runOnUiThread {
            isServerReachable = true
            updateConnectionStatus("connected")
        }
    }
}, "AndroidBridge")
```

#### 4c. Web-side: Reconnection helper (inject via `evaluateJavascript` or include in the web app)

```javascript
// This should be part of the Hermes Studio web app, or injected by the Android app
(function() {
    var wsReconnectDelay = 1000;
    var maxDelay = 30000;

    function attemptReconnect() {
        if (!window.__ANDROID_ONLINE__) {
            // Don't attempt — we know we're offline
            console.log('[OfflineCache] Skipping reconnect — offline');
            return;
        }
        // Normal reconnection logic here
        if (window.AndroidBridge) {
            window.AndroidBridge.onWebSocketDisconnected();
        }
    }

    // Override or hook into the existing WebSocket connection logic
    // The key insight: when __ANDROID_ONLINE__ is false, the web app
    // should display cached content WITHOUT trying to reconnect

    window.addEventListener('android-connectivity', function(e) {
        if (e.detail.online) {
            console.log('[OfflineCache] Online — triggering reconnect');
            attemptReconnect();
        } else {
            console.log('[OfflineCache] Offline — pausing reconnection');
        }
    });
})();
```

#### 4d. Detect WebSocket state from Android side

```kotlin
/**
 * Check if the web app has an active WebSocket connection.
 * Useful for knowing if the page is truly functional or just
 * showing cached HTML with a dead WebSocket.
 */
private fun checkWebSocketState() {
    webView.evaluateJavascript("""
        (function() {
            // Check for common WebSocket disconnection indicators
            var disconnected = document.querySelector('.disconnected, .ws-error, [data-status="offline"]');
            var connected = document.querySelector('.connected, .ws-connected, [data-status="online"]');
            if (disconnected) return 'disconnected';
            if (connected) return 'connected';
            return 'unknown';
        })()
    """) { result ->
        when {
            result?.contains("disconnected") == true -> {
                // Page loaded from cache but WebSocket is dead
                // Try reload if we're actually online
                if (isNetworkAvailable()) {
                    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webView.reload()
                }
            }
            result?.contains("connected") == true -> {
                isServerReachable = true
                updateConnectionStatus("connected")
            }
        }
    }
}
```

---

## 5. Advanced: Service Worker for Full Offline Support

If you control the web app (Hermes Studio Web UI), adding a Service Worker provides the most robust offline experience:

### 5a. Service Worker Registration (web-side)

```javascript
// sw.js — register from the main page
if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').then(function(reg) {
        console.log('SW registered:', reg.scope);
    });
}

// sw.js — the actual service worker
const CACHE_NAME = 'hermes-studio-v1';
const PRECACHE_URLS = [
    '/',
    '/index.html',
    '/static/js/main.js',
    '/static/css/main.css'
];

self.addEventListener('install', function(event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function(cache) {
            return cache.addAll(PRECACHE_URLS);
        })
    );
});

self.addEventListener('fetch', function(event) {
    event.respondWith(
        caches.match(event.request).then(function(cached) {
            if (cached) {
                // Return cached version, but also fetch update in background
                var fetchPromise = fetch(event.request).then(function(response) {
                    if (response.ok) {
                        var clone = response.clone();
                        caches.open(CACHE_NAME).then(function(cache) {
                            cache.put(event.request, clone);
                        });
                    }
                    return response;
                }).catch(function() {
                    // Network failed, cached version is already returned
                });
                return cached;
            }
            // No cache — fetch from network
            return fetch(event.request);
        })
    );
});
```

### 5b. Enable Service Workers in WebView

```kotlin
// Service Workers are enabled by default in modern WebView,
// but ensure DOM storage is enabled (required):
webView.settings.domStorageEnabled = true

// For debugging Service Workers:
if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```

### 5c. Service Worker + Cache Mode Synergy

When a Service Worker is present:
1. **WebView cache** handles the initial page load (HTML, CSS, JS)
2. **Service Worker cache** handles API calls, XHR, fetch requests
3. **`LOAD_CACHE_ELSE_NETWORK`** ensures the main page loads from cache
4. The Service Worker serves cached API responses even when offline

This is the **gold standard** for offline-first WebView apps.

---

## 6. Recommended Changes to Existing Code

### Changes to `MainActivity.kt`:

1. **Line 177**: Change `LOAD_DEFAULT` → Smart switching (see 2c above)
2. **Add `onSaveInstanceState`/`onRestoreInstanceState`** for WebView state persistence
3. **Enhance `onReceivedError`** to not show white screen when cache is available
4. **Add `showOfflineFallback`** for first-launch-without-network case
5. **Update `onLost` callback** to switch to cache mode instead of just showing offline notice
6. **Add `databaseEnabled = true`** to WebView settings

### Server-side recommendations:

Configure the Hermes Studio web server to send:
```
Cache-Control: public, max-age=0, stale-while-revalidate=86400
```
This tells the WebView to always revalidate (so online users get fresh content) but allows serving stale content while revalidating (so offline users get the last version).

---

## 7. Test Scenarios

| Scenario | Expected Behavior |
|----------|-------------------|
| First launch, no network | Native offline fallback page (not white screen) |
| First launch, has network | Page loads, gets cached. Works offline next time |
| Has cache, go offline | Cached page shows instantly, offline notice hidden |
| Has cache, go online | `LOAD_DEFAULT` + `reload()` fetches fresh content |
| App killed, relaunched offline | `restoreState()` restores WebView; `LOAD_CACHE_ELSE_NETWORK` shows cached page |
| App killed, relaunched online | Fresh load from network |
| Server returns 502/503 | Switch to `LOAD_CACHE_ELSE_NETWORK`, reload |
| WebSocket disconnects while offline | No crash; "reconnecting" status shown when back online |

---

## 8. Summary of Key Code Changes

```kotlin
// MINIMUM VIABLE CHANGES to fix the white screen issue:

// 1. In setupWebView(), change line 177:
// FROM: cacheMode = WebSettings.LOAD_DEFAULT
// TO:   cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

// 2. Add after line 178:
databaseEnabled = true

// 3. In networkCallback.onAvailable(), add before webView.reload():
webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

// 4. In networkCallback.onLost(), add:
webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

// 5. In onReceivedError, add main-frame check:
if (request?.isForMainFrame != true) return

// 6. Add onSaveInstanceState/onRestoreInstanceState for process death
```

These 6 changes will fix the white screen problem with minimal code modification.
