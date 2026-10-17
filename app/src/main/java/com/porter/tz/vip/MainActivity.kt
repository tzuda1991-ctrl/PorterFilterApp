package com.porter.tz.vip

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusBadge: TextView
    private var selectedSpeed = "NORMAL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060913")

        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        selectedSpeed = prefs.getString("SWIPE_SPEED", "NORMAL") ?: "NORMAL"

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#060913"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 50, 45, 80)
        }

        // ================= HERO BRANDING CARD =================
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(50, 55, 50, 55)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#1E1B4B"),
                    Color.parseColor("#0F172A"),
                    Color.parseColor("#042F2E")
                )
            ).apply {
                cornerRadius = 48f
                setStroke(3, Color.parseColor("#38BDF8"))
            }
        }

        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.ic_vip_logo)
            layoutParams = LinearLayout.LayoutParams(170, 170).apply {
                bottomMargin = 20
            }
        }

        val brandTitle = TextView(this).apply {
            text = "ZU Smart"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
        }

        val brandSub = TextView(this).apply {
            text = "INTELLIGENT AUTO-DISPATCH ENGINE"
            textSize = 11f
            setTextColor(Color.parseColor("#00F5D4"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 28)
        }

        statusBadge = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(36, 16, 36, 16)
            gravity = Gravity.CENTER
        }

        heroCard.addView(logoView)
        heroCard.addView(brandTitle)
        heroCard.addView(brandSub)
        heroCard.addView(statusBadge)
        container.addView(heroCard)

        // ================= 1. FARE FILTER CARD =================
        val fareCard = createSectionCard()
        val fareHeaderRow = createSwitchRow(
            title = "Minimum Fare Filter",
            subtitle = "Accept orders above target amount",
            accentColor = "#00F5D4",
            initialState = prefs.getBoolean("USE_FARE_FILTER", true)
        )
        val fareSwitch = fareHeaderRow.second

        val minFareInput = createStyledInput(
            hintText = "Enter minimum fare (e.g. 200)",
            initialValue = prefs.getInt("MIN_FARE", 200).toString(),
            isNumber = true,
            strokeHex = "#00F5D4"
        )

        fun syncFareState(enabled: Boolean) {
            minFareInput.isEnabled = enabled
            minFareInput.alpha = if (enabled) 1.0f else 0.35f
        }
        syncFareState(fareSwitch.isChecked)
        fareSwitch.setOnCheckedChangeListener { _, isChecked -> syncFareState(isChecked) }

        fareCard.addView(fareHeaderRow.first)
        fareCard.addView(minFareInput)
        container.addView(fareCard)

        // ================= 2. ROUTE FILTER CARD =================
        val routeCard = createSectionCard()

        val pickupRow = createSwitchRow(
            title = "Pickup Zone Filter",
            subtitle = "Match keywords in pickup address only",
            accentColor = "#38BDF8",
            initialState = prefs.getBoolean("USE_PICKUP_FILTER", false)
        )
        val pickupSwitch = pickupRow.second
        val pickupInput = createStyledInput(
            hintText = "e.g. Koramangala, Indiranagar",
            initialValue = prefs.getString("PICKUP_FILTER", "") ?: "",
            isNumber = false,
            strokeHex = "#38BDF8"
        )

        fun syncPickupState(enabled: Boolean) {
            pickupInput.isEnabled = enabled
            pickupInput.alpha = if (enabled) 1.0f else 0.35f
        }
        syncPickupState(pickupSwitch.isChecked)
        pickupSwitch.setOnCheckedChangeListener { _, isChecked -> syncPickupState(isChecked) }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                3
            ).apply {
                topMargin = 35
                bottomMargin = 30
            }
        }

        val dropRow = createSwitchRow(
            title = "Drop Zone Filter",
            subtitle = "Match keywords in drop address only",
            accentColor = "#F43F5E",
            initialState = prefs.getBoolean("USE_DROP_FILTER", false)
        )
        val dropSwitch = dropRow.second
        val dropInput = createStyledInput(
            hintText = "e.g. Krishnarajapuram, Whitefield",
            initialValue = prefs.getString("DROP_FILTER", "") ?: "",
            isNumber = false,
            strokeHex = "#F43F5E"
        )

        fun syncDropState(enabled: Boolean) {
            dropInput.isEnabled = enabled
            dropInput.alpha = if (enabled) 1.0f else 0.35f
        }
        syncDropState(dropSwitch.isChecked)
        dropSwitch.setOnCheckedChangeListener { _, isChecked -> syncDropState(isChecked) }

        routeCard.addView(pickupRow.first)
        routeCard.addView(pickupInput)
        routeCard.addView(divider)
        routeCard.addView(dropRow.first)
        routeCard.addView(dropInput)
        container.addView(routeCard)

        // ================= 3. SWIPE VELOCITY ENGINE CARD =================
        val speedCard = createSectionCard()

        val speedHeader = TextView(this).apply {
            text = "SWIPE VELOCITY ENGINE"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 25)
        }
        speedCard.addView(speedHeader)

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        val speedCards = mutableMapOf<String, LinearLayout>()

        fun refreshSpeedUI() {
            for ((mode, cardView) in speedCards) {
                val active = (mode == selectedSpeed)
                cardView.background = GradientDrawable().apply {
                    setColor(if (active) Color.parseColor("#1E293B") else Color.parseColor("#0B0F19"))
                    cornerRadius = 28f
                    setStroke(if (active) 4 else 2, if (active) Color.parseColor("#00F5D4") else Color.parseColor("#1E293B"))
                }
            }
        }

        fun buildSpeedOption(modeKey: String, icon: String, label: String, msText: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 30, 16, 30)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (modeKey != "HUMAN") 16 else 0
                }
                val tvIcon = TextView(context).apply {
                    text = icon
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
                val tvName = TextView(context).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 8, 0, 4)
                }
                val tvMs = TextView(context).apply {
                    text = msText
                    textSize = 11f
                    setTextColor(Color.parseColor("#38BDF8"))
                    gravity = Gravity.CENTER
                }
                addView(tvIcon)
                addView(tvName)
                addView(tvMs)
                setOnClickListener {
                    selectedSpeed = modeKey
                    refreshSpeedUI()
                }
            }
        }

        val quickBox = buildSpeedOption("QUICK", "⚡", "Quick", "150 ms")
        val normalBox = buildSpeedOption("NORMAL", "🚀", "Normal", "320 ms")
        val humanBox = buildSpeedOption("HUMAN", "🧑", "Human", "550 ms")

        speedCards["QUICK"] = quickBox
        speedCards["NORMAL"] = normalBox
        speedCards["HUMAN"] = humanBox

        speedRow.addView(quickBox)
        speedRow.addView(normalBox)
        speedRow.addView(humanBox)
        refreshSpeedUI()

        speedCard.addView(speedRow)
        container.addView(speedCard)

        // ================= ACTION BUTTONS =================
        val saveBtn = createGradientButton(
            textStr = "SAVE CONFIGURATION",
            colors = intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#06B6D4"))
        ) {
            val fare = minFareInput.text.toString().toIntOrNull() ?: 200
            val pickup = pickupInput.text.toString().trim()
            val drop = dropInput.text.toString().trim()

            prefs.edit()
                .putBoolean("USE_FARE_FILTER", fareSwitch.isChecked)
                .putInt("MIN_FARE", fare)
                .putBoolean("USE_PICKUP_FILTER", pickupSwitch.isChecked)
                .putString("PICKUP_FILTER", pickup)
                .putBoolean("USE_DROP_FILTER", dropSwitch.isChecked)
                .putString("DROP_FILTER", drop)
                .putString("SWIPE_SPEED", selectedSpeed)
                .apply()

            if (Settings.canDrawOverlays(this)) {
                startFloatingWidget()
            }
            Toast.makeText(this, "ZU Smart configuration saved ($selectedSpeed Mode)", Toast.LENGTH_SHORT).show()
        }

        val floatBtn = createGradientButton(
            textStr = "LAUNCH FLOATING CONTROLLER",
            colors = intArrayOf(Color.parseColor("#059669"), Color.parseColor("#10B981"))
        ) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay Permission for ZU Smart", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startFloatingWidget()
                Toast.makeText(this, "ZU Smart Floating Controller Active", Toast.LENGTH_SHORT).show()
            }
        }

        val openPorterBtn = createGradientButton(
            textStr = "🚀 LAUNCH PORTER DRIVER APP",
            colors = intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#DB2777"))
        ) {
            val intent = packageManager.getLaunchIntentForPackage("com.theporter.android.driverapp")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Porter Driver App not installed on this device", Toast.LENGTH_SHORT).show()
            }
        }

        val accBtn = createGradientButton(
            textStr = "ACCESSIBILITY ENGINE SETTINGS",
            colors = intArrayOf(Color.parseColor("#D97706"), Color.parseColor("#F59E0B"))
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        container.addView(saveBtn)
        container.addView(floatBtn)
        container.addView(openPorterBtn)
        container.addView(accBtn)

        root.addView(container)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateServiceBadge()
    }

    private fun updateServiceBadge() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isAccActive = enabledServices.contains(packageName, ignoreCase = true)
        val isOverlayOk = Settings.canDrawOverlays(this)

        if (isAccActive && isOverlayOk) {
            statusBadge.text = "● ENGINE READY & ARMED"
            statusBadge.setTextColor(Color.parseColor("#10B981"))
            statusBadge.background = GradientDrawable().apply {
                setColor(Color.parseColor("#064E3B"))
                cornerRadius = 50f
                setStroke(2, Color.parseColor("#10B981"))
            }
        } else {
            statusBadge.text = "● PERMISSION REQUIRED (TAP BELOW)"
            statusBadge.setTextColor(Color.parseColor("#FBBF24"))
            statusBadge.background = GradientDrawable().apply {
                setColor(Color.parseColor("#451A03"))
                cornerRadius = 50f
                setStroke(2, Color.parseColor("#F59E0B"))
            }
        }
    }

    private fun createSectionCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 45, 45, 45)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111827"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#1F2937"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 35
            }
        }
    }

    private fun createSwitchRow(
        title: String,
        subtitle: String,
        accentColor: String,
        initialState: Boolean
    ): Pair<LinearLayout, Switch> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSub = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 4, 0, 0)
        }

        textCol.addView(tvTitle)
        textCol.addView(tvSub)

        val toggle = Switch(this).apply {
            isChecked = initialState
            thumbTintList = ColorStateList.valueOf(Color.parseColor(accentColor))
            trackTintList = ColorStateList.valueOf(Color.parseColor("#334155"))
        }

        row.addView(textCol)
        row.addView(toggle)
        return Pair(row, toggle)
    }

    private fun createStyledInput(
        hintText: String,
        initialValue: String,
        isNumber: Boolean,
        strokeHex: String
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#475569"))
            setTextColor(Color.WHITE)
            textSize = if (isNumber) 20f else 15f
            typeface = if (isNumber) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            inputType = if (isNumber) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setText(initialValue)
            setPadding(35, 28, 35, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#060913"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor(strokeHex))
            }
        }
    }

    private fun createGradientButton(
        textStr: String,
        colors: IntArray,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = textStr
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
                cornerRadius = 30f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32
            }
            setPadding(20, 36, 20, 36)
            setOnClickListener { onClick() }
        }
    }

    private fun startFloatingWidget() {
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
