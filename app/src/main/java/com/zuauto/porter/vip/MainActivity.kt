package com.zuauto.porter.vip

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var minFareInput: EditText
    private lateinit var fareSwitch: Switch
    private lateinit var pickupSwitch: Switch
    private lateinit var dropSwitch: Switch
    private lateinit var footerOnlineText: TextView
    private lateinit var footerSystemText: TextView
    private lateinit var userBox: TextView
    private lateinit var planBadge: TextView

    private var isUpdatingSwitches = false
    private var selectedSpeed = "QUICK"
    private var selectedCity = "Bengaluru"
    private val selectedPickupSet = mutableSetOf<String>()
    private val selectedDropSet = mutableSetOf<String>()

    private val cityAreasMap = linkedMapOf(
        "Bengaluru" to emptyArray<String>(),
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

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#10264D")
        window.navigationBarColor = Color.parseColor("#10264D")

        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        selectedSpeed = prefs.getString("SWIPE_SPEED", "QUICK") ?: "QUICK"
        selectedCity = prefs.getString("SELECTED_CITY", "Bengaluru") ?: "Bengaluru"

        prefs.getString("PICKUP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedPickupSet.addAll(it)
        }
        prefs.getString("DROP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedDropSet.addAll(it)
        }

        // Check if first-time user registration needed
        if (!ZuAutoCloudManager.isUserRegistered(this)) {
            showRegistrationDialog()
        }

        // Check remote in-app OTA version update
        ZuAutoCloudManager.checkRemoteAppVersion(this) { isForce, newVer, notes, dlUrl ->
            showUpdateDialog(isForce, newVer, notes, dlUrl)
        }

        val configuration = resources.configuration
        val compact = configuration.screenWidthDp < 480 || configuration.fontScale >= 1.3f
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT

        fun cardParams(weight: Float = 1f) = if (compact) {
            LinearLayout.LayoutParams(match, wrap)
        } else {
            LinearLayout.LayoutParams(0, wrap, weight)
        }

        // MASTER ROOT: Left Navigation Rail + Right Main Cockpit
        val masterRoot = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EEF5FD"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ================= LEFT NAVIGATION SIDEBAR =================
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F8FBFF"), Color.parseColor("#EAF2FC"))
            ).apply {
                setStroke(dp(1), Color.parseColor("#CBD9E9"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val menuIcon = TextView(this).apply {
            text = "☰"
            textSize = 18f
            setTextColor(Color.parseColor("#42536D"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }
        fun buildNavItem(iconStr: String, labelStr: String, isSelected: Boolean, onClick: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(8), dp(2), dp(8))
                if (isSelected) {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#DDEBFE"), Color.parseColor("#A9771D"))
                    ).apply {
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), Color.parseColor("#7DD3FC"))
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }

                val ic = TextView(context).apply {
                    text = iconStr
                    textSize = 14f
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#42536D"))
                    gravity = Gravity.CENTER
                }
                val lb = TextView(context).apply {
                    text = labelStr
                    textSize = 7.5f
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#42536D"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                addView(ic)
                addView(lb)
                setOnClickListener { onClick() }
            }
        }

        val navActions = linkedMapOf<String, () -> Unit>()
        fun addNavItem(icon: String, label: String, selected: Boolean, action: () -> Unit) {
            navActions[label] = action
            if (!compact) sidebar.addView(buildNavItem(icon, label, selected, action))
        }

        addNavItem("🏠", "Home", true) {}
        addNavItem("📦", "History", false) { showOrderHistoryDialog() }
        addNavItem("🏍️", "Porter", false) { launchPorterPartnerApp(this) }
        addNavItem("🔑", "License", false) { showLicenseKeyDialog() }
        addNavItem("⚙️", "Settings", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addNavItem("🔄", "Update", false) {
            Toast.makeText(this, "Checking for ZuAuto updates...", Toast.LENGTH_SHORT).show()
            ZuAutoCloudManager.checkRemoteAppVersion(this) { isForce, newVer, notes, dlUrl ->
                showUpdateDialog(isForce, newVer, notes, dlUrl)
            }
        }

        if (compact) {
            val menu = Button(this).apply {
                text = "☰ Menu"
                setOnClickListener {
                    PopupMenu(this@MainActivity, this).apply {
                        navActions.keys.forEach { this.menu.add(it) }
                        setOnMenuItemClickListener { item ->
                            navActions[item.title.toString()]?.invoke()
                            true
                        }
                        show()
                    }
                }
            }
            masterRoot.addView(menu, LinearLayout.LayoutParams(match, wrap))
        } else {
            sidebar.addView(menuIcon, 0)
            val navScroll = ScrollView(this).apply { addView(sidebar) }
            masterRoot.addView(navScroll, LinearLayout.LayoutParams(dp(64), match))
        }

        // ================= RIGHT MAIN CONTENT AREA =================
        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
            layoutParams = android.widget.FrameLayout.LayoutParams(match, wrap)
        }

        // 1. TOP BAR: Logo + ZuAuto + History + Driver Profile + VIP Plan Pill
        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_vip_logo)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(8) }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val zTitle = TextView(this).apply {
            text = "ZU Smart"
            textSize = 16f
        }
        val zSub = TextView(this).apply {
            text = "Driver dashboard"
            textSize = 6.8f
            setTextColor(Color.parseColor("#42536D"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        titleCol.addView(zTitle)
        titleCol.addView(zSub)

        val historyBtn = TextView(this).apply {
            text = "📦 Trips"
            textSize = 9f
            setTextColor(Color.parseColor("#10264D"))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#F7FAFF", "#A9771D", 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(6) }
            setOnClickListener { showOrderHistoryDialog() }
        }

        val savedDriverName = prefs.getString("USER_NAME", "Driver") ?: "Driver"
        userBox = TextView(this).apply {
            text = Html.fromHtml("<font color='#42536D'>Driver</font><br><b><font color='#10264D'>$savedDriverName ▾</font></b>", Html.FROM_HTML_MODE_LEGACY)
            textSize = 8f
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { showDriverProfileAndValidityDialog() }
        }

        val savedPlan = prefs.getString("USER_PLAN", "VIP")?.uppercase(Locale.US) ?: "VIP"
        planBadge = TextView(this).apply {
            text = "👑 $savedPlan"
            textSize = 8f
            setTextColor(Color.parseColor("#8A5D13"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = createNeonBox("#FFF2CF", "#F59E0B", 40)
            setOnClickListener {
                val active = !prefs.getBoolean("BOT_ACTIVE", true)
                prefs.edit().putBoolean("BOT_ACTIVE", active).apply()
                updateSystemStatusBar()
                if (Settings.canDrawOverlays(this@MainActivity)) startFloatingWidget()
            }
        }

        topHeader.addView(logoImg)
        topHeader.addView(titleCol)
        mainCol.addView(topHeader)
        val accountRow = if (compact) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(6))
                mainCol.addView(this, LinearLayout.LayoutParams(match, wrap))
            }
        } else {
            topHeader
        }
        accountRow.addView(historyBtn)
        userBox.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
        accountRow.addView(userBox)
        planBadge.maxWidth = dp(104)
        accountRow.addView(planBadge)

        val heroBanner = ImageView(this).apply {
            contentDescription = "Delivery scooter in front of Vidhana Soudha"
            setImageResource(R.drawable.sapphire_bengaluru)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = createNeonBox("#F7FAFF", "#C79838", 18)
            clipToOutline = true
        }
        mainCol.addView(heroBanner, LinearLayout.LayoutParams(match, dp(if (compact) 160 else 200)).apply {
            bottomMargin = dp(10)
        })
        val shortcutIndex = mainCol.childCount

        // 3. FARE MODE & ACTIVE CITY
        val fareCityRow = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val fareCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createNeonBox("#F7FAFF", "#A9771D", 16)
            layoutParams = cardParams().apply {
                if (compact) bottomMargin = dp(6) else marginEnd = dp(4)
            }
        }

        val fareTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val rupeeCircle = TextView(this).apply {
            text = "₹"
            textSize = 15f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createNeonBox("#DDEBFE", "#A9771D", 50)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) }
        }
        val fareTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val fTitle = TextView(this).apply {
            text = "MIN FARE (₹)"
            textSize = 9.5f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val fSub = TextView(this).apply {
            text = "Set threshold amount"
            textSize = 7.5f
            setTextColor(Color.parseColor("#42536D"))
        }
        fareTitleCol.addView(fTitle)
        fareTitleCol.addView(fSub)

        fareSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_FARE_FILTER", true)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#A9771D"))
        }
        fareTop.addView(rupeeCircle)
        fareTop.addView(fareTitleCol)
        fareTop.addView(fareSwitch)

        val stepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val minusBtn = TextView(this).apply {
            text = "−"
            textSize = 15f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createNeonBox("#E3EEFC", "#A9771D", 40)
            minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(dp(40), wrap)
            setOnClickListener {
                val c = minFareInput.text.toString().toIntOrNull() ?: 200
                minFareInput.setText((c - 20).coerceAtLeast(50).toString())
            }
        }
        minFareInput = EditText(this).apply {
            setText(prefs.getInt("MIN_FARE", 200).toString())
            setTextColor(Color.parseColor("#A9771D"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(0, 0, 0, 0)
            background = createNeonBox("#E8F1FE", "#A9771D", 10)
            minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        }
        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 15f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createNeonBox("#E3EEFC", "#A9771D", 40)
            minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(dp(40), wrap)
            setOnClickListener {
                val c = minFareInput.text.toString().toIntOrNull() ?: 200
                minFareInput.setText((c + 20).toString())
            }
        }
        stepperRow.addView(minusBtn)
        stepperRow.addView(minFareInput)
        stepperRow.addView(plusBtn)

        fareCard.addView(fareTop)
        fareCard.addView(stepperRow)

        val cityCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createNeonBox("#F7FAFF", "#C79838", 16)
            layoutParams = cardParams().apply {
                leftMargin = dp(4)
            }
        }
        val cityTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val pinCircle = TextView(this).apply {
            text = "📍"
            textSize = 13f
            gravity = Gravity.CENTER
            background = createNeonBox("#E3EEFC", "#C79838", 50)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) }
        }
        val cityTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val cTitle = TextView(this).apply {
            text = "ACTIVE CITY"
            textSize = 9.5f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val cSub = TextView(this).apply {
            text = "Filter geographic bounds"
            textSize = 7.5f
            setTextColor(Color.parseColor("#42536D"))
        }
        cityTitleCol.addView(cTitle)
        cityTitleCol.addView(cSub)
        cityTop.addView(pinCircle)
        cityTop.addView(cityTitleCol)

        val citySelectBtn = TextView(this).apply {
            text = "📍  $selectedCity   ▾"
            textSize = 11.5f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            background = createNeonBox("#E8F1FE", "#C79838", 10)
            minimumHeight = dp(32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        cityCard.addView(cityTop)
        cityCard.addView(citySelectBtn)

        fareCityRow.addView(fareCard)
        fareCityRow.addView(cityCard)
        mainCol.addView(fareCityRow)

        // 4. ROUTE ZONE FILTER + RADAR MAP
        val routeMasterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createNeonBox("#F7FAFF", "#DDEBFE", 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val routeTopHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val mapIconBadge = TextView(this).apply {
            text = "🗺️"
            textSize = 12f
            gravity = Gravity.CENTER
            background = createNeonBox("#E3EEFC", "#38BDF8", 50)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(6) }
        }
        val routeHeadCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rh1 = TextView(this).apply {
            text = "ROUTE ZONE FILTER"
            textSize = 9f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val rh2 = TextView(this).apply {
            text = "(Auto-disables fare limit when active)"
            textSize = 7f
            setTextColor(Color.parseColor("#42536D"))
        }
        routeHeadCol.addView(rh1)
        routeHeadCol.addView(rh2)

        val pickupTogglePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(4), dp(2))
            background = createNeonBox("#E2F3EA", "#10B981", 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(4) }
        }
        val pToggleLabel = TextView(this).apply {
            text = "🟢 Pickup"
            textSize = 8f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        pickupSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_PICKUP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
        }
        pickupTogglePill.addView(pToggleLabel)
        pickupTogglePill.addView(pickupSwitch)

        val dropTogglePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(4), dp(2))
            background = createNeonBox("#FCE8EC", "#F43F5E", 10)
        }
        val dToggleLabel = TextView(this).apply {
            text = "🔴 Drop"
            textSize = 8f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        dropSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_DROP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#F43F5E"))
        }
        dropTogglePill.addView(dToggleLabel)
        dropTogglePill.addView(dropSwitch)

        routeTopHeader.addView(mapIconBadge)
        routeTopHeader.addView(routeHeadCol)
        routeMasterCard.addView(routeTopHeader)
        val zoneToggles = LinearLayout(this).apply {
            orientation = if (configuration.fontScale >= 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(pickupTogglePill)
            addView(dropTogglePill)
        }
        routeMasterCard.addView(zoneToggles)

        val routeBodyRow = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val zonesLeftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            weightSum = 2f
            layoutParams = cardParams(1.08f).apply {
                rightMargin = dp(6)
            }
        }

        val pickupBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#F0FAF5", "#10B981", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        val pBoxHeader = TextView(this).apply {
            text = "🟢 PICKUP ZONE  •  Select Locality"
            textSize = 8f
            setTextColor(Color.parseColor("#17613D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val pickupDropdown = TextView(this).apply {
            textSize = 9.5f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#E2F3EA", "#10B981", 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        pickupBoxCard.addView(pBoxHeader)
        pickupBoxCard.addView(pickupDropdown)

        val dropBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#FFF4F5", "#F43F5E", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val dBoxHeader = TextView(this).apply {
            text = "🔴 DROP ZONE  •  Select Locality"
            textSize = 8f
            setTextColor(Color.parseColor("#922B49"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val dropDropdown = TextView(this).apply {
            textSize = 9.5f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#FCE8EC", "#F43F5E", 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        dropBoxCard.addView(dBoxHeader)
        dropBoxCard.addView(dropDropdown)

        zonesLeftCol.addView(pickupBoxCard)
        zonesLeftCol.addView(dropBoxCard)

        val mapContainer = FrameLayout(this).apply {
            background = createNeonBox("#060D1A", "#C79838", 14)
            layoutParams = if (compact) LinearLayout.LayoutParams(match, dp(120)) else LinearLayout.LayoutParams(0, dp(132), 0.92f)
        }
        val radarMapView = RadarMapCanvasView(this)
        mapContainer.addView(radarMapView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        routeBodyRow.addView(zonesLeftCol)
        routeBodyRow.addView(mapContainer)
        routeMasterCard.addView(routeBodyRow)
        mainCol.addView(routeMasterCard)

        fun syncAllFilterStates() {
            val fActive = fareSwitch.isChecked
            minFareInput.isEnabled = fActive
            minusBtn.isEnabled = fActive
            plusBtn.isEnabled = fActive
            stepperRow.alpha = if (fActive) 1f else 0.35f

            pickupDropdown.text = if (selectedPickupSet.isEmpty()) {
                "Select pickup zones  ▾"
            } else {
                "${selectedPickupSet.joinToString(", ")}  ▾"
            }
            pickupBoxCard.alpha = if (pickupSwitch.isChecked) 1f else 0.45f

            dropDropdown.text = if (selectedDropSet.isEmpty()) {
                "Select drop zones  ▾"
            } else {
                "${selectedDropSet.joinToString(", ")}  ▾"
            }
            dropBoxCard.alpha = if (dropSwitch.isChecked) 1f else 0.45f
        }

        fareSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitches) return@setOnCheckedChangeListener
            isUpdatingSwitches = true
            if (isChecked) {
                pickupSwitch.isChecked = false
                dropSwitch.isChecked = false
            }
            isUpdatingSwitches = false
            syncAllFilterStates()
        }

        pickupSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitches) return@setOnCheckedChangeListener
            isUpdatingSwitches = true
            if (isChecked) {
                fareSwitch.isChecked = false
            } else if (!dropSwitch.isChecked) {
                fareSwitch.isChecked = true
            }
            isUpdatingSwitches = false
            syncAllFilterStates()
        }

        dropSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitches) return@setOnCheckedChangeListener
            isUpdatingSwitches = true
            if (isChecked) {
                fareSwitch.isChecked = false
            } else if (!pickupSwitch.isChecked) {
                fareSwitch.isChecked = true
            }
            isUpdatingSwitches = false
            syncAllFilterStates()
        }

        syncAllFilterStates()

        pickupDropdown.setOnClickListener {
            pickupSwitch.isChecked = true
            showAreaMultiSelectDialog("Select Pickup Zones ($selectedCity)", selectedPickupSet) {
                syncAllFilterStates()
            }
        }

        dropDropdown.setOnClickListener {
            dropSwitch.isChecked = true
            showAreaMultiSelectDialog("Select Drop Zones ($selectedCity)", selectedDropSet) {
                syncAllFilterStates()
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
                        citySelectBtn.text = "📍  $selectedCity   ▾"
                        syncAllFilterStates()
                    }
                }
                .show()
        }

        // 5. SWIPE RESPONSE VELOCITY CARD
        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = createNeonBox("#F7FAFF", "#C79838", 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val speedHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val boltCircle = TextView(this).apply {
            text = "⚡"
            textSize = 11f
            gravity = Gravity.CENTER
            background = createNeonBox("#E3EEFC", "#A9771D", 50)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(6) }
        }
        val speedTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val st1 = TextView(this).apply {
            text = "SWIPE RESPONSE VELOCITY"
            textSize = 9f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val st2 = TextView(this).apply {
            text = "Set gesture swipe timing algorithm"
            textSize = 7f
            setTextColor(Color.parseColor("#42536D"))
        }
        speedTitleCol.addView(st1)
        speedTitleCol.addView(st2)

        val ultraFastBadge = TextView(this).apply {
            text = "⚡ Ultra Fast"
            textSize = 8f
            setTextColor(Color.parseColor("#A9771D"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = createNeonBox("#E8F1FE", "#A9771D", 30)
        }
        speedHeaderRow.addView(boltCircle)
        speedHeaderRow.addView(speedTitleCol)
        speedHeaderRow.addView(ultraFastBadge)
        speedCard.addView(speedHeaderRow)

        val speedTilesRow = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val speedTileMap = mutableMapOf<String, LinearLayout>()

        fun updateVelocityVisuals() {
            for ((k, tile) in speedTileMap) {
                val active = (k == selectedSpeed)
                val strokeHex = when (k) {
                    "QUICK" -> if (active) "#00F5D4" else "#1E293B"
                    "NORMAL" -> if (active) "#38BDF8" else "#1E293B"
                    else -> if (active) "#C79838" else "#3B1D5E"
                }
                val bgHex = when (k) {
                    "QUICK" -> if (active) "#06373A" else "#070E1C"
                    "NORMAL" -> if (active) "#0A2540" else "#070E1C"
                    else -> if (active) "#281042" else "#120924"
                }
                tile.background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(if (active) 2 else 1), Color.parseColor(strokeHex))
                }
            }
        }

        fun makeVelocityCard(key: String, iconStr: String, titleStr: String, msStr: String, msColor: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = cardParams().apply {
                    if (key != "NATURAL") {
                        if (compact) bottomMargin = dp(5) else marginEnd = dp(5)
                    }
                }

                val icCircle = TextView(context).apply {
                    text = iconStr
                    textSize = 12f
                    gravity = Gravity.CENTER
                    background = createNeonBox("#E8F1FE", msColor, 50)
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(6) }
                }

                val tCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvT = TextView(context).apply {
                    text = if (key == "QUICK") "$titleStr ✓" else titleStr
                    textSize = 9.5f
                    setTextColor(Color.parseColor("#10264D"))
                    typeface = Typeface.DEFAULT_BOLD
                }
                val tvM = TextView(context).apply {
                    text = msStr
                    textSize = 8.5f
                    setTextColor(Color.parseColor(msColor))
                    typeface = Typeface.DEFAULT_BOLD
                }
                tCol.addView(tvT)
                tCol.addView(tvM)

                addView(icCircle)
                addView(tCol)

                setOnClickListener {
                    selectedSpeed = key
                    updateVelocityVisuals()
                    Toast.makeText(this@MainActivity, "Velocity set: $titleStr ($msStr)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val tQuick = makeVelocityCard("QUICK", "⚡", "Quick", "120 ms", "#00F5D4")
        val tNormal = makeVelocityCard("NORMAL", "🏎️", "Normal", "250 ms", "#38BDF8")
        val tNatural = makeVelocityCard("NATURAL", "🌱", "Human", "450 ms", "#C79838")

        speedTileMap["QUICK"] = tQuick
        speedTileMap["NORMAL"] = tNormal
        speedTileMap["NATURAL"] = tNatural

        speedTilesRow.addView(tQuick)
        speedTilesRow.addView(tNormal)
        speedTilesRow.addView(tNatural)
        updateVelocityVisuals()

        speedCard.addView(speedTilesRow)
        mainCol.addView(speedCard)

        // 6. ACTION DOCK
        val dockGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }

        val dockRow1 = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply {
                bottomMargin = dp(5)
            }
        }

        val saveBtn = createCyberActionPill(
            iconStr = "💾",
            title = "SAVE PROFILE",
            sub = "Apply saved filters",
            c1 = "#10264D",
            c2 = "#184B94",
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

            if (Settings.canDrawOverlays(this)) startFloatingWidget()
            Toast.makeText(this, "✅ Config Saved: ZuAuto Active", Toast.LENGTH_SHORT).show()
        }

        val hudBtn = createCyberActionPill(
            iconStr = "🥞",
            title = "FLOATING HUD",
            sub = "Launch Overlay Bar",
            c1 = "#059669",
            c2 = "#10B981",
            isLeft = false
        ) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant Overlay Permission for ZuAuto", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startFloatingWidget()
                Toast.makeText(this, "⚡ ZuAuto Floating HUD Active", Toast.LENGTH_SHORT).show()
            }
        }
        dockRow1.addView(saveBtn)
        dockRow1.addView(hudBtn)

        val dockRow2 = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(match, wrap)
        }

        val porterBtn = createCyberActionPill(
            iconStr = "🔗",
            title = "LAUNCH PORTER",
            sub = "Open Partner App",
            c1 = "#6D28D9",
            c2 = "#A855F7",
            isLeft = true
        ) {
            launchPorterPartnerApp(this)
        }

        val accBtn = createCyberActionPill(
            iconStr = "⚙️",
            title = "ACCESSIBILITY",
            sub = "Dispatch Engine Setup",
            c1 = "#D97706",
            c2 = "#F59E0B",
            isLeft = false
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        dockRow2.addView(porterBtn)
        dockRow2.addView(accBtn)

        dockGrid.addView(dockRow1)
        dockGrid.addView(dockRow2)
        mainCol.addView(dockGrid)

        // 7. BOTTOM STATUS FOOTER
        val footerBar = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        footerSystemText = TextView(this).apply {
            text = "ılılı  ZuAuto Engine Ready"
            textSize = 8.5f
            setTextColor(Color.parseColor("#A9771D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val footerCenterText = TextView(this).apply {
            text = "ZUAUTO  •  PORTER VIP ENGINE"
            textSize = 8f
            setTextColor(Color.parseColor("#42536D"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        footerOnlineText = TextView(this).apply {
            text = "● Online"
            textSize = 8.5f
            setTextColor(Color.parseColor("#10B981"))
            typeface = Typeface.DEFAULT_BOLD
        }
        footerBar.addView(footerSystemText)
        if (!compact) footerBar.addView(footerCenterText)
        footerBar.addView(footerOnlineText)
        mainCol.addView(footerBar)

        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun addShortcut(label: String, action: () -> Unit) {
            shortcuts.addView(TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setTextColor(Color.parseColor("#10264D"))
                background = createNeonBox("#F7FAFF", "#C79838", 12)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        addShortcut("Pickup") { pickupDropdown.performClick() }
        addShortcut("Drop") { dropDropdown.performClick() }
        addShortcut("HUD") { hudBtn.performClick() }
        addShortcut("Trips") { showOrderHistoryDialog() }
        mainCol.addView(shortcuts, shortcutIndex, LinearLayout.LayoutParams(match, wrap).apply {
            bottomMargin = dp(10)
        })

        val contentScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(mainCol)
        }
        val contentParams = if (compact) LinearLayout.LayoutParams(match, 0, 1f)
            else LinearLayout.LayoutParams(0, match, 1f)
        masterRoot.addView(contentScroll, contentParams)
        setContentView(masterRoot)
    }

    private fun createCyberActionPill(
        iconStr: String,
        title: String,
        sub: String,
        c1: String,
        c2: String,
        isLeft: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val configuration = resources.configuration
        val compact = configuration.screenWidthDp < 480 || configuration.fontScale >= 1.3f
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(8), dp(4), dp(10), dp(4))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
            layoutParams = if (compact) {
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
            } else {
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (isLeft) marginEnd = dp(4) else marginStart = dp(4)
                }
            }

            val iconCircle = TextView(context).apply {
                text = iconStr
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33000000"))
                    cornerRadius = dp(40).toFloat()
                    setStroke(dp(1), Color.parseColor("#66FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(8) }
            }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val t1 = TextView(context).apply {
                text = title
                textSize = 9.5f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val t2 = TextView(context).apply {
                text = sub
                textSize = 7.5f
                setTextColor(Color.parseColor("#E2E8F0"))
            }
            textCol.addView(t1)
            textCol.addView(t2)

            val arrowTv = TextView(context).apply {
                text = "›"
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(iconCircle)
            addView(textCol)
            addView(arrowTv)
            setOnClickListener { onClick() }
        }
    }

    // ---------------- FIRST-TIME REGISTRATION DIALOG ----------------
    private fun showRegistrationDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val titleTv = TextView(this).apply {
            text = "⚡ Welcome to ZuAuto VIP"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }
        val subTv = TextView(this).apply {
            text = "Please complete registration to activate the auto-dispatch engine on this device."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(titleTv)
        root.addView(subTv)

        val nameIn = EditText(this).apply {
            hint = "Full Name (e.g. Sameer Khan)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 13f
            background = createNeonBox("#0F172A", "#334155", 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
        val phoneIn = EditText(this).apply {
            hint = "Mobile Number (+91 ...)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            inputType = InputType.TYPE_CLASS_PHONE
            background = createNeonBox("#0F172A", "#334155", 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
        val keyIn = EditText(this).apply {
            hint = "License Key (Optional, e.g. ZU-30D-XXXX)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#FACC15"))
            textSize = 13f
            background = createNeonBox("#0F172A", "#F59E0B", 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }

        root.addView(nameIn)
        root.addView(phoneIn)
        root.addView(keyIn)

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(false)
            .create()

        val submitBtn = Button(this).apply {
            text = "ACTIVATE & START RIDING"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#00E5FF"))
            ).apply { cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                val n = nameIn.text.toString().trim()
                val p = phoneIn.text.toString().trim()
                val k = keyIn.text.toString().trim()
                if (n.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please fill in your name and phone number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                ZuAutoCloudManager.registerDriver(
                    context = this@MainActivity,
                    name = n,
                    phone = p,
                    city = selectedCity,
                    vehicle = "Porter Delivery",
                    licenseKey = if (k.isNotEmpty()) k else null
                ) { success, msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    userBox.text = Html.fromHtml("<font color='#94A3B8'>Driver</font><br><b><font color='#FFFFFF'>$n ▾</font></b>", Html.FROM_HTML_MODE_LEGACY)
                    dialog.dismiss()
                }
            }
        }
        root.addView(submitBtn)
        dialog.show()
    }

    // ---------------- MANDATORY OTA IN-APP UPDATE DIALOG ----------------
    private fun showUpdateDialog(isForce: Boolean, newVersionName: String, releaseNotes: String, downloadUrl: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val headerBadge = TextView(this).apply {
            text = if (isForce) "🚨 MANDATORY UPDATE REQUIRED" else "✨ NEW VERSION AVAILABLE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isForce) Color.parseColor("#F43F5E") else Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }

        val verTitle = TextView(this).apply {
            text = "ZuAuto v$newVersionName"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val notesBox = TextView(this).apply {
            text = if (releaseNotes.isNotBlank()) releaseNotes else "• Performance optimizations\n• Direct OTA update system\n• Zero-delay dispatch"
            textSize = 11f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createNeonBox("#050811", "#1E293B", 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(14)
            }
        }

        root.addView(headerBadge)
        root.addView(verTitle)
        root.addView(notesBox)

        val dlBtn = Button(this).apply {
            text = "DOWNLOAD APK"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#06B6D4"))
            ).apply { cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                val apk = Uri.parse(downloadUrl)
                try {
                    val fileVersion = newVersionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val request = android.app.DownloadManager.Request(apk)
                        .setTitle("ZU Smart $newVersionName")
                        .setMimeType("application/vnd.android.package-archive")
                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                            "ZU-Smart-$fileVersion-${System.currentTimeMillis()}.apk")
                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    val downloads = getSystemService(android.app.DownloadManager::class.java)
                    checkNotNull(downloads).enqueue(request)
                    Toast.makeText(this@MainActivity, "APK download started. Open it from Downloads when ready.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Unable to start APK download", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(dlBtn)

        val updateDialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(!isForce)
            .create()

        updateDialog.show()
    }

    // ---------------- ORDER HISTORY DIALOG ----------------
    private fun showOrderHistoryDialog() {
        val history = ZuAutoCloudManager.getLocalOrderHistory(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val headTv = TextView(this).apply {
            text = "📦 Accepted Trips History (${history.size})"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(headTv)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(340)
            )
        }
        val itemsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (history.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No trips auto-accepted yet.\nTurn on Bot and start Porter to receive rides."
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(30), 0, dp(30))
            }
            itemsCol.addView(emptyTv)
        } else {
            for (ord in history) {
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = createNeonBox("#050811", "#1E293B", 10)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                }

                val row1 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val fareTv = TextView(this).apply {
                    text = "₹${ord.fare}"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#00E5FF"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val timeTv = TextView(this).apply {
                    text = ord.timestamp
                    textSize = 9f
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                row1.addView(fareTv)
                row1.addView(timeTv)

                val routeTv = TextView(this).apply {
                    text = "🟢 ${ord.pickup}\n🔴 ${ord.drop}"
                    textSize = 10f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setPadding(0, dp(3), 0, 0)
                }

                item.addView(row1)
                item.addView(routeTv)
                itemsCol.addView(item)
            }
        }

        scroll.addView(itemsCol)
        root.addView(scroll)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Close", null)
            .show()
    }


    // ---------------- DRIVER PROFILE & VALIDITY / BUY DIALOG ----------------
    private fun showDriverProfileAndValidityDialog() {
        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("USER_NAME", "Driver") ?: "Driver"
        val phone = prefs.getString("USER_PHONE", "--") ?: "--"
        val uid = prefs.getString("USER_UID", "DRV-1001") ?: "DRV-1001"
        val city = prefs.getString("USER_CITY", selectedCity) ?: selectedCity
        val vehicle = prefs.getString("USER_VEHICLE", "Porter Vehicle") ?: "Porter Vehicle"
        val plan = prefs.getString("USER_PLAN", "TRIAL")?.uppercase(Locale.US) ?: "TRIAL"
        val expiresAtMillis = prefs.getLong("PLAN_EXPIRES_AT_MILLIS", 0L)
        val now = System.currentTimeMillis()

        val isTrial = plan.contains("TRIAL")
        val isExpired = expiresAtMillis in 1..now
        val isActive = expiresAtMillis == 0L || expiresAtMillis > now

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070C18"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Header: Profile
        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val avatar = TextView(this).apply {
            text = "👤"
            textSize = 28f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) }
        }
        val profileCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameTv = TextView(this).apply {
            text = name
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val metaTv = TextView(this).apply {
            text = "$phone • $city ($uid)"
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        profileCol.addView(nameTv)
        profileCol.addView(metaTv)
        headRow.addView(avatar)
        headRow.addView(profileCol)
        root.addView(headRow)

        // Validity Card
        val validityCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                if (isActive) {
                    setColor(Color.parseColor("#0A1F2D"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                } else {
                    setColor(Color.parseColor("#2D0A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        val valTitle = TextView(this).apply {
            text = if (isExpired) "⚠️ MEMBERSHIP EXPIRED" else if (isTrial) "🕒 3-DAY FREE TRIAL ACTIVE" else "👑 $plan VIP ACCESS ACTIVE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isExpired) Color.parseColor("#EF4444") else if (isTrial) Color.parseColor("#FACC15") else Color.parseColor("#10B981"))
        }
        val valSub = TextView(this).apply {
            if (expiresAtMillis > 0L) {
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val expDateStr = sdf.format(Date(expiresAtMillis))
                val remainingMs = expiresAtMillis - now
                val hours = (remainingMs / (1000 * 60 * 60)).coerceAtLeast(0)
                text = if (isExpired) "Expired on $expDateStr. Renew to restore auto-accept." else "Valid until $expDateStr (Remaining: ~${hours}h)"
            } else {
                text = "Lifetime VIP Access Enabled"
            }
            textSize = 10f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(2), 0, 0)
        }
        validityCard.addView(valTitle)
        validityCard.addView(valSub)
        root.addView(validityCard)

        // Plans Section Title
        val plansTitle = TextView(this).apply {
            text = "🚀 SELECT VIP AUTOPILOT PLAN"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(plansTitle)

        // Dynamic Plans Container
        val plansScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        }
        val plansListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        plansScroll.addView(plansListContainer)
        root.addView(plansScroll)

        var mainDialog: AlertDialog? = null

        // Fetch Plans from Firestore
        ZuAutoCloudManager.fetchSubscriptionPlans { plans ->
            plansListContainer.removeAllViews()
            for (p in plans) {
                val pCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#0D1526"))
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                }
                val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val pName = TextView(this).apply {
                    text = "${p.name} (${p.durationDays} Days)"
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val pPrice = TextView(this).apply {
                    text = "₹${p.price}"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#10B981"))
                }
                r1.addView(pName)
                r1.addView(pPrice)

                val pDesc = TextView(this).apply {
                    text = p.description
                    textSize = 9.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(2), 0, dp(6))
                }

                val buyBtn = Button(this).apply {
                    text = "BUY NOW - ₹${p.price}"
                    setTextColor(Color.parseColor("#050811"))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 11f
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0284C7"))
                    ).apply { cornerRadius = dp(6).toFloat() }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
                    setOnClickListener {
                        mainDialog?.dismiss()
                        showUpiCheckoutDialog(p)
                    }
                }

                pCard.addView(r1)
                pCard.addView(pDesc)
                pCard.addView(buyBtn)
                plansListContainer.addView(pCard)
            }
        }

        // Bottom Actions: Key Redeem
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val redeemKeyBtn = Button(this).apply {
            text = "🔑 REDEEM KEY"
            textSize = 10.5f
            setTextColor(Color.parseColor("#FACC15"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161E2E"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(6) }
            setOnClickListener {
                mainDialog?.dismiss()
                showLicenseKeyDialog()
            }
        }
        val closeBtn = Button(this).apply {
            text = "CLOSE"
            textSize = 10.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 0.6f)
            setOnClickListener { mainDialog?.dismiss() }
        }
        actionsRow.addView(redeemKeyBtn)
        actionsRow.addView(closeBtn)
        root.addView(actionsRow)

        mainDialog = AlertDialog.Builder(this)
            .setView(root)
            .create()
        mainDialog.show()
    }

    // ---------------- UPI CHECKOUT & UTR PROOF SUBMISSION DIALOG ----------------
    private fun showUpiCheckoutDialog(plan: ZuAutoCloudManager.SubscriptionPlan) {
        ZuAutoCloudManager.fetchPaymentSettings { upiId, qrUrl ->
            if (isFinishing || isDestroyed) return@fetchPaymentSettings
            if (upiId.isNullOrBlank()) {
                Toast.makeText(this, "Payment settings unavailable. Try again later.", Toast.LENGTH_LONG).show()
            } else {
                renderUpiCheckout(plan, upiId, qrUrl.orEmpty())
            }
        }
    }

    private fun renderUpiCheckout(plan: ZuAutoCloudManager.SubscriptionPlan, upiId: String, qrUrl: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070C18"))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        val title = TextView(this).apply {
            text = "💳 UPI Instant Payment"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
        }
        val sub = TextView(this).apply {
            text = "Pay for ${plan.name} (₹${plan.price}) and submit your UTR for admin approval."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(3), 0, dp(12))
        }
        root.addView(title)
        root.addView(sub)

        // UPI Box
        val upiBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        val upiLabel = TextView(this).apply {
            text = "OFFICIAL ADMIN UPI ID:"
            textSize = 9f
            setTextColor(Color.parseColor("#64748B"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val upiIdTv = TextView(this).apply {
            text = upiId
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FACC15"))
            setPadding(0, dp(2), 0, dp(6))
        }
        val copyBtn = Button(this).apply {
            text = "📋 COPY UPI ID"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7"))
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            setOnClickListener {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("UPI ID", upiId))
                Toast.makeText(this@MainActivity, "UPI ID Copied to Clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        upiBox.addView(upiLabel)
        upiBox.addView(upiIdTv)
        upiBox.addView(copyBtn)
        root.addView(upiBox)
        if (Uri.parse(qrUrl).scheme == "https") {
            val qrImage = android.widget.ImageView(this).apply {
                contentDescription = "UPI payment QR code"
                visibility = View.GONE
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            root.addView(qrImage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)))
            Thread {
                val bitmap = runCatching {
                    val connection = java.net.URL(qrUrl).openConnection().apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    connection.getInputStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                }.getOrNull()
                runOnUiThread {
                    if (bitmap != null && !isDestroyed) {
                        qrImage.setImageBitmap(bitmap)
                        qrImage.visibility = View.VISIBLE
                    }
                }
            }.start()
        }

        // UTR Input
        val utrLabel = TextView(this).apply {
            text = "ENTER 12-DIGIT UPI / UTR REFERENCE NO:"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(utrLabel)

        val utrInput = EditText(this).apply {
            hint = "e.g. 428901238491"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = createNeonBox("#050811", "#00E5FF", 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        root.addView(utrInput)

        var checkoutDialog: AlertDialog? = null

        val submitBtn = Button(this).apply {
            text = "SUBMIT PAYMENT PROOF"
            setTextColor(Color.parseColor("#050811"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#059669"))
            ).apply { cornerRadius = dp(10).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            setOnClickListener {
                val utr = utrInput.text.toString().trim()
                if (utr.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please enter valid UTR reference number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isEnabled = false
                ZuAutoCloudManager.submitPaymentProof(this@MainActivity, plan, utr, "Payment proof via UPI App") { success, msg ->
                    isEnabled = true
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    if (success) checkoutDialog?.dismiss()
                }
            }
        }
        root.addView(submitBtn)

        checkoutDialog = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(root) })
            .setNegativeButton("Cancel", null)
            .create()
        checkoutDialog.show()
    }

    // ---------------- LICENSE KEY REDEMPTION DIALOG ----------------
    private fun showLicenseKeyDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val titleTv = TextView(this).apply {
            text = "🔑 Apply License Key"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FACC15"))
        }
        val subTv = TextView(this).apply {
            text = "Enter a subscription activation code provided by Admin to extend VIP access."
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(3), 0, dp(12))
        }
        root.addView(titleTv)
        root.addView(subTv)

        val keyIn = EditText(this).apply {
            hint = "ZU-KEY-XXXXX"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#FACC15"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = createNeonBox("#050811", "#F59E0B", 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        root.addView(keyIn)

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .create()

        val applyBtn = Button(this).apply {
            text = "REDEEM KEY"
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#FACC15"))
            ).apply { cornerRadius = dp(10).toFloat() }
            setOnClickListener {
                val k = keyIn.text.toString().trim()
                if (k.isEmpty()) return@setOnClickListener

                ZuAutoCloudManager.redeemLicenseKey(this@MainActivity, k) { success, msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
                        val planStr = prefs.getString("USER_PLAN", "VIP")?.uppercase(Locale.US) ?: "VIP"
                        planBadge.text = "👑 $planStr"
                        dialog.dismiss()
                    }
                }
            }
        }
        root.addView(applyBtn)
        dialog.show()
    }

    private val bengaluruAreas by lazy {
        assets.open("bengaluru-areas.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line -> line.substringBefore('\t') to line.substringAfter('\t', "") }
        }
    }

    private fun showAreaMultiSelectDialog(
        title: String,
        targetSet: MutableSet<String>,
        onUpdated: () -> Unit
    ) {
        val areaPins = if (selectedCity == "Bengaluru") bengaluruAreas else emptyMap()
        val baseAreas = if (selectedCity == "Bengaluru") areaPins.keys.toList()
            else cityAreasMap[selectedCity].orEmpty().toList()
        val allAreas = (baseAreas + targetSet).distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val selections = targetSet.toMutableSet()
        val visibleAreas = allAreas.toMutableList()
        val search = EditText(this).apply {
            hint = if (areaPins.isEmpty()) "Search areas" else "Search area or PIN code"
            setSingleLine(true)
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, visibleAreas)
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ ->
                val area = visibleAreas[position]
                val previous = selections.firstOrNull { it.equals(area, ignoreCase = true) }
                if (isItemChecked(position)) {
                    if (previous == null) selections.add(area)
                } else if (previous != null) {
                    selections.remove(previous)
                }
            }
        }
        fun refreshAreas(query: String) {
            val key = query.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
            val selectedKeys = selections.map { it.lowercase(Locale.ROOT) }.toSet()
            list.clearChoices()
            visibleAreas.clear()
            visibleAreas.addAll(allAreas.filter { area ->
                area.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT).contains(key) ||
                    areaPins[area].orEmpty().split(' ').any { it.startsWith(key) }
            })
            adapter.notifyDataSetChanged()
            visibleAreas.forEachIndexed { index, area ->
                if (area.lowercase(Locale.ROOT) in selectedKeys) list.setItemChecked(index, true)
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshAreas(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        refreshAreas("")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(search)
            val listHeight = (resources.configuration.screenHeightDp / 2).coerceIn(100, 360)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(listHeight)))
            if (areaPins.isNotEmpty()) addView(TextView(context).apply { text = "Postal data © GeoNames" })
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("Done") { _, _ ->
                targetSet.clear()
                targetSet.addAll(selections)
                onUpdated()
            }
            .setNeutralButton("+ Custom Area") { _, _ ->
                val input = EditText(this).apply {
                    hint = "Enter custom area / pincode"
                }
                AlertDialog.Builder(this)
                    .setTitle("Add Custom Area")
                    .setView(input)
                    .setPositiveButton("Add") { _, _ ->
                        val custom = input.text.toString().trim()
                        if (custom.isNotEmpty() && ',' !in custom) {
                            targetSet.clear()
                            targetSet.addAll(selections)
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
        updateSystemStatusBar()
    }

    private fun updateSystemStatusBar() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isAccActive = enabledServices.contains(packageName, ignoreCase = true)
        val isOverlayOk = Settings.canDrawOverlays(this)

        if (isAccActive && isOverlayOk) {
            footerSystemText.text = "ılılı  ZuAuto Engine Ready"
            footerOnlineText.text = "● Online"
            footerOnlineText.setTextColor(Color.parseColor("#10B981"))
        } else {
            footerSystemText.text = "ılılı  Setup Needed"
            footerOnlineText.text = "● Permission"
            footerOnlineText.setTextColor(Color.parseColor("#F59E0B"))
        }
    }

    private fun createNeonBox(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun startFloatingWidget() {
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    // Custom Radar Map Canvas View
    class RadarMapCanvasView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val routePath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#060D1A")
            canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.parseColor("#1E293B")
            for (i in 1..6) {
                val x = (w / 6f) * i
                val y = (h / 6f) * i
                canvas.drawLine(x, 0f, x - 25f, h, paint)
                canvas.drawLine(0f, y, w, y + 15f, paint)
            }

            val px = w * 0.26f
            val py = h * 0.45f
            val dx = w * 0.78f
            val dy = h * 0.75f

            routePath.reset()
            routePath.moveTo(px, py)
            routePath.lineTo(w * 0.42f, h * 0.62f)
            routePath.lineTo(w * 0.56f, h * 0.55f)
            routePath.lineTo(dx, dy)

            paint.color = Color.parseColor("#5500E5FF")
            paint.strokeWidth = 10f
            canvas.drawPath(routePath, paint)

            paint.color = Color.parseColor("#00E5FF")
            paint.strokeWidth = 4f
            canvas.drawPath(routePath, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#4410B981")
            canvas.drawCircle(px, py, 20f, paint)
            paint.color = Color.parseColor("#10B981")
            canvas.drawCircle(px, py, 9f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Pickup", px - 26f, py - 18f, paint)

            paint.color = Color.parseColor("#44F43F5E")
            canvas.drawCircle(dx, dy, 20f, paint)
            paint.color = Color.parseColor("#F43F5E")
            canvas.drawCircle(dx, dy, 9f, paint)
            paint.color = Color.WHITE
            canvas.drawText("Drop", dx - 20f, dy - 18f, paint)

            paint.color = Color.parseColor("#CC0F172A")
            canvas.drawRoundRect(w * 0.54f, 10f, w - 10f, 44f, 16f, 16f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#38BDF8")
            canvas.drawRoundRect(w * 0.54f, 10f, w - 10f, 44f, 16f, 16f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 17f
            canvas.drawText("🗺️ Live Map", w * 0.58f, 32f, paint)
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
            Toast.makeText(context, "Porter Partner app not detected on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
