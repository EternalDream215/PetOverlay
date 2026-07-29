package com.qiuyu.petoverlay.utils

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class SupabaseSync(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val webView: WebView?,
    private val tts: TextToSpeech?
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
        Log.d(TAG, "Supabase polling started")
        pollTimer = Timer()
        pollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { pollMessages() }
        }, 0, POLL_INTERVAL)
    }

    private val MOSS_API_KEY = "sk-ef5061fa149916b116de49be0992bed7f7fbe6a7b0d8ae24"
    private val MOSS_VOICE_ID = "b64395c7-545f-46b7-839d-625f8a10748f"
    private val MOSS_API_URL = "https://api.mosi.cn/v1/audio/speech"

    private fun pollMessages() {
        try {
            val url = URL(supabaseUrl + "/rest/v1/pet_messages?sender=eq.user&order=id.desc&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer " + supabaseKey)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val response = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(response)
            if (arr.length() > 0) {
                val msg = arr.getJSONObject(0)
                val id = msg.getLong("id")
                if (id > latestMessageId) {
                    latestMessageId = id
                    val content = msg.getString("content")
                    Log.d(TAG, "New msg[$id]: $content")
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
            try {
                val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                val js = "window.petEngine && window.petEngine.showBubble(\"" + escaped + "\")"
                webView?.evaluateJavascript(js, null)
                synthesizeAndPlay(content)
                Log.d(TAG, "Bubble + TTS: $content")
            } catch (e: Exception) {
                Log.e(TAG, "applyUserMessage err", e)
            }
        }
    }

    private fun postToTable(table: String, body: JSONObject) {
        Thread {
            try {
                val url = URL(supabaseUrl + "/rest/v1/" + table)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer " + supabaseKey)
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
                Log.e(TAG, "POST $table err", e)
            }
        }.start()
    }

    fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }

    private fun synthesizeAndPlay(text: String) {
        try {
            val apiUrl = URL(MOSS_API_URL)
            val conn = apiUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $MOSS_API_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val body = JSONObject().apply {
                put("model", "moss-tts")
                put("input", text)
                put("voice_id", MOSS_VOICE_ID)
                put("response_format", "mp3")
                put("delivery_method", "url")
            }
            conn.outputStream.write(body.toString().toByteArray())

            val respCode = conn.responseCode
            Log.d(TAG, "MOSS API resp: $respCode")
            if (respCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                Log.e(TAG, "MOSS API error: $err")
                conn.disconnect()
                return
            }

            val respBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val respJson = JSONObject(respBody)
            val audioUrl = respJson.getString("url")
            Log.d(TAG, "Audio URL: $audioUrl")

            val cacheFile = java.io.File((webView?.context ?: return).cacheDir, "tts_" + System.currentTimeMillis() + ".mp3")
            val audioConn = java.net.URL(audioUrl).openConnection() as HttpURLConnection
            audioConn.connectTimeout = 15000
            audioConn.readTimeout = 30000
            val inp = audioConn.inputStream
            val out = java.io.FileOutputStream(cacheFile)
            inp.copyTo(out)
            out.close()
            inp.close()
            audioConn.disconnect()
            Log.d(TAG, "Downloaded audio: ${cacheFile.length()} bytes")

            val player = android.media.MediaPlayer()
            player.setDataSource(cacheFile.absolutePath)
            player.prepare()
            player.setOnCompletionListener {
                it.release()
                cacheFile.delete()
                Log.d(TAG, "Playback complete")
            }
            player.start()
            Log.d(TAG, "Playing MOSS TTS audio")

        } catch (e: Exception) {
            Log.e(TAG, "MOSS TTS error: ${e.message}", e)
        }
    }

}