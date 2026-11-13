package com.sza.streamsplayer.ui.settings.about

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sza.streamsplayer.BuildConfig
import com.sza.streamsplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for [SettingsAboutDestinationView] and [DiagnosticReportExporter]
 * (SETTINGS-NAVIGATION 2.0 section 3 rule 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsAboutDestinationViewTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_StreamsPlayer)
    }

    @Test
    fun `about view displays version from build metadata and concise description`() {
        val view = SettingsAboutDestinationView(context)

        val versionView = view.findViewById<TextView>(R.id.about_text_version)
        assertNotNull(versionView)
        assertTrue(versionView.text.contains(BuildConfig.VERSION_NAME))

        val descView = view.findViewById<TextView>(R.id.about_text_description)
        assertNotNull(descView)
        assertTrue(descView.text.isNotEmpty())
    }

    @Test
    fun `about view provides complete mandatory action set`() {
        val view = SettingsAboutDestinationView(context)

        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_license))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_privacy))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_help))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_portal))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_support))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_repository))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_export_diagnostics))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_updates))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_related_fms))
        assertNotNull(view.findViewById<MaterialButton>(R.id.about_btn_related_windows))

        assertNotNull(view.findViewById<TextView>(R.id.about_text_fms))
        assertNotNull(view.findViewById<TextView>(R.id.about_text_streamsplayer_win))
        assertNotNull(view.findViewById<TextView>(R.id.about_text_resources))
    }

    @Test
    fun `privacy link opens the hosted page in the app language and English otherwise`() {
        val base = SettingsAboutDestinationView.PRIVACY_BASE
        assertEquals("${base}privacy-ru.html", SettingsAboutDestinationView.privacyUrlFor("ru"))
        assertEquals("${base}privacy-uk.html", SettingsAboutDestinationView.privacyUrlFor("UK"))
        for (language in listOf("en", "de", "zh-Hans", "pt", "", "ru-RU", "uk_UA", "be")) {
            assertEquals("language '$language'", "${base}privacy.html", SettingsAboutDestinationView.privacyUrlFor(language))
        }
    }

    @Test
    fun `every privacy address is a page the site generator writes`() {
        // S0033: tools/site/build-site.ps1 owns the hosted address and the page names; the app link must match both.
        val generator = File("../tools/site/build-site.ps1").readText()
        val siteBase = Regex("""\${'$'}base = '([^']+)'""").find(generator)!!.groupValues[1]
        val pages = Regex("""File = '([^']+\.html)'""").findAll(generator).map { "$siteBase/${it.groupValues[1]}" }.toSet()

        val built = listOf("en", "ru", "uk").map { SettingsAboutDestinationView.privacyUrlFor(it) }.toSet()
        assertEquals(3, built.size)
        assertEquals(pages, built)
    }

    @Test
    fun `clicking privacy opens the hosted policy page`() {
        val view = SettingsAboutDestinationView(context)

        view.findViewById<MaterialButton>(R.id.about_btn_privacy).performClick()

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(SettingsAboutDestinationView.PRIVACY_URL, started.dataString)
    }

    @Test
    fun `the download and update link shows only once its page is known and opens it`() {
        val view = SettingsAboutDestinationView(context)
        val updates = view.findViewById<MaterialButton>(R.id.about_btn_updates)
        assertEquals(android.view.View.GONE, updates.visibility)

        view.updatesUrl = "https://example.invalid/updates"
        assertEquals(android.view.View.VISIBLE, updates.visibility)
        updates.performClick()

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.invalid/updates", started.dataString)
    }

    @Test
    fun `related products open their site family addresses`() {
        val view = SettingsAboutDestinationView(context)

        view.findViewById<MaterialButton>(R.id.about_btn_related_fms).performClick()
        assertEquals(
            SettingsAboutDestinationView.FMS_SITE_URL,
            shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.dataString
        )
        view.findViewById<MaterialButton>(R.id.about_btn_related_windows).performClick()
        assertEquals(
            SettingsAboutDestinationView.WINDOWS_SITE_URL,
            shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.dataString
        )
    }

    @Test
    fun `diagnostic report exporter produces sanitized structured report`() {
        val report = DiagnosticReportExporter.generateReport(context)

        assertTrue(report.contains("=== STREAMSPLAYER ANDROID DIAGNOSTIC REPORT ==="))
        assertTrue(report.contains("[Application]"))
        assertTrue(report.contains("VersionName: ${BuildConfig.VERSION_NAME}"))
        assertTrue(report.contains("[Device & Platform]"))
        assertTrue(report.contains("[Display & Sizing]"))
        assertTrue(report.contains("[Memory (MB)]"))
        assertTrue(report.contains("=== END OF REPORT ==="))
    }

    @Test
    fun `clicking export diagnostics triggers listener with report`() {
        val view = SettingsAboutDestinationView(context)
        var capturedReport: String? = null
        view.onExportDiagnosticsListener = { capturedReport = it }

        val exportBtn = view.findViewById<MaterialButton>(R.id.about_btn_export_diagnostics)
        exportBtn.performClick()

        assertNotNull(capturedReport)
        assertTrue(capturedReport!!.contains("=== STREAMSPLAYER ANDROID DIAGNOSTIC REPORT ==="))
    }
}
