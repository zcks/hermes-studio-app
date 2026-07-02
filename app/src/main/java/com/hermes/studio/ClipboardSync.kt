package com.hermes.studio

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

object ClipboardSync {

    private var lastClipText: String? = null
    private var clipboardManager: ClipboardManager? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkClipboard()
            handler.postDelayed(this, 500L)
        }
    }
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context, getWebView: () -> WebView?) {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        // Record current clipboard so we don't trigger on existing content
        lastClipText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
        handler.post(checkRunnable)
    }

    fun stop() {
        handler.removeCallbacks(checkRunnable)
    }

    private fun checkClipboard() {
        val text = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        if (text == lastClipText) return
        lastClipText = text

        val webView = getWebView() ?: return
        val escaped = text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\$", "\\\$")
        // Try common selectors for chat input boxes
        val js = """
            (function(text) {
                var selectors = [
                    'textarea[name="message"]',
                    'textarea#message',
                    'textarea[placeholder]',
                    'div[contenteditable="true"]',
                    'textarea'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                            el.focus();
                            el.value = text;
                            el.dispatchEvent(new Event('input', {bubbles: true}));
                        } else {
                            el.focus();
                            el.textContent = text;
                            el.dispatchEvent(new Event('input', {bubbles: true}));
                        }
                        return true;
                    }
                }
                return false;
            })('$escaped');
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
