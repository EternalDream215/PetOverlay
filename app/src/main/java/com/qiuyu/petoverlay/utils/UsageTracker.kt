package com.qiuyu.petoverlay.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.Timer
import java.util.TimerTask

class UsageTracker(
    private val context: Context,
    private val webView: WebView?
) {
    private var timer: Timer? = null
    private var lastApp: String = ""

    companion object {
        private const val POLL_INTERVAL = 3000L
    }

    fun start() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val current = getForegroundApp()
                if (current.isNotEmpty() && current != lastApp) {
                    lastApp = current
                    onAppChanged(current)
                }
            }
        }, 0, POLL_INTERVAL)
    }

    private fun getForegroundApp(): String {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000, now)
            val event = UsageEvents.Event()
            var foreground = ""
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foreground = event.packageName
                }
            }
            foreground
        } catch (_: Exception) {
            ""
        }
    }

    private fun onAppChanged(packageName: String) {
        Handler(Looper.getMainLooper()).post {
            webView?.evaluateJavascript(
                "window._nativeBridge && window._nativeBridge.onAppChanged(\"$packageName\")",
                null
            )
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }
}
