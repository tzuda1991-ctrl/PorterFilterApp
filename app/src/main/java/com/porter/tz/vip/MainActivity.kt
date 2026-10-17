package com.porter.tz.vip

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
            setPadding(60, 80, 60, 80)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // VIP Header
        val title = TextView(this).apply {
            text = "⚡ PORTER TZ VIP"
            textSize = 28f
            setTextColor(Color.parseColor("#FACC15"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Smart Auto-Slide • Target: com.theporter.android.driverapp"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 60)
        }

        // Card Layout for Filter Settings
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 36f
                setStroke(2, Color.parseColor("#334155"))
            }
        }

        val fareLabel = TextView(this).apply {
            text = "Minimum Order Fare (₹)"
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val minFareInput = EditText(this).apply {
            hint = "जैसे: 150"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("MIN_FARE", 150).toString())
            setPadding(30, 30, 30, 30)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#3B82F6"))
            }
        }

        val pickupLabel = TextView(this).apply {
            text = "Pickup Keyword (Optional - खाली छोड़ सकते हैं)"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 35, 0, 10)
        }

        val pickupInput = EditText(this).apply {
            hint = "जैसे: Koramangala (या खाली छोड़ें)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setText(prefs.getString("PICKUP_FILTER", "") ?: "")
            setPadding(30, 25, 30, 25)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 20f
            }
        }

        val saveBtn = createStyledButton("💾  SAVE VIP FILTER", "#2563EB") {
            val fare = minFareInput.text.toString().toIntOrNull() ?: 150
            val pickup = pickupInput.text.toString().trim()
            prefs.edit()
                .putInt("MIN_FARE", fare)
                .putString("PICKUP_FILTER", pickup)
                .apply()
            Toast.makeText(this, "✅ VIP Filter Saved: Min ₹$fare", Toast.LENGTH_SHORT).show()
        }

        card.addView(fareLabel)
        card.addView(minFareInput)
        card.addView(pickupLabel)
        card.addView(pickupInput)
        card.addView(saveBtn)

        // Floating Button Toggle Card
        val floatBtn = createStyledButton("🎯  START FLOATING ON/OFF ICON", "#10B981") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "कृपया 'Display over other apps' परमिशन ऑन करें", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, FloatingControlService::class.java))
                Toast.makeText(this, "⚡ Floating VIP Icon चालू हो गया!", Toast.LENGTH_SHORT).show()
            }
        }

        val stopFloatBtn = createStyledButton("❌  HIDE FLOATING ICON", "#EF4444") {
            stopService(Intent(this, FloatingControlService::class.java))
            Toast.makeText(this, "Floating Icon बंद कर दिया गया", Toast.LENGTH_SHORT).show()
        }

        val accBtn = createStyledButton("⚙️  ENABLE ACCESSIBILITY SERVICE", "#F59E0B") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val infoText = TextView(this).apply {
            text = "ℹ️ कैसे काम करता है:\n• यह सिर्फ ऊपर लिखे ₹ अमाउंट (जैसे ₹113) को पढ़ता है।\n• एड्रेस के नंबर (जैसे 985) या नीचे के बैनर (₹500) को अपने आप इग्नोर कर देता है।\n• स्क्रीन पर Floating बटन से आप कभी भी इसे 1 क्लिक में ON या OFF कर सकते हैं।"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(15, 50, 15, 20)
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(card)
        container.addView(floatBtn)
        container.addView(stopFloatBtn)
        container.addView(accBtn)
        container.addView(infoText)

        root.addView(container)
        setContentView(root)
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
            setPadding(20, 35, 20, 35)
            setOnClickListener { onClick() }
        }
    }
}
