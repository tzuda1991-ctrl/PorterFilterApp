package com.example.autoaccept

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoAcceptService : AccessibilityService() {
    private var lastSwipeTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSwipeTime < 4000) return

        val prefs = getSharedPreferences("FilterPrefs", Context.MODE_PRIVATE)
        val minFare = prefs.getInt("MIN_FARE", 300)

        val screenTexts = mutableListOf<String>()
        extractText(rootNode, screenTexts)

        var hasOrderKeyword = false
        var matchesFare = false

        for (text in screenTexts) {
            if (text.contains("Accept", ignoreCase = true) || text.contains("Slide", ignoreCase = true)) {
                hasOrderKeyword = true
            }
            val regex = Regex("""(?:₹|Rs\.?)\s*(\d+)""")
            val match = regex.find(text)
            if (match != null) {
                val price = match.groupValues[1].toIntOrNull() ?: 0
                if (price >= minFare) {
                    matchesFare = true
                }
            }
        }

        if (hasOrderKeyword && matchesFare) {
            lastSwipeTime = currentTime
            performSlideGesture()
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.let { list.add(it.toString()) }
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), list)
        }
    }

    private fun performSlideGesture() {
        val metrics = resources.displayMetrics
        val startX = metrics.widthPixels * 0.15f
        val endX = metrics.widthPixels * 0.85f
        val y = metrics.heightPixels * 0.85f

        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}
}
