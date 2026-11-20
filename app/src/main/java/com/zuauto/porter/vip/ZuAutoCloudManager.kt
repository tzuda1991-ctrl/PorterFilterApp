package com.zuauto.porter.vip

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

object ZuAutoCloudManager {

    val CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
    val CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME

    private val auth get() = com.google.firebase.auth.FirebaseAuth.getInstance()
    private val FIREBASE_API_KEY get() = com.google.firebase.FirebaseApp.getInstance().options.apiKey
    private val FIRESTORE_BASE_URL
        get() = "https://firestore.googleapis.com/v1/projects/${com.google.firebase.FirebaseApp.getInstance().options.projectId}/databases/(default)/documents"

    private fun URL.authorizedConnection(): HttpURLConnection {
        val user = checkNotNull(auth.currentUser)
        val token = com.google.android.gms.tasks.Tasks.await(
            user.getIdToken(false), 10, java.util.concurrent.TimeUnit.SECONDS
        ).token ?: error("Sign-in token unavailable")
        return (openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class OrderRecord(
        val orderId: String,
        val fare: Int,
        val pickup: String,
        val drop: String,
        val city: String,
        val speed: String,
        val timestamp: String
    )

    data class SubscriptionPlan(
        val id: String,
        val name: String,
        val price: Int,
        val durationDays: Int,
        val badge: String,
        val description: String
    )

    fun fetchPaymentSettings(callback: (String?, String?) -> Unit) {
        executor.execute {
            val result = runCatching {
                val connection = URL("$FIRESTORE_BASE_URL/app_config/payment_settings?key=$FIREBASE_API_KEY")
                    .authorizedConnection()
                try {
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    val fields = connection.inputStream.bufferedReader().use {
                        JSONObject(it.readText()).getJSONObject("fields")
                    }
                    fields.getJSONObject("upiId").getString("stringValue") to
                        fields.optJSONObject("qrUrl")?.optString("stringValue").orEmpty()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            mainHandler.post { callback(result?.first, result?.second) }
        }
    }

    fun isUserRegistered(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        return auth.currentUser?.uid?.let { it == prefs.getString("USER_UID", null) } == true &&
            prefs.getBoolean("IS_REGISTERED", false)
    }

    fun isSubscriptionActive(context: Context): Boolean {
        if (!isUserRegistered(context)) return false
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val accountStatus = prefs.getString("DRIVER_ACCOUNT_STATUS", "active")
        if (accountStatus == "suspended" || accountStatus == "deleted") return false
        val expiresAt = prefs.getLong("PLAN_EXPIRES_AT_MILLIS", 0L)
        if (expiresAt == 0L) return false
        return System.currentTimeMillis() < expiresAt
    }

    // ---------------- REMOTE INBUILT OTA UPDATE CHECK ----------------
    fun checkRemoteAppVersion(
        context: Context,
        onUpdateNeeded: (isForce: Boolean, newVersionName: String, notes: String, downloadUrl: String) -> Unit
    ) {
        @Suppress("DEPRECATION")
        val installedCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/app_config/version_control?key=$FIREBASE_API_KEY")
                val conn = url.authorizedConnection()
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val fields = json.optJSONObject("fields")
                    if (fields != null) {
                        val remoteCode = fields.optJSONObject("currentVersionCode")?.optInt("integerValue", CURRENT_VERSION_CODE)
                            ?: CURRENT_VERSION_CODE
                        val minCode = fields.optJSONObject("minSupportedVersionCode")?.optInt("integerValue", CURRENT_VERSION_CODE)
                            ?: CURRENT_VERSION_CODE
                        val remoteName = fields.optJSONObject("currentVersionName")?.optString("stringValue", CURRENT_VERSION_NAME)
                            ?: CURRENT_VERSION_NAME
                        val downloadUrl = fields.optJSONObject("apkDownloadUrl")?.optString("stringValue", "")
                            ?: ""
                        val releaseNotes = fields.optJSONObject("releaseNotes")?.optString("stringValue", "")
                            ?: ""
                        val forceUpdate = fields.optJSONObject("forceUpdate")?.optBoolean("booleanValue", false)
                            ?: false

                        val isOlder = installedCode < remoteCode
                        val isBelowMin = installedCode < minCode
                        val mustForce = forceUpdate && isOlder || isBelowMin

                        val apk = android.net.Uri.parse(downloadUrl)
                        val hasApkLink = apk.scheme == "https" && !apk.host.isNullOrBlank() &&
                            apk.path?.endsWith(".apk", ignoreCase = true) == true
                        if (hasApkLink && (isOlder || mustForce)) {
                            mainHandler.post {
                                onUpdateNeeded(mustForce, remoteName, releaseNotes, downloadUrl)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ---------------- USER REGISTRATION ----------------
    fun connectGoogleDriver(context: Context, callback: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: return callback(false, "Sign in with Google first.")
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("IS_REGISTERED", false).apply()
        executor.execute {
            val result = runCatching {
                val token = com.google.android.gms.tasks.Tasks.await(
                    user.getIdToken(false), 10, java.util.concurrent.TimeUnit.SECONDS
                )
                val authTime = (token.claims["auth_time"] as Number).toLong()
                val path = "$FIRESTORE_BASE_URL/users/${user.uid}"
                val initial = URL("$path?key=$FIREBASE_API_KEY").authorizedConnection()
                val (code, fields) = try {
                    val code = initial.responseCode
                    val json = if (code == 200) initial.inputStream.bufferedReader().use {
                        JSONObject(it.readText()).getJSONObject("fields")
                    } else null
                    code to json
                } finally {
                    initial.disconnect()
                }
                if (code == 403) error("This account may already be linked to another sign-in. Ask your admin to reset it.")
                check(code == 200 || code == 404) { "Could not verify this device. Try again." }
                val reset = fields?.optJSONObject("sessionAuthTime")?.optString("integerValue") == "0"
                if (reset) {
                    val resetAfter = fields?.optJSONObject("sessionResetAfter")?.optLong("integerValue") ?: 0L
                    check(authTime > resetAfter) { "Phone link was reset. Sign out and choose your Google account again." }
                }
                if (code == 404 || reset) {
                    val parameters = if (reset) "&updateMask.fieldPaths=sessionAuthTime&currentDocument.exists=true"
                        else "&currentDocument.exists=false"
                    val connection = URL("$path?key=$FIREBASE_API_KEY$parameters").authorizedConnection()
                    try {
                        connection.requestMethod = "PATCH"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        val profile = JSONObject()
                            .put("sessionAuthTime", JSONObject().put("integerValue", authTime.toString()))
                        if (!reset) {
                            val now = java.time.Instant.now()
                            mapOf(
                                "uid" to user.uid, "name" to (user.displayName ?: "Driver"),
                                "email" to user.email.orEmpty(), "status" to "active",
                                "planName" to "TRIAL", "subscriptionStatus" to "trial"
                            ).forEach { (key, value) ->
                                profile.put(key, JSONObject().put("stringValue", value))
                            }
                            profile.put("validUntil", JSONObject().put("timestampValue", now.plusSeconds(259200).toString()))
                        }
                        connection.outputStream.bufferedWriter().use {
                            it.write(JSONObject().put("fields", profile).toString())
                        }
                        check(connection.responseCode in 200..299) { "Device could not be linked. Try again or contact admin." }
                    } finally {
                        connection.disconnect()
                    }
                }
                check(auth.currentUser?.uid == user.uid)
                prefs.edit().putString("USER_UID", user.uid).apply()
            }
            mainHandler.post {
                if (result.isFailure) {
                    callback(false, result.exceptionOrNull()?.message ?: "Could not connect.")
                } else {
                    syncDriverValidityFromCloud(context) { synced ->
                        if (synced && auth.currentUser?.uid == user.uid &&
                            prefs.getString("DRIVER_ACCOUNT_STATUS", "") == "active") {
                            prefs.edit().putBoolean("IS_REGISTERED", true).apply()
                            callback(true, "")
                        } else {
                            callback(false, "Driver access unavailable. Check your connection or contact admin.")
                        }
                    }
                }
            }
        }
    }

    // ---------------- SYNC DRIVER VALIDITY FROM CLOUD ----------------
    fun syncDriverValidityFromCloud(context: Context, onSynced: (Boolean) -> Unit) {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("USER_UID", "") ?: ""
        if (uid.isEmpty()) {
            onSynced(false)
            return
        }

        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/users/$uid?key=$FIREBASE_API_KEY")
                val conn = url.authorizedConnection()
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                val code = conn.responseCode
                if (code in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val resp = reader.readText()
                    reader.close()

                    val fields = JSONObject(resp).getJSONObject("fields")
                    fun value(key: String): String {
                        val field = fields.optJSONObject(key) ?: return ""
                        return field.optString("stringValue", field.optString("timestampValue", ""))
                    }
                    fun parseDate(value: String): Long? = runCatching {
                        java.time.Instant.parse(value).toEpochMilli()
                    }.getOrNull()

                    val reset = fields.optJSONObject("sessionAuthTime")?.optLong("integerValue", -1) == 0L
                    val status = if (reset) {
                        prefs.edit().putBoolean("IS_REGISTERED", false).apply()
                        "suspended"
                    } else value("status").lowercase(Locale.ROOT)
                    val editor = prefs.edit().putString(
                        "DRIVER_ACCOUNT_STATUS", when (status) {
                            "suspended", "blocked" -> "suspended"
                            "deleted" -> "deleted"
                            else -> "active"
                        }
                    )
                    mapOf("name" to "USER_NAME", "phone" to "USER_PHONE",
                        "city" to "USER_CITY", "vehicle" to "USER_VEHICLE").forEach { (field, preference) ->
                        value(field).takeIf { it.isNotBlank() }?.let { editor.putString(preference, it) }
                    }
                    editor.apply()

                    val planName = value("planName").ifBlank { value("plan") }
                    val expires = parseDate(value("validUntil")) ?: parseDate(value("planExpiresAt"))
                    check(planName.isNotBlank() && expires != null)
                    val savedPlan = prefs.getString("USER_PLAN", "TRIAL").orEmpty()
                    val localKeyPlan = planName.equals("TRIAL", true) &&
                        !savedPlan.equals("TRIAL", true) &&
                        prefs.getLong("PLAN_EXPIRES_AT_MILLIS", 0L) > expires
                    if (!localKeyPlan) {
                        prefs.edit()
                            .putString("USER_PLAN", planName)
                            .putLong("PLAN_EXPIRES_AT_MILLIS", expires)
                            .putLong("PLAN_STARTED_AT_MILLIS", parseDate(value("subscriptionStartsAt")) ?: 0L)
                            .putInt("PLAN_DURATION_DAYS", value("subscriptionDurationDays").toIntOrNull() ?: 0)
                            .apply()
                    }
                    mainHandler.post { onSynced(true) }
                } else if (code == 403 || code == 404) {
                    prefs.edit().putBoolean("IS_REGISTERED", false).apply()
                    prefs.edit().putString("DRIVER_ACCOUNT_STATUS", if (code == 404) "deleted" else "suspended").apply()
                    mainHandler.post { onSynced(true) }
                } else {
                    mainHandler.post { onSynced(false) }
                }
            } catch (_: Exception) {
                mainHandler.post { onSynced(false) }
            }
        }
    }

    // ---------------- FETCH DYNAMIC SUBSCRIPTION PLANS ----------------
    fun fetchSubscriptionPlans(callback: (List<SubscriptionPlan>) -> Unit) {
        executor.execute {
            val list = mutableListOf<SubscriptionPlan>()
            try {
                val url = URL("$FIRESTORE_BASE_URL:runQuery?key=$FIREBASE_API_KEY")
                val conn = url.authorizedConnection()
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val queryBody = JSONObject().apply {
                    val structured = JSONObject().apply {
                        val fromArr = JSONArray().apply {
                            put(JSONObject().put("collectionId", "subscription_plans"))
                        }
                        put("from", fromArr)
                    }
                    put("structuredQuery", structured)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(queryBody.toString())
                writer.flush()
                writer.close()

                if (conn.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val resp = reader.readText()
                    reader.close()

                    val jsonArray = JSONArray(resp)
                    for (i in 0 until jsonArray.length()) {
                        val row = jsonArray.getJSONObject(i)
                        val doc = row.optJSONObject("document") ?: continue
                        val fields = doc.optJSONObject("fields") ?: continue
                        val path = doc.optString("name", "")
                        val id = path.substringAfterLast("/")

                        val name = fields.optJSONObject("name")?.optString("stringValue", "Plan") ?: "Plan"
                        val price = fields.optJSONObject("price")?.optInt("integerValue", 199) ?: 199
                        val duration = fields.optJSONObject("durationDays")?.optInt("integerValue", 30) ?: 30
                        val badge = fields.optJSONObject("badge")?.optString("stringValue", "POPULAR") ?: "POPULAR"
                        val desc = fields.optJSONObject("description")?.optString("stringValue", "Full automated order acceptance") ?: ""

                        list.add(SubscriptionPlan(id, name, price, duration, badge, desc))
                    }
                }
            } catch (_: Exception) {}

            if (list.isEmpty()) {
                list.add(SubscriptionPlan("plan-daily", "Daily Pass", 49, 1, "QUICK", "1-day unlimited high-speed auto booking"))
                list.add(SubscriptionPlan("plan-weekly", "Weekly Boost", 149, 7, "POPULAR", "7-day fast order snatcher for peak hours"))
                list.add(SubscriptionPlan("plan-monthly", "Pro Monthly VIP", 499, 30, "BEST VALUE", "30-day VIP filter + unlimited high fare orders"))
                list.add(SubscriptionPlan("plan-quarterly", "Super VIP 90-Day", 1199, 90, "ENTERPRISE", "Quarterly automated dominance + priority support"))
            }

            mainHandler.post { callback(list) }
        }
    }

    // ---------------- SUBMIT UPI PAYMENT REQUEST WITH UTR ----------------
    fun submitPaymentProof(
        context: Context,
        plan: SubscriptionPlan,
        utr: String,
        proofData: String,
        callback: (Boolean, String) -> Unit
    ) {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("USER_UID", "DRV-1001") ?: "DRV-1001"
        val driverName = prefs.getString("USER_NAME", "Driver") ?: "Driver"
        val driverPhone = prefs.getString("USER_PHONE", "+91 98765 43210") ?: "+91 98765 43210"

        val reqId = "PAY-" + java.util.UUID.randomUUID().toString()
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/payment_requests/$reqId?key=$FIREBASE_API_KEY")
                val conn = url.authorizedConnection()
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val body = JSONObject().apply {
                    val f = JSONObject()
                    f.put("id", JSONObject().put("stringValue", reqId))
                    f.put("userId", JSONObject().put("stringValue", uid))
                    f.put("userName", JSONObject().put("stringValue", driverName))
                    f.put("userPhone", JSONObject().put("stringValue", driverPhone))
                    f.put("planId", JSONObject().put("stringValue", plan.id))
                    f.put("planName", JSONObject().put("stringValue", plan.name))
                    f.put("amount", JSONObject().put("integerValue", plan.price.toString()))
                    f.put("durationDays", JSONObject().put("integerValue", plan.durationDays.toString()))
                    f.put("utrNumber", JSONObject().put("stringValue", utr.trim()))
                    f.put("proofScreenshot", JSONObject().put("stringValue", proofData))
                    f.put("status", JSONObject().put("stringValue", "pending"))
                    f.put("createdAt", JSONObject().put("stringValue", nowIso))
                    put("fields", f)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()

                val code = conn.responseCode
                if (code in 200..299) {
                    mainHandler.post {
                        callback(true, "Payment request & UTR submitted successfully! Admin approval ke baad validity activate ho jayegi.")
                    }
                } else {
                    mainHandler.post {
                        callback(false, "Payment proof could not be saved (HTTP $code). Retry with the same UTR.")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "Submission could not be confirmed. Keep your UTR for verification.")
                }
            }
        }
    }

    // ---------------- LICENSE KEY ACTIVATION ----------------
    fun recordAcceptedOrder(
        context: Context,
        fare: Int,
        pickup: String,
        drop: String,
        city: String,
        speed: String
    ) {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("USER_UID", "DRV-1001") ?: "DRV-1001"
        val driverName = prefs.getString("USER_NAME", "Sameer") ?: "Sameer"
        val driverPhone = prefs.getString("USER_PHONE", "+91 98765 43210") ?: "+91 98765 43210"

        val orderId = "PTR-ORD-" + java.util.UUID.randomUUID().toString()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val timeStr = sdf.format(Date())

        val historyStr = prefs.getString("LOCAL_ORDER_HISTORY", "[]") ?: "[]"
        val list = JSONArray(historyStr)
        val newOrderObj = JSONObject().apply {
            put("orderId", orderId)
            put("fare", fare)
            put("pickup", pickup)
            put("drop", drop)
            put("city", city)
            put("speed", speed)
            put("timestamp", timeStr)
        }
        list.put(newOrderObj)
        prefs.edit().putString("LOCAL_ORDER_HISTORY", list.toString()).apply()

        // Sync to Cloud Firestore orders collection
        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/orders/$orderId?key=$FIREBASE_API_KEY")
                val conn = url.authorizedConnection()
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

                val body = JSONObject().apply {
                    val f = JSONObject()
                    f.put("orderId", JSONObject().put("stringValue", orderId))
                    f.put("userId", JSONObject().put("stringValue", uid))
                    f.put("driverName", JSONObject().put("stringValue", driverName))
                    f.put("driverPhone", JSONObject().put("stringValue", driverPhone))
                    f.put("fare", JSONObject().put("integerValue", fare.toString()))
                    f.put("pickupArea", JSONObject().put("stringValue", pickup))
                    f.put("dropArea", JSONObject().put("stringValue", drop))
                    f.put("city", JSONObject().put("stringValue", city))
                    f.put("speedMode", JSONObject().put("stringValue", speed))
                    f.put("status", JSONObject().put("stringValue", "Accepted"))
                    f.put("acceptedAt", JSONObject().put("stringValue", nowIso))
                    put("fields", f)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()
                conn.responseCode
            } catch (_: Exception) {}
        }
    }

    fun getLocalOrderHistory(context: Context): List<OrderRecord> {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val historyStr = prefs.getString("LOCAL_ORDER_HISTORY", "[]") ?: "[]"
        val list = mutableListOf<OrderRecord>()
        try {
            val jsonArr = JSONArray(historyStr)
            for (i in (jsonArr.length() - 1) downTo 0) {
                val o = jsonArr.getJSONObject(i)
                list.add(
                    OrderRecord(
                        orderId = o.optString("orderId"),
                        fare = o.optInt("fare"),
                        pickup = o.optString("pickup"),
                        drop = o.optString("drop"),
                        city = o.optString("city"),
                        speed = o.optString("speed"),
                        timestamp = o.optString("timestamp")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
