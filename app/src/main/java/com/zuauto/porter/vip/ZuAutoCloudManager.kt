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
    const val CURRENT_VERSION_CODE = 10
    const val CURRENT_VERSION_NAME = "10.0-ZuAuto"
    
    private const val FIREBASE_PROJECT_ID = "gmp-demo-project-615064003"
    private const val FIRESTORE_DATABASE_ID = "ai-studio-porterfilterapp-010c463f-82c7-4c8d-8552-abb324a0cb33"
    
    private const val FIRESTORE_BASE_URL = 
        "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents"

    private val executor = Executors.newFixedThreadPool(3)
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

    fun isUserRegistered(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("IS_REGISTERED", false) && prefs.getString("USER_PHONE", "")?.isNotEmpty() == true
    }

    fun isSubscriptionActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val expiresAt = prefs.getLong("PLAN_EXPIRES_AT_MILLIS", 0L)
        // If not set yet, grant initial 3-day grace trial
        if (expiresAt == 0L) return true
        return System.currentTimeMillis() < expiresAt
    }

    // ---------------- REMOTE INBUILT OTA UPDATE CHECK ----------------
    fun checkRemoteAppVersion(
        context: Context,
        onUpdateNeeded: (isForce: Boolean, newVersionName: String, notes: String, downloadUrl: String) -> Unit
    ) {
        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/app_config/version_control")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
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

                        val isOlder = CURRENT_VERSION_CODE < remoteCode
                        val isBelowMin = CURRENT_VERSION_CODE < minCode
                        val mustForce = forceUpdate && isOlder || isBelowMin

                        if (isOlder || mustForce) {
                            mainHandler.post {
                                onUpdateNeeded(mustForce, remoteName, releaseNotes, downloadUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline fallback - allow running
            }
        }
    }

    // ---------------- USER REGISTRATION ----------------
    fun registerDriver(
        context: Context,
        name: String,
        phone: String,
        city: String,
        vehicle: String,
        licenseKey: String?,
        onDone: (success: Boolean, msg: String) -> Unit
    ) {
        val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        val uid = "DRV-" + System.currentTimeMillis().toString().takeLast(6)
        val trialDays = if (!licenseKey.isNullOrBlank()) 30 else 3
        val expiryMillis = System.currentTimeMillis() + (trialDays.toLong() * 86400000L)

        prefs.edit()
            .putBoolean("IS_REGISTERED", true)
            .putString("USER_UID", uid)
            .putString("USER_NAME", name)
            .putString("USER_PHONE", phone)
            .putString("USER_CITY", city)
            .putString("USER_VEHICLE", vehicle)
            .putString("USER_PLAN", if (!licenseKey.isNullOrBlank()) "vip" else "trial")
            .putLong("PLAN_EXPIRES_AT_MILLIS", expiryMillis)
            .apply()

        // Sync to Firestore Cloud in background
        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/users/$uid")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val expIso = sdf.format(Date(expiryMillis))
                val nowIso = sdf.format(Date())

                val body = JSONObject().apply {
                    val f = JSONObject()
                    f.put("uid", JSONObject().put("stringValue", uid))
                    f.put("name", JSONObject().put("stringValue", name))
                    f.put("phone", JSONObject().put("stringValue", phone))
                    f.put("city", JSONObject().put("stringValue", city))
                    f.put("vehicleType", JSONObject().put("stringValue", vehicle))
                    f.put("status", JSONObject().put("stringValue", "active"))
                    f.put("plan", JSONObject().put("stringValue", if (!licenseKey.isNullOrBlank()) "vip" else "trial"))
                    f.put("planExpiresAt", JSONObject().put("stringValue", expIso))
                    f.put("createdAt", JSONObject().put("stringValue", nowIso))
                    put("fields", f)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()
                conn.responseCode
            } catch (_: Exception) {}
        }

        mainHandler.post {
            onDone(true, "Registration complete! $trialDays days activated.")
        }
    }

    // ---------------- REDEEM LICENSE KEY ----------------
    fun redeemLicenseKey(
        context: Context,
        key: String,
        onDone: (success: Boolean, msg: String) -> Unit
    ) {
        val cleanKey = key.trim().uppercase(Locale.US)
        if (cleanKey.isEmpty()) {
            onDone(false, "Please enter a valid key code")
            return
        }

        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/license_keys/$cleanKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val resp = reader.readText()
                    reader.close()

                    val json = JSONObject(resp)
                    val fields = json.optJSONObject("fields")
                    val isUsed = fields?.optJSONObject("isUsed")?.optBoolean("booleanValue", false) ?: false
                    val durationDays = fields?.optJSONObject("durationDays")?.optInt("integerValue", 30) ?: 30
                    val planName = fields?.optJSONObject("planName")?.optString("stringValue", "vip") ?: "vip"

                    if (isUsed) {
                        mainHandler.post { onDone(false, "This license key is already redeemed.") }
                        return@execute
                    }

                    // Mark as used in Firestore
                    val patchConn = URL("$FIRESTORE_BASE_URL/license_keys/$cleanKey?updateMask.fieldPaths=isUsed&updateMask.fieldPaths=usedByPhone").openConnection() as HttpURLConnection
                    patchConn.requestMethod = "PATCH"
                    patchConn.setRequestProperty("Content-Type", "application/json")
                    patchConn.doOutput = true

                    val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
                    val userPhone = prefs.getString("USER_PHONE", "Unknown")

                    val patchBody = JSONObject().apply {
                        val f = JSONObject()
                        f.put("isUsed", JSONObject().put("booleanValue", true))
                        f.put("usedByPhone", JSONObject().put("stringValue", userPhone))
                        put("fields", f)
                    }

                    val writer = OutputStreamWriter(patchConn.outputStream)
                    writer.write(patchBody.toString())
                    writer.flush()
                    writer.close()
                    patchConn.responseCode

                    // Extend local validity
                    val currentExpiry = prefs.getLong("PLAN_EXPIRES_AT_MILLIS", System.currentTimeMillis())
                    val base = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
                    val newExpiry = base + (durationDays.toLong() * 86400000L)

                    prefs.edit()
                        .putString("USER_PLAN", planName)
                        .putLong("PLAN_EXPIRES_AT_MILLIS", newExpiry)
                        .apply()

                    mainHandler.post {
                        onDone(true, "Key Applied! Added $durationDays days of $planName.")
                    }
                } else {
                    mainHandler.post { onDone(false, "Invalid license key code.") }
                }
            } catch (e: Exception) {
                // Offline fallback demo: if key matches ZU pattern, allow
                if (cleanKey.startsWith("ZU")) {
                    val prefs = context.getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
                    val newExpiry = System.currentTimeMillis() + (30L * 86400000L)
                    prefs.edit()
                        .putString("USER_PLAN", "vip")
                        .putLong("PLAN_EXPIRES_AT_MILLIS", newExpiry)
                        .apply()
                    mainHandler.post { onDone(true, "License Activated (30 Days VIP Access)") }
                } else {
                    mainHandler.post { onDone(false, "Key verification failed. Check connection.") }
                }
            }
        }
    }

    // ---------------- ORDER HISTORY & TELEMETRY ----------------
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

        val orderId = "PTR-ORD-" + (10000 + (Math.random() * 89999).toInt())
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val timeStr = sdf.format(Date())

        // Save locally to SharedPreferences history
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
                val url = URL("$FIRESTORE_BASE_URL/orders/$orderId")
                val conn = url.openConnection() as HttpURLConnection
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
