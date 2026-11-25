package com.swift.browser

import android.app.Application
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class SwiftApp : Application() {
    lateinit var runtime: GeckoRuntime; private set
    lateinit var prefs: Prefs; private set
    lateinit var store: Store; private set
    lateinit var extensions: ExtensionHost; private set
    lateinit var browser: BrowserState; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = Store(this)

        val settings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .javaScriptEnabled(true)
            .build()
        runtime = GeckoRuntime.create(this, settings)
        applyProtection()
        applyTheme()

        extensions = ExtensionHost(this)
        browser = BrowserState(this)
        extensions.install()
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
