package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class StockVibeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StockVibeRepository(application)
    private val networkMonitor = NetworkMonitor(application)

    // Web navigation state
    private val _currentUrl = MutableStateFlow(repository.getStartingUrl())
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadProgress = MutableStateFlow(0)
    val loadProgress: StateFlow<Int> = _loadProgress.asStateFlow()

    private val _webTitle = MutableStateFlow("StokVibe EDU")
    val webTitle: StateFlow<String> = _webTitle.asStateFlow()

    // Persistent settings/history elements
    private val _bookmarks = MutableStateFlow(repository.getBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notifications = MutableStateFlow(repository.getNotifications())
    val notifications: StateFlow<List<NotificationLog>> = _notifications.asStateFlow()

    private val _isMobileOptimized = MutableStateFlow(repository.isMobileOptimized())
    val isMobileOptimized: StateFlow<Boolean> = _isMobileOptimized.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(repository.isOnboardingCompleted())
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    // Network connection state
    private val _isOnline = MutableStateFlow(networkMonitor.isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Flag to trigger navigation to a new URL inside WebView
    private val _navigateToUrlEvent = MutableStateFlow<String?>(null)
    val navigateToUrlEvent: StateFlow<String?> = _navigateToUrlEvent.asStateFlow()

    init {
        // Create Notification Channel immediately
        NotificationHelper.createNotificationChannel(application)

        // Monitor network state dynamically
        networkMonitor.isOnline
            .onEach { online ->
                _isOnline.value = online
            }
            .launchIn(viewModelScope)
    }

    fun triggerNavigation(url: String) {
        _navigateToUrlEvent.value = url
    }

    fun clearNavigationEvent() {
        _navigateToUrlEvent.value = null
    }

    fun setPageLoadProgress(progress: Int) {
        _loadProgress.value = progress
        _isLoading.value = progress < 100
    }

    fun setPageTitle(title: String) {
        if (title.isNotBlank() && !title.contains("about:blank")) {
            _webTitle.value = title
        }
    }

    fun setUrlState(url: String) {
        if (url.isNotBlank()) {
            _currentUrl.value = url
        }
    }

    fun addBookmark(title: String, url: String) {
        repository.addBookmark(title, url)
        _bookmarks.value = repository.getBookmarks()
    }

    fun deleteBookmark(id: String) {
        repository.deleteBookmark(id)
        _bookmarks.value = repository.getBookmarks()
    }

    fun toggleMobileOptimization() {
        val next = !_isMobileOptimized.value
        repository.setMobileOptimized(next)
        _isMobileOptimized.value = next
    }

    fun clearNotifications() {
        repository.clearNotifications()
        _notifications.value = repository.getNotifications()
    }

    fun completeOnboarding() {
        repository.setOnboardingCompleted(true)
        _isOnboardingCompleted.value = true
    }

    // High fidelity Notification Simulator
    fun simulatePushNotification(
        title: String,
        message: String,
        type: String,
        delaySeconds: Int,
        context: Context,
        specificUrlSuffix: String? = null
    ) {
        viewModelScope.launch {
            val targetUrl = if (specificUrlSuffix != null) {
                if (specificUrlSuffix.startsWith("http")) specificUrlSuffix else "${StockVibeRepository.BASE_URL}$specificUrlSuffix"
            } else {
                StockVibeRepository.BASE_URL
            }

            if (delaySeconds > 0) {
                // Background delay simulation (works even if user minimizes app during timer!)
                delay(delaySeconds * 1000L)
            }

            // 1. Dispatch actual System Notification
            val notificationId = (System.currentTimeMillis() % 10000).toInt()
            NotificationHelper.showNotification(
                context = context,
                notificationId = notificationId,
                title = title,
                text = message,
                targetUrl = targetUrl
            )

            // 2. Add log entry to list
            repository.addNotificationLog(title, message, type, targetUrl)
            _notifications.value = repository.getNotifications()
        }
    }
}
