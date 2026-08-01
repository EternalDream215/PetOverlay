package com.qiuyu.petoverlay.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PetGestureHandler(
    private val context: Context,
    private val params: WindowManager.LayoutParams,
    private val windowManager: WindowManager,
    private val webView: WebView,
    private val screenW: Int,
    private val screenH: Int,
    private val petW: Int,
    private val petH: Int
) {
    // Use override display size (1080x2358) for WindowManager.LayoutParams coordinate system
    private val realScreenW: Int = screenW
    private val realScreenH: Int = screenH
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 10
        private const val FLING_VELOCITY = 500
        private const val FLING_TIME = 300
        private const val MULTI_TAP_TIMEOUT = 1500L
        private const val CRAWLBACK_DELAY = 1500L
    }

    fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > MOVE_THRESHOLD || abs(dy) > MOVE_THRESHOLD) {
                        hasMoved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager.updateViewLayout(webView, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (!hasMoved) {
                        when {
                            elapsed > LONG_PRESS_TIMEOUT -> {
                                tapCount = 0
                                onLongPress()
                            }
                            System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT -> {
                                tapCount = 0
                                onDoubleTap()
                            }
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                tapCount++
                                handler.removeCallbacksAndMessages(null)
                                handler.postDelayed({
                                    onMultiTap(tapCount)
                                    tapCount = 0
                                }, MULTI_TAP_TIMEOUT)
                                if (tapCount == 1) onTap()
                            }
                        }
                    } else {
                        val velocity = sqrt((dx * dx + dy * dy).toDouble())
                        if (velocity > FLING_VELOCITY && elapsed < FLING_TIME) {
                            // Fling: fly off screen
                            onFling(dx, dy)
                        } else {
                            // Just drag: clamp within screen
                            params.x = clampX(params.x)
                            params.y = clampY(params.y)
                            try { windowManager.updateViewLayout(webView, params) } catch (_: Exception) {}
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        webView.evaluateJavascript("window._nativeBridge && window._nativeBridge.onTap()", null)
    }

    private fun onDoubleTap() {
        webView.evaluateJavascript("window._nativeBridge && window._nativeBridge.onDoubleTap()", null)
    }

    private fun onLongPress() {
        webView.evaluateJavascript("window._nativeBridge && window._nativeBridge.onLongPress()", null)
    }

    private fun onFling(dx: Int, dy: Int) {
        // Fly off screen: ensure it goes past screen edges
        val magnitude = sqrt((dx * dx + dy * dy).toDouble())
        val normDx = if (magnitude > 0) dx / magnitude else 0.0
        val normDy = if (magnitude > 0) dy / magnitude else 0.0
        val flyDist = max(realScreenW, realScreenH) + petW
        val flyX = (normDx * flyDist).toInt()
        val flyY = (normDy * flyDist).toInt()
        // Move pet way off screen in the fling direction
        params.x = flyX
        params.y = flyY
        try { windowManager.updateViewLayout(webView, params) } catch (_: Exception) {}
        // Tell JS to show the fly animation + "啊啊啊"
        webView.evaluateJavascript("window._nativeBridge && window._nativeBridge.onFling()", null)
        // After delay, crawl back to a random safe position across the FULL screen
        handler.postDelayed({
            val margin = 20
            val randX = (margin until (realScreenW - petW + petW / 2 - margin)).random()
            val randY = (margin until (realScreenH - petH + petH / 2 - margin)).random()
            params.x = randX
            params.y = randY
            try { windowManager.updateViewLayout(webView, params) } catch (_: Exception) {}
            webView.evaluateJavascript("window._nativeBridge && window._nativeBridge.onCrawlBack()", null)
        }, CRAWLBACK_DELAY)
    }

    private fun onMultiTap(count: Int) {
        webView.evaluateJavascript(
            "window.petEngine && window.petEngine.onMultiTap($count)", null
        )
    }

    private fun clampX(x: Int): Int = max(0, min(x, realScreenW - petW))
    private fun clampY(y: Int): Int = max(0, min(y, realScreenH - petH))
}