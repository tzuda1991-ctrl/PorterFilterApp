package com.porter.tz.vip

import android.app.AlertDialog
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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusBadge: TextView
    private lateinit var masterStatusText: TextView
    private lateinit var masterToggleSwitch: Switch
    private lateinit var minFareInput: EditText

    private var selectedSpeed = "QUICK"
    private var selectedCity = "Bengaluru"

    private val selectedPickupSet = mutableSetOf<String>()
    private val selectedDropSet = mutableSetOf<String>()

    private val cityAreasMap = linkedMapOf(
        "Bengaluru" to arrayOf(
            "Krishnarajapuram", "Hoodi", "Yelahanka", "Hebbal", "Electronic City",
            "Whitefield", "Koramangala", "Indiranagar", "Marathahalli", "HSR Layout",
            "BTM Layout", "Jayanagar", "JP Nagar", "Peenya", "Yeshwanthpur",
            "Bommasandra", "Sarjapur", "Banashankari", "Rajajinagar", "Malleshwaram",
            "Bellandur", "Devanahalli", "Hoskote", "Kengeri", "Mahadevapura"
        ),
        "Mumbai" to arrayOf(
            "Andheri", "Bandra", "Borivali", "Bhiwandi", "Goregaon", "Malad",
            "Kandivali", "Powai", "Kurla", "Ghatkopar", "Mulund", "Thane",
            "Airoli", "Vashi", "Turbhe", "Panvel", "Kalyan", "Dombivli",
            "Vasai", "Virar", "Dadar", "Worli", "Lower Parel", "Chembur", "Santacruz"
        ),
        "Delhi NCR" to arrayOf(
            "Okhla", "Bawana", "Narela", "Mayapuri", "Naraina", "Dwarka",
            "Rohini", "Janakpuri", "Karol Bagh", "Lajpat Nagar", "Saket",
            "Noida", "Greater Noida", "Gurugram", "Manesar", "Faridabad",
            "Ghaziabad", "Indirapuram", "Kundli", "Connaught Place"
        ),
        "Hyderabad" to arrayOf(
            "Gachibowli", "Madhapur", "HITEC City", "Kukatpally", "Balanagar",
            "Jeedimetla", "Uppal", "LB Nagar", "Secunderabad", "Banjara Hills",
            "Jubilee Hills", "Miyapur", "Patancheru", "Shamshabad", "Kompally", "Attapur"
        ),
        "Chennai" to arrayOf(
            "Guindy", "Ambattur", "Sriperumbudur", "Oragadam", "Anna Nagar",
            "T Nagar", "Velachery", "Tambaram", "Porur", "OMR", "Perungudi",
            "Poonamallee", "Madhavaram", "Chromepet", "Adyar"
        ),
        "Pune" to arrayOf(
            "Chakan", "Pimpri", "Chinchwad", "Bhosari", "Hinjewadi", "Hadapsar",
            "Kharadi", "Wagholi", "Wakad", "Baner", "Kothrud", "Viman Nagar",
            "Katraj", "Talegaon", "Ranjangaon"
        )
    )

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060A12")
        window.navigationBarColor = Color.parseColor("#060A12")

        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        selectedSpeed = prefs.getString("SWIPE_SPEED", "QUICK") ?: "QUICK"
        selectedCity = prefs.getString("SELECTED_CITY", "Bengaluru") ?: "Bengaluru"

        prefs.getString("PICKUP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedPickupSet.addAll(it)
        }
        prefs.getString("DROP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedDropSet.addAll(it)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#060A12"),
                    Color.parseColor("#0B1325"),
                    Color.parseColor("#070B14")
                )
            )
            setPadding(dp(16), dp(12), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ================= 1. FIXED HEADER CARD (No text cut) =================
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#162038"), Color.parseColor("#0F172A"))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#2DD4BF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.15f
            ).apply { bottomMargin = dp(10) }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_vip_logo)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) }
        }

        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val brandName = TextView(this).apply {
            text = "ZU Smart"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.04f
        }

        val brandTag = TextView(this).apply {
            text = "AUTONOMOUS DISPATCH SYSTEM"
            textSize = 8.5f
            setTextColor(Color.parseColor("#2DD4BF"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }

        brandCol.addView(brandName)
        brandCol.addView(brandTag)

        statusBadge = TextView(this).apply {
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }

        topRow.addView(logoImg)
        topRow.addView(brandCol)
        topRow.addView(statusBadge)

        val headerDivider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        }

        val masterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        masterStatusText = TextView(this).apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        masterToggleSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("BOT_ACTIVE", true)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            trackTintList = ColorStateList.valueOf(Color.parseColor("#334155"))
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("BOT_ACTIVE", isChecked).apply()
                refreshMasterEngineBar(isChecked)
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startFloatingWidget()
                }
            }
        }

        refreshMasterEngineBar(masterToggleSwitch.isChecked)

        masterRow.addView(masterStatusText)
        masterRow.addView(masterToggleSwitch)

        headerCard.addView(topRow)
        headerCard.addView(headerDivider)
        headerCard.addView(masterRow)
        root.addView(headerCard)

        // ================= 2. FARE STEPPER & CITY HUB =================
        val midRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.2f
            ).apply { bottomMargin = dp(10) }
        }

        val fareCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createGlassCard("#111A2E", "#1E293B", 18)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f).apply {
                rightMargin = dp(6)
            }
        }

        val fareHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val fareTitle = TextView(this).apply {
            text = "MIN FARE (₹)"
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val fareSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_FARE_FILTER", true)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#2DD4BF"))
        }

        fareHead.addView(fareTitle)
        fareHead.addView(fareSwitch)

        val stepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val minusBtn = TextView(this).apply {
            text = "−"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createGlassCard("#1E293B", "#334155", 10)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(38))
            setOnClickListener {
                val cur = minFareInput.text.toString().toIntOrNull() ?: 200
                val next = (cur - 20).coerceAtLeast(50)
                minFareInput.setText(next.toString())
            }
        }

        minFareInput = EditText(this).apply {
            setText(prefs.getInt("MIN_FARE", 200).toString())
            setTextColor(Color.parseColor("#2DD4BF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(dp(2), dp(4), dp(2), dp(4))
            background = createGlassCard("#070B14", "#2DD4BF", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        }

        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createGlassCard("#1E293B", "#334155", 10)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(38))
            setOnClickListener {
                val cur = minFareInput.text.toString().toIntOrNull() ?: 200
                val next = cur + 20
                minFareInput.setText(next.toString())
            }
        }

        stepperRow.addView(minusBtn)
        stepperRow.addView(minFareInput)
        stepperRow.addView(plusBtn)

        fun syncFareUI(enabled: Boolean) {
            minFareInput.isEnabled = enabled
            minusBtn.isEnabled = enabled
            plusBtn.isEnabled = enabled
            stepperRow.alpha = if (enabled) 1f else 0.35f
        }
        syncFareUI(fareSwitch.isChecked)
        fareSwitch.setOnCheckedChangeListener { _, isChecked -> syncFareUI(isChecked) }

        fareCard.addView(fareHead)
        fareCard.addView(stepperRow)

        val cityCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createGlassCard("#111A2E", "#1E293B", 18)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.9f).apply {
                leftMargin = dp(6)
            }
        }

        val cityHead = TextView(this).apply {
            text = "OPERATING REGION"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(0, 0, 0, dp(8))
        }

        val citySelectBtn = TextView(this).apply {
            text = "$selectedCity  ▾"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createGlassCard("#070B14", "#38BDF8", 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            )
        }

        cityCard.addView(cityHead)
        cityCard.addView(citySelectBtn)

        midRow.addView(fareCard)
        midRow.addView(cityCard)
        root.addView(midRow)

        // ================= 3. ROUTE INTELLIGENCE MATRIX =================
        val routeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = createGlassCard("#111A2E", "#1E293B", 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                2.2f
            ).apply { bottomMargin = dp(10) }
        }

        val routeCardHeader = TextView(this).apply {
            text = "GEOFENCE ROUTE FILTERS (ZONE PRIORITY OVERRIDE)"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(0, 0, 0, dp(6))
        }
        routeCard.addView(routeCardHeader)

        // Pickup Block
        val pickupTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pickupBadge = TextView(this).apply {
            text = "PICKUP"
            textSize = 9f
            setTextColor(Color.parseColor("#060A12"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#38BDF8"))
                cornerRadius = dp(6).toFloat()
            }
        }

        val pickupLabel = TextView(this).apply {
            text = "  Origin Zone Match"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pickupSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_PICKUP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
        }

        pickupTopRow.addView(pickupBadge)
        pickupTopRow.addView(pickupLabel)
        pickupTopRow.addView(pickupSwitch)

        val pickupDropdown = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = createGlassCard("#070B14", "#38BDF8", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(8) }
        }

        // Drop Block
        val dropTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dropBadge = TextView(this).apply {
            text = "DROP"
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(2), dp(10), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F43F5E"))
                cornerRadius = dp(6).toFloat()
            }
        }

        val dropLabel = TextView(this).apply {
            text = "  Destination Zone Match"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val dropSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_DROP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#F43F5E"))
        }

        dropTopRow.addView(dropBadge)
        dropTopRow.addView(dropLabel)
        dropTopRow.addView(dropSwitch)

        val dropDropdown = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = createGlassCard("#070B14", "#F43F5E", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }

        fun updateZoneButtons() {
            pickupDropdown.text = if (selectedPickupSet.isEmpty()) {
                "Select $selectedCity Pickup Zones   ▾"
            } else {
                "[ ${selectedPickupSet.size} Zones ]  ${selectedPickupSet.joinToString(", ")}   ▾"
            }
            pickupDropdown.alpha = if (pickupSwitch.isChecked) 1f else 0.38f

            dropDropdown.text = if (selectedDropSet.isEmpty()) {
                "Select $selectedCity Drop Zones   ▾"
            } else {
                "[ ${selectedDropSet.size} Zones ]  ${selectedDropSet.joinToString(", ")}   ▾"
            }
            dropDropdown.alpha = if (dropSwitch.isChecked) 1f else 0.38f
        }

        updateZoneButtons()
        pickupSwitch.setOnCheckedChangeListener { _, _ -> updateZoneButtons() }
        dropSwitch.setOnCheckedChangeListener { _, _ -> updateZoneButtons() }

        pickupDropdown.setOnClickListener {
            pickupSwitch.isChecked = true
            showAreaMultiSelectDialog("Select Pickup Zones ($selectedCity)", selectedPickupSet) {
                updateZoneButtons()
            }
        }

        dropDropdown.setOnClickListener {
            dropSwitch.isChecked = true
            showAreaMultiSelectDialog("Select Drop Zones ($selectedCity)", selectedDropSet) {
                updateZoneButtons()
            }
        }

        citySelectBtn.setOnClickListener {
            val cities = cityAreasMap.keys.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Operating City")
                .setItems(cities) { _, which ->
                    val chosen = cities[which]
                    if (chosen != selectedCity) {
                        selectedCity = chosen
                        selectedPickupSet.clear()
                        selectedDropSet.clear()
                        citySelectBtn.text = "$selectedCity  ▾"
                        updateZoneButtons()
                    }
                }
                .show()
        }

        routeCard.addView(pickupTopRow)
        routeCard.addView(pickupDropdown)
        routeCard.addView(dropTopRow)
        routeCard.addView(dropDropdown)
        root.addView(routeCard)

        // ================= 4. SWIPE VELOCITY SELECTOR =================
        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = createGlassCard("#111A2E", "#1E293B", 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.15f
            ).apply { bottomMargin = dp(10) }
        }

        val speedHead = TextView(this).apply {
            text = "SWIPE RESPONSE VELOCITY"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(0, 0, 0, dp(6))
        }
        speedCard.addView(speedHead)

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val speedViews = mutableMapOf<String, LinearLayout>()

        fun refreshVelocityCards() {
            for ((k, box) in speedViews) {
                val active = (k == selectedSpeed)
                box.background = if (active) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#0F2942"), Color.parseColor("#064E3B"))
                    ).apply {
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(2), Color.parseColor("#2DD4BF"))
                    }
                } else {
                    createGlassCard("#070B14", "#1E293B", 12)
                }
            }
        }

        fun buildVelocityTile(key: String, title: String, sub: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (key != "HUMAN") rightMargin = dp(6)
                }
                val t1 = TextView(context).apply {
                    text = title
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                val t2 = TextView(context).apply {
                    text = sub
                    textSize = 10f
                    setTextColor(Color.parseColor("#2DD4BF"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                addView(t1)
                addView(t2)
                setOnClickListener {
                    selectedSpeed = key
                    refreshVelocityCards()
                }
            }
        }

        val tileQuick = buildVelocityTile("QUICK", "TURBO", "150 ms")
        val tileNormal = buildVelocityTile("NORMAL", "BALANCED", "320 ms")
        val tileHuman = buildVelocityTile("NATURAL", "NATURAL", "550 ms")

        speedViews["QUICK"] = tileQuick
        speedViews["NORMAL"] = tileNormal
        speedViews["NATURAL"] = tileHuman

        speedRow.addView(tileQuick)
        speedRow.addView(tileNormal)
        speedRow.addView(tileHuman)
        refreshVelocityCards()

        speedCard.addView(speedRow)
        root.addView(speedCard)

        // ================= 5. BOTTOM COMMAND DOCK =================
        val bottomDock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.35f
            )
        }

        val dockRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { bottomMargin = dp(6) }
        }

        val saveBtn = createDockButton(
            title = "SAVE PROFILE",
            sub = "Apply Active Filters",
            c1 = "#2563EB",
            c2 = "#06B6D4",
            isLeft = true
        ) {
            val fare = minFareInput.text.toString().toIntOrNull() ?: 200
            prefs.edit()
                .putBoolean("USE_FARE_FILTER", fareSwitch.isChecked)
                .putInt("MIN_FARE", fare)
                .putString("SELECTED_CITY", selectedCity)
                .putBoolean("USE_PICKUP_FILTER", pickupSwitch.isChecked)
                .putString("PICKUP_FILTER", selectedPickupSet.joinToString(","))
                .putBoolean("USE_DROP_FILTER", dropSwitch.isChecked)
                .putString("DROP_FILTER", selectedDropSet.joinToString(","))
                .putString("SWIPE_SPEED", selectedSpeed)
                .apply()

            if (Settings.canDrawOverlays(this)) {
                startFloatingWidget()
            }
            Toast.makeText(this, "ZU Smart Profile Saved ($selectedSpeed)", Toast.LENGTH_SHORT).show()
        }

        val floatBtn = createDockButton(
            title = "FLOATING HUD",
            sub = "Launch Overlay Bar",
            c1 = "#059669",
            c2 = "#10B981",
            isLeft = false
        ) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant Overlay Permission for ZU Smart", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startFloatingWidget()
                Toast.makeText(this, "ZU Smart HUD Overlay Active", Toast.LENGTH_SHORT).show()
            }
        }

        dockRow1.addView(saveBtn)
        dockRow1.addView(floatBtn)

        val dockRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val porterBtn = createDockButton(
            title = "LAUNCH PORTER",
            sub = "Open Partner App",
            c1 = "#6D28D9",
            c2 = "#9333EA",
            isLeft = true
        ) {
            launchPorterPartnerApp(this)
        }

        val accBtn = createDockButton(
            title = "ACCESSIBILITY",
            sub = "System Engine Setup",
            c1 = "#B45309",
            c2 = "#D97706",
            isLeft = false
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        dockRow2.addView(porterBtn)
        dockRow2.addView(accBtn)

        bottomDock.addView(dockRow1)
        bottomDock.addView(dockRow2)
        root.addView(bottomDock)

        setContentView(root)
    }

    private fun refreshMasterEngineBar(isActive: Boolean) {
        if (isActive) {
            masterStatusText.text = "●  AUTO-SWIPE DISPATCHER: ARMED"
            masterStatusText.setTextColor(Color.parseColor("#2DD4BF"))
        } else {
            masterStatusText.text = "○  AUTO-SWIPE DISPATCHER: STANDBY"
            masterStatusText.setTextColor(Color.parseColor("#F87171"))
        }
    }

    private fun createDockButton(
        title: String,
        sub: String,
        c1: String,
        c2: String,
        isLeft: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#40FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (isLeft) rightMargin = dp(4) else leftMargin = dp(4)
            }
            val tvMain = TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.04f
                gravity = Gravity.CENTER
            }
            val tvSub = TextView(context).apply {
                text = sub
                textSize = 9.5f
                setTextColor(Color.parseColor("#E2E8F0"))
                gravity = Gravity.CENTER
            }
            addView(tvMain)
            addView(tvSub)
            setOnClickListener { onClick() }
        }
    }

    private fun showAreaMultiSelectDialog(
        title: String,
        targetSet: MutableSet<String>,
        onUpdated: () -> Unit
    ) {
        val baseAreas = cityAreasMap[selectedCity] ?: emptyArray()
        val combinedList = (baseAreas.toList() + targetSet).distinct().toTypedArray()
        val checkedItems = BooleanArray(combinedList.size) { idx ->
            targetSet.contains(combinedList[idx])
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(combinedList, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    targetSet.add(combinedList[which])
                } else {
                    targetSet.remove(combinedList[which])
                }
            }
            .setPositiveButton("Apply") { _, _ -> onUpdated() }
            .setNeutralButton("+ Custom Area") { _, _ ->
                val input = EditText(this).apply {
                    hint = "Enter Area or Pincode"
                }
                AlertDialog.Builder(this)
                    .setTitle("Add Custom Zone")
                    .setView(input)
                    .setPositiveButton("Add") { _, _ ->
                        val custom = input.text.toString().trim()
                        if (custom.isNotEmpty()) {
                            targetSet.add(custom)
                            onUpdated()
                        }
                    }
                    .show()
            }
            .setNegativeButton("Clear All") { _, _ ->
                targetSet.clear()
                onUpdated()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        val active = prefs.getBoolean("BOT_ACTIVE", true)
        if (::masterToggleSwitch.isInitialized) {
            masterToggleSwitch.isChecked = active
            refreshMasterEngineBar(active)
        }
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
            statusBadge.text = "● ONLINE"
            statusBadge.setTextColor(Color.parseColor("#10B981"))
            statusBadge.background = createGlassCard("#064E3B", "#10B981", 24)
        } else {
            statusBadge.text = "● PERMISSION"
            statusBadge.setTextColor(Color.parseColor("#FBBF24"))
            statusBadge.background = createGlassCard("#451A03", "#F59E0B", 24)
        }
    }

    private fun createGlassCard(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(strokeHex))
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

    companion object {
        fun launchPorterPartnerApp(context: Context) {
            val pm = context.packageManager
            val knownPackages = listOf(
                "com.theporter.android.driverapp",
                "com.theporter.android.partnerapp",
                "in.theporter.driverapp"
            )

            for (pkg in knownPackages) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return
                }
            }

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (info in apps) {
                val pkgName = info.activityInfo.packageName.lowercase()
                val appLabel = info.loadLabel(pm).toString().lowercase()
                if (pkgName != context.packageName.lowercase() &&
                    (pkgName.contains("porter") || appLabel.contains("porter"))
                ) {
                    val launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return
                    }
                }
            }

            Toast.makeText(context, "Porter Partner App not found!", Toast.LENGTH_SHORT).show()
        }
    }
}
