package com.qiuyu.petoverlay.utils

import android.content.Context
import android.util.Log
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.media.MediaPlayer

class SupabaseSync(
    private val context: Context,
    private val webView: WebView?
) {
    companion object {
        private const val TAG = "SupabaseSync"
        private const val SUPABASE_URL = "https://xaxcfztcaulzfzwpziho.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhheGNmenRjYXVsZmZ3cHppaG8iLCJyb2UiOiJhdXRoIiwiaWF0IjoxNzUzNzYxMzczLCJleHAiOjIwNjkzMzczNzN9.Q8nM"
        private const val MOSS_API_KEY = "sk-ef5061fa149916b116de49be0992bed7f7fbe6a7b0d8ae24"
        private const val MOSS_VOICE_ID = "b64395c7-545f-46b7-839d-625f8a10748f"
        private const val MOSS_API_URL = "https://api.mosi.cn/v1/audio/speech"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var lastMsgId: Long = 0
    private var polling = false

    fun startPolling() {
        if (polling) return
        polling = true
        executor.execute {
            try {
                val initId = fetchLatestId()
                if (initId > 0) lastMsgId = initId
                Log.d(TAG, "Init lastMsgId=$lastMsgId")
            } catch (e: Exception) {
                Log.e(TAG, "Init error: ${e.message}")
            }
            while (polling) {
                try {
                    Thread.sleep(5000)
                    pollMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                }
            }
        }
    }

    fun stopPolling() { polling = false }

    private fun fetchLatestId(): Long {
        val url = URL("${SUPABASE_URL}/rest/v1/pet_messages?id=gt.0&order=id.desc&limit=1&select=id")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONArray(resp)
        return if (arr.length() > 0) arr.getJSONObject(0).getLong("id") else 0
    }

    private fun pollMessages() {
        val url = URL("${SUPABASE_URL}/rest/v1/pet_messages?id=gt.${lastMsgId}&order=id.asc&limit=5&select=id,sender,content,created_at")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONArray(resp)
        Log.d(TAG, "Polled ${arr.length()} new messages")
        for (i in 0 until arr.length()) {
            val msg = arr.getJSONObject(i)
            val id = msg.getLong("id")
            val sender = msg.optString("sender", "user")
            val content = msg.optString("content", "")
            if (id > lastMsgId && content.isNotEmpty()) {
                lastMsgId = id
                Log.d(TAG, "New msg [$sender]: $content")
                applyUserMessage(content)
            }
        }
    }

    private fun applyUserMessage(content: String) {
        val escaped = content
            .replace("\", "\\\\")
            .replace(""", "\\"")
            .replace("
", "\\n")
            .replace("", "")
            .replace("	", " ")
        val js = "javascript:window.petEngine && window.petEngine.showBubble(\"" + escaped + "\", 8000)"
        Log.d(TAG, "JS eval: $js")
        android.os.Handler(context.mainLooper).post {
            webView?.evaluateJavascript(js) { result ->
                Log.d(TAG, "JS result: $result")
            }
        }
        synthesizeAndPlay(content)
    }

    private fun synthesizeAndPlay(text: String) {
        executor.execute {
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
                    return@execute
                }

                val respBody = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val respJson = JSONObject(respBody)
                val audioUrl = respJson.getString("url")
                Log.d(TAG, "Audio URL: $audioUrl")

                val cacheFile = File(context.cacheDir, "tts_" + System.currentTimeMillis() + ".mp3")
                val audioConn = URL(audioUrl).openConnection() as HttpURLConnection
                audioConn.connectTimeout = 15000
                audioConn.readTimeout = 30000
                val input = audioConn.inputStream
                val output = FileOutputStream(cacheFile)
                input.copyTo(output)
                output.close()
                input.close()
                audioConn.disconnect()
                Log.d(TAG, "Downloaded audio: ${cacheFile.length()} bytes")

                val player = MediaPlayer()
                player.setDataSource(cacheFile.absolutePath)
                player.prepare()
                player.setOnCompletionListener {
                    it.release()
                    cacheFile.delete()
                    Log.d(TAG, "Playback complete, cache cleaned")
                }
                player.start()
                Log.d(TAG, "Playing MOSS TTS audio")

            } catch (e: Exception) {
                Log.e(TAG, "MOSS TTS error: ${e.message}", e)
            }
        }
    }
}
