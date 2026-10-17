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

        // Pill-shaped Floating Bar containing: [ON/OFF] [⚙️ Open App] [✖ Close]
        floatingBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = 90f
                setStroke(3, Color.parseColor("#334155"))
            }
        }

        // 1. Main ON/OFF Toggle Button
        statusToggleBtn = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(28, 18, 28, 18)
        }

        // 2. Open App Button (⚙️)
        val openAppBtn = TextView(this).apply {
            text = "⚙️"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 60f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 12 }
            layoutParams = lp
            setOnClickListener {
                val intent = Intent(this@FloatingControlService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        }

        // 3. Close Floating Bar Button (✖)
        val closeFloatBtn = TextView(this).apply {
            text = "✖"
            textSize = 13f
            setTextColor(Color.parseColor("#F87171"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 60f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 10 }
            layoutParams = lp
            setOnClickListener {
                Toast.makeText(this@FloatingControlService, "Floating Icon बंद कर दिया गया", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }

        floatingBar.addView(statusToggleBtn)
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
            y = 320
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
                        val msg = if (!current) "⚡ Auto-Accept: ON" else "💤 Auto-Accept: OFF"
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
        val minFare = prefs.getInt("MIN_FARE", 150)
        val label = if (useFare) "₹$minFare+" else "ANY ₹"

        if (isEnabled) {
            statusToggleBtn.text = "⚡ ON ($label)"
            statusToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#16A34A"))
                cornerRadius = 70f
                setStroke(3, Color.parseColor("#FACC15"))
            }
        } else {
            statusToggleBtn.text = "💤 OFF"
            statusToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = 70f
                setStroke(3, Color.WHITE)
            }
        }
    }

    private fun startForegroundShield() {
        val channelId = "porter_vip_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Porter VIP Floating Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Porter TZ VIP Active")
                .setContentText("Floating Control Bar चल रहा है")
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
