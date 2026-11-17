package com.example

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.StockVibeAppShell
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StockVibeViewModel by viewModels()
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE
            )
        )

        handleDeepLink(intent)

        setContent {
            MyApplicationTheme {
                StockVibeAppShell(viewModel = viewModel) { web ->
                    webView = web
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val dataUri = intent?.data
        if (dataUri != null) {
            val urlString = dataUri.toString()
            if (urlString.isNotBlank()) {
                viewModel.triggerNavigation(urlString)
                return
            }
        }

        val targetUrl = intent?.getStringExtra("TARGET_URL")
        if (!targetUrl.isNullOrBlank()) {
            viewModel.triggerNavigation(targetUrl)
        }
    }
}
