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
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 80)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "⚡ PORTER TZ VIP v3.0"
            textSize = 26f
            setTextColor(Color.parseColor("#FACC15"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Smart Pickup/Drop • 3-Speed Engine • Anti-Ghost Swipe"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 45)
        }

        // Main Settings Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 45, 45, 45)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 36f
                setStroke(2, Color.parseColor("#334155"))
            }
        }

        // 1. Minimum Fare + ON/OFF CheckBox
        val fareCheckBox = CheckBox(this).apply {
            text = " Enable Minimum Fare Filter (किराया फ़िल्टर ऑन/ऑफ)"
            setTextColor(Color.parseColor("#FACC15"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isChecked = prefs.getBoolean("USE_FARE_FILTER", true)
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#FACC15"))
        }

        val minFareInput = EditText(this).apply {
            hint = "Minimum Fare (जैसे: 150)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("MIN_FARE", 150).toString())
            isEnabled = fareCheckBox.isChecked
            alpha = if (fareCheckBox.isChecked) 1.0f else 0.4f
            setPadding(30, 25, 30, 25)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#3B82F6"))
            }
        }

        fareCheckBox.setOnCheckedChangeListener { _, isChecked ->
            minFareInput.isEnabled = isChecked
            minFareInput.alpha = if (isChecked) 1.0f else 0.4f
        }

        // 2. Pickup Location Filter
        val pickupCheckBox = CheckBox(this).apply {
            text = " Filter by Pickup Location (सिर्फ पिकअप एरिया चेक करे)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isChecked = prefs.getBoolean("USE_PICKUP_FILTER", false)
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            setPadding(0, 25, 0, 5)
        }

        val pickupInput = EditText(this).apply {
            hint = "Pickup Area (जैसे: Koramangala)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setText(prefs.getString("PICKUP_FILTER", "") ?: "")
            isEnabled = pickupCheckBox.isChecked
            alpha = if (pickupCheckBox.isChecked) 1.0f else 0.4f
            setPadding(30, 22, 30, 22)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#0284C7"))
            }
        }

        pickupCheckBox.setOnCheckedChangeListener { _, isChecked ->
            pickupInput.isEnabled = isChecked
            pickupInput.alpha = if (isChecked) 1.0f else 0.4f
        }

        // 3. Drop Location Filter
        val dropCheckBox = CheckBox(this).apply {
            text = " Filter by Drop Location (सिर्फ ड्रॉप एरिया चेक करे)"
            setTextColor(Color.parseColor("#F43F5E"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isChecked = prefs.getBoolean("USE_DROP_FILTER", false)
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#F43F5E"))
            setPadding(0, 25, 0, 5)
        }

        val dropInput = EditText(this).apply {
            hint = "Drop Area (जैसे: Krishnarajapuram)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setText(prefs.getString("DROP_FILTER", "") ?: "")
            isEnabled = dropCheckBox.isChecked
            alpha = if (dropCheckBox.isChecked) 1.0f else 0.4f
            setPadding(30, 22, 30, 22)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#E11D48"))
            }
        }

        dropCheckBox.setOnCheckedChangeListener { _, isChecked ->
            dropInput.isEnabled = isChecked
            dropInput.alpha = if (isChecked) 1.0f else 0.4f
        }

        // 4. Three Speed Mode Selector
        val speedTitle = TextView(this).apply {
            text = "⚡ Select Swipe Speed Mode (स्वाइप स्पीड चुनें):"
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 35, 0, 15)
        }

        val speedGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val rbQuick = RadioButton(this).apply {
            id = 101
            text = "⚡ Quick Speed (150ms - Super Fast)"
            setTextColor(Color.WHITE)
            textSize = 14f
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
        }

        val rbNormal = RadioButton(this).apply {
            id = 102
            text = "🚀 Normal Speed (320ms - Recommended)"
            setTextColor(Color.WHITE)
            textSize = 14f
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
        }

        val rbHuman = RadioButton(this).apply {
            id = 103
            text = "🧑 Human Speed (550ms - Natural Curve)"
            setTextColor(Color.WHITE)
            textSize = 14f
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
        }

        speedGroup.addView(rbQuick)
        speedGroup.addView(rbNormal)
        speedGroup.addView(rbHuman)

        when (prefs.getString("SWIPE_SPEED", "NORMAL")) {
            "QUICK" -> speedGroup.check(101)
            "HUMAN" -> speedGroup.check(103)
            else -> speedGroup.check(102)
        }

        // Save Button
        val saveBtn = createStyledButton("💾  SAVE ALL VIP SETTINGS", "#2563EB") {
            val fare = minFareInput.text.toString().toIntOrNull() ?: 150
            val pickup = pickupInput.text.toString().trim()
            val drop = dropInput.text.toString().trim()
            val speedMode = when (speedGroup.checkedRadioButtonId) {
                101 -> "QUICK"
                103 -> "HUMAN"
                else -> "NORMAL"
            }

            prefs.edit()
                .putBoolean("USE_FARE_FILTER", fareCheckBox.isChecked)
                .putInt("MIN_FARE", fare)
                .putBoolean("USE_PICKUP_FILTER", pickupCheckBox.isChecked)
                .putString("PICKUP_FILTER", pickup)
                .putBoolean("USE_DROP_FILTER", dropCheckBox.isChecked)
                .putString("DROP_FILTER", drop)
                .putString("SWIPE_SPEED", speedMode)
                .apply()

            // Refresh Floating Widget immediately if running
            if (Settings.canDrawOverlays(this)) {
                startFloatingWidget()
            }

            Toast.makeText(this, "✅ Settings Saved! Speed: $speedMode", Toast.LENGTH_SHORT).show()
        }

        card.addView(fareCheckBox)
        card.addView(minFareInput)
        card.addView(pickupCheckBox)
        card.addView(pickupInput)
        card.addView(dropCheckBox)
        card.addView(dropInput)
        card.addView(speedTitle)
        card.addView(speedGroup)
        card.addView(saveBtn)

        val floatBtn = createStyledButton("🎯  SHOW FLOATING CONTROL BAR", "#10B981") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "कृपया 'Display over other apps' परमिशन ऑन करें", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startFloatingWidget()
                Toast.makeText(this, "⚡ Floating Bar चालू हो गया!", Toast.LENGTH_SHORT).show()
            }
        }

        val accBtn = createStyledButton("⚙️  OPEN ACCESSIBILITY SETTINGS", "#F59E0B") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(card)
        container.addView(floatBtn)
        container.addView(accBtn)

        root.addView(container)
        setContentView(root)
    }

    private fun startFloatingWidget() {
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun createStyledButton(textStr: String, hexColor: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = textStr
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(hexColor))
                cornerRadius = 24f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 35
            }
            layoutParams = params
            setPadding(20, 32, 20, 32)
            setOnClickListener { onClick() }
        }
    }
}
