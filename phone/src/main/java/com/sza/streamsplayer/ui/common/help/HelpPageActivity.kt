package com.sza.streamsplayer.ui.common.help

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.databinding.ActivityHelpPageBinding
import com.sza.streamsplayer.utils.HelpPageBodyLoader
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Offline help page viewer (`CONTEXT-HELP` rules 7 and 12): a bundled `assets/help/` page in a WebView with
 * JavaScript off and no network. A link that leaves the package is handed to the system browser after a
 * confirmation; Back walks back through the pages and then returns to the screen that asked.
 */
class HelpPageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpPageBinding

    private val assetUrl: String by lazy { intent.getStringExtra(EXTRA_ASSET_URL).orEmpty() }
    private val siteUrl: String by lazy { intent.getStringExtra(EXTRA_SITE_URL).orEmpty() }

    // The header mirrors through the layout direction; the WebView body needs the same forced on the page.
    private val mirrorPageBody: Boolean
        get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val webView = binding.helpWebView
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()
        setupToolbar()
        setupWebView()
        onBackPressedDispatcher.addCallback(this, backCallback)
        if (assetUrl.startsWith(HelpTargetResolver.ASSET_ROOT)) {
            loadHelpPage()
        } else {
            Timber.w("Help viewer refused a start URL outside the bundled pages")
            finish()
        }
    }

    private fun setupToolbar() {
        binding.helpToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val siteItem = binding.helpToolbar.menu.findItem(R.id.menu_help_open_on_site)
        siteItem.isVisible = siteUrl.isNotBlank()
        binding.helpToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_help_open_on_site) {
                HelpOpenManager.openInBrowser(this, siteUrl)
                true
            } else {
                false
            }
        }
    }

    private fun setupWebView() {
        val webView = binding.helpWebView
        webView.setBackgroundColor(MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSurface))
        webView.settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            allowContentAccess = false
            // Asset URLs stay readable: allowFileAccess does not gate file:///android_asset/.
            allowFileAccess = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val external = !url.startsWith(HelpTargetResolver.ASSET_ROOT)
                if (external) confirmExternalLink(url)
                return external
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                // WebView reports the URL itself as the title of a page without <title>.
                val pageTitle = title?.takeUnless { it.isBlank() || it.startsWith(FILE_SCHEME) }
                binding.helpToolbar.title = pageTitle ?: getString(R.string.help_page_title)
            }
        }
    }

    // A WebView never calls shouldInterceptRequest for android_asset, so the mirrored body is read,
    // patched and served through loadDataWithBaseURL; a plain locale keeps the direct asset load.
    private fun loadHelpPage() {
        val webView = binding.helpWebView
        if (!mirrorPageBody) {
            webView.loadUrl(assetUrl)
            return
        }
        lifecycleScope.launch {
            val html = HelpPageBodyLoader.load(this@HelpPageActivity, assetUrl, mirrorBody = true)
            if (html == null) {
                webView.loadUrl(assetUrl)
                return@launch
            }
            webView.loadDataWithBaseURL(assetUrl, html, "text/html", "utf-8", assetUrl)
        }
    }

    private fun confirmExternalLink(url: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_external_link_title)
            .setMessage(R.string.help_external_link_message)
            .setPositiveButton(R.string.help_external_link_open) { _, _ -> HelpOpenManager.openInBrowser(this, url) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.helpPageRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        val webView = binding.helpWebView
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ASSET_URL = "help_asset_url"
        private const val EXTRA_SITE_URL = "help_site_url"
        private const val FILE_SCHEME = "file:"

        fun intent(context: Context, assetUrl: String, siteUrl: String?): Intent =
            Intent(context, HelpPageActivity::class.java)
                .putExtra(EXTRA_ASSET_URL, assetUrl)
                .putExtra(EXTRA_SITE_URL, siteUrl.orEmpty())
    }
}
