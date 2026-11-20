package com.zu.auth

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
        FirebaseMessaging.getInstance().token.addOnSuccessListener { register(activity.applicationContext, it) }
    }

    @Synchronized
    private fun installation(context: Context): String {
        val prefs = preferences(context)
        return prefs.getString("installation", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation", it).apply()
        }
    }

    fun register(context: Context, token: String) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val uid = user.uid
        val audience = if (context.packageName == "com.zu.admin") "admin" else "driver"
        user.getIdToken(false).addOnSuccessListener { result ->
            val authTime = (result.claims["auth_time"] as? Number)?.toLong() ?: return@addOnSuccessListener
            if (auth.currentUser?.uid != uid) return@addOnSuccessListener
            FirebaseFirestore.getInstance().collection("push_accounts").document(uid)
                .collection("push_devices").document(installation(context))
                .set(mapOf("audience" to audience, "token" to token, "sessionAuthTime" to authTime))
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
