package com.qiqi.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.hypot

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var lastExpression = ""
    private var lastBubble = ""

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var lastApp: String? = null

    private val jealousApps = mapOf(
        "com.ss.android.ugc.aweme" to "抖音",
        "com.smile.gifmaker" to "快手",
        "com.xingin.xhs" to "小红书",
        "com.tencent.mm" to "微信",
        "com.tencent.mobileqq" to "QQ",
        "com.sina.weibo" to "微博",
        "tv.danmaku.bili" to "哔哩哔哩",
        "com.kuaishou.nebula" to "快手极速版",
        "com.ss.android.ugc.live" to "抖音直播",
        "com.byted.pangle" to "穿山甲",
        "com.shuqiyuling" to "书旗小说",
        "com.ss.android.article.news" to "今日头条"
    )

    private val SUPABASE_URL = "https://figinkxgnjcgdquhrvwk.supabase.co"
    private val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZpZ2lua3hnbmpjZ2RxdWhydndrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU4NDQ3MjgsImV4cCI6MjEwMTQyMDcyOH0.0JM2iwAo_1nhdJF6Ybi8BRajBHYrPMnWKtXPrRqhfCg"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1, buildNotification())
        showOverlay()
        handler.postDelayed(pollRunnable, 5000)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pet", "祁渊的桌宠",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "pet")
                .setContentTitle("祁渊在这里")
                .setContentText("趴在你屏幕上陪你")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("祁渊在这里")
                .setContentText("趴在你屏幕上陪你")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .build()
        }
    }

    private fun showOverlay() {
        val wv = WebView(this)
        wv.setBackgroundColor(0x00000000)
        wv.settings.javaScriptEnabled = true
        wv.settings.setSupportZoom(false)
        wv.settings.mediaPlaybackRequiresUserGesture = false

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 400
        }

        wv.setOnTouchListener { v, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastX = downX
                    lastY = downY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    p.x += dx.toInt()
                    p.y += dy.toInt()
                    lastX = event.rawX
                    lastY = event.rawY
                    try {
                        windowManager.updateViewLayout(wv, p)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dt = System.currentTimeMillis() - downTime
                    val dist = hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                    if (dt < 300 && dist < 24.0) {
                        reportGesture("tap")
                        wv.evaluateJavascript("window.petTap()", null)
                    }
                    true
                }
                else -> false
            }
        }

        wv.loadUrl("file:///android_asset/pet.html")
        windowManager.addView(wv, params)
        webView = wv
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            executor.execute { pollState() }
            handler.postDelayed(this, 5000)
        }
    }

    private fun pollState() {
        try {
            detectForeground()
            val url = URL(
                "$SUPABASE_URL/rest/v1/clawd_state?select=expression,bubble_text,bubble_style&order=id.desc&limit=1"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            if (code == 200 && body.isNotBlank() && body != "[]") {
                val arr = JSONArray(body)
                val obj = arr.getJSONObject(0)
                val expr = obj.optString("expression", "idle")
                val bubble = obj.optString("bubble_text", "")
                val style = obj.optString("bubble_style", "normal")
                if (expr != lastExpression || bubble != lastBubble) {
                    lastExpression = expr
                    lastBubble = bubble
                    val state = JSONObject()
                        .put("expression", expr)
                        .put("bubble", bubble)
                        .put("style", style)
                    val js = "window.setPetState($state)"
                    handler.post { webView?.evaluateJavascript(js, null) }
                }
            }
        } catch (e: Exception) {
            Log.w("PetOverlay", "poll failed", e)
        }
    }

    private fun detectForeground() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - 3000, end)
            val ev = UsageEvents.Event()
            var pkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                when (ev.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> pkg = ev.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> pkg = null
                    else -> {
                        if (Build.VERSION.SDK_INT >= 29) {
                            when (ev.eventType) {
                                UsageEvents.Event.ACTIVITY_RESUMED -> pkg = ev.packageName
                                UsageEvents.Event.ACTIVITY_PAUSED -> pkg = null
                            }
                        }
                    }
                }
            }
            if (pkg != null && pkg != lastApp) {
                lastApp = pkg
                if (pkg == packageName) return
                reportApp(pkg)
                val name = jealousApps[pkg]
                if (name != null) {
                    val js = "window.setPetState({expression:'angry',bubble:'又在刷$name，都不理我',style:'red'})"
                    handler.post { webView?.evaluateJavascript(js, null) }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun reportApp(pkg: String) {
        executor.execute {
            try {
                val url = URL("$SUPABASE_URL/rest/v1/app_activity")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("apikey", SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "return=minimal")
                val now = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.US
                ).format(java.util.Date())
                val body = JSONObject()
                    .put("package_name", pkg)
                    .put("app_name", pkg)
                    .put("started_at", now)
                    .put("duration_seconds", 0)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("PetOverlay", "app report failed", e)
            }
        }
    }

    private fun reportGesture(gesture: String) {
        executor.execute {
            try {
                val url = URL("$SUPABASE_URL/rest/v1/gesture_logs")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("apikey", SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "return=minimal")
                val body = JSONObject().put("gesture", gesture).toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("PetOverlay", "report failed", e)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        webView?.let { wv ->
            try {
                windowManager.removeView(wv)
            } catch (_: Exception) {
            }
        }
        webView = null
        super.onDestroy()
    }
}
