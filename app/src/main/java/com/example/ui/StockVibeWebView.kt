package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.FileOutputStream

private const val TAG = "StockVibeWebView"
private const val STOKVIBE_EDU_HOST = "institute.stokvibe.com"
private const val STOKVIBE_EDU_LOGIN_URL = "https://institute.stokvibe.com/login"

@Keep
private class BlobDownloadBridge(private val context: Context) {
    @JavascriptInterface
    fun onBlobData(base64Data: String, fileName: String, mimeType: String) {
        saveBase64ToDownloads(context, base64Data, fileName, mimeType)
    }
}

@Keep
private class AndroidNotificationBridge(
    private val context: Context,
    private val onRequestPermissionFromWeb: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) "granted" else "default"
        } else {
            "granted"
        }
    }

    @JavascriptInterface
    fun requestPermission(): String {
        val current = getPermission()
        if (current == "granted") return "granted"
        mainHandler.post {
            onRequestPermissionFromWeb()
        }
        return "default"
    }

    @JavascriptInterface
    fun showNotification(title: String, body: String, targetUrl: String?) {
        val safeTitle = title.ifBlank { "StokVibe EDU" }
        val safeUrl = targetUrl?.takeIf { it.isNotBlank() } ?: STOKVIBE_EDU_LOGIN_URL
        mainHandler.post {
            NotificationHelper.showNotification(
                context = context,
                notificationId = (System.currentTimeMillis() % 100000).toInt(),
                title = safeTitle,
                text = body,
                targetUrl = safeUrl
            )
        }
    }
}

/**
 * Firebase push for the website's "Push notifications" toggle (a WebView has no Web Push). Answers go back to the
 * page through window.__stokvibePushPermission(state) and window.__stokvibePushToken(token).
 */
@Keep
private class AndroidPushBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?,
    private val onRequestPermissionFromWeb: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** False when the build has no google-services.json, so the site keeps saying push isn't supported. */
    @JavascriptInterface
    fun isAvailable(): Boolean = try {
        FirebaseApp.getApps(context).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    @JavascriptInterface
    fun getPermission(): String {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return when {
            !runtimeGranted -> "default"
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> "granted"
            else -> "denied" // Turned off in the phone's settings.
        }
    }

    @JavascriptInterface
    fun requestPermission() {
        mainHandler.post { onRequestPermissionFromWeb() }
    }

    @JavascriptInterface
    fun requestToken() {
        mainHandler.post {
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result.orEmpty() else ""
                    if (!task.isSuccessful) Log.w(TAG, "Couldn't get FCM token", task.exception)
                    // FCM tokens are [A-Za-z0-9_:-], so they're safe inside a JS string literal.
                    val safe = token.filter { it.isLetterOrDigit() || it == '_' || it == ':' || it == '-' }
                    webViewProvider()?.evaluateJavascript(
                        "window.__stokvibePushToken && window.__stokvibePushToken('$safe');",
                        null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase isn't set up in this build", e)
                webViewProvider()?.evaluateJavascript("window.__stokvibePushToken && window.__stokvibePushToken('');", null)
            }
        }
    }
}

@Keep
private class AndroidPrintBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun printPage() {
        mainHandler.post {
            val webView = webViewProvider() ?: return@post
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    ?: return@post
                val jobName = "${webView.title?.takeIf { it.isNotBlank() } ?: "StokVibe EDU"} Document"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open native print dialog", e)
            }
        }
    }
}

@Keep
private class AndroidThemeBridge(
    private val onThemeDetected: (Boolean, Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onThemeChanged(isDark: Boolean, r: Int, g: Int, b: Int) {
        val colorInt = android.graphics.Color.rgb(
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )
        mainHandler.post {
            onThemeDetected(isDark, colorInt)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StockVibeWebViewContainer(
    urlToLoad: String,
    reloadTrigger: Int,
    isOnline: Boolean,
    isMobileOptimized: Boolean,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, Boolean, Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onReceivedTitle: (String) -> Unit,
    onErrorReceived: (String, String?) -> Unit,
    onNavigatedState: (Boolean, Boolean) -> Unit,
    onRenderProcessGone: () -> Unit,
    onWebThemeChanged: (Boolean, Int) -> Unit = { _, _ -> },
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnReceivedTitle by rememberUpdatedState(onReceivedTitle)
    val currentOnErrorReceived by rememberUpdatedState(onErrorReceived)
    val currentOnNavigatedState by rememberUpdatedState(onNavigatedState)
    val currentOnRenderProcessGone by rememberUpdatedState(onRenderProcessGone)
    val currentOnWebThemeChanged by rememberUpdatedState(onWebThemeChanged)
    val currentOnWebViewCreated by rememberUpdatedState(onWebViewCreated)

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastReportedPageUrl by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val resultState = if (isGranted) "granted" else "denied"
        webViewRef?.evaluateJavascript(
            "window.__stokvibeResolveNotificationPermission && window.__stokvibeResolveNotificationPermission('$resultState');" +
                "window.__stokvibePushPermission && window.__stokvibePushPermission('$resultState');",
            null
        )
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            if (uris != null) {
                callback.onReceiveValue(uris)
            } else if (data?.data != null) {
                callback.onReceiveValue(arrayOf(data.data!!))
            } else {
                callback.onReceiveValue(null)
            }
        } else {
            callback.onReceiveValue(null)
        }
    }

    val webViewInstance = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            isHapticFeedbackEnabled = true

            val webView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
                userAgentString = userAgentString.replace("; wv", "")
                textZoom = if (isMobileOptimized) 100 else 115
                cacheMode = if (isOnline) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                WebSettingsCompat.setWebAuthenticationSupport(
                    settings,
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                )
                Log.d(
                    TAG,
                    "WebAuthenticationSupport configured: " +
                        WebSettingsCompat.getWebAuthenticationSupport(settings)
                )
            } else {
                Log.w(TAG, "WebViewFeature.WEB_AUTHENTICATION is not supported on this device WebView.")
            }

            addJavascriptInterface(BlobDownloadBridge(context), "AndroidBlobBridge")
            addJavascriptInterface(AndroidPrintBridge(context) { webView }, "AndroidPrintBridge")
            addJavascriptInterface(
                AndroidThemeBridge { isDark, bgColorInt ->
                    webView.setBackgroundColor(bgColorInt)
                    currentOnWebThemeChanged(isDark, bgColorInt)
                },
                "AndroidThemeBridge"
            )
            addJavascriptInterface(
                AndroidNotificationBridge(context) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        webView.evaluateJavascript(
                            "window.__stokvibeResolveNotificationPermission && window.__stokvibeResolveNotificationPermission('granted');",
                            null
                        )
                    }
                },
                "AndroidNotificationBridge"
            )
            addJavascriptInterface(
                AndroidPushBridge(context, { webView }) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        webView.evaluateJavascript(
                            "window.__stokvibePushPermission && window.__stokvibePushPermission('granted');",
                            null
                        )
                    }
                },
                "AndroidPushBridge"
            )

            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    EMBEDDED_APP_COMPAT_SCRIPT,
                    setOf(
                        "https://$STOKVIBE_EDU_HOST",
                        "https://www.$STOKVIBE_EDU_HOST",
                        "https://stokvibe.com",
                        "https://www.stokvibe.com"
                    )
                )
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    val redirectUrl = redirectToLoginIfRoot(url)
                    if (redirectUrl != null) {
                        view.stopLoading()
                        view.loadUrl(redirectUrl)
                        return
                    }
                    super.onPageStarted(view, url, favicon)
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        view.evaluateJavascript(EMBEDDED_APP_COMPAT_SCRIPT, null)
                    }
                    currentOnPageStarted(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    val redirectUrl = redirectToLoginIfRoot(url)
                    if (redirectUrl != null) {
                        view.loadUrl(redirectUrl)
                        return
                    }
                    super.onPageFinished(view, url)
                    lastReportedPageUrl = url
                    view.evaluateJavascript("window.__stokvibeSyncTheme && window.__stokvibeSyncTheme();", null)
                    CookieManager.getInstance().flush()
                    currentOnPageFinished(url, view.canGoBack(), view.canGoForward())
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (!url.isNullOrBlank()) {
                        val redirectUrl = redirectToLoginIfRoot(url)
                        if (redirectUrl != null) {
                            view.loadUrl(redirectUrl)
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: ""
                    if (scheme == "https" || scheme == "http") {
                        val redirectUrl = redirectToLoginIfRoot(uri.toString())
                        if (redirectUrl != null) {
                            view.loadUrl(redirectUrl)
                            return true
                        }
                        return false
                    }
                    if (scheme == "intent") {
                        return try {
                            val parsedIntent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            if (parsedIntent.resolveActivity(context.packageManager) != null) {
                                parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(parsedIntent)
                                true
                            } else {
                                val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                if (!fallbackUrl.isNullOrBlank()) {
                                    view.loadUrl(fallbackUrl)
                                }
                                true
                            }
                        } catch (_: Exception) {
                            true
                        }
                    }
                    return try {
                        val externalIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(externalIntent)
                        true
                    } catch (_: Exception) {
                        true
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        currentOnErrorReceived(
                            request.url.toString(),
                            error.description?.toString()
                        )
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                        currentOnErrorReceived(
                            request.url.toString(),
                            "Server returned HTTP ${errorResponse.statusCode}"
                        )
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    currentOnRenderProcessGone()
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    currentOnProgressChanged(newProgress)
                    currentOnNavigatedState(view.canGoBack(), view.canGoForward())
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        currentOnReceivedTitle(title)
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val originHost = request.origin?.host?.lowercase()
                    if (originHost == STOKVIBE_EDU_HOST) {
                        val allowed = request.resources.filter {
                            it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                        }.toTypedArray()
                        if (allowed.isNotEmpty()) {
                            request.grant(allowed)
                            return
                        }
                    }
                    request.deny()
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    return try {
                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        fileChooserLauncher.launch(intent)
                        true
                    } catch (_: Exception) {
                        filePathCallback = null
                        false
                    }
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                handleWebDownload(
                    context = context,
                    webView = this,
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType
                )
            }

            webViewRef = this
            currentOnWebViewCreated(this)
        }
    }

    LaunchedEffect(webViewInstance, isOnline, isMobileOptimized) {
        webViewInstance.settings.cacheMode = if (isOnline) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        webViewInstance.settings.textZoom = if (isMobileOptimized) 100 else 115
    }

    LaunchedEffect(webViewInstance, urlToLoad) {
        if (urlToLoad == lastReportedPageUrl) return@LaunchedEffect
        val targetUrl = redirectToLoginIfRoot(urlToLoad) ?: urlToLoad
        if (targetUrl.isNotBlank() && webViewInstance.url != targetUrl) {
            webViewInstance.loadUrl(targetUrl)
        }
    }

    LaunchedEffect(webViewInstance, reloadTrigger) {
        if (reloadTrigger > 0 && urlToLoad.isNotBlank()) {
            val targetUrl = redirectToLoginIfRoot(urlToLoad) ?: urlToLoad
            if (webViewInstance.url.isNullOrBlank() || webViewInstance.url != targetUrl) {
                webViewInstance.loadUrl(targetUrl)
            } else {
                webViewInstance.reload()
            }
        }
    }

    DisposableEffect(lifecycleOwner, webViewInstance) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViewInstance.onResume()
                Lifecycle.Event.ON_PAUSE -> webViewInstance.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            webViewInstance.stopLoading()
            webViewInstance.destroy()
        }
    }

    AndroidView(
        factory = { webViewInstance },
        modifier = modifier.fillMaxSize()
    )
}

private val EMBEDDED_APP_COMPAT_SCRIPT = """
    (function() {
        if (window.__stokvibeCompatInstalled) return;
        window.__stokvibeCompatInstalled = true;

        // 1. Native Notification & ServiceWorker Notification bridge
        if (window.AndroidNotificationBridge) {
            var pendingPermissionResolvers = [];
            window.__stokvibeResolveNotificationPermission = function(state) {
                while (pendingPermissionResolvers.length > 0) {
                    var resolve = pendingPermissionResolvers.shift();
                    try { resolve(state); } catch (_) {}
                }
            };

            if (typeof window.Notification === 'undefined') {
                var NativeNotification = function(title, options) {
                    options = options || {};
                    this.title = String(title || 'StokVibe EDU');
                    this.body = String(options.body || '');
                    this.tag = options.tag ? String(options.tag) : '';
                    this.icon = options.icon ? String(options.icon) : '';
                    this.dir = options.dir || 'auto';
                    this.lang = options.lang || '';
                    this.data = options.data || null;
                    this.onclick = null;
                    this.onshow = null;
                    this.onerror = null;
                    this.onclose = null;
                    var self = this;
                    var targetUrl = (options.data && options.data.url) ? String(options.data.url) : window.location.href;
                    window.AndroidNotificationBridge.showNotification(this.title, this.body, targetUrl);
                    setTimeout(function() {
                        if (typeof self.onshow === 'function') {
                            try { self.onshow(new Event('show')); } catch (_) {}
                        }
                    }, 0);
                };
                NativeNotification.prototype.close = function() {
                    if (typeof this.onclose === 'function') {
                        try { this.onclose(new Event('close')); } catch (_) {}
                    }
                };
                NativeNotification.prototype.addEventListener = function() {};
                NativeNotification.prototype.removeEventListener = function() {};
                NativeNotification.prototype.dispatchEvent = function() { return true; };

                Object.defineProperty(NativeNotification, 'permission', {
                    get: function() {
                        return window.AndroidNotificationBridge.getPermission();
                    },
                    configurable: true
                });
                Object.defineProperty(NativeNotification, 'maxActions', {
                    get: function() { return 2; },
                    configurable: true
                });
                NativeNotification.requestPermission = function(callback) {
                    var p = new Promise(function(resolve) {
                        var current = window.AndroidNotificationBridge.getPermission();
                        if (current === 'granted') {
                            resolve('granted');
                            return;
                        }
                        pendingPermissionResolvers.push(resolve);
                        window.AndroidNotificationBridge.requestPermission();
                    });
                    if (typeof callback === 'function') {
                        p.then(callback);
                    }
                    return p;
                };
                window.Notification = NativeNotification;
            }

            if (typeof ServiceWorkerRegistration !== 'undefined' && ServiceWorkerRegistration.prototype) {
                ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
                    options = options || {};
                    var targetUrl = (options.data && options.data.url) ? String(options.data.url) : window.location.href;
                    window.AndroidNotificationBridge.showNotification(
                        String(title || 'StokVibe EDU'),
                        String(options.body || ''),
                        targetUrl
                    );
                    return Promise.resolve();
                };
                if (!ServiceWorkerRegistration.prototype.getNotifications) {
                    ServiceWorkerRegistration.prototype.getNotifications = function() {
                        return Promise.resolve([]);
                    };
                }
            }

            if (navigator.permissions && typeof navigator.permissions.query === 'function') {
                var originalPermissionsQuery = navigator.permissions.query.bind(navigator.permissions);
                navigator.permissions.query = function(descriptor) {
                    if (descriptor && descriptor.name === 'notifications') {
                        var p = window.AndroidNotificationBridge.getPermission();
                        var state = (p === 'default') ? 'prompt' : p;
                        return Promise.resolve({
                            name: 'notifications',
                            state: state,
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; }
                        });
                    }
                    return originalPermissionsQuery(descriptor);
                };
            }
        }

        // 2. Native Android PrintManager bridge for receipts & reports
        if (window.AndroidPrintBridge) {
            window.print = function() {
                window.AndroidPrintBridge.printPage();
            };
        }

        // 3. Dynamic light/dark theme & system bar color sync
        window.__stokvibeSyncTheme = function() {
            if (!window.AndroidThemeBridge || !document.documentElement) return;
            try {
                var body = document.body;
                var html = document.documentElement;
                var bgStr = body ? getComputedStyle(body).backgroundColor : '';
                if (!bgStr || bgStr === 'rgba(0, 0, 0, 0)' || bgStr === 'transparent') {
                    bgStr = getComputedStyle(html).backgroundColor;
                }
                var match = bgStr && bgStr.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
                var r = 255, g = 255, b = 255;
                if (match) {
                    r = parseInt(match[1], 10);
                    g = parseInt(match[2], 10);
                    b = parseInt(match[3], 10);
                } else if (html.classList.contains('dark')) {
                    r = 15; g = 23; b = 42;
                }
                var luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
                var isDark = html.classList.contains('dark') || luminance < 0.5;
                window.AndroidThemeBridge.onThemeChanged(isDark, r, g, b);
            } catch (_) {}
        };

        var attachThemeObserver = function() {
            window.__stokvibeSyncTheme();
            if (typeof MutationObserver !== 'undefined' && document.documentElement) {
                var obs = new MutationObserver(function() {
                    window.__stokvibeSyncTheme();
                });
                obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style', 'data-theme'] });
                if (document.body) {
                    obs.observe(document.body, { attributes: true, attributeFilter: ['class', 'style'] });
                }
            }
        };
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', attachThemeObserver);
        } else {
            attachThemeObserver();
        }
    })();
""".trimIndent()

private fun redirectToLoginIfRoot(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path ?: ""
        if ((host == STOKVIBE_EDU_HOST || host == "www.$STOKVIBE_EDU_HOST") &&
            (path.isEmpty() || path == "/")
        ) {
            STOKVIBE_EDU_LOGIN_URL
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun handleWebDownload(
    context: Context,
    webView: WebView,
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimeType: String
) {
    val resolvedMime = mimeType.ifBlank { "application/octet-stream" }
    val guessedName = URLUtil.guessFileName(url, contentDisposition, resolvedMime)
        .ifBlank { "stokvibe_edu_export_${System.currentTimeMillis()}" }

    when {
        url.startsWith("blob:", ignoreCase = true) -> {
            val safeName = guessedName.replace("'", "")
            val safeMime = resolvedMime.replace("'", "")
            val js = """
                (function() {
                    fetch('$url').then(function(res) {
                        return res.blob();
                    }).then(function(blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            if (window.AndroidBlobBridge && reader.result) {
                                window.AndroidBlobBridge.onBlobData(reader.result, '$safeName', '$safeMime');
                            }
                        };
                        reader.readAsDataURL(blob);
                    });
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
        url.startsWith("data:", ignoreCase = true) -> {
            saveBase64ToDownloads(context, url, guessedName, resolvedMime)
        }
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(resolvedMime)
                    val cookies = CookieManager.getInstance().getCookie(url)
                    if (!cookies.isNullOrBlank()) {
                        addRequestHeader("Cookie", cookies)
                    }
                    addRequestHeader("User-Agent", userAgent)
                    setTitle(guessedName)
                    setDescription("Downloading StokVibe EDU file")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessedName)
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "Downloading $guessedName...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to start download.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun saveBase64ToDownloads(
    context: Context,
    dataUri: String,
    fileName: String,
    mimeType: String
) {
    try {
        val base64Part = dataUri.substringAfter("base64,", "")
        if (base64Part.isBlank()) return
        val bytes = Base64.decode(base64Part, Base64.DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_SHORT).show()
            }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            Toast.makeText(context, "Saved $fileName", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Failed to save exported file.", Toast.LENGTH_SHORT).show()
    }
}
