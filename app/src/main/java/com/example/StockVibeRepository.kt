package com.example

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(
    val id: String,
    val title: String,
    val url: String
)

data class NotificationLog(
    val id: Int,
    val title: String,
    val text: String,
    val type: String, // "FEE_DUE", "BATCH_UPDATE", "GENERAL"
    val timestamp: Long,
    val targetUrl: String?
)

class StockVibeRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stockvibe_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BOOKMARKS = "saved_bookmarks"
        private const val KEY_NOTIFICATIONS = "notifications_log"
        private const val KEY_DEFAULT_URL = "default_view_url"
        private const val KEY_OPTIMIZED_MODE = "mobile_optimized_mode"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed_state"
        const val BASE_URL = "https://institute.stokvibe.com/login"
    }

    init {
        val savedDefaultUrl = prefs.getString(KEY_DEFAULT_URL, null)
        if (savedDefaultUrl != null && isLegacyOrRootUrl(savedDefaultUrl)) {
            prefs.edit().putString(KEY_DEFAULT_URL, BASE_URL).apply()
        }

        if (!prefs.contains(KEY_BOOKMARKS)) {
            val defaults = listOf(
                Bookmark("1", "StokVibe EDU Login", BASE_URL),
                Bookmark("2", "Institute Dashboard", "https://institute.stokvibe.com/dashboard"),
                Bookmark("3", "Students & Batches", "https://institute.stokvibe.com/students")
            )
            saveBookmarks(defaults)
        } else {
            val existing = getBookmarks()
            if (existing.any { isLegacyOrRootUrl(it.url) }) {
                val migrated = existing.map { b ->
                    if (isLegacyOrRootUrl(b.url)) {
                        b.copy(
                            title = if (b.id == "1") "StokVibe EDU Login" else b.title,
                            url = BASE_URL
                        )
                    } else {
                        b
                    }
                }.distinctBy { it.url }
                saveBookmarks(migrated)
            }
        }

        if (!prefs.contains(KEY_NOTIFICATIONS)) {
            val defaults = listOf(
                NotificationLog(
                    id = 1001,
                    title = "Institute Portal Connected",
                    text = "StokVibe EDU mobile workspace is ready.",
                    type = "GENERAL",
                    timestamp = System.currentTimeMillis() - 7200000,
                    targetUrl = BASE_URL
                ),
                NotificationLog(
                    id = 1002,
                    title = "Fee & Batch Alerts Ready",
                    text = "Real-time notifications are configured for your institute.",
                    type = "FEE_DUE",
                    timestamp = System.currentTimeMillis() - 3600000,
                    targetUrl = BASE_URL
                )
            )
            saveNotifications(defaults)
        }
    }

    private fun isLegacyOrRootUrl(url: String): Boolean {
        val clean = url.trim().trimEnd('/')
        return clean.contains("arionys.online") ||
            clean.contains("arionys.software") ||
            clean.contains("business.stokvibe.com") ||
            clean == "https://institute.stokvibe.com" ||
            clean == "http://institute.stokvibe.com" ||
            clean == "https://www.institute.stokvibe.com" ||
            clean == "http://www.institute.stokvibe.com"
    }

    fun getBookmarks(): List<Bookmark> {
        val jsonStr = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<Bookmark>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Bookmark(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        url = obj.getString("url")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveBookmarks(bookmarks: List<Bookmark>) {
        try {
            val array = JSONArray()
            for (b in bookmarks) {
                val obj = JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("url", b.url)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addBookmark(title: String, url: String) {
        val list = getBookmarks().toMutableList()
        list.removeAll { it.url == url }
        list.add(Bookmark(System.currentTimeMillis().toString(), title, url))
        saveBookmarks(list)
    }

    fun deleteBookmark(id: String) {
        val list = getBookmarks().toMutableList()
        list.removeAll { it.id == id }
        saveBookmarks(list)
    }

    fun getNotifications(): List<NotificationLog> {
        val jsonStr = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NotificationLog>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NotificationLog(
                        id = obj.getInt("id"),
                        title = obj.getString("title"),
                        text = obj.getString("text"),
                        type = obj.getString("type"),
                        timestamp = obj.getLong("timestamp"),
                        targetUrl = obj.optString("targetUrl").takeIf { it.isNotEmpty() }
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveNotifications(logs: List<NotificationLog>) {
        try {
            val array = JSONArray()
            for (log in logs) {
                val obj = JSONObject().apply {
                    put("id", log.id)
                    put("title", log.title)
                    put("text", log.text)
                    put("type", log.type)
                    put("timestamp", log.timestamp)
                    if (log.targetUrl != null) {
                        put("targetUrl", log.targetUrl)
                    }
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_NOTIFICATIONS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addNotificationLog(title: String, text: String, type: String, targetUrl: String? = null) {
        val list = getNotifications().toMutableList()
        list.add(
            NotificationLog(
                id = (System.currentTimeMillis() % 100000).toInt(),
                title = title,
                text = text,
                type = type,
                timestamp = System.currentTimeMillis(),
                targetUrl = targetUrl
            )
        )
        saveNotifications(list)
    }

    fun clearNotifications() {
        saveNotifications(emptyList())
    }

    fun getStartingUrl(): String {
        return prefs.getString(KEY_DEFAULT_URL, BASE_URL) ?: BASE_URL
    }

    fun saveStartingUrl(url: String) {
        prefs.edit().putString(KEY_DEFAULT_URL, url).apply()
    }

    fun isMobileOptimized(): Boolean {
        return prefs.getBoolean(KEY_OPTIMIZED_MODE, true)
    }

    fun setMobileOptimized(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPTIMIZED_MODE, enabled).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}
