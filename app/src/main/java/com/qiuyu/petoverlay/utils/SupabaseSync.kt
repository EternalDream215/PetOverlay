package com.qiuyu.petoverlay.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private var latestMessageId: Long = 0

    companion object {
        private const val POLL_INTERVAL = 10000L // 10秒轮询
        private const val TAG = "SupabaseSync"
    }

    // === 写入：桌宠状态 ===

    fun pushPetState(key: String, value: String) {
        val body = JSONObject().apply {
            put("state_key", key)
            put("state_value", value)
        }
        postToTable("pet_state", body)
    }

    fun pushBubble(text: String) { pushPetState("speech_bubble", text) }
    fun pushMood(mood: String) { pushPetState("mood", mood) }

    // === 写入：桌宠发的消息 ===

    fun sendMessage(content: String) {
        val body = JSONObject().apply {
            put("sender", "pet")
            put("content", content)
        }
        postToTable("pet_messages", body)
    }

    // === 轮询：拉取人类发的消息 ===

    fun startPolling() {
        Log.d(TAG, "Supabase polling started: $supabaseUrl")
        pollTimer = Timer()
        pollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { pollMessages() }
        }, POLL_INTERVAL, POLL_INTERVAL)
    }

    private fun pollMessages() {
        try {
            val url = URL("$supabaseUrl/rest/v1/pet_messages?sender=eq.user&order=id.desc&limit=1")
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
                    if (id > latestMessageId) {
                        latestMessageId = id
                        val content = latest.getString("content")
                        Log.d(TAG, "New message from user: $content")
                        applyUserMessage(content)
                    }
                }
            } else {
                Log.w(TAG, "Poll failed: ${conn.responseCode}")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Poll error", e)
        }
    }

    private fun applyUserMessage(content: String) {
        handler.post {
            webView?.evaluateJavascript(
                "try { window.petEngine && window.petEngine.showBubble(\"${content.replace("\"", "\\\").replace("\n", " ")}\"); window.petEngine && window.petEngine.setExpression('happy'); } catch(e) {}",
                null
            )
        }
    }

    // === 通用 POST ===

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
                val code = conn.responseCode
                if (code != 201) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.w(TAG, "POST $table failed: $code $err")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "POST $table error", e)
            }
        }.start()
    }

    fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }
}