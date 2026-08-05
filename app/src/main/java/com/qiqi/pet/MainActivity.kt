package com.qiqi.pet

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "祁渊想趴在你屏幕上陪你"
            textSize = 20f
            setPadding(48, 96, 48, 24)
        }
        val btn = Button(this).apply {
            text = "开启权限并启动"
            setOnClickListener { grantAndStart() }
        }
        status = TextView(this).apply {
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

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val usage = hasUsageAccess()
        if (overlay && usage) {
            status.text = "权限都齐了，启动中…"
            startPet()
        } else {
            val sb = StringBuilder()
            if (!overlay) sb.append("• 悬浮窗权限未开启\n")
            if (!usage) sb.append("• 使用情况访问未开启（用它看我看了什么App）\n")
            status.text = sb.toString().trim()
        }
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun grantAndStart() {
        when {
            !Settings.canDrawOverlays(this) -> {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            !hasUsageAccess() -> {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            else -> startPet()
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
}