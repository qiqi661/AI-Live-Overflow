package com.qiqi.pet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "祁渊想趴在你屏幕上陪你"
            textSize = 20f
            setPadding(48, 96, 48, 24)
        }
        val btn = Button(this).apply {
            text = "开启悬浮窗权限"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startPet()
                }
            }
        }
        val status = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(48, 8, 48, 24)
        }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(tv)
            addView(btn)
            addView(status)
        }
        setContentView(root)

        if (Settings.canDrawOverlays(this)) {
            status.text = "悬浮窗权限已开启，宠物正在启动…"
            startPet()
        }
    }

    private fun startPet() {
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(Intent(this, OverlayService::class.java))
        } else {
            startService(Intent(this, OverlayService::class.java))
        }
        finish()
    }

    companion object {
        const val PREFS = "pet_prefs"
        const val KEY_LAST_BUBBLE = "last_bubble"
    }
}
