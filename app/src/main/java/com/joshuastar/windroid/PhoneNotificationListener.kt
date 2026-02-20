package com.joshuastar.windroid

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class PhoneNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"

        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.google.android.gms",
            "com.joshuastar.windroid"
        )

        fun send(title: String, text: String, appName: String, packageName: String, key: String = "") {
            ConnectionManager.send("NOTIF|$appName|$title|$text|$packageName|$key")
            Log.d(TAG, "Sent: [$appName] $title")
        }

        fun reply(key: String, replyText: String) {
            // handled via InputService from Windows
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        if (packageName in IGNORED_PACKAGES) return
        if (sbn.isOngoing) return  // blocks silent/persistent notifications
        if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val key: String = sbn.key ?: return

        val extras = sbn.notification.extras
        val title   = extras.getCharSequence("android.title")?.toString() ?: ""
        val text    = extras.getCharSequence("android.text")?.toString()  ?: ""
        val appName = getAppName(packageName)

        if (title.isBlank() && text.isBlank()) return

        send(title, text, appName, packageName, key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed: ${sbn.key}")
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}