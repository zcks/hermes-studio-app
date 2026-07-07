# Android WebView Voice Input Integration — Research Summary

## 1. Can Android WebView Support Web Speech API (SpeechRecognition)?

**Short answer: No, not reliably.**

- The Web Speech API (`SpeechRecognition` / `webkitSpeechRecognition`) is implemented by Chrome's browser engine, but **Android WebView uses a different, stripped-down Chromium engine** that does NOT include the speech recognition component.
- In Android WebView, `window.SpeechRecognition` and `window.webkitSpeechRecognition` are both `undefined`.
- Even if you set `webView.settings.javaScriptEnabled = true`, the SpeechRecognition API is simply not available in WebView's Chromium build.
- Google Chrome on Android (the standalone browser) has it, but WebView does not.

**Why it fails:** WebView's Chromium is compiled without the speech recognition service. The speech-to-text backend requires Google Play Services' voice recognition, which isn't exposed through WebView's JS engine.

---

## 2. Approaches to Add Voice Input to an Android WebView App

### Approach A: Native SpeechRecognizer + JavaScript Bridge (RECOMMENDED)

**How it works:**
1. Android uses its built-in `android.speech.SpeechRecognizer` to capture and transcribe audio
2. Results are bridged to JavaScript via `@JavascriptInterface`
3. JavaScript calls a native method to start/stop recognition, Android calls back JS with results

**Pros:**
- Works offline (on-device recognition, no network needed)
- Uses Android's built-in `SpeechRecognizer` (available on all devices with Google Play Services)
- No Google API key needed — it uses the on-device recognizer by default
- Works in China (on-device recognition doesn't need external network)
- Full control over the recognition lifecycle

**Cons:**
- `SpeechRecognizer` requires Google Play Services (most Chinese phones have this, or use HMS)
- Language support depends on installed speech models
- UI feedback must be handled manually (Android shows a system dialog for some recognizers)

### Approach B: Audio Recording + Server-Side STT

**How it works:**
1. JavaScript uses `navigator.mediaDevices.getUserMedia()` to capture audio
2. Audio is recorded via `MediaRecorder` API (works in WebView!)
3. Audio blob is sent to a server endpoint for transcription
4. Server returns the transcribed text

**Pros:**
- Works in WebView without native code
- Can use any STT service (Whisper, Baidu, iFlytek, etc.)
- Works in China with Chinese STT services
- No Google dependency at all

**Cons:**
- Requires network connection
- Requires a running server with STT capability
- Adds latency and cost
- The existing Hermes web UI already uses this approach (`/api/hermes/stt/transcribe`)

### Approach C: Web Speech API Polyfill via WebView Message Bridge

**How it works:**
1. Inject JavaScript that detects `SpeechRecognition` is missing
2. Replace it with a polyfill that calls native Android methods
3. Android handles the actual speech recognition

**Pros:**
- Web code doesn't need to change (transparent to the web app)
- Web Speech API interface is preserved

**Cons:**
- Complex to implement correctly
- Must handle all SpeechRecognition events/callbacks
- Race conditions between JS and native callbacks

### Approach D: Android Intent-Based Recognition

**How it works:**
1. JavaScript calls native to launch `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`
2. Android shows the system speech recognition dialog
3. Results are returned via `onActivityResult` -> bridge to JS

**Pros:**
- Simplest implementation
- Uses system UI (no custom recording UI needed)

**Cons:**
- Disruptive UX (opens a new activity/dialog)
- No streaming/partial results
- User must tap the system "Done" button
- Not suitable for continuous voice input

---

## 3. Recommended Architecture: Approach A (Native Bridge)

### How the Bridge Works

```
┌─────────────────────────────────────────┐
│  WebView (JavaScript)                   │
│                                         │
│  1. User clicks mic button              │
│  2. JS calls: AndroidVoiceBridge        │
│     .startRecognition("zh-CN")          │
│  3. Android receives the call           │
│                                         │
│  ← Results come back via:              │
│     webView.evaluateJavascript(         │
│       "window.onVoiceResult('text')"    │
│     )                                  │
│                                         │
│  4. JS receives "text" and inserts it   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│  Android (Kotlin)                       │
│                                         │
│  1. @JavascriptInterface method         │
│     receives startRecognition call      │
│  2. Creates SpeechRecognizer instance   │
│  3. Sets up RecognitionListener         │
│  4. Calls recognizer.startListening()   │
│  5. On results:                         │
│     - Partial → webView.evaluateJS(     │
│         "onVoicePartial('partial')")    │
│     - Final → webView.evaluateJS(       │
│         "onVoiceResult('final')")       │
│  6. On error:                           │
│     webView.evaluateJS(                 │
│       "onVoiceError('error msg')")      │
└─────────────────────────────────────────┘
```

### Key Android APIs

```kotlin
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
```

**`SpeechRecognizer`** — The core API for on-device speech recognition.

**`RecognitionListener`** — Callback interface for recognition events:
- `onReadyForSpeech()` — Ready to start listening
- `onBeginningOfSpeech()` — User started speaking
- `onRmsChanged()` — Audio level changes (for VU meter)
- `onPartialResults(Bundle)` — Interim transcription results
- `onResults(Bundle)` — Final transcription results
- `onError(int)` — Error occurred (e.g., no speech, network timeout)
- `onEndOfSpeech()` — User stopped speaking

### Permission Requirements

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

This is a **dangerous permission** that requires runtime request on Android 6.0+ (API 23+).

---

## 4. Complete Example Code

### Kotlin: `VoiceBridge.kt`

```kotlin
package com.hermes.studio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Bridge between Android's native SpeechRecognizer and WebView JavaScript.
 * 
 * Usage:
 * 1. Add to WebView: webView.addJavascriptInterface(VoiceBridge(this, webView), "AndroidVoiceBridge")
 * 2. In JS: window.AndroidVoiceBridge.startRecognition("zh-CN")
 * 3. JS listens for window.onVoiceResult(), window.onVoicePartial(), window.onVoiceError()
 */
class VoiceBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
    }

    /**
     * Check if speech recognition is available on this device.
     */
    @JavascriptInterface
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(activity)
    }

    /**
     * Start speech recognition.
     * @param language Language code, e.g. "zh-CN" for Chinese, "en-US" for English
     */
    @JavascriptInterface
    fun startRecognition(language: String = "zh-CN") {
        // Must run on main thread
        activity.runOnUiThread {
            // Check permission first
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQUEST_CODE
                )
                callJs("onVoiceError", "'Permission denied. Please grant microphone permission.'")
                return@runOnUiThread
            }

            if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
                callJs("onVoiceError", "'Speech recognition not available on this device.'")
                return@runOnUiThread
            }

            if (isListening) {
                stopRecognition()
                return@runOnUiThread
            }

            startListening(language)
        }
    }

    /**
     * Stop speech recognition.
     */
    @JavascriptInterface
    fun stopRecognition() {
        activity.runOnUiThread {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            callJs("onVoiceStopped", "")
        }
    }

    /**
     * Cancel speech recognition (no results).
     */
    @JavascriptInterface
    fun cancelRecognition() {
        activity.runOnUiThread {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            callJs("onVoiceCancelled", "")
        }
    }

    private fun startListening(language: String) {
        // Destroy previous instance
        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)

        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                callJs("onVoiceReady", "")
            }

            override fun onBeginningOfSpeech() {
                callJs("onVoiceSpeaking", "")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Pass RMS level for VU meter (0.0 to ~10.0)
                callJs("onVoiceLevel", rmsdB.toString())
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                callJs("onVoiceEnd", "")
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    else -> "Unknown error ($error)"
                }
                callJs("onVoiceError", "'$errorMsg'")
                speechRecognizer?.destroy()
                speechRecognizer = null
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val conf = confidence?.getOrNull(0) ?: 0f
                    // Escape for JS
                    val escapedText = escapeJs(text)
                    callJs("onVoiceResult", "'$escapedText', $conf")
                } else {
                    callJs("onVoiceError", "'No results'")
                }
                // Clean up
                isListening = false
                speechRecognizer?.destroy()
                speechRecognizer = null
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val escapedText = escapeJs(text)
                    callJs("onVoicePartial", "'$escapedText'")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * Call a JavaScript function in the WebView.
     */
    private fun callJs(functionName: String, args: String) {
        val script = "if (typeof window.$functionName === 'function') { window.$functionName($args); }"
        activity.runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Escape a string for safe embedding in JavaScript.
     */
    private fun escapeJs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Clean up resources. Call from Activity.onDestroy().
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}
```

### Kotlin: Changes to `MainActivity.kt`

```kotlin
// In onCreate(), after webView setup:
val voiceBridge = VoiceBridge(this, webView)
webView.addJavascriptInterface(voiceBridge, "AndroidVoiceBridge")

// In onDestroy():
voiceBridge.destroy()
```

### JavaScript: Voice Recognition Helper (inject into WebView)

```javascript
// voice-bridge.js — Inject this into the WebView page

(function() {
    // Only run in Android WebView
    const isAndroidWebView = typeof window.AndroidVoiceBridge !== 'undefined';
    if (!isAndroidWebView) return;

    console.log('[VoiceBridge] Android voice bridge detected');

    // State
    let isRecording = false;
    let onResultCallback = null;
    let onPartialCallback = null;
    let onErrorCallback = null;
    let onStateChangeCallback = null;

    // Expose global callbacks that Android will call
    window.onVoiceReady = function() {
        isRecording = true;
        notifyState('ready');
    };

    window.onVoiceSpeaking = function() {
        notifyState('speaking');
    };

    window.onVoiceLevel = function(level) {
        // Optional: update VU meter
        const event = new CustomEvent('voice-level', { detail: { level: parseFloat(level) } });
        window.dispatchEvent(event);
    };

    window.onVoicePartial = function(text) {
        if (onPartialCallback) onPartialCallback(text);
        const event = new CustomEvent('voice-partial', { detail: { text } });
        window.dispatchEvent(event);
    };

    window.onVoiceResult = function(text, confidence) {
        isRecording = false;
        notifyState('idle');
        if (onResultCallback) onResultCallback(text, confidence);
        const event = new CustomEvent('voice-result', { detail: { text, confidence } });
        window.dispatchEvent(event);
    };

    window.onVoiceError = function(error) {
        isRecording = false;
        notifyState('error');
        if (onErrorCallback) onErrorCallback(error);
        const event = new CustomEvent('voice-error', { detail: { error } });
        window.dispatchEvent(event);
    };

    window.onVoiceStopped = function() {
        isRecording = false;
        notifyState('idle');
    };

    window.onVoiceCancelled = function() {
        isRecording = false;
        notifyState('idle');
    };

    window.onVoiceEnd = function() {
        // Speech ended, waiting for final results
        notifyState('processing');
    };

    function notifyState(state) {
        if (onStateChangeCallback) onStateChangeCallback(state);
        const event = new CustomEvent('voice-state', { detail: { state } });
        window.dispatchEvent(event);
    }

    // Public API
    window.VoiceInput = {
        /**
         * Start voice recognition.
         * @param {string} lang - Language code (default: 'zh-CN')
         */
        start(lang = 'zh-CN') {
            if (isRecording) return;
            window.AndroidVoiceBridge.startRecognition(lang);
        },

        /** Stop and get final result. */
        stop() {
            window.AndroidVoiceBridge.stopRecognition();
        },

        /** Cancel without results. */
        cancel() {
            window.AndroidVoiceBridge.cancelRecognition();
        },

        /** Check if speech recognition is available. */
        isAvailable() {
            return window.AndroidVoiceBridge.isAvailable();
        },

        /** Get current recording state. */
        get isRecording() {
            return isRecording;
        },

        /** Register callbacks. */
        onResult(cb) { onResultCallback = cb; },
        onPartial(cb) { onPartialCallback = cb; },
        onError(cb) { onErrorCallback = cb; },
        onStateChange(cb) { onStateChangeCallback = cb; },
    };

    console.log('[VoiceBridge] VoiceInput API ready');
})();
```

### Usage in Web App (Hermes Web UI)

```javascript
// In the chat input area, add a mic button
// When mic is clicked:

if (window.VoiceInput && window.VoiceInput.isAvailable()) {
    // Android WebView — use native bridge
    VoiceInput.onResult((text, confidence) => {
        document.querySelector('#chat-input').value = text;
        console.log('Voice result:', text, 'confidence:', confidence);
    });
    VoiceInput.onPartial((text) => {
        // Show interim results in the input field
        document.querySelector('#chat-input').value = text;
    });
    VoiceInput.onError((error) => {
        console.error('Voice error:', error);
    });
    VoiceInput.start('zh-CN');
} else if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
    // Desktop Chrome — use Web Speech API directly
    const recognition = new (window.SpeechRecognition || window.webkitSpeechRecognition)();
    recognition.lang = 'zh-CN';
    recognition.onresult = (event) => {
        const text = event.results[event.results.length - 1][0].transcript;
        document.querySelector('#chat-input').value = text;
    };
    recognition.start();
} else {
    // Fallback — server-side STT via MediaRecorder
    // (the existing Hermes approach)
    console.log('Using server-side STT fallback');
}
```

---

## 5. Runtime Permission Handling

On Android 6.0+ (API 23+), `RECORD_AUDIO` is a dangerous permission that must be requested at runtime. Add this to `MainActivity.kt`:

```kotlin
// In MainActivity.kt

// Store the voice bridge reference as a class member
private var voiceBridge: VoiceBridge? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing code ...

    voiceBridge = VoiceBridge(this, webView)
    webView.addJavascriptInterface(voiceBridge!!, "AndroidVoiceBridge")
}

override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == VoiceBridge.PERMISSION_REQUEST_CODE) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted — the bridge will work now
            // Optionally retry the last recognition request
        }
    }
}

override fun onDestroy() {
    voiceBridge?.destroy()
    // ... existing cleanup ...
}
```

### AndroidManifest.xml Addition

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

---

## 6. China-Specific Considerations

| Concern | Approach A (Native Bridge) | Approach B (Server STT) |
|---------|---------------------------|------------------------|
| Google dependency | Uses Android's built-in SpeechRecognizer (on-device) — works without network | No Google dependency |
| Google Play Services | Most Chinese phones include GMS. Huawei/Honor phones use HMS. | Not needed |
| Network requirement | **No network needed** for on-device recognition | Requires network |
| Chinese language | Supported by on-device recognizer on most devices | Use Baidu/iFlytek/Aliyun STT |
| Speed | Instant (on-device) | ~1-3s latency |

**Key insight for China:** Android's `SpeechRecognizer` with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` defaults to **on-device recognition** when available. This means:
- No network call to Google
- Works offline
- Chinese language (zh-CN) is well-supported on Chinese phones
- No GMS requirement for on-device mode (HMS phones also support it)

**Fallback:** If on-device recognition is not available (some devices), `SpeechRecognizer` automatically falls back to network-based recognition (which may need Google). To handle this gracefully:

```kotlin
// Check if on-device recognition is available
val onDeviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
if (onDeviceAvailable) {
    intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
}
```

---

## 7. Implementation Checklist

1. [ ] Add `RECORD_AUDIO` permission to `AndroidManifest.xml`
2. [ ] Create `VoiceBridge.kt` with `@JavascriptInterface` methods
3. [ ] Register `VoiceBridge` in `MainActivity.onCreate()`
4. [ ] Handle runtime permission request in `MainActivity`
5. [ ] Inject `voice-bridge.js` into WebView after page loads
6. [ ] Add mic button to Hermes Web UI (or detect bridge availability)
7. [ ] Test on Chinese Android devices (Xiaomi, Huawei, Oppo)
8. [ ] Handle edge cases: permission denied, no recognizer, recognizer busy

---

## 8. Alternative: Approach C (Web Speech API Polyfill)

If you want the web app's existing `SpeechRecognition` code to work transparently without modification, inject a polyfill:

```javascript
// Inject into WebView after page load
// This replaces the missing SpeechRecognition with a bridge to Android

(function() {
    if (typeof window.SpeechRecognition !== 'undefined' || 
        typeof window.webkitSpeechRecognition !== 'undefined') {
        return; // Already available, don't override
    }
    if (typeof window.AndroidVoiceBridge === 'undefined') {
        return; // No native bridge
    }

    class AndroidSpeechRecognition {
        constructor() {
            this.continuous = false;
            this.interimResults = false;
            this.lang = 'zh-CN';
            this.maxAlternatives = 1;
            this.onresult = null;
            this.onerror = null;
            this.onend = null;
            this.onstart = null;
            this.onspeechstart = null;
            this.onspeechend = null;
            this.onnomatch = null;
            this._finalTranscript = '';
        }

        start() {
            this._finalTranscript = '';
            
            window.onVoiceReady = () => {
                if (this.onstart) this.onstart();
            };
            window.onVoiceSpeaking = () => {
                if (this.onspeechstart) this.onspeechstart();
            };
            window.onVoicePartial = (text) => {
                if (this.interimResults && this.onresult) {
                    const event = { results: this._makeResults(text, false), resultIndex: 0 };
                    this.onresult(event);
                }
            };
            window.onVoiceResult = (text, confidence) => {
                this._finalTranscript = text;
                if (this.onresult) {
                    const event = { results: this._makeResults(text, true), resultIndex: 0 };
                    this.onresult(event);
                }
                if (this.onend) this.onend();
            };
            window.onVoiceEnd = () => {
                if (this.onspeechend) this.onspeechend();
            };
            window.onVoiceError = (error) => {
                if (this.onerror) {
                    this.onerror({ error: 'speech-error', message: error });
                }
            };

            window.AndroidVoiceBridge.startRecognition(this.lang);
        }

        stop() {
            window.AndroidVoiceBridge.stopRecognition();
        }

        abort() {
            window.AndroidVoiceBridge.cancelRecognition();
        }

        _makeResults(text, isFinal) {
            const result = {
                0: { transcript: text, confidence: isFinal ? 0.9 : 0 },
                length: 1,
                isFinal: isFinal
            };
            return { 0: result, length: 1 };
        }
    }

    window.SpeechRecognition = AndroidSpeechRecognition;
    window.webkitSpeechRecognition = AndroidSpeechRecognition;
    console.log('[SpeechPolyfill] Web Speech API polyfilled via Android bridge');
})();
```

---

## Summary

| Question | Answer |
|----------|--------|
| Does WebView support Web Speech API? | **No.** WebView's Chromium build excludes speech recognition. |
| Best approach for China? | **Native SpeechRecognizer bridge** — on-device, no network, no Google dependency |
| How to bridge? | `@JavascriptInterface` methods call Android; `evaluateJavascript()` calls back to JS |
| Minimum changes needed? | 1 new Kotlin file + 3 lines in MainActivity + permission in manifest + JS bridge script |

The recommended approach (A) requires approximately:
- **1 new file:** `VoiceBridge.kt` (~150 lines)
- **3 lines in `MainActivity.kt`:** add bridge, destroy on cleanup
- **1 line in `AndroidManifest.xml`:** RECORD_AUDIO permission
- **1 JS file:** `voice-bridge.js` (~80 lines)
- **~20 lines in web app:** mic button + VoiceInput.start() call
