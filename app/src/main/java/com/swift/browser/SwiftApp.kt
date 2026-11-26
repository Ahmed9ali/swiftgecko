package com.swift.browser

import android.app.Application
import android.os.Build
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class SwiftApp : Application() {
    lateinit var runtime: GeckoRuntime; private set
    lateinit var prefs: Prefs; private set
    lateinit var store: Store; private set
    lateinit var extensions: ExtensionHost; private set
    lateinit var browser: BrowserState; private set

    /** Set when the engine could not start (shown on screen instead of crashing). */
    var startupError: String? = null; private set
    val ready: Boolean get() = startupError == null && ::browser.isInitialized

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
        else try { java.io.File("/proc/self/cmdline").readText().substringBefore('\u0000') } catch (_: Exception) { "" }

    override fun onCreate() {
        super.onCreate()
        // GeckoView runs page, GPU and extension work in SEPARATE Android processes (":tab0", ":gpu", ...)
        // that load this Application class too. Starting a second GeckoRuntime there crashes the browser.
        if (processName() != packageName) return

        CrashLog.install(this)
        DebugLog.d("app", "start sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()} process=${processName()}")
        try {
            prefs = Prefs(this)
            store = Store(this)

            val settings = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(false)
                .consoleOutput(BuildConfig.DEBUG)
                .remoteDebuggingEnabled(BuildConfig.DEBUG)
                .javaScriptEnabled(true)
                .build()
            runtime = GeckoRuntime.create(this, settings)
            DebugLog.d("app", "GeckoRuntime created")
            applyProtection()
            applyTheme()

            extensions = ExtensionHost(this)
            browser = BrowserState(this)
            extensions.install()
        } catch (t: Throwable) {
            startupError = android.util.Log.getStackTraceString(t)
            DebugLog.d("app", "STARTUP FAILED: ${t.message}")
            try { CrashLog.write(this, "Startup failed", t) } catch (_: Throwable) { }
        }
    }

    /** Gecko's own tracking protection (Disconnect lists, social/fingerprinting blocking). */
    fun applyProtection() {
        val level = if (prefs.strict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT
        runtime.settings.contentBlocking.setEnhancedTrackingProtectionLevel(level)
    }

    fun applyTheme() {
        val scheme = when (prefs.theme) {
            1 -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            2 -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        }
        runtime.settings.setPreferredColorScheme(scheme)
    }
}
