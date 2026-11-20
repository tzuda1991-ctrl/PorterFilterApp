package com.zuauto.porter.admin

import android.app.AlertDialog
import com.zu.auth.AreaCatalog
import com.zu.auth.CityCatalog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class AdminMainActivity : AppCompatActivity() {

    private var isAuthenticated = false
    private var currentTab = "DASHBOARD" // DASHBOARD, DRIVERS, KEYS, OTA, ORDERS

    private lateinit var contentFrame: FrameLayout
    private lateinit var headerStatusTv: TextView

    private val driverList = mutableListOf<AdminCloudService.DriverItem>()
    private val orderList = mutableListOf<AdminCloudService.OrderItem>()
    private val paymentList = mutableListOf<AdminCloudService.PaymentRequestItem>()

    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (isAuthenticated) {
                refreshData()
            }
            autoRefreshHandler.postDelayed(this, 3500)
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#050811")
        window.navigationBarColor = Color.parseColor("#050811")

        if (!isAuthenticated) {
            showLoginGate()
        } else {
            buildAdminDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticated) {
            autoRefreshHandler.post(autoRefreshRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
    }

    private fun showLoginGate() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(20), dp(28), dp(20))
            setBackgroundColor(Color.parseColor("#050811"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val logo = TextView(this).apply {
            text = "🛡️"
            textSize = 42f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }

        val title = TextView(this).apply {
            text = "ZuAuto Admin"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Executive Fleet & Remote Update Controller"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(24))
        }

        val status = TextView(this).apply {
            text = "Use your authorized Google account."
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
        }
        val loginBtn = Button(this).apply {
            text = "Continue with Google"
            isAllCaps = false
        }
        val signOut = Button(this).apply {
            text = "Use another Google account"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                showLoginGate()
            }
        }
        fun verifyAccess(user: com.google.firebase.auth.FirebaseUser) {
            loginBtn.isEnabled = false
            status.text = "Checking admin access…"
            AdminCloudService.checkAdminAccess(user.uid) { allowed ->
                if (isFinishing || isDestroyed) return@checkAdminAccess
                if (allowed) {
                    isAuthenticated = true
                    buildAdminDashboard()
                    autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
                    autoRefreshHandler.post(autoRefreshRunnable)
                } else {
                    status.text = "Admin access unavailable for ${user.email.orEmpty()}.\nAccount UID: ${user.uid}"
                    signOut.visibility = View.VISIBLE
                    loginBtn.text = "Check access again"
                    loginBtn.isEnabled = true
                    loginBtn.setOnClickListener { verifyAccess(user) }
                }
            }
        }
        loginBtn.setOnClickListener {
            loginBtn.isEnabled = false
            com.zu.auth.GoogleAccountSignIn.signIn(
                this, getString(R.string.default_web_client_id), android.os.CancellationSignal()
            ) { result ->
                if (isFinishing || isDestroyed) return@signIn
                result.onSuccess { verifyAccess(it) }.onFailure {
                    status.text = "Google sign-in did not complete. Try again."
                    loginBtn.isEnabled = true
                }
            }
        }

        root.addView(logo)
        root.addView(title)
        root.addView(sub)
        root.addView(loginBtn)
        root.addView(status)
        root.addView(signOut)
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { verifyAccess(it) }

        setContentView(root)
    }

    private fun buildAdminDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050811"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0A1224"), Color.parseColor("#060B16"))
            ).apply { setStroke(dp(1), Color.parseColor("#162238")) }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appTitle = TextView(this).apply {
            text = Html.fromHtml("<b><font color='#FFFFFF'>ZuAuto</font> <font color='#00E5FF'>ADMIN</font></b>", Html.FROM_HTML_MODE_LEGACY)
            textSize = 18f
        }

        headerStatusTv = TextView(this).apply {
            text = "● Connected (${driverList.size} Drivers)"
            textSize = 10f
            setTextColor(Color.parseColor("#10B981"))
            typeface = Typeface.DEFAULT_BOLD
        }
        titleCol.addView(appTitle)
        titleCol.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}"
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
        })
        titleCol.addView(headerStatusTv)

        val refreshBtn = TextView(this).apply {
            text = "🔄"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener { refreshData() }
        }
        header.addView(titleCol)
        header.addView(refreshBtn)
        root.addView(header)

        // Main Content Container
        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(contentFrame)

        // Bottom Tab Bar
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#080D1A"))
                setStroke(dp(1), Color.parseColor("#162238"))
            }
        }

        val pendingCount = paymentList.count { it.status.equals("pending", true) }
        val payBadgeText = if (pendingCount > 0) "💳 ($pendingCount)" else "💳"
        val tabs = listOf(
            Triple("DASHBOARD", "📊", "Stats"),
            Triple("PAYMENTS", payBadgeText, "Payments"),
            Triple("DRIVERS", "👥", "Drivers"),
            Triple("KEYS", "🔑", "Keys"),
            Triple("OTA", "🚀", "Updates"),
            Triple("ORDERS", "📦", "Orders")
        )

        for ((id, icon, label) in tabs) {
            val tabBtn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(4), dp(4), dp(4), dp(4))

                val ic = TextView(context).apply {
                    text = icon
                    textSize = 15f
                    gravity = Gravity.CENTER
                }
                val lb = TextView(context).apply {
                    text = label
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (currentTab == id) Color.parseColor("#00E5FF") else Color.parseColor("#64748B"))
                    gravity = Gravity.CENTER
                }
                addView(ic)
                addView(lb)

                setOnClickListener {
                    currentTab = id
                    buildAdminDashboard()
                }
            }
            bottomNav.addView(tabBtn)
        }

        root.addView(bottomNav)
        setContentView(root)

        renderCurrentTab()
    }

    private fun renderCurrentTab() {
        contentFrame.removeAllViews()
        when (currentTab) {
            "DASHBOARD" -> renderDashboardView()
            "PAYMENTS" -> renderPaymentsView()
            "DRIVERS" -> renderDriversView()
            "KEYS" -> renderKeysView()
            "OTA" -> renderOtaView()
            "ORDERS" -> renderOrdersView()
        }
    }

    private fun refreshData() {
        AdminCloudService.fetchDrivers { list ->
            driverList.clear()
            driverList.addAll(list)
            if (::headerStatusTv.isInitialized) {
                headerStatusTv.text = "● Connected (${driverList.size} Drivers)"
            }
            if (currentTab == "DASHBOARD" || currentTab == "DRIVERS") {
                renderCurrentTab()
            }
        }
        AdminCloudService.fetchOrders { orders ->
            orderList.clear()
            orderList.addAll(orders)
            if (currentTab == "ORDERS" || currentTab == "DASHBOARD") {
                renderCurrentTab()
            }
        }
        AdminCloudService.fetchPaymentRequests { reqs ->
            paymentList.clear()
            paymentList.addAll(reqs)
            if (currentTab == "PAYMENTS" || currentTab == "DASHBOARD") {
                renderCurrentTab()
            }
        }
    }

    // ---------------- 1. DASHBOARD TAB ----------------
    private fun renderDashboardView() {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val activeCount = driverList.count { it.status.equals("active", true) }
        val totalRevenue = orderList.sumOf { it.fare }

        fun makeKpi(title: String, value: String, sub: String, hex1: String, hex2: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(hex1))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.parseColor(hex2))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }

                val t = TextView(context).apply {
                    text = title
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                val v = TextView(context).apply {
                    text = value
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(2), 0, dp(2))
                }
                val s = TextView(context).apply {
                    text = sub
                    textSize = 9.5f
                    setTextColor(Color.parseColor(hex2))
                }
                addView(t)
                addView(v)
                addView(s)
            }
        }

        col.addView(makeKpi("TOTAL DRIVERS REGISTERED", "${driverList.size}", "● $activeCount Currently Active (incl. ttz)", "#0C162E", "#0284C7"))
        col.addView(makeKpi("TOTAL ORDERS AUTO-ACCEPTED", "${orderList.size}", "100% Automated Instant Dispatch", "#07201C", "#10B981"))
        col.addView(makeKpi("FLEET FARE VOLUME", "₹$totalRevenue", "Recorded accepted rides", "#24180A", "#F59E0B"))

        // Fast Actions Row
        val fastActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }

        val genKeysBtn = Button(this).apply {
            text = "+ GENERATE KEYS"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 10.5f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#00E5FF"))
            ).apply { cornerRadius = dp(10).toFloat() }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) }
            setOnClickListener { showGenerateKeysDialog() }
        }

        val pushOtaBtn = Button(this).apply {
            text = "🚀 PUSH v10 UPDATE"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 10.5f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#A855F7"))
            ).apply { cornerRadius = dp(10).toFloat() }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) }
            setOnClickListener {
                currentTab = "OTA"
                buildAdminDashboard()
            }
        }

        fastActions.addView(genKeysBtn)
        fastActions.addView(pushOtaBtn)
        col.addView(fastActions)

        scroll.addView(col)
        contentFrame.addView(scroll)
    }

    // ---------------- 2. DRIVERS TAB ----------------
    private fun renderDriversView() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val searchIn = EditText(this).apply {
            hint = "Search name, phone, city or driver ID"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D1526"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        col.addView(Button(this).apply {
            text = "City & area management"
            setOnClickListener { showCityManagement() }
        })
        col.addView(searchIn)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val itemsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun renderMatches(query: String) {
            itemsCol.removeAllViews()
            val matches = driverList.filter { driver ->
                listOf(driver.name, driver.phone, driver.uid, driver.city).any {
                    it.contains(query.trim(), ignoreCase = true)
                }
            }
            if (matches.isEmpty()) {
                itemsCol.addView(TextView(this).apply {
                    text = "No matching drivers"
                    setTextColor(Color.parseColor("#CBD5E1"))
                    setPadding(dp(12), dp(20), dp(12), dp(20))
                })
            }
            for (driver in matches) {
                itemsCol.addView(TextView(this).apply {
                    val status = if (driver.status.equals("active", true)) "Active" else "Blocked"
                    text = "${driver.name}   ›\n${driver.phone} · ${driver.city}\n$status · ${driver.plan}\n${driverValidity(driver)}"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#101B30"))
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), Color.parseColor("#314664"))
                    }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
                    setOnClickListener { showDriverDetails(driver) }
                })
            }
        }
        renderMatches("")
        searchIn.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderMatches(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        scroll.addView(itemsCol)
        col.addView(scroll)
        contentFrame.addView(col)
    }

    private fun showCityManagement() {
        val loading = AlertDialog.Builder(this).setMessage("Loading cities…").show()
        CityCatalog.fetch { result ->
            if (!loading.isShowing || isFinishing || isDestroyed) return@fetch
            loading.dismiss()
            if (result == null) {
                Toast.makeText(this, "Could not load cities. Try again.", Toast.LENGTH_LONG).show()
                return@fetch
            }
            val cities = result.sortedWith(String.CASE_INSENSITIVE_ORDER)
            AlertDialog.Builder(this)
                .setTitle("City & area management")
                .setItems(cities.toTypedArray()) { _, selection -> showCityActions(result, cities[selection]) }
                .setPositiveButton("Add city") { _, _ -> showAddCity(result) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showCityActions(cities: List<String>, city: String) {
        AlertDialog.Builder(this)
            .setTitle(city)
            .setItems(arrayOf("Manage areas", "Remove city")) { _, option ->
                if (option == 0) {
                    showAreaManagement(city)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Remove $city?")
                        .setMessage("This removes the city from driver dropdowns. Existing driver profiles and saved area catalogs stay in place.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            saveCities(cities, cities - city) { success ->
                                if (success) showCityManagement()
                            }
                        }
                        .show()
                }
            }
            .setNegativeButton("Back") { _, _ -> showCityManagement() }
            .show()
    }

    private fun showAddCity(cities: List<String>) {
        val input = EditText(this).apply {
            hint = "City name"
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Add city")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .show()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.setOnClickListener {
            val city = input.text.toString().trim()
            when {
                !city.matches(Regex("""[\p{L}\p{N}][\p{L}\p{N} .&'()-]{0,59}""")) ->
                    input.error = "Enter a city name"
                cities.any { it.equals(city, ignoreCase = true) } -> input.error = "City already exists"
                else -> {
                    save.isEnabled = false
                    saveCities(cities, cities + city) { success ->
                        if (!dialog.isShowing) return@saveCities
                        save.isEnabled = true
                        if (success) {
                            dialog.dismiss()
                            showCityManagement()
                        }
                    }
                }
            }
        }
    }

    private fun saveCities(expected: List<String>, updated: List<String>, callback: (Boolean) -> Unit) {
        val document = CityCatalog.document()
        com.google.firebase.firestore.FirebaseFirestore.getInstance().runTransaction { transaction ->
            check(CityCatalog.read(transaction.get(document)) == expected)
            transaction.set(document, mapOf("cities" to updated))
            true
        }.addOnCompleteListener {
            if (!it.isSuccessful) Toast.makeText(this, "Could not save cities. Reopen the list and retry.", Toast.LENGTH_LONG).show()
            callback(it.isSuccessful)
        }
    }

    private fun showAreaManagement(city: String) {
        val loading = AlertDialog.Builder(this).setMessage("Loading $city areas…").show()
        val document = AreaCatalog.document(city)
        document.get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { task ->
            if (!loading.isShowing || isFinishing || isDestroyed) return@addOnCompleteListener
            loading.dismiss()
            if (!task.isSuccessful) {
                Toast.makeText(this, "Could not load areas. Try again.", Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            val areas = AreaCatalog.read(this, city, task.result)
            val names = areas.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
            val visible = names.toMutableList()
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, visible)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
            }
            val search = EditText(this).apply {
                hint = "Search name or PIN"
                setSingleLine(true)
            }
            val list = ListView(this).apply { this.adapter = adapter }
            panel.addView(search)
            panel.addView(list, LinearLayout.LayoutParams(-1,
                dp((resources.configuration.screenHeightDp / 2).coerceIn(100, 360))))
            val dialog = AlertDialog.Builder(this)
                .setTitle("$city · ${areas.size} areas")
                .setView(panel)
                .setNegativeButton("Close", null)
                .setPositiveButton("Add area") { _, _ -> showEditArea(city, areas, null) }
                .show()
            list.setOnItemClickListener { _, _, position, _ ->
                val name = visible[position]
                dialog.dismiss()
                showEditArea(city, areas, name)
            }
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString()?.trim().orEmpty()
                    visible.clear()
                    visible.addAll(names.filter {
                        it.contains(query, ignoreCase = true) || areas[it].orEmpty().contains(query)
                    })
                    adapter.notifyDataSetChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
    }

    private fun showEditArea(city: String, areas: Map<String, String>, original: String?) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val name = EditText(this).apply {
            hint = "Area name"
            setText(original.orEmpty())
        }
        val pins = EditText(this).apply {
            hint = "PIN codes, separated by spaces"
            setText(original?.let { areas[it] }.orEmpty())
        }
        panel.addView(name)
        panel.addView(pins)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (original == null) "Add area in $city" else "Edit area")
            .setView(panel)
            .setNegativeButton("Back") { _, _ -> showAreaManagement(city) }
            .setPositiveButton("Save", null)
            .apply { if (original != null) setNeutralButton("Delete", null) }
            .show()
        fun saveAreas(updated: Map<String, String>) {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            save.isEnabled = false
            delete?.isEnabled = false
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val document = AreaCatalog.document(city)
            firestore.runTransaction { transaction ->
                val latest = AreaCatalog.read(this, city, transaction.get(document))
                check(latest == areas) { "Area list changed. Open it again." }
                transaction.set(document, mapOf("areas" to updated))
                true
            }.addOnCompleteListener { task ->
                if (!dialog.isShowing) return@addOnCompleteListener
                save.isEnabled = true
                delete?.isEnabled = true
                if (task.isSuccessful) {
                    dialog.dismiss()
                    showAreaManagement(city)
                } else {
                    Toast.makeText(this, "Could not save changes. Reopen the city list and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = name.text.toString().trim()
            val codes = pins.text.toString().trim().split(Regex("[, ]+")).filter { it.isNotBlank() }
            when {
                title.isBlank() || title.length > 100 || ',' in title -> name.error = "Enter an area name without commas"
                areas.keys.any { it != original && it.equals(title, ignoreCase = true) } -> name.error = "Area already exists"
                codes.any { !it.matches(Regex("[0-9]{6}")) } -> pins.error = "Enter six-digit PIN codes"
                else -> saveAreas(areas.toMutableMap().apply {
                    original?.let(::remove)
                    put(title, codes.distinct().joinToString(" "))
                })
            }
        }
        if (original != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete $original?")
                .setMessage("This area will disappear from the driver area's selection list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> saveAreas(areas - original) }
                .show()
        }
    }

    private fun driverValidity(driver: AdminCloudService.DriverItem): String {
        val expiry = runCatching { java.time.Instant.parse(driver.planExpiresAt) }.getOrNull()
            ?: return "Expiry unavailable"
        val date = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")
            .withZone(java.time.ZoneId.systemDefault()).format(expiry)
        return if (expiry.isAfter(java.time.Instant.now())) "Valid until $date" else "Expired on $date"
    }

    private fun showDriverDetails(driver: AdminCloudService.DriverItem) {
        val blocked = !driver.status.equals("active", true)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        panel.addView(TextView(this).apply {
            text = "Driver ID: ${driver.uid}\nPhone: ${driver.phone}\nCity: ${driver.city}\nVehicle: ${driver.vehicle}\nStatus: ${if (blocked) "Blocked" else "Active"}\nPlan: ${driver.plan}\n${driverValidity(driver)}"
            textSize = 15f
            setPadding(0, 0, 0, dp(12))
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(driver.name)
            .setView(ScrollView(this).apply { addView(panel) })
            .setNegativeButton("Close", null)
            .create()

        fun action(label: String, onClick: () -> Unit) {
            panel.addView(Button(this).apply {
                text = label
                isAllCaps = false
                minimumHeight = dp(48)
                setOnClickListener { onClick() }
            })
        }

        action("Edit profile") {
            dialog.dismiss()
            showEditDriver(driver)
        }
        action("Extend validity") {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Extend validity for ${driver.name}")
                .setItems(arrayOf("7 days", "30 days", "90 days")) { _, index ->
                    AdminCloudService.extendDriverSubscription(driver, intArrayOf(7, 30, 90)[index]) {
                        finishDriverChange(it, "Validity extended.")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        action("Schedule plan expiry") {
            val options = arrayOf("Now", "In 5 minutes", "In 20 minutes", "In 1 day")
            val delays = longArrayOf(0, 5, 20, 1440)
            AlertDialog.Builder(this)
                .setTitle("Expire plan for ${driver.name}")
                .setItems(options) { _, selected ->
                    AlertDialog.Builder(this)
                        .setTitle("Confirm plan expiry")
                        .setMessage("Expire ${driver.name}'s plan ${options[selected].lowercase(Locale.ROOT)}?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Confirm") { _, _ ->
                            dialog.dismiss()
                            AdminCloudService.scheduleDriverExpiry(driver, delays[selected]) {
                                finishDriverChange(it, "Plan expiry updated.")
                            }
                        }
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        val toggle = if (blocked) "Unblock driver" else "Block driver"
        action(toggle) {
            AlertDialog.Builder(this)
                .setTitle("$toggle?")
                .setMessage(driver.name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(toggle) { _, _ ->
                    dialog.dismiss()
                    AdminCloudService.updateDriverStatus(driver.uid, if (blocked) "active" else "suspended") {
                        finishDriverChange(it, if (blocked) "Driver unblocked." else "Driver blocked.")
                    }
                }
                .show()
        }
        action("Reset phone link") {
            AlertDialog.Builder(this)
                .setTitle("Reset phone link?")
                .setMessage("The next sign-in can link this driver's Google account again. Plan validity is preserved.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ ->
                    dialog.dismiss()
                    AdminCloudService.resetDriverSession(driver.uid) {
                        finishDriverChange(it, "Phone link reset.")
                    }
                }
                .show()
        }
        action("Delete profile") {
            AlertDialog.Builder(this)
                .setTitle("Delete ${driver.name}?")
                .setMessage("This removes ${driver.uid} from the driver list and disables access. Payment and order records stay in history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete profile") { _, _ ->
                    dialog.dismiss()
                    AdminCloudService.deleteDriver(driver.uid) { finishDriverChange(it, "Driver profile deleted.") }
                }
                .show()
        }
        dialog.show()
    }

    private fun showEditDriver(driver: AdminCloudService.DriverItem) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        fun field(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            panel.addView(TextView(this).apply { text = label })
            return EditText(this).apply {
                inputType = type
                setText(value)
                panel.addView(this)
            }
        }
        val name = field("Driver name", driver.name)
        val phone = field("Phone", driver.phone, InputType.TYPE_CLASS_PHONE)
        val city = field("City", driver.city)
        val vehicle = field("Vehicle", driver.vehicle)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit driver")
            .setView(ScrollView(this).apply { addView(panel) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .show()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.setOnClickListener {
            val values = listOf(name, phone, city, vehicle).map { it.text.toString().trim() }
            val missing = values.indexOfFirst { it.isEmpty() }
            if (missing >= 0) {
                listOf(name, phone, city, vehicle)[missing].error = "Required"
                return@setOnClickListener
            }
            save.isEnabled = false
            AdminCloudService.editDriver(driver.uid, values[0], values[1], values[2], values[3]) { success ->
                save.isEnabled = true
                if (success) dialog.dismiss()
                finishDriverChange(success, "Profile updated.")
            }
        }
    }

    private fun finishDriverChange(success: Boolean, message: String) {
        Toast.makeText(this, if (success) message else "Change failed. Refresh and try again.", Toast.LENGTH_LONG).show()
        refreshData()
    }

    // ---------------- 3. KEYS TAB ----------------
    private fun renderKeysView() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val topBtn = Button(this).apply {
            text = "⚡ GENERATE BATCH LICENSE KEYS"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#00E5FF"))
            ).apply { cornerRadius = dp(12).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener { showGenerateKeysDialog() }
        }
        col.addView(topBtn)

        val infoTv = TextView(this).apply {
            text = "Generated keys can be shared with drivers to unlock app access immediately."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        col.addView(infoTv)

        contentFrame.addView(col)
    }

    private fun showGenerateKeysDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val title = TextView(this).apply {
            text = "Generate License Keys"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(title)

        val daysSpinner = Spinner(this)
        val durations = arrayOf("30 Days (1 Month)", "7 Days (1 Week)", "90 Days (3 Months)", "365 Days (1 Year)")
        daysSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, durations)
        root.addView(daysSpinner)

        val countIn = EditText(this).apply {
            hint = "Quantity (e.g. 5)"
            setText("3")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
        }
        root.addView(countIn)

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Generate", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val count = countIn.text.toString().toIntOrNull() ?: 3
            val selectedDays = when (daysSpinner.selectedItemPosition) {
                0 -> 30
                1 -> 7
                2 -> 90
                else -> 365
            }

            Toast.makeText(this, "Generating $count keys...", Toast.LENGTH_SHORT).show()
            AdminCloudService.generateLicenseKeys(count, "vip", selectedDays) { keys ->
                dialog.dismiss()
                showKeysResultDialog(keys)
            }
        }
    }

    private fun showKeysResultDialog(keys: List<String>) {
        val allKeysText = keys.joinToString("\n")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val t = TextView(this).apply {
            text = "✅ ${keys.size} Keys Created!"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
        }
        val box = TextView(this).apply {
            text = allKeysText
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#050811"))
                cornerRadius = dp(8).toFloat()
            }
        }
        root.addView(t)
        root.addView(box)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Copy All") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ZuAuto Keys", allKeysText))
                Toast.makeText(this, "Copied to Clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Share via WhatsApp") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "⚡ ZuAuto VIP License Keys:\n\n$allKeysText\n\nDownload App: https://github.com/tzuda1991-ctrl/PorterFilterApp")
                }
                startActivity(Intent.createChooser(intent, "Share Keys"))
            }
            .show()
    }

    // ---------------- 4. OTA UPDATES TAB ----------------
    private fun renderOtaView() {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val title = TextView(this).apply {
            text = "🚀 Remote App Version & OTA Update"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val sub = TextView(this).apply {
            text = "Push mandatory updates directly to driver phones."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(12))
        }
        col.addView(title)
        col.addView(sub)

        val verCodeIn = EditText(this).apply {
            hint = "Version Code (e.g. 10)"
            setText("10")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1428"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val verNameIn = EditText(this).apply {
            hint = "Version Name (e.g. 10.0-ZuAuto)"
            setText("10.0-ZuAuto")
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1428"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val dlUrlIn = EditText(this).apply {
            hint = "Direct APK Link (https://.../app-debug.apk)"
            setText("https://github.com/tzuda1991-ctrl/PorterFilterApp/releases/download/v10.0/ZuAuto-v10.0.apk")
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1428"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val notesIn = EditText(this).apply {
            hint = "Changelog Release Notes"
            setText("• Major ZuAuto Rebrand\n• Inbuilt OTA Update Engine\n• Instant Key Licensing\n• Cloud Order Telemetry")
            setTextColor(Color.WHITE)
            setLines(3)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1428"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val forceSwitch = Switch(this).apply {
            text = "Mandatory Force Update (Block v9)"
            setTextColor(Color.parseColor("#F43F5E"))
            isChecked = true
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#F43F5E"))
        }

        col.addView(verCodeIn)
        col.addView(verNameIn)
        col.addView(dlUrlIn)
        col.addView(notesIn)
        col.addView(forceSwitch)

        val pushBtn = Button(this).apply {
            text = "PUSH UPDATE TO ALL DRIVERS"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#DC2626"), Color.parseColor("#F43F5E"))
            ).apply { cornerRadius = dp(12).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(16)
            }
            setOnClickListener {
                val code = verCodeIn.text.toString().toIntOrNull() ?: 0
                val name = verNameIn.text.toString().trim()
                val url = dlUrlIn.text.toString().trim()
                val notes = notesIn.text.toString().trim()
                val force = forceSwitch.isChecked

                val apk = android.net.Uri.parse(url)
                if (code <= 0 || name.isBlank() || apk.scheme != "https" ||
                    apk.host.isNullOrBlank() || apk.path?.endsWith(".apk", ignoreCase = true) != true) {
                    Toast.makeText(this@AdminMainActivity, "Enter a version and direct HTTPS APK link", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                AdminCloudService.pushNewVersion(
                    code = code,
                    name = name,
                    minCode = if (force) code else 1,
                    dlUrl = url,
                    notes = notes,
                    force = force,
                    notice = "⚡ ZuAuto v$name update required!"
                ) { success ->
                    if (success) {
                        Toast.makeText(this@AdminMainActivity, "✅ Update Pushed to Firebase!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@AdminMainActivity, "Error pushing update", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        col.addView(pushBtn)

        AdminCloudService.fetchVersionConfig { code, name, _, url, notes, force ->
            verCodeIn.setText(code.toString())
            verNameIn.setText(name)
            dlUrlIn.setText(url)
            notesIn.setText(notes)
            forceSwitch.isChecked = force
        }

        scroll.addView(col)
        contentFrame.addView(scroll)
    }

    // ---------------- 5. ORDERS TAB ----------------
    private fun renderOrdersView() {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val headTv = TextView(this).apply {
            text = "Live Accepted Orders (${orderList.size})"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }
        col.addView(headTv)

        if (orderList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No orders accepted yet."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(40))
            }
            col.addView(emptyTv)
        } else {
            for (ord in orderList) {
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#081022"))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                }

                val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val fareTv = TextView(this).apply {
                    text = "₹${ord.fare}"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#00E5FF"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val driverTv = TextView(this).apply {
                    text = "${ord.driverName} (${ord.speed})"
                    textSize = 10f
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                row1.addView(fareTv)
                row1.addView(driverTv)

                val routeTv = TextView(this).apply {
                    text = "🟢 ${ord.pickup} → 🔴 ${ord.drop}"
                    textSize = 10f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setPadding(0, dp(2), 0, 0)
                }

                item.addView(row1)
                item.addView(routeTv)
                col.addView(item)
            }
        }

        scroll.addView(col)
        contentFrame.addView(scroll)
    }

    // ---------------- PAYMENTS VERIFICATION TAB ----------------
    private fun showPlanManagement() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Plan management")
            .setView(ScrollView(this).apply { addView(panel) })
            .setNegativeButton("Close", null)
            .setPositiveButton("Add plan") { _, _ -> showEditPlan(null) }
            .show()
        val plans = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("subscription_plans")
        plans.get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { task ->
            if (!dialog.isShowing) return@addOnCompleteListener
            if (!task.isSuccessful || task.result.isEmpty) {
                panel.addView(TextView(this).apply {
                    text = if (task.isSuccessful) "No plans yet. Add a plan to show it in the driver app."
                        else "Could not load plans. Reopen Plan management to retry."
                })
                return@addOnCompleteListener
            }
            task.result.documents.sortedBy { it.getLong("price") ?: Long.MAX_VALUE }.forEach { plan ->
                panel.addView(Button(this).apply {
                    val name = plan.getString("name").orEmpty()
                    val days = plan.getLong("durationDays") ?: 0
                    val price = plan.getLong("price") ?: 0
                    val state = if (plan.getBoolean("enabled") == false) "Hidden" else "Visible"
                    text = "$name · ₹$price · $days days\n$state"
                    isAllCaps = false
                    setOnClickListener {
                        dialog.dismiss()
                        showEditPlan(plan)
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }

    private fun showEditPlan(plan: com.google.firebase.firestore.DocumentSnapshot?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        fun field(label: String, value: String, type: Int): EditText {
            form.addView(TextView(this).apply { text = label })
            return EditText(this).apply {
                inputType = type
                setText(value)
                form.addView(this)
            }
        }
        val name = field("Plan name", plan?.getString("name").orEmpty(), InputType.TYPE_CLASS_TEXT)
        val amount = field("Price (₹)", plan?.getLong("price")?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val days = field("Validity (days)", plan?.getLong("durationDays")?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val details = field("Description", plan?.getString("description").orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val enabled = Switch(this).apply {
            text = "Show in driver app"
            isChecked = plan?.getBoolean("enabled") != false
        }
        form.addView(enabled)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (plan == null) "Add plan" else "Edit plan")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Back") { _, _ -> showPlanManagement() }
            .setPositiveButton("Save", null)
            .show()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.setOnClickListener {
            val title = name.text.toString().trim()
            val price = amount.text.toString().toLongOrNull()
            val duration = days.text.toString().toLongOrNull()
            when {
                title.isEmpty() -> name.error = "Enter a plan name"
                price == null || price !in 1..1_000_000 -> amount.error = "Enter a valid price"
                duration == null || duration !in 1..3650 -> days.error = "Enter 1 to 3650 days"
                else -> {
                    val document = plan?.reference ?: com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("subscription_plans").document()
                    save.isEnabled = false
                    document.set(mapOf(
                        "name" to title,
                        "price" to price,
                        "durationDays" to duration,
                        "description" to details.text.toString().trim(),
                        "enabled" to enabled.isChecked
                    ), com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener {
                        if (!dialog.isShowing) return@addOnCompleteListener
                        save.isEnabled = true
                        if (it.isSuccessful) {
                            dialog.dismiss()
                            showPlanManagement()
                        } else {
                            Toast.makeText(this, "Could not save plan. Try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showSupportSettings() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }
        val phone = EditText(this).apply {
            hint = "Support phone with country code"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val whatsapp = EditText(this).apply {
            hint = "WhatsApp number with country code"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        form.addView(phone)
        form.addView(whatsapp)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Support contact")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .show()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.isEnabled = false
        val document = com.zu.auth.SupportContact.document()
        document.get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { task ->
            if (!dialog.isShowing) return@addOnCompleteListener
            if (task.isSuccessful) {
                phone.setText(task.result.getString("phone").orEmpty())
                whatsapp.setText(task.result.getString("whatsapp").orEmpty())
                save.isEnabled = true
            } else {
                Toast.makeText(this, "Could not load support settings.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }
        save.setOnClickListener {
            val numbers = listOf(phone, whatsapp)
            val values = numbers.map { it.text.toString().trim().replace(Regex("[ ()-]"), "") }
            val invalid = values.indexOfFirst { it.isNotEmpty() && !it.matches(Regex("[+][1-9][0-9]{7,14}")) }
            if (invalid >= 0) {
                numbers[invalid].error = "Use an international number such as +919876543210"
                return@setOnClickListener
            }
            save.isEnabled = false
            document.set(mapOf("phone" to values[0], "whatsapp" to values[1])).addOnCompleteListener { task ->
                save.isEnabled = true
                if (task.isSuccessful) dialog.dismiss()
                Toast.makeText(this, if (task.isSuccessful) "Support contact saved." else "Save failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPaymentSettings() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        val upi = EditText(this).apply { hint = "UPI ID" }
        val qr = EditText(this).apply { hint = "Custom QR image URL (HTTPS)" }
        val showUpi = Switch(this).apply {
            text = "Show UPI ID in checkout"
            isChecked = true
            minHeight = dp(48)
        }
        form.addView(upi)
        form.addView(showUpi)
        form.addView(qr)
        form.addView(TextView(this).apply {
            text = "Leave blank to generate a QR from the saved UPI ID and plan amount."
            setPadding(0, dp(6), 0, dp(6))
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Payment settings")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .show()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.isEnabled = false
        AdminCloudService.fetchPaymentSettings { settings ->
            if (!dialog.isShowing) return@fetchPaymentSettings
            upi.setText(settings?.upiId.orEmpty())
            qr.setText(settings?.qrUrl.orEmpty())
            showUpi.isChecked = settings?.showUpiId ?: true
            save.isEnabled = true
        }
        save.setOnClickListener {
            val id = upi.text.toString().trim()
            val url = qr.text.toString().trim()
            val uri = android.net.Uri.parse(url)
            if (!id.matches(Regex("[A-Za-z0-9._-]+@[A-Za-z0-9._-]+"))) {
                upi.error = "Enter a valid UPI ID"
            } else if (url.isNotEmpty() && (uri.scheme != "https" || uri.host.isNullOrBlank())) {
                qr.error = "Enter an HTTPS image URL"
            } else {
                save.isEnabled = false
                AdminCloudService.savePaymentSettings(com.zu.auth.PaymentSettings(id, url, showUpi.isChecked)) { success ->
                    save.isEnabled = true
                    if (success) dialog.dismiss()
                    Toast.makeText(this, if (success) "Payment settings saved" else "Save failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderPaymentsView() {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val pendingCount = paymentList.count { it.status.equals("pending", true) }
        val headTv = TextView(this).apply {
            text = "💳 Payment Requests & UTR Proofs ($pendingCount Pending)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }
        col.addView(Button(this).apply {
            text = "UPI & QR SETTINGS"
            setOnClickListener { showPaymentSettings() }
        })
        col.addView(Button(this).apply {
            text = "SUPPORT CONTACT"
            setOnClickListener { showSupportSettings() }
        })
        col.addView(Button(this).apply {
            text = "PLAN MANAGEMENT"
            setOnClickListener { showPlanManagement() }
        })
        col.addView(headTv)

        if (paymentList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No payment requests submitted yet.\nUsers can buy plans via UPI in Driver App."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(40))
            }
            col.addView(emptyTv)
        } else {
            for (req in paymentList) {
                val isPending = req.status.equals("pending", true)
                val isApproved = req.status.equals("approved", true)

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#081022"))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), if (isPending) Color.parseColor("#F59E0B") else Color.parseColor("#1E293B"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }

                // Row 1: User Name & Plan
                val r1 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val nameTv = TextView(this).apply {
                    text = "${req.userName} • ₹${req.amount}"
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val statusBadge = TextView(this).apply {
                    text = req.status.uppercase(Locale.US)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isPending) Color.parseColor("#F59E0B") else if (isApproved) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    background = GradientDrawable().apply {
                        setColor(if (isPending) Color.parseColor("#261C08") else if (isApproved) Color.parseColor("#062817") else Color.parseColor("#280B0B"))
                        cornerRadius = dp(6).toFloat()
                    }
                }
                r1.addView(nameTv)
                r1.addView(statusBadge)
                card.addView(r1)

                // Meta: Phone & Plan
                val metaTv = TextView(this).apply {
                    text = "${req.planName} (${req.durationDays} Days) • Phone: ${req.userPhone}"
                    textSize = 10.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(2), 0, dp(4))
                }
                card.addView(metaTv)

                // UTR Row
                val utrRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#0F172A"))
                        cornerRadius = dp(6).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                }
                val utrTv = TextView(this).apply {
                    text = "UTR: ${req.utrNumber}"
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#00E5FF"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val copyUtr = TextView(this).apply {
                    text = "COPY"
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#38BDF8"))
                    setOnClickListener {
                        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("UTR", req.utrNumber))
                        Toast.makeText(this@AdminMainActivity, "UTR Copied!", Toast.LENGTH_SHORT).show()
                    }
                }
                utrRow.addView(utrTv)
                utrRow.addView(copyUtr)
                card.addView(utrRow)

                // Action Buttons for Pending Requests
                if (isPending) {
                    val btnRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    val approveBtn = Button(this).apply {
                        text = "✓ APPROVE & ACTIVATE"
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.parseColor("#050811"))
                        background = GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#059669"))
                        ).apply { cornerRadius = dp(6).toFloat() }
                        layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { rightMargin = dp(6) }
                        setOnClickListener {
                            AdminCloudService.approvePaymentRequest(req) { success, msg ->
                                Toast.makeText(this@AdminMainActivity, msg, Toast.LENGTH_LONG).show()
                                refreshData()
                            }
                        }
                    }
                    val rejectBtn = Button(this).apply {
                        text = "✕ REJECT"
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#991B1B"))
                            cornerRadius = dp(6).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.6f)
                        setOnClickListener {
                            AdminCloudService.rejectPaymentRequest(req.id) { success, msg ->
                                Toast.makeText(this@AdminMainActivity, msg, Toast.LENGTH_SHORT).show()
                                refreshData()
                            }
                        }
                    }
                    btnRow.addView(approveBtn)
                    btnRow.addView(rejectBtn)
                    card.addView(btnRow)
                }

                col.addView(card)
            }
        }

        scroll.addView(col)
        contentFrame.addView(scroll)
    }

}