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
        private const val POLL_INTERVAL = 5000L
        private const val TAG = "SupabaseSync"
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
    fun sendMessage(content: String) {
        val body = JSONObject().apply {
            put("sender", "pet")
            put("content", content)
        }
        postToTable("pet_messages", body)
    }
    fun startPolling() {
        Log.d(TAG, "Supabase polling started: $supabaseUrl")
        pollTimer = Timer()
        pollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { pollMessages() }
        }, 0, POLL_INTERVAL)
    }
    private fun pollMessages() {
        try {
            val url = URL("$supabaseUrl/rest/v1/pet_messages?sender=eq.user&order=created_at.desc&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            val response = conn.inputStream.bufferedReader().readText()
            val arr = org.json.JSONArray(response)
            if (arr.length() > 0) {
                val msg = arr.getJSONObject(0)
                val id = msg.getLong("id")
                if (id > latestMessageId) {
                    latestMessageId = id
                    val content = msg.getString("content")
                    applyUserMessage(content)
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Poll error", e)
        }
    }
    private fun applyUserMessage(content: String) {
        handler.post {
            val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
            val js = "window._nativeBridge && window._nativeBridge.onBubble(\"" + escaped + "\")"
            webView?.evaluateJavascript(js, null)
        }
    }
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
