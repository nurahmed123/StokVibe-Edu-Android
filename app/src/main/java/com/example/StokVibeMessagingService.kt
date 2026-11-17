package com.example

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives StokVibe EDU push notifications from Firebase, whether the app is open, in the background or closed.
 * The server sends data-only messages ({ title, body, url }) so this always runs and draws the notification itself.
 */
class StokVibeMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: "StokVibe EDU"
        val body = data["body"] ?: message.notification?.body ?: ""
        val url = absoluteUrl(data["url"])

        // The channel is normally made when the app opens; a push can arrive while the app has never been opened.
        NotificationHelper.createNotificationChannel(applicationContext)
        NotificationHelper.showNotification(
            context = applicationContext,
            notificationId = (System.currentTimeMillis() % 100000).toInt(),
            title = title,
            text = body,
            targetUrl = url
        )
    }

    override fun onNewToken(token: String) {
        // Nothing to send from here: the website registers the current token the next time notifications are
        // checked or turned on in the app (it asks through AndroidPushBridge.requestToken()).
        Log.d(TAG, "FCM token refreshed")
    }

    private fun absoluteUrl(url: String?): String {
        if (url.isNullOrBlank()) return StockVibeRepository.BASE_URL
        if (url.startsWith("https://")) return url
        return "$SITE_ORIGIN/${url.trimStart('/')}"
    }

    companion object {
        private const val TAG = "StokVibeMessaging"
        private const val SITE_ORIGIN = "https://institute.stokvibe.com"
    }
}
