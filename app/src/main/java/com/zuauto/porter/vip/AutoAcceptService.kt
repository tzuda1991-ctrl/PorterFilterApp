package com.zuauto.porter.vip

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

    private val areaAliases = mapOf(
        "krishnarajapuram" to listOf("krishnarajapuram", "kr puram", "k r puram", "k.r.puram", "k.r. puram", "medahalli", "battarahalli", "tc palya", "devasandra", "kithaganur", "kittaganuru", "560036", "560049"),
        "electronic city" to listOf("electronic city", "electronics city", "e-city", "konappana agrahara", "veerasandra", "560100"),
        "whitefield" to listOf("whitefield", "itpl", "kadugodi", "pattandur agrahara", "immadihalli", "560066"),
        "yelahanka" to listOf("yelahanka", "jakkur", "kogilu", "attur", "560064"),
        "hoodi" to listOf("hoodi", "mahadevapura", "garudacharpalya", "560048"),
        "hebbal" to listOf("hebbal", "kempapura", "guddadahalli", "kodigehalli", "560024"),
        "koramangala" to listOf("koramangala", "ejipura", "jakkasandra", "560034", "560095"),
        "indiranagar" to listOf("indiranagar", "indira nagar", "domlur", "hal 2nd stage", "560038"),
        "hsr layout" to listOf("hsr layout", "h.s.r", "agara", "mangammanapalya", "haralur", "560102"),
        "btm layout" to listOf("btm layout", "b.t.m", "madiwala", "tavarekere", "560029", "560076"),
        "peenya" to listOf("peenya", "dasarahalli", "jalahalli", "nelagadaranahalli", "560058"),
        "bommasandra" to listOf("bommasandra", "jigani", "chandapura", "attibele", "560099")
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences("ZuAutoPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("BOT_ACTIVE", true)) return

        // Check subscription validity
        if (!ZuAutoCloudManager.isSubscriptionActive(this)) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSwipeTime < 5000) return

        val metrics = resources.displayMetrics
        val nodes = mutableListOf<NodeItem>()

        val windowList = windows
        if (windowList != null && windowList.isNotEmpty()) {
            for (win in windowList) {
                val root = win.root
                if (root != null) {
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg.contains("porter", ignoreCase = true) && pkg != packageName) {
                        collectVisibleNodes(root, nodes, metrics.widthPixels, metrics.heightPixels)
                    }
                }
            }
        } else {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: ""
            if (pkg.contains("porter", ignoreCase = true) && pkg != packageName) {
                collectVisibleNodes(root, nodes, metrics.widthPixels, metrics.heightPixels)
            }
        }

        if (nodes.isEmpty()) return

        for (item in nodes) {
            val t = item.text
            if (t.contains("Start Trip", ignoreCase = true) ||
                t.contains("Cancel Trip", ignoreCase = true) ||
                t.contains("Cash to Collect", ignoreCase = true) ||
                t.contains("Trip Fare", ignoreCase = true) ||
                t.contains("End Trip", ignoreCase = true) ||
                t.contains("Arrived", ignoreCase = true)) {
                return
            }
        }

        var targetAcceptNode: NodeItem? = null
        val minY = (metrics.heightPixels * 0.25).toInt()
        val maxY = (metrics.heightPixels * 0.80).toInt()

        for (item in nodes) {
            val t = item.text.trim()
            val validText = t.equals("Accept", ignoreCase = true) || t.contains("Swipe to Accept", ignoreCase = true)
            if (validText && item.bounds.centerY() in minY..maxY) {
                targetAcceptNode = item
                break
            }
        }

        val acceptNode = targetAcceptNode ?: return
        val acceptCenterY = acceptNode.bounds.centerY()

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

        val kmRegex = Regex("""\(\s*\d+(?:\.\d+)?\s*km\s*\)""", RegexOption.IGNORE_CASE)

        if (detectedOrderFare == null) {
            var firstKmTopY = acceptCenterY
            for (item in nodes) {
                if (kmRegex.containsMatchIn(item.text) && item.bounds.top < firstKmTopY) {
                    firstKmTopY = item.bounds.top
                }
            }

            for (item in nodes) {
                if (item.bounds.centerY() < firstKmTopY) {
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

        val orderFare = detectedOrderFare ?: return

        val locationNodes = nodes.filter {
            it.bounds.top >= fareBottomY - 15 &&
            it.bounds.bottom <= acceptNode.bounds.top + 15
        }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))

        val kmAnchorNodes = locationNodes.filter { kmRegex.containsMatchIn(it.text) }
            .sortedBy { it.bounds.top }

        val pickupFullBlock: String
        val dropFullBlock: String

        if (kmAnchorNodes.size >= 2) {
            val dropStartY = kmAnchorNodes[1].bounds.top - 8
            pickupFullBlock = locationNodes.filter { it.bounds.centerY() < dropStartY }.joinToString(" ") { it.text }
            dropFullBlock = locationNodes.filter { it.bounds.centerY() >= dropStartY }.joinToString(" ") { it.text }
        } else if (locationNodes.size >= 2) {
            val midIndex = locationNodes.size / 2
            pickupFullBlock = locationNodes.subList(0, midIndex).joinToString(" ") { it.text }
            dropFullBlock = locationNodes.subList(midIndex, locationNodes.size).joinToString(" ") { it.text }
        } else {
            val singleText = locationNodes.joinToString(" ") { it.text }
            val lines = singleText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val half = (lines.size / 2).coerceAtLeast(1)
            pickupFullBlock = lines.take(half).joinToString(" ")
            dropFullBlock = lines.drop(half).joinToString(" ")
        }

        val usePickupFilter = prefs.getBoolean("USE_PICKUP_FILTER", false)
        val pickupQueryStr = prefs.getString("PICKUP_FILTER", "")?.trim() ?: ""
        val useDropFilter = prefs.getBoolean("USE_DROP_FILTER", false)
        val dropQueryStr = prefs.getString("DROP_FILTER", "")?.trim() ?: ""

        if (usePickupFilter || useDropFilter) {
            if (usePickupFilter) {
                if (pickupQueryStr.isEmpty()) return
                val selectedPickups = pickupQueryStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                var pickupOk = false
                for (area in selectedPickups) {
                    if (matchesAreaInAddressBlock(pickupFullBlock, area)) {
                        pickupOk = true
                        break
                    }
                }
                if (!pickupOk) return
            }

            if (useDropFilter) {
                if (dropQueryStr.isEmpty()) return
                val selectedDrops = dropQueryStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                var dropOk = false
                for (area in selectedDrops) {
                    if (matchesAreaInAddressBlock(dropFullBlock, area)) {
                        dropOk = true
                        break
                    }
                }
                if (!dropOk) return
            }
        } else {
            val useFareFilter = prefs.getBoolean("USE_FARE_FILTER", true)
            if (useFareFilter) {
                val minFare = prefs.getInt("MIN_FARE", 200)
                if (orderFare < minFare) return
            }
        }

        val speedMode = prefs.getString("SWIPE_SPEED", "QUICK") ?: "QUICK"
        lastSwipeTime = currentTime

        // Log to local history and Cloud Firestore
        val city = prefs.getString("SELECTED_CITY", "Bengaluru") ?: "Bengaluru"
        ZuAutoCloudManager.recordAcceptedOrder(
            context = applicationContext,
            fare = orderFare,
            pickup = pickupFullBlock.take(45),
            drop = dropFullBlock.take(45),
            city = city,
            speed = speedMode
        )

        mainHandler.post {
            Toast.makeText(
                applicationContext,
                "⚡ ZuAuto VIP: ₹$orderFare Accepted ($speedMode)",
                Toast.LENGTH_SHORT
            ).show()
        }

        performSpeedSlide(acceptNode.bounds, speedMode)
    }

    private fun matchesAreaInAddressBlock(addressBlock: String, selectedArea: String): Boolean {
        if (addressBlock.isBlank() || selectedArea.isBlank()) return false
        val normalizedBlock = addressBlock.lowercase()
            .replace(".", " ")
            .replace("-", " ")
            .replace(",", " ")
            .replace(Regex("""\s+"""), " ")

        val cleanAreaKey = selectedArea.lowercase().trim()
        if (normalizedBlock.contains(cleanAreaKey)) return true

        val aliases = areaAliases[cleanAreaKey]
        if (aliases != null) {
            for (alias in aliases) {
                val normalizedAlias = alias.lowercase().replace(".", " ").replace("-", " ").trim()
                if (normalizedBlock.contains(normalizedAlias)) return true
            }
        }
        return false
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
        val slideY = if (acceptBounds.centerY() > 100) acceptBounds.centerY().toFloat() else metrics.heightPixels * 0.44f
        val startX = metrics.widthPixels * 0.15f
        val endX = metrics.widthPixels * 0.74f

        val (delayMs, durationMs, curveOffset) = when (speedMode) {
            "QUICK" -> Triple(15L, 120L, 0f)
            "NATURAL" -> Triple(100L, 450L, 5f)
            else -> Triple(40L, 250L, 2f)
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
