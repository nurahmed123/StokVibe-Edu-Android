package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.StockVibeRepository
import com.example.StockVibeViewModel
import com.example.ui.theme.StokVibeBlue
import com.example.ui.theme.StokVibeCyan
import com.example.ui.theme.StokVibeNavy
import com.example.ui.theme.StokVibeSlate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockVibeAppShell(
    viewModel: StockVibeViewModel,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val navigateToUrlEvent by viewModel.navigateToUrlEvent.collectAsStateWithLifecycle()
    val isMobileOptimized by viewModel.isMobileOptimized.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()

    val shortcutsSheetState = rememberModalBottomSheetState()
    var showShortcutsSheet by remember { mutableStateOf(false) }

    val diagnosticsSheetState = rememberModalBottomSheetState()
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    var webHasError by remember { mutableStateOf(false) }
    var failingUrlState by remember { mutableStateOf("") }
    var errorDetailState by remember { mutableStateOf<String?>(null) }
    var showPasskeyBrowserDialog by remember { mutableStateOf(false) }

    var canGoBackState by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Brief branded splash only on cold start, guaranteed to dismiss within 1.8s
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1800L)
        showSplash = false
    }
    LaunchedEffect(loadProgress, webHasError, isOnboardingCompleted) {
        if (!isOnboardingCompleted || loadProgress >= 65 || webHasError) {
            showSplash = false
        }
    }

    // Request notification permission cleanly after onboarding rather than blocking startup
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Real-time institute alerts enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isOnboardingCompleted) {
        if (isOnboardingCompleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Handle external deep links / notification tap navigation events
    LaunchedEffect(navigateToUrlEvent) {
        val targetUrl = navigateToUrlEvent ?: return@LaunchedEffect
        webHasError = false
        errorDetailState = null
        when (targetUrl) {
            "COMMAND_REFRESH" -> reloadTrigger++
            "COMMAND_HOME" -> {
                viewModel.setUrlState(StockVibeRepository.BASE_URL)
                reloadTrigger++
            }
            else -> viewModel.setUrlState(targetUrl)
        }
        viewModel.clearNavigationEvent()
    }

    // Intercept system back button for WebView history & overlays
    BackHandler(enabled = showShortcutsSheet || showDiagnosticsSheet || webHasError || canGoBackState) {
        when {
            showShortcutsSheet -> showShortcutsSheet = false
            showDiagnosticsSheet -> showDiagnosticsSheet = false
            webHasError -> {
                webHasError = false
                errorDetailState = null
                reloadTrigger++
            }
            webViewRef?.canGoBack() == true -> {
                webViewRef?.goBack()
            }
        }
    }

    // Root Box ensures WebView, Error Fallback, Onboarding, and Splash never crush each other in a Column
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (!isOnline && !webHasError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFD97706))
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.offline_banner_text),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                key(webViewGeneration) {
                    StockVibeWebViewContainer(
                        urlToLoad = currentUrl,
                        reloadTrigger = reloadTrigger,
                        isOnline = isOnline,
                        isMobileOptimized = isMobileOptimized,
                        onPageStarted = { _ ->
                            viewModel.setPageLoadProgress(5)
                            webHasError = false
                            errorDetailState = null
                        },
                        onPageFinished = { url, back, _ ->
                            viewModel.setPageLoadProgress(100)
                            viewModel.setUrlState(url)
                            canGoBackState = back
                        },
                        onProgressChanged = { progress ->
                            viewModel.setPageLoadProgress(progress)
                        },
                        onReceivedTitle = { title ->
                            viewModel.setPageTitle(title)
                        },
                        onErrorReceived = { failingUrl, errorDetail ->
                            webHasError = true
                            failingUrlState = failingUrl
                            errorDetailState = errorDetail
                        },
                        onNavigatedState = { back, _ ->
                            canGoBackState = back
                        },
                        onRenderProcessGone = {
                            webHasError = false
                            webViewGeneration++
                        },
                        onPasskeyDomainError = { _, _ ->
                            showPasskeyBrowserDialog = true
                        },
                        onWebViewCreated = { web ->
                            webViewRef = web
                            onWebViewCreated(web)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (webHasError) {
                    WebErrorFallbackScreen(
                        isOnline = isOnline,
                        failingUrl = failingUrlState,
                        errorDetail = errorDetailState,
                        onRetry = {
                            webHasError = false
                            errorDetailState = null
                            reloadTrigger++
                        },
                        onGoHome = {
                            webHasError = false
                            errorDetailState = null
                            viewModel.setUrlState(StockVibeRepository.BASE_URL)
                            reloadTrigger++
                        }
                    )
                }

                if (isLoading && !webHasError) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopStart),
                        color = StokVibeCyan,
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        // Onboarding overlay
        AnimatedVisibility(
            visible = !isOnboardingCompleted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OnboardingScreen(
                onGetStarted = { viewModel.completeOnboarding() }
            )
        }

        // Branded cold-start splash overlay
        AnimatedVisibility(
            visible = showSplash && isOnboardingCompleted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    StokVibeLogo()
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = StokVibeNavy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.brand_tagline),
                        fontSize = 14.sp,
                        color = StokVibeSlate,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(36.dp))
                    CircularProgressIndicator(
                        color = StokVibeBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    if (showShortcutsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShortcutsSheet = false },
            sheetState = shortcutsSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ShortcutsTrayContent(
                bookmarks = bookmarks,
                onSelectShortcut = { shortcutUrl ->
                    webHasError = false
                    viewModel.triggerNavigation(shortcutUrl)
                    coroutineScope.launch {
                        shortcutsSheetState.hide()
                    }.invokeOnCompletion {
                        if (!shortcutsSheetState.isVisible) {
                            showShortcutsSheet = false
                        }
                    }
                },
                onDeleteShortcut = { id ->
                    viewModel.deleteBookmark(id)
                }
            )
        }
    }

    if (showDiagnosticsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDiagnosticsSheet = false },
            sheetState = diagnosticsSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AlertsDiagnosticsTrayContent(
                notifications = notifications,
                onTriggerMockNotification = { title, message, type, delaySec ->
                    viewModel.simulatePushNotification(
                        title = title,
                        message = message,
                        type = type,
                        delaySeconds = delaySec,
                        context = context
                    )
                    Toast.makeText(
                        context,
                        if (delaySec > 0) "Alert scheduled in $delaySec seconds!" else "Alert triggered!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onClearLog = {
                    viewModel.clearNotifications()
                    Toast.makeText(context, "Log cleared.", Toast.LENGTH_SHORT).show()
                },
                onLogTapped = { targetUrl ->
                    if (!targetUrl.isNullOrEmpty()) {
                        webHasError = false
                        viewModel.triggerNavigation(targetUrl)
                        coroutineScope.launch {
                            diagnosticsSheetState.hide()
                        }.invokeOnCompletion {
                            if (!diagnosticsSheetState.isVisible) {
                                showDiagnosticsSheet = false
                            }
                        }
                    }
                }
            )
        }
    }

    if (showPasskeyBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showPasskeyBrowserDialog = false },
            title = {
                Text(
                    text = "Passkey Requires Browser Link",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Passkeys inside the app require Digital Asset Links (assetlinks.json) on institute.stokvibe.com. You can open this page in Chrome to set up or use your passkey on the site's own address.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasskeyBrowserDialog = false
                        val target = currentUrl.ifBlank { StockVibeRepository.BASE_URL }
                        try {
                            val customTabsIntent = CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .build()
                            customTabsIntent.launchUrl(context, Uri.parse(target))
                        } catch (_: Exception) {
                            try {
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "No browser available.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Open in Browser")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasskeyBrowserDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
