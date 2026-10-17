package com.porter.tz.vip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBar: LinearLayout
    private lateinit var statusToggleBtn: TextView
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        startForegroundShield()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)

        floatingBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 10, 14, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2090D16"))
                cornerRadius = 90f
                setStroke(3, Color.parseColor("#38BDF8"))
            }
        }

        statusToggleBtn = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(24, 14, 24, 14)
        }

        val openPorterBtn = TextView(this).apply {
            text = "🚀"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C3AED"))
                cornerRadius = 60f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
            setOnClickListener {
                MainActivity.launchPorterPartnerApp(this@FloatingControlService)
            }
        }

        val openAppBtn = TextView(this).apply {
            text = "⚙️"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 60f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
            setOnClickListener {
                val intent = Intent(this@FloatingControlService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        }

        val closeFloatBtn = TextView(this).apply {
            text = "✖"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 60f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
            setOnClickListener {
                Toast.makeText(this@FloatingControlService, "ZU Smart Overlay Closed", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }

        floatingBar.addView(statusToggleBtn)
        floatingBar.addView(openPorterBtn)
        floatingBar.addView(openAppBtn)
        floatingBar.addView(closeFloatBtn)

        updateVisualState()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 280
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        statusToggleBtn.setOnTouchListener { _: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingBar, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 18 && diffY < 18) {
                        val current = prefs.getBoolean("BOT_ACTIVE", true)
                        prefs.edit().putBoolean("BOT_ACTIVE", !current).apply()
                        updateVisualState()
                        val msg = if (!current) "ZU Smart: ACTIVE" else "ZU Smart: PAUSED"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingBar, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::statusToggleBtn.isInitialized) {
            updateVisualState()
        }
        return START_STICKY
    }

    private fun updateVisualState() {
        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("BOT_ACTIVE", true)
        val useFare = prefs.getBoolean("USE_FARE_FILTER", true)
        val minFare = prefs.getInt("MIN_FARE", 200)
        val label = if (useFare) "₹$minFare+" else "ALL"

        if (isEnabled) {
            statusToggleBtn.text = "⚡ ZU ON ($label)"
            statusToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669"))
                cornerRadius = 70f
                setStroke(2, Color.parseColor("#00F5D4"))
            }
        } else {
            statusToggleBtn.text = "💤 ZU OFF"
            statusToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = 70f
                setStroke(2, Color.WHITE)
            }
        }
    }

    private fun startForegroundShield() {
        val channelId = "zu_smart_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ZU Smart Controller",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification = Notification.Builder(this, channelId)
                .setContentTitle("ZU Smart Active")
                .setContentText("Floating Controller is running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            startForeground(1001, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingBar.isInitialized) {
            try {
                windowManager.removeView(floatingBar)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
