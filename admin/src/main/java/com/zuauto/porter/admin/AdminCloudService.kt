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

    data class PaymentRequestItem(
        val id: String,
        val userId: String,
        val userName: String,
        val userPhone: String,
        val planId: String,
        val planName: String,
        val amount: Int,
        val durationDays: Int,
        val utrNumber: String,
        val proofScreenshot: String,
        val status: String,
        val createdAt: String
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
                        val f = doc.optJSONObject("fields") ?: continue
                        val path = doc.optString("name", "")
                        val uid = f.optJSONObject("uid")?.optString("stringValue")
                            ?: path.substringAfterLast("/")
                        val name = f.optJSONObject("name")?.optString("stringValue", "Driver") ?: "Driver"
                        val phone = f.optJSONObject("phone")?.optString("stringValue", "") ?: ""
                        val city = f.optJSONObject("city")?.optString("stringValue", "Bengaluru") ?: "Bengaluru"
                        val vehicle = f.optJSONObject("vehicle")?.optString("stringValue", "Porter") ?: "Porter"
                        val status = f.optJSONObject("status")?.optString("stringValue", "active") ?: "active"
                        val plan = f.optJSONObject("planName")?.optString("stringValue", "VIP") ?: "VIP"
                        val exp = f.optJSONObject("validUntil")?.optString("stringValue", "") ?: ""
                        list.add(DriverItem(uid, name, phone, city, vehicle, status, plan, exp))
                    }
                }
            } catch (_: Exception) {}
            mainHandler.post { callback(list) }
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

    // ---------------- FETCH PAYMENT REQUESTS VIA RUNQUERY ----------------
    fun fetchPaymentRequests(callback: (List<PaymentRequestItem>) -> Unit) {
        executor.execute {
            val list = mutableListOf<PaymentRequestItem>()
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
                            put(JSONObject().put("collectionId", "payment_requests"))
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
                        val path = doc.optString("name", "")
                        val id = f.optJSONObject("id")?.optString("stringValue") ?: path.substringAfterLast("/")
                        val uid = f.optJSONObject("userId")?.optString("stringValue", "") ?: ""
                        val uName = f.optJSONObject("userName")?.optString("stringValue", "Driver") ?: "Driver"
                        val uPhone = f.optJSONObject("userPhone")?.optString("stringValue", "") ?: ""
                        val pId = f.optJSONObject("planId")?.optString("stringValue", "vip") ?: "vip"
                        val pName = f.optJSONObject("planName")?.optString("stringValue", "Pro Plan") ?: "Pro Plan"
                        val amt = f.optJSONObject("amount")?.optInt("integerValue", 499) ?: 499
                        val dur = f.optJSONObject("durationDays")?.optInt("integerValue", 30) ?: 30
                        val utr = f.optJSONObject("utrNumber")?.optString("stringValue", "") ?: ""
                        val proof = f.optJSONObject("proofScreenshot")?.optString("stringValue", "") ?: ""
                        val status = f.optJSONObject("status")?.optString("stringValue", "pending") ?: "pending"
                        val created = f.optJSONObject("createdAt")?.optString("stringValue", "") ?: ""
                        list.add(PaymentRequestItem(id, uid, uName, uPhone, pId, pName, amt, dur, utr, proof, status, created))
                    }
                }
            } catch (_: Exception) {}
            mainHandler.post { callback(list) }
        }
    }

    // ---------------- APPROVE PAYMENT REQUEST & ACTIVATE DRIVER ----------------
    fun approvePaymentRequest(req: PaymentRequestItem, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val newExpiryMillis = System.currentTimeMillis() + (req.durationDays.toLong() * 86400000L)
                val newExpiryIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(newExpiryMillis))

                // 1. Update Payment Request status
                val payUrl = URL("$FIRESTORE_BASE_URL/payment_requests/${req.id}?updateMask.fieldPaths=status&updateMask.fieldPaths=reviewedAt&key=$FIREBASE_API_KEY")
                val payConn = payUrl.openConnection() as HttpURLConnection
                payConn.requestMethod = "PATCH"
                payConn.setRequestProperty("Content-Type", "application/json")
                payConn.doOutput = true

                val payBody = JSONObject().apply {
                    val f = JSONObject()
                    f.put("status", JSONObject().put("stringValue", "approved"))
                    f.put("reviewedAt", JSONObject().put("stringValue", nowIso))
                    put("fields", f)
                }
                val payWriter = OutputStreamWriter(payConn.outputStream)
                payWriter.write(payBody.toString())
                payWriter.flush()
                payWriter.close()
                payConn.responseCode

                // 2. Update Driver user validity
                val userUrl = URL("$FIRESTORE_BASE_URL/users/${req.userId}?updateMask.fieldPaths=subscriptionStatus&updateMask.fieldPaths=planName&updateMask.fieldPaths=validUntil&key=$FIREBASE_API_KEY")
                val userConn = userUrl.openConnection() as HttpURLConnection
                userConn.requestMethod = "PATCH"
                userConn.setRequestProperty("Content-Type", "application/json")
                userConn.doOutput = true

                val userBody = JSONObject().apply {
                    val f = JSONObject()
                    f.put("subscriptionStatus", JSONObject().put("stringValue", "active"))
                    f.put("planName", JSONObject().put("stringValue", req.planName))
                    f.put("validUntil", JSONObject().put("stringValue", newExpiryIso))
                    put("fields", f)
                }
                val userWriter = OutputStreamWriter(userConn.outputStream)
                userWriter.write(userBody.toString())
                userWriter.flush()
                userWriter.close()
                userConn.responseCode

                mainHandler.post {
                    callback(true, "Payment Approved! ${req.userName}'s ${req.planName} is now active.")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "Approval failed: ${e.message}")
                }
            }
        }
    }

    // ---------------- REJECT PAYMENT REQUEST ----------------
    fun rejectPaymentRequest(reqId: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val payUrl = URL("$FIRESTORE_BASE_URL/payment_requests/$reqId?updateMask.fieldPaths=status&updateMask.fieldPaths=reviewedAt&key=$FIREBASE_API_KEY")
                val payConn = payUrl.openConnection() as HttpURLConnection
                payConn.requestMethod = "PATCH"
                payConn.setRequestProperty("Content-Type", "application/json")
                payConn.doOutput = true

                val payBody = JSONObject().apply {
                    val f = JSONObject()
                    f.put("status", JSONObject().put("stringValue", "rejected"))
                    f.put("reviewedAt", JSONObject().put("stringValue", nowIso))
                    put("fields", f)
                }
                val payWriter = OutputStreamWriter(payConn.outputStream)
                payWriter.write(payBody.toString())
                payWriter.flush()
                payWriter.close()
                payConn.responseCode

                mainHandler.post {
                    callback(true, "Payment request rejected.")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "Rejection failed: ${e.message}")
                }
            }
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

    // ---------------- LICENSE KEYS CREATION ----------------
    fun generateLicenseKeys(
        count: Int,
        plan: String,
        days: Int,
        callback: (List<String>) -> Unit
    ) {
        executor.execute {
            val generated = mutableListOf<String>()
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            for (i in 1..count) {
                val randPart = UUID.randomUUID().toString().take(8).uppercase(Locale.US)
                val key = "ZU-$randPart"
                try {
                    val url = URL("$FIRESTORE_BASE_URL/license_keys/$key?key=$FIREBASE_API_KEY")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PATCH"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    val body = JSONObject().apply {
                        val f = JSONObject()
                        f.put("key", JSONObject().put("stringValue", key))
                        f.put("planName", JSONObject().put("stringValue", plan))
                        f.put("durationDays", JSONObject().put("integerValue", days.toString()))
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
}
