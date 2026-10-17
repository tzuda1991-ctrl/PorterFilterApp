package com.porter.tz.vip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoAcceptService : AccessibilityService() {

    private var lastSwipeTime = 0L

    data class NodeItem(val text: String, val bounds: Rect)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 1. सिर्फ Porter Driver App को ही चेक करे
        val pkg = event?.packageName?.toString() ?: ""
        if (pkg != "com.theporter.android.driverapp") return

        val prefs = getSharedPreferences("PorterVipPrefs", Context.MODE_PRIVATE)
        // 2. अगर Floating बटन से OFF किया है तो कुछ न करे
        if (!prefs.getBoolean("BOT_ACTIVE", true)) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSwipeTime < 3500) return

        val rootNode = rootInActiveWindow ?: return
        val nodes = mutableListOf<NodeItem>()
        collectNodes(rootNode, nodes)

        // 3. 'Swipe to Accept' बटन ढूंढें और उसकी स्क्रीन पर जगह (Coordinates) निकालें
        val swipeNode = nodes.firstOrNull {
            it.text.contains("Swipe to Accept", ignoreCase = true)
        } ?: return

        val swipeCenterY = swipeNode.bounds.centerY()

        // 4. सिर्फ सबसे ऊपर लिखा प्राइस (जैसे ₹113) डिटेक्ट करें:
        // शर्त A: वह 'Swipe to Accept' बटन के ऊपर होना चाहिए (इससे नीचे के बैनर का ₹500 इग्नोर हो जाएगा)
        // शर्त B: उसमें सिर्फ ₹ और नंबर होना चाहिए (इससे एड्रेस का 985 और पिनकोड 560034 कभी डिटेक्ट नहीं होगा)
        val priceRegex = Regex("""^\s*₹\s*(\d+(?:,\d+)*)\s*$""")
        var detectedOrderFare: Int? = null
        var topMostY = Int.MAX_VALUE

        for (item in nodes) {
            if (item.bounds.centerY() < swipeCenterY) {
                val match = priceRegex.find(item.text)
                if (match != null) {
                    val priceVal = match.groupValues[1].replace(",", "").toIntOrNull()
                    if (priceVal != null && item.bounds.top < topMostY) {
                        topMostY = item.bounds.top
                        detectedOrderFare = priceVal
                    }
                }
            }
        }

        val orderFare = detectedOrderFare ?: return
        val minFare = prefs.getInt("MIN_FARE", 150)

        // 5. अगर आपने Pickup Location का नाम भी डाला है तो उसे चेक करे
        val pickupFilter = prefs.getString("PICKUP_FILTER", "")?.trim() ?: ""
        if (pickupFilter.isNotEmpty()) {
            val matchesPickup = nodes.any {
                it.bounds.centerY() < swipeCenterY &&
                it.text.contains(pickupFilter, ignoreCase = true)
            }
            if (!matchesPickup) return
        }

        // 6. अगर ऊपर वाला किराया आपके फिल्टर के बराबर या ज़्यादा है, तो ठीक Swipe बटन पर स्लाइड करे!
        if (orderFare >= minFare) {
            lastSwipeTime = currentTime
            performExactButtonSlide(swipeNode.bounds)
        }
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, list: MutableList<NodeItem>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            list.add(NodeItem(text, rect))
        }
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), list)
        }
    }

    private fun performExactButtonSlide(swipeBounds: Rect) {
        val metrics = resources.displayMetrics
        // स्क्रीन पर जहां 'Swipe to Accept' लिखा है, ठीक उसी ऊंचाई (Y) पर बाएं से दाएं स्लाइड करेगा
        val slideY = if (swipeBounds.centerY() > 100) {
            swipeBounds.centerY().toFloat()
        } else {
            metrics.heightPixels * 0.48f
        }

        // बाएं गोले (>>) से लेकर दाएं छोर तक स्मूथ स्वाइप
        val startX = metrics.widthPixels * 0.22f
        val endX = metrics.widthPixels * 0.72f

        val path = Path().apply {
            moveTo(startX, slideY)
            lineTo(endX, slideY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 320))
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}
}
