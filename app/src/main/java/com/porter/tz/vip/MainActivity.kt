package com.porter.tz.vip

import android.app.AlertDialog
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
    private lateinit var weatherCityText: TextView

    private var isUpdatingSwitches = false
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

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#050811")
        window.navigationBarColor = Color.parseColor("#050811")

        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        selectedSpeed = prefs.getString("SWIPE_SPEED", "QUICK") ?: "QUICK"
        selectedCity = prefs.getString("SELECTED_CITY", "Bengaluru") ?: "Bengaluru"

        prefs.getString("PICKUP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedPickupSet.addAll(it)
        }
        prefs.getString("DROP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedDropSet.addAll(it)
        }

        // MASTER ROOT: Left Sidebar Rail + Right Main Cockpit (Matches 2nd Pic)
        val masterRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#050811"))
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
                intArrayOf(Color.parseColor("#080E1E"), Color.parseColor("#040710"))
            ).apply {
                setStroke(dp(1), Color.parseColor("#162238"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val menuIcon = TextView(this).apply {
            text = "☰"
            textSize = 18f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }
        sidebar.addView(menuIcon)

        fun buildNavItem(iconStr: String, labelStr: String, isSelected: Boolean, onClick: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(8), dp(2), dp(8))
                if (isSelected) {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#00E5FF"))
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
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#64748B"))
                    gravity = Gravity.CENTER
                }
                val lb = TextView(context).apply {
                    text = labelStr
                    textSize = 7.5f
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#64748B"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                addView(ic)
                addView(lb)
                setOnClickListener { onClick() }
            }
        }

        sidebar.addView(buildNavItem("🏠", "Home", true) {})
        sidebar.addView(buildNavItem("📊", "Analytics", false) {
            Toast.makeText(this, "ZU Smart Analytics Active", Toast.LENGTH_SHORT).show()
        })
        sidebar.addView(buildNavItem("🏍️", "Rides", false) { launchPorterPartnerApp(this) })
        sidebar.addView(buildNavItem("₹", "Earnings", false) {})
        sidebar.addView(buildNavItem("⚙️", "Settings", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        sidebar.addView(buildNavItem("?", "Help", false) {})

        val sidebarSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        sidebar.addView(sidebarSpacer)

        val sloganText = TextView(this).apply {
            text = "👑\nBetter\nRides\nBigger\nDreams"
            textSize = 7.5f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        sidebar.addView(sloganText)
        masterRoot.addView(sidebar)

        // ================= RIGHT MAIN CONTENT AREA =================
        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        // 1. TOP BAR: Logo + ZU Smart + Bell + Hello Sameer + PREMIUM
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
            text = Html.fromHtml("<b><font color='#FFFFFF'>ZU</font> <font color='#00E5FF'>Smart</font></b>", Html.FROM_HTML_MODE_LEGACY)
            textSize = 16f
        }

        val zSub = TextView(this).apply {
            text = "INTELLIGENT AUTO-DISPATCH PRO"
            textSize = 7f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        titleCol.addView(zTitle)
        titleCol.addView(zSub)

        val bellBadge = TextView(this).apply {
            text = "🔔³"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = createNeonBox("#0E172A", "#1E293B", 50)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(6) }
        }

        val userBox = TextView(this).apply {
            text = Html.fromHtml("<font color='#94A3B8'>Hello,</font><br><b><font color='#FFFFFF'>Sameer ▾</font></b>", Html.FROM_HTML_MODE_LEGACY)
            textSize = 8f
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        val premiumPill = TextView(this).apply {
            text = "👑 PREMIUM"
            textSize = 8f
            setTextColor(Color.parseColor("#FACC15"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = createNeonBox("#1C1608", "#F59E0B", 40)
            setOnClickListener {
                val active = !prefs.getBoolean("BOT_ACTIVE", true)
                prefs.edit().putBoolean("BOT_ACTIVE", active).apply()
                updateSystemStatusBar()
                if (Settings.canDrawOverlays(this@MainActivity)) startFloatingWidget()
            }
        }

        topHeader.addView(logoImg)
        topHeader.addView(titleCol)
        topHeader.addView(bellBadge)
        topHeader.addView(userBox)
        topHeader.addView(premiumPill)
        mainCol.addView(topHeader)

        // 2. HERO BANNER: Ride Smart Earn More + Cyberpunk Bike + Weather/Time Widget (Weight 1.25)
        val heroBanner = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.22f
            ).apply { bottomMargin = dp(8) }
        }

        val heroGraphic = HeroBannerCanvasView(this)
        heroBanner.addView(heroGraphic, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val heroContentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val heroLeftTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }

        val rideSmartTv = TextView(this).apply {
            text = "Ride Smart"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }

        val earnMoreTv = TextView(this).apply {
            text = "Earn More"
            textSize = 20f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val heroBullets = TextView(this).apply {
            text = "Auto Dispatch • Faster Rides • Higher Earnings"
            textSize = 7.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(2), 0, 0)
        }

        heroLeftTextCol.addView(rideSmartTv)
        heroLeftTextCol.addView(earnMoreTv)
        heroLeftTextCol.addView(heroBullets)

        // Weather & Date Glass Card on Right
        val weatherCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createNeonBox("#CC0B132B", "#6366F1", 14)
            layoutParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        weatherCityText = TextView(this).apply {
            text = "📍 $selectedCity"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val tempRow = TextView(this).apply {
            text = "⛅ 28°C\nPartly Cloudy"
            textSize = 8.5f
            setTextColor(Color.parseColor("#FACC15"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }

        val sdf = SimpleDateFormat("dd MMM yyyy\nhh:mm a", Locale.ENGLISH)
        val dateTv = TextView(this).apply {
            text = "📅 " + sdf.format(Date())
            textSize = 7f
            setTextColor(Color.parseColor("#94A3B8"))
        }

        weatherCard.addView(weatherCityText)
        weatherCard.addView(tempRow)
        weatherCard.addView(dateTv)

        heroContentRow.addView(heroLeftTextCol)
        heroContentRow.addView(weatherCard)
        heroBanner.addView(heroContentRow)
        mainCol.addView(heroBanner)

        // 3. FARE MODE (₹) & ACTIVE CITY ROW (Weight 1.25)
        val fareCityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.25f
            ).apply { bottomMargin = dp(8) }
        }

        // Left: FARE MODE CARD (Cyan Neon Border)
        val fareCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createNeonBox("#09152B", "#00E5FF", 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                rightMargin = dp(4)
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
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createNeonBox("#0284C7", "#00E5FF", 50)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) }
        }

        val fareTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val fTitle = TextView(this).apply {
            text = "FARE MODE (₹)"
            textSize = 9.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val fSub = TextView(this).apply {
            text = "Set your fare amount"
            textSize = 7.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        fareTitleCol.addView(fTitle)
        fareTitleCol.addView(fSub)

        fareSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_FARE_FILTER", true)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
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
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createNeonBox("#0F2442", "#00E5FF", 40)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(32))
            setOnClickListener {
                val c = minFareInput.text.toString().toIntOrNull() ?: 200
                minFareInput.setText((c - 20).coerceAtLeast(50).toString())
            }
        }

        minFareInput = EditText(this).apply {
            setText(prefs.getInt("MIN_FARE", 200).toString())
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(0, 0, 0, 0)
            background = createNeonBox("#050B16", "#00E5FF", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        }

        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createNeonBox("#0F2442", "#00E5FF", 40)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(32))
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

        // Right: ACTIVE CITY CARD (Purple/Magenta Neon Border)
        val cityCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createNeonBox("#180D2E", "#D946EF", 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
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
            background = createNeonBox("#4A154B", "#D946EF", 50)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) }
        }

        val cityTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val cTitle = TextView(this).apply {
            text = "ACTIVE CITY"
            textSize = 9.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val cSub = TextView(this).apply {
            text = "Select your city"
            textSize = 7.5f
            setTextColor(Color.parseColor("#D8B4FE"))
        }
        cityTitleCol.addView(cTitle)
        cityTitleCol.addView(cSub)

        cityTop.addView(pinCircle)
        cityTop.addView(cityTitleCol)

        val citySelectBtn = TextView(this).apply {
            text = "📍  $selectedCity   ▾"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            background = createNeonBox("#0D071A", "#D946EF", 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
        }

        cityCard.addView(cityTop)
        cityCard.addView(citySelectBtn)

        fareCityRow.addView(fareCard)
        fareCityRow.addView(cityCard)
        mainCol.addView(fareCityRow)

        // 4. ROUTE ZONE FILTER + LIVE NEON RADAR MAP (Weight 2.15)
        val routeMasterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createNeonBox("#081226", "#0284C7", 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                2.15f
            ).apply { bottomMargin = dp(8) }
        }

        // Top Bar of Route Card: Title + Pickup Switch Pill + Drop Switch Pill
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
            background = createNeonBox("#0F2442", "#38BDF8", 50)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(6) }
        }

        val routeHeadCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rh1 = TextView(this).apply {
            text = "ROUTE ZONE FILTER"
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val rh2 = TextView(this).apply {
            text = "(Auto-disables fare limit when ON)"
            textSize = 7f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        routeHeadCol.addView(rh1)
        routeHeadCol.addView(rh2)

        // Pickup Toggle Box
        val pickupTogglePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(4), dp(2))
            background = createNeonBox("#051F20", "#10B981", 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(4) }
        }
        val pToggleLabel = TextView(this).apply {
            text = "🟢 Pickup"
            textSize = 8f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        pickupSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("USE_PICKUP_FILTER", false)
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
        }
        pickupTogglePill.addView(pToggleLabel)
        pickupTogglePill.addView(pickupSwitch)

        // Drop Toggle Box
        val dropTogglePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(4), dp(2))
            background = createNeonBox("#260B18", "#F43F5E", 10)
        }
        val dToggleLabel = TextView(this).apply {
            text = "🔴 Drop"
            textSize = 8f
            setTextColor(Color.WHITE)
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
        routeTopHeader.addView(pickupTogglePill)
        routeTopHeader.addView(dropTogglePill)
        routeMasterCard.addView(routeTopHeader)

        // Body of Route Card: Left (Pickup + Drop Boxes) & Right (Live Radar Map)
        val routeBodyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val zonesLeftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.08f).apply {
                rightMargin = dp(6)
            }
        }

        // Green Pickup Sub-Card
        val pickupBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#062223", "#10B981", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { bottomMargin = dp(4) }
        }

        val pBoxHeader = TextView(this).apply {
            text = "🟢 PICKUP ZONE  •  Select Pickup Zones"
            textSize = 8f
            setTextColor(Color.parseColor("#34D399"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val pickupDropdown = TextView(this).apply {
            textSize = 9.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#041214", "#10B981", 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        pickupBoxCard.addView(pBoxHeader)
        pickupBoxCard.addView(pickupDropdown)

        // Red Drop Sub-Card
        val dropBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#240A17", "#F43F5E", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val dBoxHeader = TextView(this).apply {
            text = "🔴 DROP ZONE  •  Select Drop Zones"
            textSize = 8f
            setTextColor(Color.parseColor("#FB7185"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val dropDropdown = TextView(this).apply {
            textSize = 9.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createNeonBox("#14050C", "#F43F5E", 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        dropBoxCard.addView(dBoxHeader)
        dropBoxCard.addView(dropDropdown)

        zonesLeftCol.addView(pickupBoxCard)
        zonesLeftCol.addView(dropBoxCard)

        // Right: Custom Radar Map Canvas View (Matches 2nd Pic Map with glowing pins & route)
        val mapContainer = FrameLayout(this).apply {
            background = createNeonBox("#060D1A", "#1E3A8A", 14)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.92f)
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

        // Sync & Exclusivity Logic for Fare vs Pickup/Drop
        fun syncAllFilterStates() {
            val fActive = fareSwitch.isChecked
            minFareInput.isEnabled = fActive
            minusBtn.isEnabled = fActive
            plusBtn.isEnabled = fActive
            stepperRow.alpha = if (fActive) 1f else 0.35f

            pickupDropdown.text = if (selectedPickupSet.isEmpty()) {
                "Electronic City, Whitefield, Koramangala  ▾"
            } else {
                "${selectedPickupSet.joinToString(", ")}  ▾"
            }
            pickupBoxCard.alpha = if (pickupSwitch.isChecked) 1f else 0.45f

            dropDropdown.text = if (selectedDropSet.isEmpty()) {
                "HSR Layout, Indiranagar, Marathahalli  ▾"
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
                        weatherCityText.text = "📍 $selectedCity"
                        syncAllFilterStates()
                    }
                }
                .show()
        }

        // 5. SWIPE RESPONSE VELOCITY CARD (Weight 1.15)
        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = createNeonBox("#081226", "#1E3A8A", 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.15f
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
            background = createNeonBox("#0F2442", "#00E5FF", 50)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(6) }
        }

        val speedTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val st1 = TextView(this).apply {
            text = "SWIPE RESPONSE VELOCITY"
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val st2 = TextView(this).apply {
            text = "Choose how fast you want to respond"
            textSize = 7f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        speedTitleCol.addView(st1)
        speedTitleCol.addView(st2)

        val ultraFastBadge = TextView(this).apply {
            text = "⚡ Ultra Fast"
            textSize = 8f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = createNeonBox("#051829", "#00E5FF", 30)
        }

        speedHeaderRow.addView(boltCircle)
        speedHeaderRow.addView(speedTitleCol)
        speedHeaderRow.addView(ultraFastBadge)
        speedCard.addView(speedHeaderRow)

        val speedTilesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val speedTileMap = mutableMapOf<String, LinearLayout>()

        fun updateVelocityVisuals() {
            for ((k, tile) in speedTileMap) {
                val active = (k == selectedSpeed)
                val strokeHex = when (k) {
                    "QUICK" -> if (active) "#00F5D4" else "#1E293B"
                    "NORMAL" -> if (active) "#38BDF8" else "#1E293B"
                    else -> if (active) "#D946EF" else "#3B1D5E"
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
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (key != "NATURAL") rightMargin = dp(5)
                }

                val icCircle = TextView(context).apply {
                    text = iconStr
                    textSize = 12f
                    gravity = Gravity.CENTER
                    background = createNeonBox("#0D1B30", msColor, 50)
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(6) }
                }

                val tCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvT = TextView(context).apply {
                    text = if (key == "QUICK") "$titleStr ✓" else titleStr
                    textSize = 9.5f
                    setTextColor(Color.WHITE)
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
                }
            }
        }

        val tQuick = makeVelocityCard("QUICK", "⏱️", "TURBO", "150 ms", "#00F5D4")
        val tNormal = makeVelocityCard("NORMAL", "⚖️", "BALANCED", "320 ms", "#38BDF8")
        val tNatural = makeVelocityCard("NATURAL", "🍃", "NATURAL", "550 ms", "#E879F9")

        speedTileMap["QUICK"] = tQuick
        speedTileMap["NORMAL"] = tNormal
        speedTileMap["NATURAL"] = tNatural

        speedTilesRow.addView(tQuick)
        speedTilesRow.addView(tNormal)
        speedTilesRow.addView(tNatural)
        updateVelocityVisuals()

        speedCard.addView(speedTilesRow)
        mainCol.addView(speedCard)

        // 6. 2x2 PILL ACTION BUTTONS WITH LEFT CIRCLE ICON & RIGHT ARROW (Weight 1.25)
        val dockGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.25f
            ).apply { bottomMargin = dp(4) }
        }

        val dockRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = dp(5)
            }
        }

        val saveBtn = createCyberActionPill(
            iconStr = "👤",
            title = "SAVE PROFILE",
            sub = "Apply Active Rules",
            c1 = "#0284C7",
            c2 = "#00E5FF",
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

            val modeMsg = if (pickupSwitch.isChecked || dropSwitch.isChecked) {
                "Zone Priority Mode (Any Fare)"
            } else {
                "Fare Mode (₹$fare+)"
            }
            Toast.makeText(this, "✅ Profile Saved: $modeMsg", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Grant Overlay Permission for ZU Smart", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startFloatingWidget()
                Toast.makeText(this, "⚡ ZU Smart Floating HUD Active", Toast.LENGTH_SHORT).show()
            }
        }

        dockRow1.addView(saveBtn)
        dockRow1.addView(hudBtn)

        val dockRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
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
            sub = "System Engine Setup",
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

        // 7. BOTTOM STATUS FOOTER: System Ready | RIDE • EARN • GROW | ● Online
        val footerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        footerSystemText = TextView(this).apply {
            text = "ılılı  System Ready"
            textSize = 8.5f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val footerCenterText = TextView(this).apply {
            text = "RIDE  •  EARN  •  GROW"
            textSize = 8f
            setTextColor(Color.parseColor("#64748B"))
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
        footerBar.addView(footerCenterText)
        footerBar.addView(footerOnlineText)
        mainCol.addView(footerBar)

        masterRoot.addView(mainCol)
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
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(10), dp(4))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (isLeft) rightMargin = dp(4) else leftMargin = dp(4)
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
                if (isChecked) targetSet.add(combinedList[which])
                else targetSet.remove(combinedList[which])
            }
            .setPositiveButton("Apply") { _, _ -> onUpdated() }
            .setNeutralButton("+ Custom Area") { _, _ ->
                val input = EditText(this).apply { hint = "Enter Area or Pincode" }
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
            footerSystemText.text = "ılılı  System Ready"
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

    // Custom Cyberpunk Hero Banner Canvas (Draws City Skyline, Neon Speed Streaks & Futuristic Bike Silhouette)
    class HeroBannerCanvasView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // Deep Cyberpunk Sunset/Night Gradient
            paint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.parseColor("#0A132C"),
                    Color.parseColor("#1E1B4B"),
                    Color.parseColor("#3B0764")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(0f, 0f, w, h, 36f, 36f, paint)
            paint.shader = null

            // Cyber City Skyline Buildings in Center-Right
            paint.color = Color.parseColor("#336366F1")
            val bWidths = floatArrayOf(0.38f, 0.43f, 0.48f, 0.54f, 0.60f, 0.65f)
            val bHeights = floatArrayOf(0.45f, 0.30f, 0.50f, 0.25f, 0.40f, 0.35f)
            for (i in bWidths.indices) {
                val left = w * bWidths[i]
                val top = h * bHeights[i]
                canvas.drawRect(left, top, left + w * 0.04f, h * 0.82f, paint)
            }

            // Glowing Highway Speed Streaks at Bottom
            paint.strokeWidth = 4f
            paint.color = Color.parseColor("#8800E5FF")
            canvas.drawLine(w * 0.1f, h * 0.86f, w * 0.85f, h * 0.86f, paint)
            paint.color = Color.parseColor("#88EC4899")
            canvas.drawLine(w * 0.25f, h * 0.92f, w * 0.95f, h * 0.92f, paint)

            // Futuristic Neon Bike & Rider Silhouette in Center
            val bx = w * 0.53f
            val by = h * 0.72f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.parseColor("#00E5FF")
            canvas.drawCircle(bx - 32f, by, 18f, paint)
            canvas.drawCircle(bx + 32f, by, 18f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#1E293B")
            path.reset()
            path.moveTo(bx - 30f, by)
            path.lineTo(bx - 5f, by - 32f)
            path.lineTo(bx + 22f, by - 22f)
            path.lineTo(bx + 32f, by)
            path.close()
            canvas.drawPath(path, paint)

            // Neon Border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.parseColor("#38BDF8")
            canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, 36f, 36f, paint)
        }
    }

    // Custom Radar Map Canvas View (Draws Cyber Map Grid, Glowing Blue Route Line, Green Pickup Pin & Red Drop Pin)
    class RadarMapCanvasView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val routePath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // Dark Map Base
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#060D1A")
            canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, paint)

            // Perspective Street Grid Lines
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.parseColor("#1E293B")
            for (i in 1..6) {
                val x = (w / 6f) * i
                val y = (h / 6f) * i
                canvas.drawLine(x, 0f, x - 25f, h, paint)
                canvas.drawLine(0f, y, w, y + 15f, paint)
            }

            // Glowing Cyan/Blue Route Path from Pickup to Drop
            val px = w * 0.26f
            val py = h * 0.45f
            val dx = w * 0.78f
            val dy = h * 0.75f

            routePath.reset()
            routePath.moveTo(px, py)
            routePath.lineTo(w * 0.42f, h * 0.62f)
            routePath.lineTo(w * 0.56f, h * 0.55f)
            routePath.lineTo(dx, dy)

            // Outer Glow
            paint.color = Color.parseColor("#5500E5FF")
            paint.strokeWidth = 10f
            canvas.drawPath(routePath, paint)

            // Core Neon Line
            paint.color = Color.parseColor("#00E5FF")
            paint.strokeWidth = 4f
            canvas.drawPath(routePath, paint)

            // Green Pickup Pin & Halo
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#4410B981")
            canvas.drawCircle(px, py, 20f, paint)
            paint.color = Color.parseColor("#10B981")
            canvas.drawCircle(px, py, 9f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Pickup", px - 26f, py - 18f, paint)

            // Red Drop Pin & Halo
            paint.color = Color.parseColor("#44F43F5E")
            canvas.drawCircle(dx, dy, 20f, paint)
            paint.color = Color.parseColor("#F43F5E")
            canvas.drawCircle(dx, dy, 9f, paint)
            paint.color = Color.WHITE
            canvas.drawText("Drop", dx - 20f, dy - 18f, paint)

            // Top-Right "Live Map" Pill
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
