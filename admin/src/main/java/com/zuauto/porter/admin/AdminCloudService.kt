package com.zuauto.porter.admin

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

object AdminCloudService {
    private const val FIREBASE_PROJECT_ID = "gmp-demo-project-615064003"
    private const val FIRESTORE_DATABASE_ID = "ai-studio-porterfilterapp-010c463f-82c7-4c8d-8552-abb324a0cb33"
    private const val FIREBASE_API_KEY = "AIzaSyD3qvSOb402ydPohe7F-Ua_MVXzur0igSc"

    private const val FIRESTORE_BASE_URL =
        "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents"

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class DriverItem(
        val uid: String,
        val name: String,
        val phone: String,
        val city: String,
        val vehicle: String,
        val status: String,
        val plan: String,
        val planExpiresAt: String
    )

    data class OrderItem(
        val orderId: String,
        val driverName: String,
        val driverPhone: String,
        val fare: Int,
        val pickup: String,
        val drop: String,
        val city: String,
        val speed: String,
        val acceptedAt: String
    )

    data class KeyItem(
        val key: String,
        val plan: String,
        val days: Int,
        val isUsed: Boolean,
        val usedBy: String
    )

    // ---------------- FETCH DRIVERS VIA RUNQUERY ----------------
    fun fetchDrivers(callback: (List<DriverItem>) -> Unit) {
        executor.execute {
            val list = mutableListOf<DriverItem>()
            try {
                val url = URL("$FIRESTORE_BASE_URL:runQuery?key=$FIREBASE_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val queryBody = JSONObject().apply {
                    val structured = JSONObject().apply {
                        val fromArr = JSONArray().apply {
                            put(JSONObject().put("collectionId", "users"))
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

                    val jsonArr = JSONArray(resp)
                    for (i in 0 until jsonArr.length()) {
                        val item = jsonArr.getJSONObject(i)
                        val doc = item.optJSONObject("document") ?: continue
                        val fields = doc.optJSONObject("fields") ?: continue

                        val uid = fields.optJSONObject("uid")?.optString("stringValue", "") ?: ""
                        val name = fields.optJSONObject("name")?.optString("stringValue", "Driver") ?: "Driver"
                        val phone = fields.optJSONObject("phone")?.optString("stringValue", "") ?: ""
                        val city = fields.optJSONObject("city")?.optString("stringValue", "Bengaluru") ?: "Bengaluru"
                        val vehicle = fields.optJSONObject("vehicleType")?.optString("stringValue", "2-Wheeler") ?: "2-Wheeler"
                        val status = fields.optJSONObject("status")?.optString("stringValue", "active") ?: "active"
                        val plan = fields.optJSONObject("plan")?.optString("stringValue", "pro") ?: "pro"
                        val exp = fields.optJSONObject("planExpiresAt")?.optString("stringValue", "") ?: ""

                        if (uid.isNotEmpty() || phone.isNotEmpty() || name.isNotEmpty()) {
                            list.add(DriverItem(uid, name, phone, city, vehicle, status, plan, exp))
                        }
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post { callback(list) }
        }
    }

    // ---------------- UPDATE DRIVER STATUS ----------------
    fun updateDriverStatus(uid: String, newStatus: String, callback: () -> Unit) {
        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/users/$uid?updateMask.fieldPaths=status&key=$FIREBASE_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val body = JSONObject().apply {
                    val f = JSONObject()
                    f.put("status", JSONObject().put("stringValue", newStatus))
                    put("fields", f)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()
                conn.responseCode
            } catch (_: Exception) {}

            mainHandler.post { callback() }
        }
    }

    // ---------------- EXTEND DRIVER SUBSCRIPTION ----------------
    fun extendDriverSubscription(uid: String, daysToAdd: Int, plan: String, callback: () -> Unit) {
        executor.execute {
            try {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                val expIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(cal.time)

                val url = URL("$FIRESTORE_BASE_URL/users/$uid?updateMask.fieldPaths=planExpiresAt&updateMask.fieldPaths=plan&updateMask.fieldPaths=status&key=$FIREBASE_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val body = JSONObject().apply {
                    val f = JSONObject()
                    f.put("planExpiresAt", JSONObject().put("stringValue", expIso))
                    f.put("plan", JSONObject().put("stringValue", plan))
                    f.put("status", JSONObject().put("stringValue", "active"))
                    put("fields", f)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()
                conn.responseCode
            } catch (_: Exception) {}

            mainHandler.post { callback() }
        }
    }

    // ---------------- GENERATE LICENSE KEYS ----------------
    fun generateLicenseKeys(
        plan: String,
        days: Int,
        count: Int,
        callback: (List<String>) -> Unit
    ) {
        executor.execute {
            val generated = mutableListOf<String>()
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            for (i in 0 until count) {
                val randCode = UUID.randomUUID().toString().take(6).uppercase(Locale.US)
                val key = "ZU-${days}D-$randCode"

                try {
                    val url = URL("$FIRESTORE_BASE_URL/license_keys/$key?key=$FIREBASE_API_KEY")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PATCH"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    val body = JSONObject().apply {
                        val f = JSONObject()
                        f.put("key", JSONObject().put("stringValue", key))
                        f.put("durationDays", JSONObject().put("integerValue", days.toString()))
                        f.put("planName", JSONObject().put("stringValue", plan))
                        f.put("isUsed", JSONObject().put("booleanValue", false))
                        f.put("createdAt", JSONObject().put("stringValue", nowIso))
                        put("fields", f)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(body.toString())
                    writer.flush()
                    writer.close()
                    if (conn.responseCode in 200..299) {
                        generated.add(key)
                    }
                } catch (_: Exception) {}
            }

            mainHandler.post { callback(generated) }
        }
    }

    // ---------------- FETCH ORDERS VIA RUNQUERY ----------------
    fun fetchOrders(callback: (List<OrderItem>) -> Unit) {
        executor.execute {
            val list = mutableListOf<OrderItem>()
            try {
                val url = URL("$FIRESTORE_BASE_URL:runQuery?key=$FIREBASE_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val queryBody = JSONObject().apply {
                    val structured = JSONObject().apply {
                        val fromArr = JSONArray().apply {
                            put(JSONObject().put("collectionId", "orders"))
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

                    val jsonArr = JSONArray(resp)
                    for (i in 0 until jsonArr.length()) {
                        val item = jsonArr.getJSONObject(i)
                        val doc = item.optJSONObject("document") ?: continue
                        val f = doc.optJSONObject("fields") ?: continue

                        val orderId = f.optJSONObject("orderId")?.optString("stringValue", "PTR-00") ?: "PTR-00"
                        val dName = f.optJSONObject("driverName")?.optString("stringValue", "Driver") ?: "Driver"
                        val dPhone = f.optJSONObject("driverPhone")?.optString("stringValue", "") ?: ""
                        val fare = f.optJSONObject("fare")?.optInt("integerValue", 0) ?: 0
                        val pickup = f.optJSONObject("pickupArea")?.optString("stringValue", "Pickup") ?: "Pickup"
                        val drop = f.optJSONObject("dropArea")?.optString("stringValue", "Drop") ?: "Drop"
                        val city = f.optJSONObject("city")?.optString("stringValue", "Bengaluru") ?: "Bengaluru"
                        val speed = f.optJSONObject("speedMode")?.optString("stringValue", "QUICK") ?: "QUICK"
                        val time = f.optJSONObject("acceptedAt")?.optString("stringValue", "") ?: ""

                        list.add(OrderItem(orderId, dName, dPhone, fare, pickup, drop, city, speed, time))
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post { callback(list) }
        }
    }

    // ---------------- REMOTE OTA VERSION MANAGEMENT ----------------
    fun fetchVersionConfig(
        callback: (code: Int, name: String, minCode: Int, url: String, notes: String, force: Boolean) -> Unit
    ) {
        executor.execute {
            try {
                val url = URL("$FIRESTORE_BASE_URL/app_config/version_control?key=$FIREBASE_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000

                if (conn.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val resp = reader.readText()
                    reader.close()

                    val json = JSONObject(resp)
                    val f = json.optJSONObject("fields")
                    if (f != null) {
                        val code = f.optJSONObject("currentVersionCode")?.optInt("integerValue", 10) ?: 10
                        val name = f.optJSONObject("currentVersionName")?.optString("stringValue", "10.0-ZuAuto") ?: "10.0-ZuAuto"
                        val minCode = f.optJSONObject("minSupportedVersionCode")?.optInt("integerValue", 10) ?: 10
                        val dlUrl = f.optJSONObject("apkDownloadUrl")?.optString("stringValue", "") ?: ""
                        val notes = f.optJSONObject("releaseNotes")?.optString("stringValue", "") ?: ""
                        val force = f.optJSONObject("forceUpdate")?.optBoolean("booleanValue", true) ?: true

                        mainHandler.post { callback(code, name, minCode, dlUrl, notes, force) }
                        return@execute
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post { callback(10, "10.0-ZuAuto", 10, "", "• ZuAuto VIP Release", true) }
        }
    }

    fun pushNewVersion(
        code: Int,
        name: String,
        minCode: Int,
        dlUrl: String,
        notes: String,
        force: Boolean,
        notice: String,
        callback: (Boolean) -> Unit
    ) {
        executor.execute {
            var success = false
            try {
                val url = URL("$FIRESTORE_BASE_URL/app_config/version_control?key=$FIREBASE_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val body = JSONObject().apply {
                    val f = JSONObject()
                    f.put("currentVersionCode", JSONObject().put("integerValue", code.toString()))
                    f.put("currentVersionName", JSONObject().put("stringValue", name))
                    f.put("minSupportedVersionCode", JSONObject().put("integerValue", minCode.toString()))
                    f.put("apkDownloadUrl", JSONObject().put("stringValue", dlUrl))
                    f.put("releaseNotes", JSONObject().put("stringValue", notes))
                    f.put("forceUpdate", JSONObject().put("booleanValue", force))
                    f.put("announcementNotice", JSONObject().put("stringValue", notice))
                    f.put("updatedAt", JSONObject().put("stringValue", nowIso))
                    put("fields", f)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()
                success = conn.responseCode in 200..299
            } catch (_: Exception) {}

            mainHandler.post { callback(success) }
        }
    }
}
