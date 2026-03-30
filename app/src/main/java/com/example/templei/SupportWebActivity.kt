package com.example.templei

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Dedicated host for the in-app project support page.
 */
class SupportWebActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_support_web)

        val root = findViewById<View>(R.id.supportWebRoot)
        val rootPaddingStart = root.paddingStart
        val rootPaddingTop = root.paddingTop
        val rootPaddingEnd = root.paddingEnd
        val rootPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPaddingRelative(
                rootPaddingStart + systemBars.left,
                rootPaddingTop + systemBars.top,
                rootPaddingEnd + systemBars.right,
                rootPaddingBottom + systemBars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)

        webView = findViewById(R.id.supportWebView)
        loadingIndicator = findViewById(R.id.supportLoadingIndicator)
        statusText = findViewById(R.id.supportStatusText)

        findViewById<Button>(R.id.supportCloseButton).setOnClickListener {
            finish()
        }

        configureWebView()
        statusText.text = getString(R.string.main_support_loading)
        webView.loadUrl(GITHUB_SPONSORS_URL)
    }

    @Deprecated("Uses the WebView history before leaving the support screen")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun configureWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingIndicator.visibility = View.VISIBLE
                statusText.visibility = View.VISIBLE
                statusText.text = getString(R.string.main_support_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingIndicator.visibility = View.GONE
                statusText.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    loadingIndicator.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = getString(
                        R.string.main_support_error,
                        error?.description?.toString() ?: getString(R.string.screen2_unknown_error),
                    )
                }
            }
        }
    }

    private companion object {
        const val GITHUB_SPONSORS_URL = "https://github.com/sponsors/ZBZFirst"
    }
}
