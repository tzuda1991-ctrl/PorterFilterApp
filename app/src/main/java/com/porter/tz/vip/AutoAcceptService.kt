package com.porter.tz.vip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class AutoAcceptService : AccessibilityService() {

    private var lastSwipeTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    data class NodeItem(val text: String, val bounds: Rect)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("BOT_ACTIVE", true)) return

        val currentTime = System.currentTimeMillis()
        // एक बार स्वाइप करने के बाद 5 सेकंड का कूलडाउन ताकि ऑर्डर खुलते समय दोबारा स्वाइप न करे
        if (currentTime - lastSwipeTime < 5000) return

        val metrics = resources.displayMetrics
        val nodes = mutableListOf<NodeItem>()

        val windowList = windows
        if (windowList != null && windowList.isNotEmpty()) {
            for (win in windowList) {
                val root = win.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg != "com.theporter.android.driverapp") continue
                collectVisibleNodes(root, nodes, metrics.widthPixels, metrics.heightPixels)
            }
        } else {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: ""
            if (pkg != "com.theporter.android.driverapp") return
            collectVisibleNodes(root, nodes, metrics.widthPixels, metrics.heightPixels)
        }

        if (nodes.isEmpty()) return

        // 🛡️ 1. ANTI-GHOST SWIPE GUARD:
        // अगर ऑर्डर पहले ही एक्सेप्ट हो चुका है और स्क्रीन पर 'Start Trip', 'Cancel Trip', या 'Cash to Collect' दिख रहा है,
        // तो बिल्कुल भी स्वाइप न करे!
        val isOnActiveTripScreen = nodes.any {
            val t = it.text
            t.contains("Start Trip", ignoreCase = true) ||
            t.contains("Cancel Trip", ignoreCase = true) ||
            t.contains("Cash to Collect", ignoreCase = true) ||
            t.contains("Trip Fare", ignoreCase = true) ||
            t.contains("End Trip", ignoreCase = true) ||
            t.contains("Arrived", ignoreCase = true)
        }
        if (isOnActiveTripScreen) return

        // 🎯 2. 'Accept' या 'Swipe to Accept' बटन ढूंढें (जो स्क्रीन के बीच वाले हिस्से में हो)
        val acceptNode = nodes.firstOrNull {
            val t = it.text.trim()
            val validText = t.equals("Accept", ignoreCase = true) || t.contains("Swipe to Accept", ignoreCase = true)
            val validPosition = it.bounds.centerY() in (metrics.heightPixels * 0.25).toInt()..(metrics.heightPixels * 0.80).toInt()
            validText && validPosition
        } ?: return

        val acceptCenterY = acceptNode.bounds.centerY()

        // 💰 3. ऊपर लिखे असली ऑर्डर किराए (जैसे ₹204 या ₹113) को ढूंढें
        var detectedOrderFare: Int? = null
        var fareBottomY = 0
        var topMostY = Int.MAX_VALUE

        val priceWithSymbolRegex = Regex("""^\s*₹\s*(\d+(?:,\d+)*)\s*$""")
        val pureNumberRegex = Regex("""^\s*(\d{2,5})\s*$""")

        for (item in nodes) {
            if (item.bounds.centerY() < acceptCenterY) {
                val match = priceWithSymbolRegex.find(item.text)
                if (match != null) {
                    val priceVal = match.groupValues[1].replace(",", "").toIntOrNull()
                    if (priceVal != null && item.bounds.top < topMostY) {
                        topMostY = item.bounds.top
                        detectedOrderFare = priceVal
                        fareBottomY = item.bounds.bottom
                    }
                }
            }
        }

        if (detectedOrderFare == null) {
            val kmNodeY = nodes.filter { it.text.contains("km)", ignoreCase = true) }
                .minOfOrNull { it.bounds.top } ?: acceptCenterY

            for (item in nodes) {
                if (item.bounds.centerY() < kmNodeY) {
                    val cleanText = item.text.replace("₹", "").replace(",", "").trim()
                    val match = pureNumberRegex.find(cleanText)
                    if (match != null) {
                        val priceVal = match.groupValues[1].toIntOrNull()
                        if (priceVal != null && item.bounds.top < topMostY) {
                            topMostY = item.bounds.top
                            detectedOrderFare = priceVal
                            fareBottomY = item.bounds.bottom
                        }
                    }
                }
            }
        }

        // अगर पॉपअप पर ऊपर किराया नहीं मिला, तो यह असली ऑर्डर पॉपअप नहीं है
        val orderFare = detectedOrderFare ?: return

        // ☑️ 4. MINIMUM FARE CHECK (अगर चेकबॉक्स ON है तभी किराया चेक करे, OFF है तो कोई भी किराया पास करे)
        val useFareFilter = prefs.getBoolean("USE_FARE_FILTER", true)
        if (useFareFilter) {
            val minFare = prefs.getInt("MIN_FARE", 150)
            if (orderFare < minFare) return
        }

        // 📍 5. SEPARATE PICKUP vs DROP LOCATION DETECTION
        // पॉपअप में किराए (₹204) के नीचे और 'Accept' बटन के ऊपर दो लोकेशन होती हैं:
        // ऊपर वाला आधे हिस्से में Pickup होता है, और नीचे वाले आधे हिस्से में Drop होता है!
        val locationNodes = nodes.filter {
            it.bounds.top >= fareBottomY - 10 &&
            it.bounds.bottom <= acceptNode.bounds.top + 10
        }.sortedBy { it.bounds.top }

        // "(2.1 km)" और "(17.5 km)" जैसे हेडर ढूंढकर Pickup और Drop की सटीक बाउंड्री बनाएं
        val kmHeaders = locationNodes.filter { it.text.contains("km)", ignoreCase = true) }

        val splitY = if (kmHeaders.size >= 2) {
            kmHeaders[1].bounds.top - 5
        } else {
            (fareBottomY + acceptNode.bounds.top) / 2
        }

        val pickupTexts = locationNodes.filter { it.bounds.top < splitY }.joinToString(" ") { it.text }
        val dropTexts = locationNodes.filter { it.bounds.top >= splitY }.joinToString(" ") { it.text }

        // Pickup Check
        val usePickupFilter = prefs.getBoolean("USE_PICKUP_FILTER", false)
        val pickupQuery = prefs.getString("PICKUP_FILTER", "")?.trim() ?: ""
        if (usePickupFilter && pickupQuery.isNotEmpty()) {
            val queries = pickupQuery.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matchPickup = queries.any { q -> pickupTexts.contains(q, ignoreCase = true) }
            if (!matchPickup) return
        }

        // Drop Check (जैसे: Krishnarajapuram सिर्फ Drop में ही चेक होगा!)
        val useDropFilter = prefs.getBoolean("USE_DROP_FILTER", false)
        val dropQuery = prefs.getString("DROP_FILTER", "")?.trim() ?: ""
        if (useDropFilter && dropQuery.isNotEmpty()) {
            val queries = dropQuery.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matchDrop = queries.any { q -> dropTexts.contains(q, ignoreCase = true) }
            if (!matchDrop) return
        }

        // 🚀 6. सारी शर्तें पूरी हुईं! चुनी गई स्पीड (Quick / Normal / Human) से स्वाइप करें
        val speedMode = prefs.getString("SWIPE_SPEED", "NORMAL") ?: "NORMAL"
        lastSwipeTime = currentTime

        mainHandler.post {
            Toast.makeText(
                applicationContext,
                "⚡ Order ₹$orderFare Accepted! ($speedMode)",
                Toast.LENGTH_SHORT
            ).show()
        }

        performSpeedSlide(acceptNode.bounds, speedMode)
    }

    private fun collectVisibleNodes(
        node: AccessibilityNodeInfo?,
        list: MutableList<NodeItem>,
        screenW: Int,
        screenH: Int
    ) {
        if (node == null || !node.isVisibleToUser) return

        val t = node.text?.toString()?.trim()
        val d = node.contentDescription?.toString()?.trim()
        val finalStr = if (!t.isNullOrEmpty()) t else d

        if (!finalStr.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 5 && rect.height() > 5 && rect.bottom > 0 && rect.top < screenH) {
                list.add(NodeItem(finalStr, rect))
            }
        }
        for (i in 0 until node.childCount) {
            collectVisibleNodes(node.getChild(i), list, screenW, screenH)
        }
    }

    private fun performSpeedSlide(acceptBounds: Rect, speedMode: String) {
        val metrics = resources.displayMetrics
        val slideY = if (acceptBounds.centerY() > 100) {
            acceptBounds.centerY().toFloat()
        } else {
            metrics.heightPixels * 0.44f
        }

        val startX = metrics.widthPixels * 0.15f
        val endX = metrics.widthPixels * 0.74f

        val (delayMs, durationMs, curveOffset) = when (speedMode) {
            "QUICK" -> Triple(20L, 150L, 0f)     // 150ms Super Fast
            "HUMAN" -> Triple(140L, 550L, 6f)    // 550ms Natural Human-like
            else -> Triple(60L, 320L, 2f)        // 320ms Balanced Normal
        }

        val path = Path().apply {
            moveTo(startX, slideY)
            lineTo((startX + endX) / 2f, slideY + curveOffset)
            lineTo(endX, slideY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, delayMs, durationMs))
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}
}
