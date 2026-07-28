package com.qiuyu.petoverlay.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        private const val POLL_INTERVAL = 2000L
        private const val TAG = "UsageTracker"
    }

    fun start() {
        // 先设一个初始值，避免第一次就触发
        lastApp = getForegroundApp()
        Log.d(TAG, "Initial foreground app: $lastApp")

        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val current = getForegroundApp()
                    Log.d(TAG, "Poll: current=$current last=$lastApp")
                    if (current.isNotEmpty() && current != lastApp) {
                        val old = lastApp
                        lastApp = current
                        Log.d(TAG, "App changed: $old -> $current")
                        onAppChanged(current)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error", e)
                }
            }
        }, POLL_INTERVAL, POLL_INTERVAL)
    }

    private fun getForegroundApp(): String {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()

            // 方法1: queryEvents（优先）
            var result = getFromEvents(usm, now)
            if (result.isNotEmpty()) return result

            // 方法2: queryUsageStats（备选，查最近10分钟内最后使用的app）
            result = getFromUsageStats(usm, now)
            result
        } catch (e: Exception) {
            Log.e(TAG, "getForegroundApp failed", e)
            ""
        }
    }

    private fun getFromEvents(usm: UsageStatsManager, now: Long): String {
        return try {
            val events = usm.queryEvents(now - 10000, now)
            val event = UsageEvents.Event()
            var foreground = ""
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    type == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    foreground = event.packageName
                }
            }
            foreground
        } catch (_: Exception) {
            ""
        }
    }

    private fun getFromUsageStats(usm: UsageStatsManager, now: Long): String {
        return try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 600000, // 最近10分钟
                now
            )
            if (stats.isNullOrEmpty()) {
                ""
            } else {
                // 取 lastTimeUsed 最大的那个
                stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
            }
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
