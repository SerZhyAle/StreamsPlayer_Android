package com.sza.streamsplayer.ui.settings.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.sza.streamsplayer.BuildConfig
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.ui.common.support.ExchangePortalLinks
import com.sza.streamsplayer.ui.common.support.exposeAsLink
import timber.log.Timber
import java.util.Locale

/**
 * About destination page fulfilling SETTINGS-NAVIGATION 2.0 section 3 rule 3.
 *
 * Exposes:
 * - Concise product description
 * - Running version from build metadata
 * - License, privacy, in-app help, official portal, support, repository and download/update links
 * - User-initiated diagnostic report export
 * - Necessary resources and related products inventory
 */
class SettingsAboutDestinationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val REPOSITORY_URL = "https://github.com/SerZhyAle/StreamsPlayer_Android"
        const val SUPPORT_URL = "https://github.com/SerZhyAle/StreamsPlayer_Android/issues"
        const val PRIVACY_BASE = "https://serzhyale.github.io/StreamsPlayer_Android/"
        const val PRIVACY_URL = "${PRIVACY_BASE}privacy.html"
        const val LICENSE_URL = "https://github.com/SerZhyAle/StreamsPlayer_Android/blob/main/LICENSE"

        /** S0049: related products (section 3.4), at their `SITE-FAMILY-MAP` addresses. */
        const val FMS_SITE_URL = "https://serzhyale.github.io/FastMediaSorter_mob_v2/"
        const val WINDOWS_SITE_URL = "https://serzhyale.github.io/StreamsPlayer/"

        /**
         * S0033: the hosted privacy policy page (`site/`, rendered by `tools/site/build-site.ps1`) in [language].
         * The site publishes English, Russian and Ukrainian; every other app language opens the English page.
         */
        fun privacyUrlFor(language: String): String = when (language.lowercase(Locale.ROOT)) {
            "ru" -> "${PRIVACY_BASE}privacy-ru.html"
            "uk" -> "${PRIVACY_BASE}privacy-uk.html"
            else -> PRIVACY_URL
        }
    }

    private val versionTextView: TextView
    private val btnLicense: MaterialButton
    private val btnPrivacy: MaterialButton
    private val btnHelp: MaterialButton
    private val btnPortal: MaterialButton
    private val btnSupport: MaterialButton
    private val btnRepository: MaterialButton
    private val btnExportDiagnostics: MaterialButton
    private val btnUpdates: MaterialButton
    private val btnRelatedFms: MaterialButton
    private val btnRelatedWindows: MaterialButton

    var onExportDiagnosticsListener: ((String) -> Unit)? = null
    var onHelpClickListener: (() -> Unit)? = null

    /**
     * S0049: the edition's download and update page, set by the host from `Distribution`. The link stays hidden
     * until it is known, so the page never shows a link that goes nowhere.
     */
    var updatesUrl: String? = null
        set(value) {
            field = value
            btnUpdates.visibility = if (value.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_settings_about, this, true)

        versionTextView = findViewById(R.id.about_text_version)
        btnLicense = findViewById(R.id.about_btn_license)
        btnPrivacy = findViewById(R.id.about_btn_privacy)
        btnHelp = findViewById(R.id.about_btn_help)
        btnPortal = findViewById(R.id.about_btn_portal)
        btnSupport = findViewById(R.id.about_btn_support)
        btnRepository = findViewById(R.id.about_btn_repository)
        btnExportDiagnostics = findViewById(R.id.about_btn_export_diagnostics)
        btnUpdates = findViewById(R.id.about_btn_updates)
        btnRelatedFms = findViewById(R.id.about_btn_related_fms)
        btnRelatedWindows = findViewById(R.id.about_btn_related_windows)

        setupContent()
        setupListeners()
    }

    private fun setupContent() {
        val versionText = context.getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE.toString()
        )
        versionTextView.text = versionText
    }

    private fun setupListeners() {
        btnLicense.setOnClickListener { openUrl(LICENSE_URL) }
        btnPrivacy.setOnClickListener {
            val url = privacyUrlFor(LocaleHelper.getLanguage(context))
            Timber.d("S0033: privacy link opens url=$url")
            openUrl(url)
        }
        btnHelp.setOnClickListener {
            onHelpClickListener?.invoke() ?: openUrl(ExchangePortalLinks.BASE)
        }
        btnPortal.setOnClickListener {
            val lang = Locale.getDefault().language
            openUrl(ExchangePortalLinks.urlFor(ExchangePortalLinks.Page.START, lang))
        }
        btnSupport.setOnClickListener { openUrl(SUPPORT_URL) }
        btnRepository.setOnClickListener { openUrl(REPOSITORY_URL) }
        btnUpdates.setOnClickListener { updatesUrl?.let(::openUrl) }
        btnRelatedFms.setOnClickListener { openUrl(FMS_SITE_URL) }
        btnRelatedWindows.setOnClickListener { openUrl(WINDOWS_SITE_URL) }

        // BUTTONS 2.0 section 2.5: a web address keeps link semantics although it is drawn as a button.
        listOf(btnLicense, btnPrivacy, btnPortal, btnSupport, btnRepository, btnUpdates, btnRelatedFms, btnRelatedWindows)
            .forEach { it.exposeAsLink() }

        btnExportDiagnostics.setOnClickListener {
            val report = DiagnosticReportExporter.generateReport(context)
            if (onExportDiagnosticsListener != null) {
                onExportDiagnosticsListener?.invoke(report)
            } else {
                shareDiagnosticReport(report)
            }
        }
    }

    private fun shareDiagnosticReport(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StreamsPlayer Android Diagnostics")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.about_export_diagnostics)))
        } catch (_: Exception) {
            Toast.makeText(context, R.string.exchange_failure_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT).show()
        }
    }
}
