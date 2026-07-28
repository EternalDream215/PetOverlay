package com.qiuyu.petoverlay.utils

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Timer
import java.util.TimerTask

class SupabaseSync(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val webView: WebView?
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pollTimer: Timer? = null
    private var latestStateId: Long = 0

    companion object {
        private const val POLL_INTERVAL = 15000L
    }

    fun logGesture(type: String, x: Int = 0, y: Int = 0) {
        val body = JSONObject().apply {
            put("gesture_type", type)
            put("x", x)
            put("y", y)
        }
        postToTable("gesture_log", body)
    }

    fun logAppUsage(packageName: String) {
        val body = JSONObject().apply {
            put("package_name", packageName)
        }
        postToTable("app_usage", body)
    }

    fun pushPetState(key: String, value: String) {
        val body = JSONObject().apply {
            put("state_key", key)
            put("state_value", value)
        }
        postToTable("pet_state", body)
    }

    fun pushBubble(text: String) { pushPetState("speech_bubble", text) }
    fun pushMood(mood: String) { pushPetState("mood", mood) }

    private fun postToTable(table: String, body: JSONObject) {
        Thread {
            try {
                val url = URL("$supabaseUrl/rest/v1/$table")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }

    fun startPolling() {
        pollTimer = Timer()
        pollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { pollState() }
        }, POLL_INTERVAL, POLL_INTERVAL)
    }

    private fun pollState() {
        try {
            val url = URL("$supabaseUrl/rest/v1/pet_state?order=updated_at.desc&limit=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val arr = org.json.JSONArray(response)
                if (arr.length() > 0) {
                    val latest = arr.getJSONObject(0)
                    val id = latest.optLong("id", 0)
                    if (id > latestStateId) {
                        latestStateId = id
                        applyState(latest.getString("state_key"), latest.getString("state_value"))
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    private fun applyState(key: String, value: String) {
        handler.post {
            when (key) {
                "speech_bubble" -> webView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.showBubble(\"${value.replace("\"", "\\\"")}\")", null
                )
                "mood" -> webView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setExpression(\"$value\")", null
                )
            }
        }
    }

    fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }
}