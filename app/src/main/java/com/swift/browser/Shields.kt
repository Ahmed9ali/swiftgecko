package com.swift.browser

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Per-site tracking protection switch.
 * GeckoView removed/renamed ContentBlockingController.addException/removeException/checkException in newer
 * releases, so they are called by reflection when present (no compile error either way). When absent, the
 * host is kept in Prefs and GeckoSessionSettings.useTrackingProtection is toggled per page load instead.
 */
object Shields {
    private fun controller(app: SwiftApp): Any = app.runtime.contentBlockingController

    private fun call(target: Any, name: String, session: GeckoSession): Any? {
        val m = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(GeckoSession::class.java)
        } ?: throw NoSuchMethodException(name)
        return m.invoke(target, session)
    }

    private fun setSessionTp(session: GeckoSession, on: Boolean) {
        try {
            val s = session.settings
            s.javaClass.methods.firstOrNull { it.name == "setUseTrackingProtection" && it.parameterTypes.size == 1 }
                ?.invoke(s, on)
        } catch (_: Throwable) { }
    }

    /** Calls back with true when protection is ON for this tab's site. */
    fun isUp(app: SwiftApp, tab: Tab, cb: (Boolean) -> Unit) {
        try {
            @Suppress("UNCHECKED_CAST")
            val r = call(controller(app), "checkException", tab.session) as GeckoResult<Boolean>
            r.accept({ ex -> cb(ex != true) }, { _ -> cb(!app.prefs.shieldsOff(hostOf(tab.url))) })
        } catch (_: Throwable) {
            cb(!app.prefs.shieldsOff(hostOf(tab.url)))
        }
    }

    fun set(app: SwiftApp, tab: Tab, on: Boolean) {
        app.prefs.setShieldsOff(hostOf(tab.url), !on)
        try {
            call(controller(app), if (on) "removeException" else "addException", tab.session)
        } catch (_: Throwable) {
            setSessionTp(tab.session, on)
        }
    }

    /** Re-apply the stored choice when a page starts (fallback path only; harmless otherwise). */
    fun apply(app: SwiftApp, tab: Tab, url: String) {
        if (url.isBlank()) return
        try {
            controller(app).javaClass.methods.firstOrNull { it.name == "addException" } ?: run {
                setSessionTp(tab.session, !app.prefs.shieldsOff(hostOf(url)))
            }
        } catch (_: Throwable) { }
    }
}
