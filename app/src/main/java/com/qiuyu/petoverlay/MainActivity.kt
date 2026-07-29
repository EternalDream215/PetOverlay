package com.qiuyu.petoverlay

import android.content.Intent
import android.app.AppOpsManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qiuyu.petoverlay.service.PetOverlayService

class MainActivity : AppCompatActivity() {
    private var isRunning = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val statusText = findViewById<TextView>(R.id.statusText)
        val toggleBtn = findViewById<Button>(R.id.toggleBtn)
        val permBtn = findViewById<Button>(R.id.permBtn)
        val soundToggleBtn = findViewById<Button>(R.id.soundToggleBtn)
        updateSoundBtn(soundToggleBtn)
        updateStatus(statusText)

        updateStatus(statusText)

        permBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "悬浮窗权限已授予 ✓", Toast.LENGTH_SHORT).show()
            }
        }
        soundToggleBtn.setOnClickListener {
            PetOverlayService.isMuted = !PetOverlayService.isMuted
            updateSoundBtn(soundToggleBtn)
            val state = if (PetOverlayService.isMuted) "已关闭" else "已开启"
            Toast.makeText(this, "语音播报$state", Toast.LENGTH_SHORT).show()
        }

        toggleBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isRunning) {
                PetOverlayService.stop(this)
                isRunning = false
                toggleBtn.text = "启动桌宠"
                statusText.text = "状态：已停止"
            } else {
                PetOverlayService.start(this)
                isRunning = true
                toggleBtn.text = "停止桌宠"
                statusText.text = "状态：运行中 🐋"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.statusText))
    }

    private fun updateStatus(statusText: TextView) {
        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = hasUsageStatsPermission()
        val lines = mutableListOf<String>()
        lines.add("悬浮窗：${if (overlayOk) "✓" else "✗  未授权"}")
        lines.add("使用统计：${if (usageOk) "✓" else "✗  需手动开启"}")
        lines.add("截图监听：✓")
        statusText.text = lines.joinToString("\n")
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    private fun updateSoundBtn(btn: Button) {
        if (PetOverlayService.isMuted) {
            btn.text = "🔇 语音：关闭"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF9E9E9E.toInt())
        } else {
            btn.text = "🔊 语音：开启"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
        }
    }
}