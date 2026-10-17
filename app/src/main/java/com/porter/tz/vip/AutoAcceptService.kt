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
        if (currentTime - lastSwipeTime < 2500) return

        val nodes = mutableListOf<NodeItem>()

        // स्क्रीन की सभी विंडोज़ (Popup + Main Screen) को स्कैन करें
        val windowList = windows
        if (windowList != null && windowList.isNotEmpty()) {
            for (win in windowList) {
                val root = win.root ?: continue
                // अपने खुद के फ्लोटिंग बटन को इग्नोर करें
                if (root.packageName?.toString() == packageName) continue
                collectNodes(root, nodes)
            }
        } else {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() == packageName) return
            collectNodes(root, nodes)
        }

        if (nodes.isEmpty()) return

        // 1. 'Accept' या 'Swipe to Accept' दोनों में से कोई भी बटन ढूंढें
        val acceptNode = nodes.firstOrNull {
            val t = it.text.trim()
            t.equals("Accept", ignoreCase = true) ||
            t.contains("Swipe to Accept", ignoreCase = true)
        } ?: return

        val acceptCenterY = acceptNode.bounds.centerY()

        // 2. सिर्फ Accept बटन के ऊपर लिखे अमाउंट (जैसे ₹204 या ₹113) को ढूंढें
        var detectedOrderFare: Int? = null
        var topMostY = Int.MAX_VALUE

        val priceWithSymbolRegex = Regex("""^\s*₹\s*(\d+(?:,\d+)*)\s*$""")
        val pureNumberRegex = Regex("""^\s*(\d{2,5})\s*$""")

        for (item in nodes) {
            // सिर्फ Accept बटन के ऊपर के हिस्से में चेक करें (नीचे का ₹500 बैनर अपने आप छूट जाएगा)
            if (item.bounds.centerY() < acceptCenterY) {
                val match = priceWithSymbolRegex.find(item.text)
                if (match != null) {
                    val priceVal = match.groupValues[1].replace(",", "").toIntOrNull()
                    if (priceVal != null && item.bounds.top < topMostY) {
                        topMostY = item.bounds.top
                        detectedOrderFare = priceVal
                    }
                }
            }
        }

        // Fallback: अगर किसी वजह से ₹ का निशान अलग बॉक्स में हो और 204 अलग बॉक्स में हो,
        // तो पॉपअप के सबसे ऊपर (Pickup/km के भी ऊपर) लिखे नंबर को पकड़ें
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
                        }
                    }
                }
            }
        }

        val orderFare = detectedOrderFare ?: return
        val minFare = prefs.getInt("MIN_FARE", 150)

        // 3. Pickup Location फिल्टर चेक करें (अगर आपने कोई नाम डाला है)
        val pickupFilter = prefs.getString("PICKUP_FILTER", "")?.trim() ?: ""
        if (pickupFilter.isNotEmpty()) {
            val matchesPickup = nodes.any {
                it.bounds.centerY() < acceptCenterY &&
                it.text.contains(pickupFilter, ignoreCase = true)
            }
            if (!matchesPickup) return
        }

        // 4. अगर किराया आपके फिल्टर के बराबर या ज़्यादा है, तो तुरंत स्लाइड करें!
        if (orderFare >= minFare) {
            lastSwipeTime = currentTime
            mainHandler.post {
                Toast.makeText(applicationContext, "🎯 Order ₹$orderFare मिला! Auto-Swiping...", Toast.LENGTH_SHORT).show()
            }
            performExactButtonSlide(acceptNode.bounds)
        }
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, list: MutableList<NodeItem>) {
        if (node == null) return
        val t = node.text?.toString()?.trim()
        val d = node.contentDescription?.toString()?.trim()
        val finalStr = if (!t.isNullOrEmpty()) t else d

        if (!finalStr.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                list.add(NodeItem(finalStr, rect))
            }
        }
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), list)
        }
    }

    private fun performExactButtonSlide(acceptBounds: Rect) {
        val metrics = resources.displayMetrics

        // ठीक नीले Accept बटन की ऊंचाई (Center Y) पर स्वाइप करे
        val slideY = if (acceptBounds.centerY() > 100) {
            acceptBounds.centerY().toFloat()
        } else {
            metrics.heightPixels * 0.44f
        }

        // आपके स्क्रीनशॉट के हिसाब से सफेद गोला (>>) बाईं तरफ 15% पर है और बटन 74% पर खत्म होता है
        val startX = metrics.widthPixels * 0.15f
        val endX = metrics.widthPixels * 0.72f

        val path = Path().apply {
            moveTo(startX, slideY)
            // थोड़ा सा इंसानी उंगली जैसा मूवमेंट देने के लिए बीच में से होते हुए अंत तक जाए
            lineTo((startX + endX) / 2f, slideY + 2f)
            lineTo(endX, slideY)
        }

        // 120ms के छोटे से डिले के बाद 420ms का स्मूथ स्लाइड करे
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 120, 420))
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}
}
