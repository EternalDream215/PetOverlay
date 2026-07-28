package com.qiuyu.petoverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
        val hasPermission = Settings.canDrawOverlays(this)
        statusText.text = if (hasPermission) "权限：已授予 ✓" else "权限：未授予 ✗"
    }
}