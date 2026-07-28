package com.qiuyu.petoverlay.utils

import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.io.File

class ScreenshotObserver(
    private val webView: WebView?
) {
    private val observers = mutableListOf<android.os.FileObserver>()
    private val handler = Handler(Looper.getMainLooper())

    private val screenshotPaths = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .resolve("Screenshots").absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            .resolve("Screenshots").absolutePath,
        "/storage/emulated/0/Pictures/Screenshots",
        "/storage/emulated/0/DCIM/Screenshots",
    )

    fun start() {
        for (path in screenshotPaths) {
            val dir = File(path)
            if (!dir.exists()) continue

            val observer = object : android.os.FileObserver(dir, android.os.FileObserver.CREATE or android.os.FileObserver.MOVED_TO) {
                @Suppress("UNCHECKED_CAST")
                override fun onEvent(event: Int, path: String?) {
                    if (path != null && isImageFile(path)) {
                        onScreenshotDetected()
                    }
                }
            }
            observer.startWatching()
            observers.add(observer)
        }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
    }

    private fun onScreenshotDetected() {
        handler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onScreenshot()", null
            )
        }
    }

    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}