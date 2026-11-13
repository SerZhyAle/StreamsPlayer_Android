package com.sza.streamsplayer.ui.settings.about

import android.content.Context
import android.os.Build
import com.sza.streamsplayer.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates a sanitized diagnostic report bundle for user-initiated export (SETTINGS-NAVIGATION 2.0 section 3 rule 3).
 *
 * Excludes private user data, tokens, passwords and stream history.
 * Contains only runtime, device and build diagnostic metadata.
 */
object DiagnosticReportExporter {

    fun generateReport(context: Context): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = dateFormat.format(Date())

        val runtime = Runtime.getRuntime()
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val dm = context.resources.displayMetrics
        val densityDpi = dm.densityDpi
        val density = dm.density
        val fontScale = context.resources.configuration.fontScale
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels

        val locale = Locale.getDefault().toLanguageTag()

        return buildString {
            appendLine("=== STREAMSPLAYER ANDROID DIAGNOSTIC REPORT ===")
            appendLine("Generated: $timestamp")
            appendLine()
            appendLine("[Application]")
            appendLine("Package: ${context.packageName}")
            appendLine("VersionName: ${BuildConfig.VERSION_NAME}")
            appendLine("VersionCode: ${BuildConfig.VERSION_CODE}")
            appendLine("BuildType: ${BuildConfig.BUILD_TYPE}")
            appendLine()
            appendLine("[Device & Platform]")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("AndroidRelease: ${Build.VERSION.RELEASE}")
            appendLine("SdkInt: ${Build.VERSION.SDK_INT}")
            appendLine("Locale: $locale")
            appendLine()
            appendLine("[Display & Sizing]")
            appendLine("DensityDpi: $densityDpi")
            appendLine("Density: $density")
            appendLine("FontScale: $fontScale")
            appendLine("ScreenDimensions: ${screenWidth}x${screenHeight} px")
            appendLine()
            appendLine("[Memory (MB)]")
            appendLine("Total: ${totalMemMb} MB")
            appendLine("Free: ${freeMemMb} MB")
            appendLine("Max: ${maxMemMb} MB")
            appendLine("=== END OF REPORT ===")
        }
    }
}
