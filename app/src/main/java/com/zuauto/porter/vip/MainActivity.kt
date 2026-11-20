package com.zuauto.porter.vip

import android.app.AlertDialog
import com.zu.auth.AreaCatalog
import com.zu.auth.CityCatalog
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

    private var accountListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var areaListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var accessLocked = false
    private var lockReason: String? = null
    private var purchaseDialog: AlertDialog? = null
    private var restrictionDialog: AlertDialog? = null

    private lateinit var minFareInput: EditText
    private lateinit var fareSwitch: Switch
    private lateinit var pickupSwitch: Switch
    private lateinit var dropSwitch: Switch
    private lateinit var permissionStatus: TextView
    private lateinit var saveProfileButton: Button
    private lateinit var userBox: TextView
    private lateinit var planBadge: TextView
    private lateinit var planValidityText: TextView

    private var isUpdatingSwitches = false
    private var selectedSpeed = "QUICK"
    private var selectedCity = "Bengaluru"
    private val selectedPickupSet = mutableSetOf<String>()
    private val selectedDropSet = mutableSetOf<String>()


    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#10264D")
        window.navigationBarColor = Color.parseColor("#10264D")

        showGoogleLogin()
    }

    private fun showGoogleLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundColor(Color.parseColor("#EEF5FD"))
        }
        root.addView(TextView(this).apply {
            text = "ZU Smart"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10264D"))
        })
        val message = TextView(this).apply {
            text = "Sign in to connect your driver account."
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#42536D"))
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(message)
        val button = Button(this).apply {
            text = "Continue with Google"
            isAllCaps = false
        }
        root.addView(button)
        val switchAccount = Button(this).apply {
            text = "Use another Google account"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                showGoogleLogin()
            }
        }
        root.addView(switchAccount)
        setContentView(root)

        fun connect(details: ZuAutoCloudManager.DriverDetails? = null, formFailure: ((String) -> Unit)? = null) {
            button.isEnabled = false
            switchAccount.visibility = View.GONE
            message.text = "Checking driver access…"
            ZuAutoCloudManager.connectGoogleDriver(this, details, onDetailsRequired = {
                if (!isFinishing && !isDestroyed) {
                    showRequiredDriverDetails { profile, onFailure -> connect(profile, onFailure) }
                }
            }) { ready, error ->
                if (isFinishing || isDestroyed) return@connectGoogleDriver
                val status = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
                    .getString("DRIVER_ACCOUNT_STATUS", "")
                if (ready) {
                    purchaseDialog?.dismiss()
                    purchaseDialog = null
                    showDriverDashboard()
                } else if (status == "suspended" || status == "deleted") {
                    lockDriverAccess(status)
                } else if (formFailure != null) {
                    formFailure(error)
                } else {
                    message.text = error
                    button.text = "Try again"
                    button.isEnabled = true
                    button.setOnClickListener { connect() }
                    switchAccount.visibility = View.VISIBLE
                }
            }
        }
        button.setOnClickListener {
            button.isEnabled = false
            com.zu.auth.GoogleAccountSignIn.signIn(
                this, getString(R.string.default_web_client_id), android.os.CancellationSignal()
            ) { result ->
                if (isFinishing || isDestroyed) return@signIn
                result.onSuccess { connect() }.onFailure {
                    message.text = "Google sign-in did not complete. Try again."
                    button.isEnabled = true
                }
            }
        }
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) connect()
    }

    private fun showRequiredDriverDetails(
        submit: (ZuAutoCloudManager.DriverDetails, (String) -> Unit) -> Unit
    ) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val name = EditText(this).apply {
            hint = "Full name"
            setSingleLine(true)
            setText(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName.orEmpty())
        }
        val mobile = EditText(this).apply {
            hint = "10-digit mobile number"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789")
            filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        }
        var city: String? = null
        val cityPicker = Button(this).apply { text = "Select city ▾" }
        form.addView(name)
        form.addView(mobile)
        form.addView(cityPicker)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Complete driver profile")
            .setView(ScrollView(this).apply { addView(form) })
            .setCancelable(false)
            .setNegativeButton("Close app") { _, _ -> finishAndRemoveTask() }
            .setPositiveButton("Continue", null)
            .show()
        cityPicker.setOnClickListener {
            selectManagedCity { selected ->
                city = selected
                cityPicker.text = "$selected ▾"
            }
        }
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.setOnClickListener {
            val fullName = name.text.toString().trim()
            val number = mobile.text.toString()
            val selected = city
            when {
                fullName.isBlank() || fullName.length > 100 -> name.error = "Enter your full name"
                !number.matches(Regex("[0-9]{10}")) -> mobile.error = "Enter exactly 10 digits"
                selected == null -> Toast.makeText(this, "Select your city.", Toast.LENGTH_SHORT).show()
                else -> {
                    save.isEnabled = false
                    cityPicker.isEnabled = false
                    submit(ZuAutoCloudManager.DriverDetails(fullName, number, selected)) { error ->
                        if (dialog.isShowing) {
                            save.isEnabled = true
                            cityPicker.isEnabled = true
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        purchaseDialog = dialog
    }

    private fun selectManagedCity(onSelected: (String) -> Unit) {
        val loading = AlertDialog.Builder(this).setMessage("Loading cities…").show()
        CityCatalog.fetch { cities ->
            if (!loading.isShowing || isFinishing || isDestroyed) return@fetch
            loading.dismiss()
            if (accessLocked) return@fetch
            if (cities.isNullOrEmpty()) {
                Toast.makeText(this, "No cities available. Contact support or try again.", Toast.LENGTH_LONG).show()
            } else {
                val options = cities.toTypedArray()
                AlertDialog.Builder(this).setTitle("Select city")
                    .setItems(options) { _, selected -> onSelected(options[selected]) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun showDriverDashboard() {
        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        selectedSpeed = prefs.getString("SWIPE_SPEED", "QUICK") ?: "QUICK"
        val profileCity = prefs.getString("USER_CITY", "Bengaluru") ?: "Bengaluru"
        selectedCity = prefs.getString("SELECTED_CITY", profileCity) ?: profileCity

        prefs.getString("PICKUP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedPickupSet.addAll(it)
        }
        prefs.getString("DROP_FILTER", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
            selectedDropSet.addAll(it)
        }

        // Check remote in-app OTA version update
        ZuAutoCloudManager.checkRemoteAppVersion(this) { isForce, newVer, notes, dlUrl ->
            showUpdateDialog(isForce, newVer, notes, dlUrl)
        }

        val configuration = resources.configuration
        val compact = configuration.screenWidthDp < 480 || configuration.fontScale >= 1.3f
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT

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
        addNavItem("☎", "Support", false) { showSupportContact() }
        addNavItem("⚙️", "Settings", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addNavItem("🔄", "Update", false) {
            Toast.makeText(this, "Checking for ZuAuto updates...", Toast.LENGTH_SHORT).show()
            ZuAutoCloudManager.checkRemoteAppVersion(this) { isForce, newVer, notes, dlUrl ->
                showUpdateDialog(isForce, newVer, notes, dlUrl)
            }
        }

        if (!compact) {
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

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(10), dp(8))
            background = createNeonBox("#FFFFFF", "#D8E3EF", 18)
        }
        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBaselineAligned(false)
        }
        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_vip_logo)
            background = createNeonBox("#10264D", "#10264D", 8)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(9) }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
            addView(TextView(context).apply {
                text = "ZU Smart"
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#10264D"))
            })
            addView(TextView(context).apply {
                text = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}"
                textSize = 11f
                setTextColor(Color.parseColor("#42536D"))
            })
        }
        val plansButton = Button(this).apply {
            text = "Plans"
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#805514"))
            background = createNeonBox("#FFF2CF", "#C79838", 20)
            minimumHeight = dp(44)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { showDriverProfileAndValidityDialog(showPlans = true) }
        }
        topHeader.addView(logoImg)
        topHeader.addView(titleCol)
        topHeader.addView(plansButton, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(5) })
        headerCard.addView(topHeader, LinearLayout.LayoutParams(match, wrap))
        headerCard.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#E7EDF5"))
        }, LinearLayout.LayoutParams(match, dp(1)).apply {
            topMargin = dp(7)
            bottomMargin = dp(4)
        })

        val accountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBaselineAligned(false)
        }
        val savedDriverName = prefs.getString("USER_NAME", "Driver") ?: "Driver"
        userBox = TextView(this).apply {
            text = "$savedDriverName ▾"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10264D"))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            minimumHeight = dp(44)
            val profileIcon = context.getDrawable(R.drawable.ic_dashboard_profile)?.mutate()?.apply {
                setBounds(0, 0, dp(20), dp(20))
                setTint(Color.parseColor("#24558A"))
            }
            setCompoundDrawablesRelative(profileIcon, null, null, null)
            compoundDrawablePadding = dp(8)
            setOnClickListener { showDriverProfileAndValidityDialog() }
        }
        val driverSummary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBaselineAligned(false)
        }
        driverSummary.addView(userBox, LinearLayout.LayoutParams(wrap, wrap).apply {
            width = 0
            weight = 1f
            marginEnd = dp(5)
        })
        val planSummary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        planValidityText = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#42536D"))
            setPadding(dp(4), 0, 0, dp(2))
        }
        planBadge = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#805514"))
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = createNeonBox("#FFF2CF", "#E1C389", 10)
        }
        planSummary.addView(planValidityText, LinearLayout.LayoutParams(match, wrap))
        planSummary.addView(planBadge)
        driverSummary.addView(planSummary, LinearLayout.LayoutParams(0, wrap, 1.65f))
        accountRow.addView(driverSummary, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginEnd = dp(6) })
        updatePlanBadge()
        headerCard.addView(accountRow, LinearLayout.LayoutParams(match, wrap))
        mainCol.addView(headerCard, LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(7) })

        val citySelectBtn = TextView(this).apply {
            text = "📍  $selectedCity   ▾"
            textSize = 14f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = createNeonBox("#FFFFFF", "#C79838", 22)
            minimumHeight = dp(44)
            contentDescription = "Operating city: $selectedCity"
        }
        permissionStatus = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minimumHeight = dp(44)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { openMissingPermission() }
        }
        val stackStatus = configuration.screenWidthDp < 320 || configuration.fontScale >= 1.4f
        val cityStatusRow = LinearLayout(this).apply {
            orientation = if (stackStatus) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBaselineAligned(false)
        }
        cityStatusRow.addView(citySelectBtn, if (stackStatus) LinearLayout.LayoutParams(wrap, wrap)
            else LinearLayout.LayoutParams(0, wrap, 1f))
        cityStatusRow.addView(permissionStatus, if (stackStatus) {
            LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(4) }
        } else {
            LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(6) }
        })
        mainCol.addView(cityStatusRow, LinearLayout.LayoutParams(match, wrap).apply {
            bottomMargin = dp(7)
        })

        val heroBanner = ImageView(this).apply {
            contentDescription = "Delivery scooter in front of Vidhana Soudha"
            setImageResource(R.drawable.sapphire_bengaluru)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = createNeonBox("#F7FAFF", "#C79838", 18)
            clipToOutline = true
        }
        mainCol.addView(heroBanner, LinearLayout.LayoutParams(match, dp(if (compact) 96 else 150)).apply {
            bottomMargin = dp(10)
        })
        val shortcutIndex = mainCol.childCount

        val fareCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = createNeonBox("#F7FAFF", "#A9771D", 16)
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(8) }
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
            text = "Minimum fare"
            textSize = 15f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val fSub = TextView(this).apply {
            text = "Apply a minimum fare"
            textSize = 12f
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

        mainCol.addView(fareCard)

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
            text = "Pickup & drop"
            textSize = 15f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val rh2 = TextView(this).apply {
            text = "Zone filters turn off minimum fare"
            textSize = 12f
            setTextColor(Color.parseColor("#42536D"))
        }
        routeHeadCol.addView(rh1)
        routeHeadCol.addView(rh2)

        val pickupTogglePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(match, wrap)
        }
        val pToggleLabel = TextView(this).apply {
            text = "Pickup"
            textSize = 12f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
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
            layoutParams = LinearLayout.LayoutParams(match, wrap)
        }
        val dToggleLabel = TextView(this).apply {
            text = "Drop"
            textSize = 12f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
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
        val sideBySideZones = configuration.screenWidthDp >= 350 && configuration.fontScale < 1.3f
        val zonesLeftCol = LinearLayout(this).apply {
            orientation = if (sideBySideZones) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBaselineAligned(false)
        }

        val pickupBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = createNeonBox("#FFFFFF", "#D9E4F0", 12)
            layoutParams = if (sideBySideZones) {
                LinearLayout.LayoutParams(0, wrap, 1f).apply { marginEnd = dp(6) }
            } else LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(5) }
        }
        val pickupDropdown = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = createNeonBox("#EDF6F5", "#EDF6F5", 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        pickupBoxCard.addView(pickupTogglePill)
        pickupBoxCard.addView(pickupDropdown)

        val dropBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = createNeonBox("#FFFFFF", "#D9E4F0", 12)
            layoutParams = if (sideBySideZones) LinearLayout.LayoutParams(0, wrap, 1f)
                else LinearLayout.LayoutParams(match, wrap)
        }
        val dropDropdown = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = createNeonBox("#FAEFF2", "#FAEFF2", 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        dropBoxCard.addView(dropTogglePill)
        dropBoxCard.addView(dropDropdown)

        zonesLeftCol.addView(pickupBoxCard)
        zonesLeftCol.addView(dropBoxCard)

        zonesLeftCol.layoutParams = LinearLayout.LayoutParams(match, wrap)
        routeMasterCard.addView(zonesLeftCol)
        mainCol.addView(routeMasterCard)

        fun syncAllFilterStates() {
            val fActive = fareSwitch.isChecked
            minFareInput.isEnabled = fActive
            minusBtn.isEnabled = fActive
            plusBtn.isEnabled = fActive
            stepperRow.visibility = if (fActive) View.VISIBLE else View.GONE
            fSub.text = if (fActive) "Apply a minimum fare" else "Fare filter off"

            pickupDropdown.text = if (selectedPickupSet.isEmpty()) {
                "Choose areas  ▾"
            } else {
                "${selectedPickupSet.joinToString(", ")}  ▾"
            }


            dropDropdown.text = if (selectedDropSet.isEmpty()) {
                "Choose areas  ▾"
            } else {
                "${selectedDropSet.joinToString(", ")}  ▾"
            }

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

        fun observeCityAreas() {
            areaListener?.remove()
            val city = selectedCity
            areaListener = AreaCatalog.document(city)
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, _ ->
                    if (accessLocked || selectedCity != city || snapshot == null || snapshot.metadata.isFromCache) {
                        return@addSnapshotListener
                    }
                    val available = AreaCatalog.read(this, city, snapshot)
                        .keys.map { it.lowercase(Locale.ROOT) }.toSet()
                    fun isAvailable(area: String) = area.lowercase(Locale.ROOT) in available
                    val pickupChanged = selectedPickupSet.retainAll(::isAvailable)
                    val dropChanged = selectedDropSet.retainAll(::isAvailable)
                    if (pickupChanged || dropChanged) syncAllFilterStates()
                    if (prefs.getString("SELECTED_CITY", "Bengaluru") == city) {
                        val editor = prefs.edit()
                        for (key in listOf("PICKUP_FILTER", "DROP_FILTER")) {
                            val current = prefs.getString(key, "").orEmpty()
                            val updated = current.split(',').map(String::trim)
                                .filter(::isAvailable).joinToString(",")
                            if (current != updated) editor.putString(key, updated)
                        }
                        editor.apply()
                    }
                }
        }
        observeCityAreas()

        pickupDropdown.setOnClickListener {
            showAreaMultiSelectDialog("Select Pickup Zones ($selectedCity)", selectedPickupSet) {
                pickupSwitch.isChecked = selectedPickupSet.isNotEmpty()
                syncAllFilterStates()
            }
        }

        dropDropdown.setOnClickListener {
            showAreaMultiSelectDialog("Select Drop Zones ($selectedCity)", selectedDropSet) {
                dropSwitch.isChecked = selectedDropSet.isNotEmpty()
                syncAllFilterStates()
            }
        }

        citySelectBtn.setOnClickListener {
            selectManagedCity { chosen ->
                if (chosen != selectedCity) {
                    selectedCity = chosen
                    selectedPickupSet.clear()
                    selectedDropSet.clear()
                    citySelectBtn.text = "📍  $selectedCity   ▾"
                    citySelectBtn.contentDescription = "Operating city: $selectedCity"
                    observeCityAreas()
                    syncAllFilterStates()
                }
            }
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
            text = "Swipe speed"
            textSize = 15f
            setTextColor(Color.parseColor("#10264D"))
            typeface = Typeface.DEFAULT_BOLD
        }
        val st2 = TextView(this).apply {
            text = "Choose a speed"
            textSize = 12f
            setTextColor(Color.parseColor("#42536D"))
        }
        speedTitleCol.addView(st1)
        speedTitleCol.addView(st2)

        speedHeaderRow.addView(boltCircle)
        speedHeaderRow.addView(speedTitleCol)
        speedCard.addView(speedHeaderRow)

        val speedTilesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val speedTileMap = linkedMapOf<String, TextView>()

        fun updateVelocityVisuals() {
            speedTileMap.forEach { (key, tile) ->
                val active = key == selectedSpeed
                tile.isSelected = active
                tile.setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#10264D"))
                tile.background = createNeonBox(
                    if (active) "#18417B" else "#FFFFFF",
                    if (active) "#18417B" else "#CAD7E8",
                    10
                )
            }
        }

        fun addSpeedTile(key: String, label: String, timing: String) {
            val tile = TextView(this).apply {
                text = "$label\n$timing"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minimumHeight = dp(50)
                setPadding(dp(3), dp(4), dp(3), dp(4))
                setOnClickListener {
                    selectedSpeed = key
                    updateVelocityVisuals()
                }
            }
            speedTileMap[key] = tile
            speedTilesRow.addView(tile, LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        addSpeedTile("QUICK", "Quick", "120 ms")
        addSpeedTile("NORMAL", "Normal", "250 ms")
        addSpeedTile("NATURAL", "Human", "450 ms")
        updateVelocityVisuals()

        speedCard.addView(speedTilesRow)
        mainCol.addView(speedCard)

        saveProfileButton = Button(this).apply {
            text = "Save profile"
            isAllCaps = false
            textSize = 12f
            minimumHeight = dp(44)
            setPadding(dp(12), dp(3), dp(12), dp(3))
            setOnClickListener {
                if (!permissionsReady() || accessLocked) {
                    updatePermissionStatus()
                    return@setOnClickListener
                }
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

                if (Settings.canDrawOverlays(this@MainActivity)) startFloatingWidget()
                Toast.makeText(this@MainActivity, "Profile saved", Toast.LENGTH_SHORT).show()
            }
        }

        accountRow.addView(saveProfileButton, LinearLayout.LayoutParams(wrap, wrap))

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
        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun addShortcut(label: String, icon: Int, action: () -> Unit) {
            shortcuts.addView(dashboardAction(label, icon) { action() }.apply {
                minimumHeight = dp(58)
                background = createNeonBox("#FFFFFF", "#D9B36A", 14)
            }, LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        addShortcut("Porter", R.drawable.ic_dashboard_porter) { launchPorterPartnerApp(this) }
        addShortcut("Overlay", R.drawable.ic_dashboard_hud) { hudBtn.performClick() }
        addShortcut("Setup", R.drawable.ic_dashboard_setup) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
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
        if (compact) {
            val bottomNav = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = createNeonBox("#F8FBFF", "#C79838", 14)
            }
            fun addBottomItem(label: String, icon: Int, selected: Boolean = false, action: (View) -> Unit) {
                bottomNav.addView(dashboardAction(label, icon, selected, action),
                    LinearLayout.LayoutParams(0, wrap, 1f).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    })
            }
            addBottomItem("Home", R.drawable.ic_dashboard_home, selected = true) {
                contentScroll.smoothScrollTo(0, 0)
            }
            addBottomItem("Trips", R.drawable.ic_dashboard_trips) { showOrderHistoryDialog() }
            addBottomItem("Profile", R.drawable.ic_dashboard_profile) { showDriverProfileAndValidityDialog() }
            addBottomItem("More", R.drawable.ic_dashboard_more) { showMoreActions(navActions) }
            masterRoot.addView(bottomNav, LinearLayout.LayoutParams(match, wrap))
        }
        setContentView(masterRoot)
        updatePermissionStatus()
        accountListener?.remove()
        accountListener = ZuAutoCloudManager.monitorAccount(this, onRestored = {
            if (!isFinishing && !isDestroyed) {
                if (lockReason == "expired") recreate() else if (!accessLocked) updatePlanBadge()
            }
        }, onRestricted = ::lockDriverAccess)
    }

    private fun showMoreActions(actions: Map<String, () -> Unit>) {
        if (accessLocked) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("More")
            .setView(ScrollView(this).apply { addView(panel) })
            .setNegativeButton("Close", null)
            .create()
        val items = listOf(
            Triple("Porter", "Open Porter Partner", R.drawable.ic_dashboard_porter),
            Triple("Support", "Contact support", android.R.drawable.ic_menu_call),
            Triple("Settings", "Accessibility settings", R.drawable.ic_dashboard_setup),
            Triple("Update", "Check for updates", android.R.drawable.ic_popup_sync)
        )
        items.forEach { (key, label, icon) ->
            val action = actions[key] ?: return@forEach
            panel.addView(Button(this).apply {
                text = label
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(14), dp(8), dp(12), dp(8))
                isAllCaps = false
                minimumHeight = dp(52)
                val drawable = context.getDrawable(icon)?.mutate()?.apply {
                    setBounds(0, 0, dp(22), dp(22))
                    setTint(Color.parseColor("#E9C475"))
                }
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = dp(12)
                setOnClickListener {
                    dialog.dismiss()
                    if (!accessLocked) action()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        dialog.show()
    }

    private fun openExternalAction(intent: Intent, unavailable: String) {
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun supportPanel(explanation: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(8), dp(22), dp(8))
        addView(TextView(context).apply {
            text = listOfNotNull(explanation, "Loading support contact…").joinToString("\n\n")
        })
    }

    private fun loadSupportActions(dialog: AlertDialog, panel: LinearLayout, explanation: String? = null) {
        ZuAutoCloudManager.fetchSupportContact { contact ->
            if (!dialog.isShowing) return@fetchSupportContact
            panel.removeAllViews()
            explanation?.let { message ->
                panel.addView(TextView(this).apply {
                    text = message
                    setPadding(0, 0, 0, dp(8))
                })
            }
            fun addAction(label: String, icon: Int, action: () -> Unit) {
                panel.addView(Button(this).apply {
                    text = label
                    isAllCaps = false
                    minimumHeight = dp(48)
                    setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                    compoundDrawablePadding = dp(8)
                    setOnClickListener { action() }
                })
            }
            val phone = contact?.phone.orEmpty()
            val whatsapp = contact?.whatsapp.orEmpty()
            if (phone.isNotBlank()) addAction("Call  $phone", android.R.drawable.ic_menu_call) {
                openExternalAction(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)), "No phone app found.")
            }
            if (whatsapp.isNotBlank()) addAction("WhatsApp  $whatsapp", R.drawable.ic_support_whatsapp) {
                val chat = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsapp.filter(Char::isDigit)}"))
                for (appPackage in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                    try {
                        startActivity(Intent(chat).setPackage(appPackage))
                        return@addAction
                    } catch (_: android.content.ActivityNotFoundException) {}
                }
                openExternalAction(chat, "WhatsApp is unavailable.")
            }
            if (phone.isBlank() && whatsapp.isBlank()) {
                panel.addView(TextView(this).apply {
                    text = if (contact == null) "Support contact unavailable." else "Support contact has not been configured."
                })
            }
        }
    }

    private fun showSupportContact() {
        if (accessLocked) return
        val panel = supportPanel()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Support")
            .setView(panel)
            .setPositiveButton("Close", null)
            .show()
        loadSupportActions(dialog, panel)
    }

    private fun lockDriverAccess(reason: String) {
        if (isFinishing || isDestroyed || (accessLocked && (lockReason != "expired" || reason == "expired"))) return
        accessLocked = true
        lockReason = reason
        areaListener?.remove()
        areaListener = null
        purchaseDialog?.dismiss()
        restrictionDialog?.dismiss()
        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("BOT_ACTIVE", false).apply()
        stopService(Intent(this, FloatingControlService::class.java))
        if (reason != "expired") {
            prefs.edit().putBoolean("IS_REGISTERED", false).apply()
            accountListener?.remove()
            accountListener = null
        }
        setContentView(FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#10264D")) })

        val title = when (reason) {
            "expired" -> "Plan expired"
            "suspended" -> "Account blocked"
            "deleted" -> "Driver profile removed"
            else -> "Driver access restricted"
        }
        val explanation = if (reason == "expired") "Renew your plan to restore access." else "Contact support for help."
        val panel = supportPanel(explanation)
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(panel)
            .setCancelable(false)
            .setPositiveButton("Close app") { _, _ -> finishAndRemoveTask() }
            .apply { if (reason == "expired") setNeutralButton("Renew plan", null) }
            .create()
        restrictionDialog = dialog
        dialog.show()
        if (reason == "expired") {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                renderDriverProfile(synced = true, showPlans = true)
            }
        }
        dialog.setCanceledOnTouchOutside(false)
        loadSupportActions(dialog, panel, explanation)
    }

    override fun onDestroy() {
        areaListener?.remove()
        accountListener?.remove()
        restrictionDialog?.dismiss()
        purchaseDialog?.dismiss()
        super.onDestroy()
    }

    private fun dashboardAction(
        label: String,
        icon: Int,
        selected: Boolean = false,
        action: (View) -> Unit
    ): TextView = TextView(this).apply {
        val tint = Color.parseColor(if (selected) "#FFFFFF" else "#16345D")
        text = label
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minimumHeight = dp(54)
        setPadding(dp(3), dp(5), dp(3), dp(5))
        setTextColor(tint)
        compoundDrawablePadding = dp(3)
        val symbol = checkNotNull(context.getDrawable(icon)).mutate().apply {
            setBounds(0, 0, dp(20), dp(20))
            setTint(if (selected) Color.parseColor("#F6D78D") else tint)
        }
        setCompoundDrawables(null, symbol, null, null)
        isSelected = selected
        if (selected) background = createNeonBox("#132C51", "#C79838", 12)
        setOnClickListener { action(it) }
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
    // ---------------- MANDATORY OTA IN-APP UPDATE DIALOG ----------------
    private fun showUpdateDialog(isForce: Boolean, newVersionName: String, releaseNotes: String, downloadUrl: String) {
        if (accessLocked || isFinishing || isDestroyed) return
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
    private fun showDriverProfileAndValidityDialog(showPlans: Boolean = false) {
        val loading = AlertDialog.Builder(this)
            .setMessage("Checking subscription…")
            .show()
        ZuAutoCloudManager.syncDriverValidityFromCloud(this) { synced ->
            if (accessLocked || isFinishing || isDestroyed || !loading.isShowing) return@syncDriverValidityFromCloud
            loading.dismiss()
            updatePlanBadge()
            renderDriverProfile(synced, showPlans)
        }
    }

    private fun updatePlanBadge() {
        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val status = prefs.getString("DRIVER_ACCOUNT_STATUS", "active")
        planBadge.text = when (status) {
            "suspended" -> "Blocked"
            "deleted" -> "Deleted"
            else -> prefs.getString("USER_PLAN", "TRIAL") ?: "TRIAL"
        }
        val expiresAt = prefs.getLong("PLAN_EXPIRES_AT_MILLIS", 0L)
        planValidityText.text = if (expiresAt > 0L) {
            val formatted = SimpleDateFormat("dd MMM yyyy, h:mm a z", Locale.getDefault()).format(Date(expiresAt))
            val label = if (expiresAt <= System.currentTimeMillis()) "Expired" else "Valid until"
            "$label $formatted"
        } else "Validity unavailable"
        val driverName = prefs.getString("USER_NAME", "Driver") ?: "Driver"
        userBox.text = "$driverName ▾"
        userBox.setTextColor(Color.parseColor("#10264D"))
    }

    private fun renderDriverProfile(synced: Boolean, showPlans: Boolean = false) {
        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("USER_NAME", "Driver") ?: "Driver"
        val phone = prefs.getString("USER_PHONE", "--") ?: "--"
        val city = prefs.getString("USER_CITY", selectedCity) ?: selectedCity
        val plan = prefs.getString("USER_PLAN", "TRIAL") ?: "TRIAL"
        val expiresAtMillis = prefs.getLong("PLAN_EXPIRES_AT_MILLIS", 0L)
        val now = System.currentTimeMillis()

        val isTrial = plan.equals("TRIAL", ignoreCase = true)
        val isExpired = expiresAtMillis in 1..now
        val isActive = expiresAtMillis == 0L || expiresAtMillis > now
        val status = prefs.getString("DRIVER_ACCOUNT_STATUS", "active")
        val accountAvailable = status != "suspended" && status != "deleted"
        val canPurchase = showPlans && synced && accountAvailable
        val durationDays = prefs.getInt("PLAN_DURATION_DAYS", 0)
        val startedAt = prefs.getLong("PLAN_STARTED_AT_MILLIS", 0L)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
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
            val joinedAt = prefs.getLong("USER_JOINED_AT_MILLIS", 0L)
            val joined = if (joinedAt > 0) {
                SimpleDateFormat("dd MMM yyyy, h:mm a z", Locale.getDefault()).format(Date(joinedAt))
            } else "Unavailable"
            val displayId = com.zu.auth.DriverNumber.label(prefs.getLong("USER_DRIVER_NUMBER", 0L))
                ?: "Pending"
            text = "Driver ID: $displayId\nMobile: $phone\nCity: $city\nJoined: $joined"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        profileCol.addView(nameTv)
        profileCol.addView(metaTv)
        headRow.addView(avatar)
        headRow.addView(profileCol)
        if (!showPlans) root.addView(headRow)

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
            text = if (isExpired) "⚠️ MEMBERSHIP EXPIRED" else if (isTrial) "🕒 3-DAY FREE TRIAL ACTIVE" else "$plan · Active"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isExpired) Color.parseColor("#EF4444") else if (isTrial) Color.parseColor("#FACC15") else Color.parseColor("#10B981"))
        }
        val valSub = TextView(this).apply {
            if (expiresAtMillis > 0L) {
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val expDateStr = sdf.format(Date(expiresAtMillis))
                val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(
                    (expiresAtMillis - now).coerceAtLeast(0L)
                )
                text = if (isExpired) {
                    "Expired on $expDateStr"
                } else {
                    buildString {
                        if (!isTrial && durationDays > 0) append("Plan duration: $durationDays days\n")
                        if (!isTrial && startedAt > 0) append("Starts: ${sdf.format(Date(startedAt))}\n")
                        append("Valid until $expDateStr\n")
                        append("Remaining: ${minutes / 1440}d ${minutes % 1440 / 60}h ${minutes % 60}m")
                    }
                }
            } else {
                text = "Lifetime VIP Access Enabled"
            }
            textSize = 10f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(2), 0, 0)
        }
        if (!accountAvailable) {
            valTitle.text = if (status == "deleted") "Driver profile removed" else "Driver account blocked"
            valTitle.setTextColor(Color.parseColor("#FCA5A5"))
            valSub.text = "Contact the admin for account access."
        }
        validityCard.addView(valTitle)
        validityCard.addView(valSub)
        root.addView(validityCard)

        if (!synced) {
            root.addView(TextView(this).apply {
                text = "Could not refresh subscription. Showing saved status. Reopen Profile to retry."
                setTextColor(Color.parseColor("#FACC15"))
                textSize = 12f
            })
        }

        if (!showPlans) {
            if (accountAvailable && (isTrial || isExpired || expiresAtMillis == 0L)) {
                root.addView(Button(this).apply {
                    text = if (isExpired) "Renew plan" else "View plans"
                    setOnClickListener {
                        purchaseDialog?.dismiss()
                        showDriverProfileAndValidityDialog(showPlans = true)
                    }
                })
            }
            addPaymentHistory(root)
        }

        // Plans Section Title
        val plansTitle = TextView(this).apply {
            text = if (showPlans) "Available plans" else if (isTrial) "Choose a paid plan" else "Renew subscription"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, dp(8))
        }
        if (canPurchase) root.addView(plansTitle)

        // Dynamic Plans Container
        val plansScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        }
        val plansListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        plansScroll.addView(plansListContainer)
        if (canPurchase) root.addView(plansScroll)

        var mainDialog: AlertDialog? = null

        // Fetch Plans from Firestore
        if (canPurchase) ZuAutoCloudManager.fetchSubscriptionPlans { plans ->
            plansListContainer.removeAllViews()
            if (plans.isEmpty()) {
                plansListContainer.addView(TextView(this).apply {
                    text = "No plans available. Contact support."
                    setTextColor(Color.WHITE)
                })
            }
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
                    text = if (isTrial) "Buy · ₹${p.price}" else "Renew · ₹${p.price}"
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
        actionsRow.addView(closeBtn)
        root.addView(actionsRow)

        mainDialog = AlertDialog.Builder(this)
            .setTitle(if (showPlans) "Plans" else "Profile")
            .setView(if (showPlans) root else ScrollView(this).apply { addView(root) })
            .create()
        purchaseDialog = mainDialog
        mainDialog.show()
    }

    private fun addPaymentHistory(parent: LinearLayout) {
        parent.addView(TextView(this).apply {
            text = "Transaction history"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F6D897"))
            setPadding(0, dp(14), 0, dp(8))
        })
        val entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val message = TextView(this).apply {
            text = "Loading transactions…"
            setTextColor(Color.parseColor("#BBC9DD"))
        }
        entries.addView(message)
        parent.addView(entries)
        ZuAutoCloudManager.fetchPaymentHistory { history ->
            if (isDestroyed || isFinishing) return@fetchPaymentHistory
            if (history.isNullOrEmpty()) {
                message.text = if (history == null) "Could not load transactions. Reopen Profile to retry."
                    else "No transactions yet."
                return@fetchPaymentHistory
            }
            entries.removeAllViews()
            fun formattedTime(raw: String): String = runCatching {
                val date = Date(java.time.Instant.parse(raw).toEpochMilli())
                SimpleDateFormat("dd MMM yyyy, h:mm a z", Locale.getDefault()).format(date)
            }.getOrDefault(raw.ifBlank { "Unavailable" })
            history.forEach { item ->
                entries.addView(TextView(this).apply {
                    text = buildString {
                        append("${item.plan} · ₹${item.amount}\n")
                        append("Status: ${item.status.replaceFirstChar { it.uppercase() }}\n")
                        append("UTR: ${item.utr}\nSubmitted: ${formattedTime(item.submittedAt)}")
                        if (item.reviewedAt.isNotBlank()) append("\nReviewed: ${formattedTime(item.reviewedAt)}")
                    }
                    textSize = 13f
                    setTextColor(Color.parseColor("#F4F7FC"))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = createNeonBox("#182841", "#4F5D74", 12)
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }
    }

    // ---------------- UPI CHECKOUT & UTR PROOF SUBMISSION DIALOG ----------------
    private fun showUpiCheckoutDialog(plan: ZuAutoCloudManager.SubscriptionPlan) {
        ZuAutoCloudManager.fetchPaymentSettings { settings ->
            if ((accessLocked && lockReason != "expired") || isFinishing || isDestroyed) return@fetchPaymentSettings
            if (settings == null || settings.upiId.isBlank()) {
                Toast.makeText(this, "Payment settings unavailable. Try again later.", Toast.LENGTH_LONG).show()
            } else {
                renderUpiCheckout(plan, settings)
            }
        }
    }

    private fun renderUpiCheckout(plan: ZuAutoCloudManager.SubscriptionPlan, settings: com.zu.auth.PaymentSettings) {
        val upiId = settings.upiId
        val qrUrl = settings.qrUrl
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        val title = TextView(this).apply {
            text = "UPI checkout"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E9C475"))
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
                setColor(Color.parseColor("#182841"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#4F483D"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        val upiLabel = TextView(this).apply {
            text = "Pay to UPI ID"
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
            text = "Copy UPI ID"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#253A58"))
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
        if (settings.showUpiId) root.addView(upiBox)
        val paymentUri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId)
            .appendQueryParameter("pn", "ZU Smart")
            .appendQueryParameter("am", "${plan.price}.00")
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", plan.name)
            .build()
        val qrImage = ImageView(this).apply {
            contentDescription = "UPI payment QR code for ₹${plan.price}"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(qrImage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(172)))
        val qrNote = TextView(this).apply {
            text = "Scan with a UPI app on another phone"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
        }
        root.addView(qrNote)
        Thread {
            val generated = runCatching {
                val size = 384
                val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                    paymentUri.toString(), com.google.zxing.BarcodeFormat.QR_CODE, size, size
                )
                val pixels = IntArray(size * size) { index ->
                    if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
                }
                Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            }.getOrNull()
            runOnUiThread {
                if (!isDestroyed) {
                    qrImage.setImageBitmap(generated)
                    if (generated == null) qrNote.text = "QR unavailable. Use Pay now."
                }
            }
            val customUri = Uri.parse(qrUrl)
            if (customUri.scheme == "https" && !customUri.host.isNullOrBlank()) {
                val custom = runCatching {
                    val connection = java.net.URL(qrUrl).openConnection() as java.net.HttpURLConnection
                    try {
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(output.size() + count <= 2_000_000)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        check(bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600)
                        val image = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        val pixels = IntArray(image.width * image.height)
                        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                        val source = com.google.zxing.RGBLuminanceSource(image.width, image.height, pixels)
                        val qr = com.google.zxing.qrcode.QRCodeReader().decode(
                            com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
                        )
                        val link = Uri.parse(qr.text)
                        val amount = link.getQueryParameter("am")
                        check(link.scheme == "upi" && link.host == "pay" &&
                            link.getQueryParameter("pa").equals(upiId, ignoreCase = true) &&
                            (amount.isNullOrBlank() || amount.toBigDecimalOrNull()?.compareTo(plan.price.toBigDecimal()) == 0))
                        image
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
                if (custom != null) runOnUiThread {
                    if (!isDestroyed) {
                        qrImage.setImageBitmap(custom)
                        qrNote.text = "Scan on another phone and verify ₹${plan.price} before paying"
                    }
                }
            }
        }.start()
        root.addView(Button(this).apply {
            text = "Pay now · ₹${plan.price}"
            background = createNeonBox("#243A59", "#A98345", 12)
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener {
                openExternalAction(Intent.createChooser(Intent(Intent.ACTION_VIEW, paymentUri), "Choose a UPI app"),
                    "No UPI app found. Scan the QR on another phone.")
            }
        })

        // UTR Input
        val utrLabel = TextView(this).apply {
            text = "12-digit payment reference (UTR)"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(utrLabel)

        val utrInput = EditText(this).apply {
            hint = "e.g. 428901238491"
            inputType = InputType.TYPE_CLASS_NUMBER
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789")
            filters = arrayOf(android.text.InputFilter.LengthFilter(12))
            setSingleLine(true)
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = createNeonBox("#050811", "#E9C475", 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        root.addView(utrInput)

        var checkoutDialog: AlertDialog? = null

        val submitBtn = Button(this).apply {
            text = "Submit payment proof"
            setTextColor(Color.parseColor("#050811"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#F6D897"), Color.parseColor("#D0A350"))
            ).apply { cornerRadius = dp(10).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            setOnClickListener {
                val utr = utrInput.text.toString().trim()
                if (!ZuAutoCloudManager.isValidUtr(utr)) {
                    utrInput.error = "Enter exactly 12 digits"
                    utrInput.requestFocus()
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
        purchaseDialog = checkoutDialog
        checkoutDialog.show()
    }

    // ---------------- LICENSE KEY REDEMPTION DIALOG ----------------
    private fun showAreaMultiSelectDialog(
        title: String,
        targetSet: MutableSet<String>,
        onUpdated: () -> Unit
    ) {
        val city = selectedCity
        val loading = AlertDialog.Builder(this).setMessage("Loading $city areas…").show()
        AreaCatalog.document(city).get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { task ->
            if (!loading.isShowing || isFinishing || isDestroyed) return@addOnCompleteListener
            loading.dismiss()
            if (accessLocked || city != selectedCity) return@addOnCompleteListener
            if (!task.isSuccessful) {
                Toast.makeText(this, "Could not load the area list. Try again.", Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            renderAreaPicker(title, AreaCatalog.read(this, city, task.result), targetSet, onUpdated)
        }
    }

    private fun renderAreaPicker(
        title: String,
        areaPins: Map<String, String>,
        targetSet: MutableSet<String>,
        onUpdated: () -> Unit
    ) {
        val allAreas = areaPins.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val selections = allAreas.filter { area -> targetSet.any { it.equals(area, ignoreCase = true) } }.toMutableSet()
        if (allAreas.isEmpty()) {
            AlertDialog.Builder(this).setTitle(title)
                .setMessage("No areas are available for this city. Contact support.")
                .setPositiveButton("Close", null)
                .show()
            return
        }
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
            .setNegativeButton("Clear All") { _, _ ->
                targetSet.clear()
                onUpdated()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (!accessLocked && ::permissionStatus.isInitialized && ZuAutoCloudManager.isUserRegistered(this)) {
            val expiry = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE).getLong("PLAN_EXPIRES_AT_MILLIS", 0L)
            if (expiry in 1..System.currentTimeMillis()) lockDriverAccess("expired")
        }
        if (accessLocked || !::permissionStatus.isInitialized || !ZuAutoCloudManager.isUserRegistered(this)) return
        updatePermissionStatus()
        if (ZuAutoCloudManager.isUserRegistered(this)) {
            ZuAutoCloudManager.syncDriverValidityFromCloud(this) { synced ->
                if (synced && !isFinishing && !isDestroyed) updatePlanBadge()
            }
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val manager = getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
        val component = android.content.ComponentName(this, AutoAcceptService::class.java)
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val info = service.resolveInfo?.serviceInfo
                info != null && android.content.ComponentName(info.packageName, info.name) == component
            }
    }

    private fun permissionsReady() = Settings.canDrawOverlays(this) && accessibilityEnabled()

    private fun openMissingPermission() {
        if (!accessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun updatePermissionStatus() {
        if (accessLocked || !::permissionStatus.isInitialized || !::saveProfileButton.isInitialized) return
        val ready = permissionsReady()
        permissionStatus.text = if (ready) "✓ Permissions ready" else "● Permission needed"
        permissionStatus.setTextColor(Color.parseColor(if (ready) "#14623F" else "#805514"))
        permissionStatus.background = if (ready) createNeonBox("#E6F5EC", "#76BD96", 16)
            else createNeonBox("#FFF2D5", "#C79838", 16)
        permissionStatus.isClickable = !ready
        saveProfileButton.isEnabled = ready
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updatePermissionStatus()
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
            canvas.drawText("Route preview", w * 0.58f, 32f, paint)
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
