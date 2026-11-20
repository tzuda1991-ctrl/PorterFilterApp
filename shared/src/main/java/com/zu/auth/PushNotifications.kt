package com.zu.auth

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

object PushNotifications {
    const val CHANNEL = "zu_updates"

    private fun preferences(context: Context) =
        context.getSharedPreferences("ZuPush", Context.MODE_PRIVATE)

    fun createChannel(context: Context): NotificationManager? {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "ZU Smart updates", NotificationManager.IMPORTANCE_HIGH)
        )
        return manager
    }

    fun initialize(activity: Activity) {
        createChannel(activity)
        val prefs = preferences(activity)
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("askedNotificationPermission", false)) {
            prefs.edit().putBoolean("askedNotificationPermission", true).apply()
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 390)
        }
        refreshRegistration(activity.applicationContext) {}
    }

    @Synchronized
    private fun installation(context: Context): String {
        val prefs = preferences(context)
        return prefs.getString("installation", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation", it).apply()
        }
    }

    fun showSetup(activity: Activity) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Notification setup")
            .setMessage("Checking this device…")
            .setNegativeButton("Close", null)
            .setNeutralButton("Android settings") { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            }
            .setPositiveButton("Check again", null)
            .show()
        val retry = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        fun verify() {
            retry.isEnabled = false
            val manager = createChannel(activity)
            val permission = if (manager?.areNotificationsEnabled() == true &&
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) "Allowed" else "Off"
            dialog.setMessage("Android notifications: $permission\nChecking Firebase registration…")
            refreshRegistration(activity.applicationContext) { result ->
                if (!dialog.isShowing) return@refreshRegistration
                dialog.setMessage("Android notifications: $permission\n$result")
                retry.isEnabled = true
            }
        }
        retry.setOnClickListener { verify() }
        verify()
    }

    private fun refreshRegistration(context: Context, done: (String) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                val reason = task.exception?.message.orEmpty()
                val code = when {
                    reason.contains("SERVICE_NOT_AVAILABLE", ignoreCase = true) -> "SERVICE_NOT_AVAILABLE"
                    reason.contains("MISSING_INSTANCEID_SERVICE", ignoreCase = true) -> "MISSING_INSTANCEID_SERVICE"
                    reason.contains("FIS_AUTH_ERROR", ignoreCase = true) -> "FIS_AUTH_ERROR"
                    else -> "FCM token unavailable"
                }
                done("$code. Check internet and Google Play services, then retry.")
            } else {
                register(context, task.result, done)
            }
        }
    }

    fun register(context: Context, token: String, done: (String) -> Unit = {}) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return done("Sign in to register this device.")
        val uid = user.uid
        val audience = if (context.packageName == "com.zu.admin") "admin" else "driver"
        user.getIdToken(false).addOnCompleteListener { task ->
            val authTime = if (task.isSuccessful) {
                val claim = task.result.claims["auth_time"]
                (claim as? Number)?.toLong() ?: (claim as? String)?.toLongOrNull()
            } else null
            if (authTime == null || auth.currentUser?.uid != uid) {
                done("Google session unavailable. Open the app again and retry.")
                return@addOnCompleteListener
            }
            val request = FirebaseFirestore.getInstance().collection("push_accounts").document(uid)
                .collection("push_devices").document(installation(context))
                .set(mapOf("audience" to audience, "token" to token, "sessionAuthTime" to authTime))
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeout = Runnable { done("Firebase did not confirm registration. Check internet and retry.") }
            handler.postDelayed(timeout, 10_000)
            request.addOnCompleteListener { write ->
                handler.removeCallbacks(timeout)
                if (write.isSuccessful) {
                    done("Firebase registration: Ready")
                } else {
                    val code = (write.exception as? FirebaseFirestoreException)?.code
                    done(if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        "Firestore rejected device registration (PERMISSION_DENIED). Check the published notification rules and your account access."
                    } else "Firebase registration failed (${code ?: "unknown"}).")
                }
            }
        }
    }

}

class ZuMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushNotifications.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = message.notification ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = PushNotifications.createChannel(this) ?: return
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val key = message.data["eventId"] ?: message.messageId ?: UUID.randomUUID().toString()
        val openApp = PendingIntent.getActivity(this, key.hashCode(), launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = resources.getIdentifier("zu_notification_icon", "drawable", packageName)
        if (icon == 0) return
        val alert = Notification.Builder(this, PushNotifications.CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(Notification.BigTextStyle().bigText(payload.body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(key, 0, alert)
    }
}
