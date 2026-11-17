package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.StockVibeRepository
import com.example.StockVibeViewModel
import com.example.ui.theme.StokVibeBlue
import com.example.ui.theme.StokVibeCyan
import com.example.ui.theme.StokVibeNavy
import com.example.ui.theme.StokVibeSlate
import kotlinx.coroutines.delay

@Composable
fun StockVibeAppShell(
    viewModel: StockVibeViewModel,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current

    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val navigateToUrlEvent by viewModel.navigateToUrlEvent.collectAsStateWithLifecycle()
    val isMobileOptimized by viewModel.isMobileOptimized.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()

    var webHasError by remember { mutableStateOf(false) }
    var failingUrlState by remember { mutableStateOf("") }
    var errorDetailState by remember { mutableStateOf<String?>(null) }

    var canGoBackState by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var isDarkWebTheme by remember { mutableStateOf(false) }
    var webContainerBgColor by remember { mutableStateOf(Color.White) }

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

    // Intercept system back button for WebView history & error recovery
    BackHandler(enabled = webHasError || canGoBackState) {
        when {
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

    val view = LocalView.current
    val effectiveDarkBars = isOnboardingCompleted && !showSplash && isDarkWebTheme
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !effectiveDarkBars
        insetsController.isAppearanceLightNavigationBars = !effectiveDarkBars
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOnboardingCompleted && !showSplash) webContainerBgColor else Color.White)
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
                        onWebThemeChanged = { isDark, colorInt ->
                            isDarkWebTheme = isDark
                            webContainerBgColor = Color(colorInt)
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
}
