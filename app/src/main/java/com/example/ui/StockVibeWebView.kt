package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.File
import java.io.FileOutputStream

@Keep
private class BlobDownloadBridge(private val context: Context) {
    @JavascriptInterface
    fun onBlobData(base64Data: String, fileName: String, mimeType: String) {
        saveBase64ToDownloads(context, base64Data, fileName, mimeType)
    }
}

@Keep
private class PasskeyErrorBridge(
    private val onMainThreadError: (String, String) -> Unit
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun onPasskeyError(errorName: String, errorMessage: String) {
        mainHandler.post {
            onMainThreadError(errorName, errorMessage)
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
    onPasskeyDomainError: (String, String) -> Unit,
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
    val currentOnPasskeyDomainError by rememberUpdatedState(onPasskeyDomainError)
    val currentOnWebViewCreated by rememberUpdatedState(onWebViewCreated)

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

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
                builtInZoomControls = true
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
            }

            addJavascriptInterface(BlobDownloadBridge(context), "AndroidBlobBridge")
            addJavascriptInterface(
                PasskeyErrorBridge { name, message ->
                    currentOnPasskeyDomainError(name, message)
                },
                "AndroidPasskeyBridge"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    val redirectUrl = redirectToLoginIfRoot(url)
                    if (redirectUrl != null) {
                        view.stopLoading()
                        view.loadUrl(redirectUrl)
                        return
                    }
                    super.onPageStarted(view, url, favicon)
                    currentOnPageStarted(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    val redirectUrl = redirectToLoginIfRoot(url)
                    if (redirectUrl != null) {
                        view.loadUrl(redirectUrl)
                        return
                    }
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    injectPasskeyErrorObserver(view)
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

private const val STOKVIBE_EDU_HOST = "institute.stokvibe.com"
private const val STOKVIBE_EDU_LOGIN_URL = "https://institute.stokvibe.com/login"

private fun injectPasskeyErrorObserver(webView: WebView) {
    val js = """
        (function() {
            if (window.__stokvibePasskeyObserverInstalled || !navigator.credentials) return;
            window.__stokvibePasskeyObserverInstalled = true;
            var origCreate = navigator.credentials.create ? navigator.credentials.create.bind(navigator.credentials) : null;
            var origGet = navigator.credentials.get ? navigator.credentials.get.bind(navigator.credentials) : null;
            function notifyIfDomainOrSupportError(err) {
                try {
                    var name = err && err.name ? String(err.name) : '';
                    var msg = err && err.message ? String(err.message) : '';
                    var lower = msg.toLowerCase();
                    var isDomainIssue = name === 'SecurityError' ||
                        name === 'NotSupportedError' ||
                        lower.indexOf('origin') !== -1 ||
                        lower.indexOf('relying party') !== -1 ||
                        lower.indexOf('rp id') !== -1 ||
                        lower.indexOf('asset') !== -1 ||
                        lower.indexOf('domain') !== -1 ||
                        lower.indexOf('not supported') !== -1;
                    if (isDomainIssue && window.AndroidPasskeyBridge) {
                        window.AndroidPasskeyBridge.onPasskeyError(name, msg);
                    }
                } catch (_) {}
            }
            if (origCreate) {
                navigator.credentials.create = function(options) {
                    return origCreate(options).catch(function(err) {
                        if (options && options.publicKey) {
                            notifyIfDomainOrSupportError(err);
                        }
                        throw err;
                    });
                };
            }
            if (origGet) {
                navigator.credentials.get = function(options) {
                    return origGet(options).catch(function(err) {
                        if (options && options.publicKey) {
                            notifyIfDomainOrSupportError(err);
                        }
                        throw err;
                    });
                };
            }
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

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
