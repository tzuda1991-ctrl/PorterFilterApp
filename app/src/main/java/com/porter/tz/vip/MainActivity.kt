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
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusBadge: TextView
    private var selectedSpeed = "NORMAL"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#090D16")

        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        selectedSpeed = prefs.getString("SWIPE_SPEED", "NORMAL") ?: "NORMAL"
        selectedCity = prefs.getString("SELECTED_CITY", "Bengaluru") ?: "Bengaluru"

        prefs.getString("PICKUP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedPickupSet.addAll(it)
        }
        prefs.getString("DROP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedDropSet.addAll(it)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#090D16"))
            setPadding(28, 20, 28, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ================= 1. COMPACT TOP BAR =================
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#111827"), Color.parseColor("#1E293B"))
            ).apply {
                cornerRadius = 26f
                setStroke(2, Color.parseColor("#334155"))
            }
        }

        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_vip_logo)
            layoutParams = LinearLayout.LayoutParams(60, 60).apply { rightMargin = 16 }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appTitle = TextView(this).apply {
            text = "ZU Smart"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val appSub = TextView(this).apply {
            text = "INTELLIGENT DISPATCH PRO"
            textSize = 9f
            setTextColor(Color.parseColor("#00F5D4"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }

        titleCol.addView(appTitle)
        titleCol.addView(appSub)

        statusBadge = TextView(this).apply {
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(20, 10, 20, 10)
        }

        topBar.addView(logoImg)
        topBar.addView(titleCol)
        topBar.addView(statusBadge)
        root.addView(topBar)

        // ================= 2. FARE & CITY ROW (SIDE-BY-SIDE) =================
        val fareCityCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(22, 16, 22, 16)
            background = createCardBg("#111827", "#1F2937")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 }
        }

        val fareBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 12
            }
        }

        val fareToggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fareLabel = TextView(this).apply {
            text = "Min Fare (₹)"
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val fareSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_FARE_FILTER", true)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#00F5D4"))
        }

        fareToggleRow.addView(fareLabel)
        fareToggleRow.addView(fareSwitch)

        val minFareInput = EditText(this).apply {
            hint = "200"
            setHintTextColor(Color.parseColor("#475569"))
            setTextColor(Color.parseColor("#00F5D4"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("MIN_FARE", 200).toString())
            setPadding(22, 12, 22, 12)
            background = createCardBg("#090D16", "#00F5D4", 16f)
        }

        fun updateFareInputState(active: Boolean) {
            minFareInput.isEnabled = active
            minFareInput.alpha = if (active) 1f else 0.35f
        }
        updateFareInputState(fareSwitch.isChecked)
        fareSwitch.setOnCheckedChangeListener { _, isChecked -> updateFareInputState(isChecked) }

        fareBox.addView(fareToggleRow)
        fareBox.addView(minFareInput)

        val cityBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 12
            }
        }

        val cityLabel = TextView(this).apply {
            text = "Operating City"
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 8)
        }

        val citySelectBtn = TextView(this).apply {
            text = "📍 $selectedCity ▼"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(22, 18, 22, 18)
            background = createCardBg("#090D16", "#38BDF8", 16f)
        }

        cityBox.addView(cityLabel)
        cityBox.addView(citySelectBtn)

        fareCityCard.addView(fareBox)
        fareCityCard.addView(cityBox)
        root.addView(fareCityCard)

        // ================= 3. PICKUP & DROP ZONE DROPDOWN CARD =================
        val zoneCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 16, 22, 16)
            background = createCardBg("#111827", "#1F2937")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 }
        }

        val pickupHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pickupTitle = TextView(this).apply {
            text = "🟢 Pickup Zone Filter (Header + Full Address)"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pickupSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_PICKUP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
        }
        pickupHeader.addView(pickupTitle)
        pickupHeader.addView(pickupSwitch)

        val pickupSelectorBtn = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(22, 16, 22, 16)
            background = createCardBg("#090D16", "#38BDF8", 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6; bottomMargin = 12 }
        }

        val dropHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dropTitle = TextView(this).apply {
            text = "🔴 Drop Zone Filter (Header + Full Address)"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val dropSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_DROP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#F43F5E"))
        }
        dropHeader.addView(dropTitle)
        dropHeader.addView(dropSwitch)

        val dropSelectorBtn = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(22, 16, 22, 16)
            background = createCardBg("#090D16", "#F43F5E", 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6 }
        }

        fun refreshZoneTexts() {
            pickupSelectorBtn.text = if (selectedPickupSet.isEmpty()) {
                "Tap to select $selectedCity Pickup Areas ▼"
            } else {
                "☑️ (${selectedPickupSet.size}) ${selectedPickupSet.joinToString(", ")} ▼"
            }
            pickupSelectorBtn.alpha = if (pickupSwitch.isChecked) 1f else 0.4f

            dropSelectorBtn.text = if (selectedDropSet.isEmpty()) {
                "Tap to select $selectedCity Drop Areas ▼"
            } else {
                "☑️ (${selectedDropSet.size}) ${selectedDropSet.joinToString(", ")} ▼"
            }
            dropSelectorBtn.alpha = if (dropSwitch.isChecked) 1f else 0.4f
        }

        refreshZoneTexts()

        pickupSwitch.setOnCheckedChangeListener { _, _ -> refreshZoneTexts() }
        dropSwitch.setOnCheckedChangeListener { _, _ -> refreshZoneTexts() }

        pickupSelectorBtn.setOnClickListener {
            pickupSwitch.isChecked = true
            showAreaMultiSelectDialog("Select Pickup Areas ($selectedCity)", selectedPickupSet) {
                refreshZoneTexts()
            }
        }

        dropSelectorBtn.setOnClickListener {
            dropSwitch.isChecked = true
            showAreaMultiSelectDialog("Select Drop Areas ($selectedCity)", selectedDropSet) {
                refreshZoneTexts()
            }
        }

        citySelectBtn.setOnClickListener {
            val cities = cityAreasMap.keys.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Operating City")
                .setItems(cities) { _, which ->
                    val newCity = cities[which]
                    if (newCity != selectedCity) {
                        selectedCity = newCity
                        selectedPickupSet.clear()
                        selectedDropSet.clear()
                        citySelectBtn.text = "📍 $selectedCity ▼"
                        refreshZoneTexts()
                    }
                }
                .show()
        }

        zoneCard.addView(pickupHeader)
        zoneCard.addView(pickupSelectorBtn)
        zoneCard.addView(dropHeader)
        zoneCard.addView(dropSelectorBtn)
        root.addView(zoneCard)

        // ================= 4. COMPACT SWIPE SPEED BAR =================
        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 16, 22, 16)
            background = createCardBg("#111827", "#1F2937")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 }
        }

        val speedTitle = TextView(this).apply {
            text = "SWIPE VELOCITY ENGINE"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 10)
        }
        speedCard.addView(speedTitle)

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        val speedBoxes = mutableMapOf<String, TextView>()

        fun updateSpeedSelection() {
            for ((key, view) in speedBoxes) {
                val active = (key == selectedSpeed)
                view.background = createCardBg(
                    if (active) "#1E293B" else "#090D16",
                    if (active) "#00F5D4" else "#1F2937",
                    18f
                )
                view.setTextColor(if (active) Color.parseColor("#00F5D4") else Color.WHITE)
            }
        }

        fun makeSpeedPill(key: String, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(10, 18, 10, 18)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (key != "HUMAN") rightMargin = 10
                }
                setOnClickListener {
                    selectedSpeed = key
                    updateSpeedSelection()
                }
            }
        }

        val pillQuick = makeSpeedPill("QUICK", "⚡ Quick\n150ms")
        val pillNormal = makeSpeedPill("NORMAL", "🚀 Normal\n320ms")
        val pillHuman = makeSpeedPill("HUMAN", "🧑 Human\n550ms")

        speedBoxes["QUICK"] = pillQuick
        speedBoxes["NORMAL"] = pillNormal
        speedBoxes["HUMAN"] = pillHuman

        speedRow.addView(pillQuick)
        speedRow.addView(pillNormal)
        speedRow.addView(pillHuman)
        updateSpeedSelection()

        speedCard.addView(speedRow)
        root.addView(speedCard)

        // ================= 5. 2x2 COMPACT ACTION BUTTON GRID =================
        val gridRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        val saveBtn = createCompactActionBtn("💾 SAVE SETTINGS", "#2563EB", true) {
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
            Toast.makeText(this, "✅ ZU Smart Settings Saved!", Toast.LENGTH_SHORT).show()
        }

        val floatBtn = createCompactActionBtn("🎯 FLOATING BAR", "#059669", false) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant Overlay Permission for ZU Smart", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startFloatingWidget()
                Toast.makeText(this, "⚡ Floating Bar Active", Toast.LENGTH_SHORT).show()
            }
        }

        gridRow1.addView(saveBtn)
        gridRow1.addView(floatBtn)

        val gridRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }

        val porterBtn = createCompactActionBtn("🚀 OPEN PORTER", "#7C3AED", true) {
            launchPorterPartnerApp(this)
        }

        val accBtn = createCompactActionBtn("⚙️ ACCESSIBILITY", "#D97706", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        gridRow2.addView(porterBtn)
        gridRow2.addView(accBtn)

        root.addView(gridRow1)
        root.addView(gridRow2)

        setContentView(root)
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
            .setPositiveButton("Done") { _, _ -> onUpdated() }
            .setNeutralButton("+ Add Custom") { _, _ ->
                val input = EditText(this).apply {
                    hint = "Enter Area Name"
                }
                AlertDialog.Builder(this)
                    .setTitle("Add Custom Area")
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
            statusBadge.text = "● READY"
            statusBadge.setTextColor(Color.parseColor("#10B981"))
            statusBadge.background = createCardBg("#064E3B", "#10B981", 40f)
        } else {
            statusBadge.text = "● SETUP NEEDED"
            statusBadge.setTextColor(Color.parseColor("#FBBF24"))
            statusBadge.background = createCardBg("#451A03", "#F59E0B", 40f)
        }
    }

    private fun createCardBg(fillHex: String, strokeHex: String, radius: Float = 24f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = radius
            setStroke(2, Color.parseColor(strokeHex))
        }
    }

    private fun createCompactActionBtn(
        label: String,
        colorHex: String,
        isLeft: Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (isLeft) rightMargin = 8 else leftMargin = 8
            }
            setPadding(10, 22, 10, 22)
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
