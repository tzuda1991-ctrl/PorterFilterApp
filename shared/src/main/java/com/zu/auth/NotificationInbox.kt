package com.zu.auth

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NotificationInbox(private val activity: Activity, uid: String, admin: Boolean, bellIcon: Int) {
    private data class Item(
        val id: String, val title: String, val body: String, val details: List<String>, val time: Long
    )

    private val prefs = activity.getSharedPreferences("NotificationInbox", Context.MODE_PRIVATE)
    private val readKey = if (admin) "admin_$uid" else "driver_$uid"
    private val read = prefs.getStringSet(readKey, emptySet()).orEmpty().toMutableSet()
    private val items = mutableMapOf<String, List<Item>>()
    private val errors = mutableSetOf<String>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val sources = if (admin) listOf("notification_inbox_admin") else
        listOf("notification_inbox_driver", "notification_inboxes/$uid/items")
    private val gold = Color.parseColor("#F6D58A")
    private val badge = TextView(activity).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        minWidth = dp(17)
        setPadding(dp(3), 0, dp(3), 0)
        background = shape("#BA263E", "#BA263E", 10)
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val button = FrameLayout(activity).apply {
        background = if (admin) shape("#0F172A", "#475569", 15) else shape("#FFF2CF", "#C79838", 15)
        contentDescription = "Notifications"
        isFocusable = true
        addView(ImageView(activity).apply {
            setImageResource(bellIcon)
            setColorFilter(if (admin) gold else Color.parseColor("#805514"))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        addView(badge, FrameLayout.LayoutParams(-2, dp(17), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(2)
            marginEnd = dp(2)
        })
        setOnClickListener { show() }
    }

    private var dialog: AlertDialog? = null
    private var list: LinearLayout? = null
    private var closed = false

    init {
        val db = FirebaseFirestore.getInstance()
        for (source in sources) {
            listeners += db.collection(source).addSnapshotListener { snapshot, error ->
                if (closed) return@addSnapshotListener
                if (error != null) {
                    errors.add(source)
                    items.remove(source)
                } else if (snapshot != null) {
                    errors.remove(source)
                    items[source] = snapshot.documents.map { doc ->
                        Item("$source/${doc.id}", doc.getString("title").orEmpty(), doc.getString("body").orEmpty(),
                            (doc.get("details") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                            doc.getTimestamp("createdAt")?.toDate()?.time ?: 0)
                    }
                }
                render()
            }
        }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(fill: String, stroke: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        setStroke(dp(1), Color.parseColor(stroke))
        cornerRadius = dp(radius).toFloat()
    }

    private fun show() {
        if (closed || dialog != null) return
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        list = content
        dialog = AlertDialog.Builder(activity).setTitle("Notifications")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("Close", null)
            .setNeutralButton("Mark all read", null)
            .create().apply {
                setOnDismissListener {
                    dialog = null
                    list = null
                }
                show()
                getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    read.addAll(items.values.flatten().map { it.id })
                    saveRead()
                }
            }
        render()
    }

    private fun saveRead() {
        prefs.edit().putStringSet(readKey, read.toSet()).apply()
        render()
    }

    private fun render() {
        val notifications = items.values.flatten().sortedWith(compareByDescending<Item> { it.time }.thenBy { it.id })
        val count = notifications.count { it.id !in read }
        badge.text = if (count > 99) "99+" else count.toString()
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        button.contentDescription = if (count == 0) "Notifications" else "Notifications, $count unread"
        val panel = list ?: return
        dialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = count > 0
        panel.removeAllViews()
        val status = when {
            errors.isNotEmpty() -> "Some notifications couldn't load."
            items.size < sources.size -> "Loading notifications…"
            notifications.isEmpty() -> "No notifications yet."
            else -> null
        }
        if (status != null) panel.addView(TextView(activity).apply {
            text = status
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dp(6), dp(8), dp(6), dp(8))
        })
        val date = SimpleDateFormat("dd MMM yyyy, h:mm a 'IST'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        for (item in notifications) {
            val unread = item.id !in read
            val card = TextView(activity).apply {
                text = buildString {
                    if (unread) append("● ")
                    append(item.title)
                    append("\n").append(item.body)
                    for (detail in item.details) append("\n").append(detail)
                    if (item.time > 0) append("\n").append(date.format(item.time))
                }
                setTextColor(if (unread) gold else Color.parseColor("#E2E8F0"))
                textSize = 14f
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = shape("#192A43", if (unread) "#B8904D" else "#475569", 12)
                if (unread) setOnClickListener {
                    read.add(item.id)
                    saveRead()
                }
            }
            panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    fun close() {
        closed = true
        listeners.forEach { it.remove() }
        listeners.clear()
        dialog?.dismiss()
    }
}
