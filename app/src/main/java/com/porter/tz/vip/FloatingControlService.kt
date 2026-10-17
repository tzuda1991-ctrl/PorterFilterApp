package com.porter.tz.vip

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
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: TextView

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)

        floatingButton = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(35, 25, 35, 25)
        }

        fun updateVisualState() {
            val isEnabled = prefs.getBoolean("BOT_ACTIVE", true)
            val minFare = prefs.getInt("MIN_FARE", 150)
            if (isEnabled) {
                floatingButton.text = "⚡ ON\n₹$minFare+"
                floatingButton.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#16A34A")) // Green
                    cornerRadius = 100f
                    setStroke(4, Color.parseColor("#FACC15"))
                }
            } else {
                floatingButton.text = "💤 OFF\nVIP"
                floatingButton.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DC2626")) // Red
                    cornerRadius = 100f
                    setStroke(4, Color.WHITE)
                }
            }
        }

        updateVisualState()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 350
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingButton.setOnTouchListener { _: View, event: MotionEvent ->
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
                    windowManager.updateViewLayout(floatingButton, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 15 && diffY < 15) {
                        val current = prefs.getBoolean("BOT_ACTIVE", true)
                        prefs.edit().putBoolean("BOT_ACTIVE", !current).apply()
                        updateVisualState()
                        val msg = if (!current) "⚡ Porter Auto-Accept: ON" else "🛑 Porter Auto-Accept: OFF"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingButton, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) {
            windowManager.removeView(floatingButton)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
