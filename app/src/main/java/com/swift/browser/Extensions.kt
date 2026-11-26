package com.swift.browser

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ExtEntry(val dir: String, val id: String) {
    var ext by mutableStateOf<WebExtension?>(null)
    var enabled by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    val name: String get() = ext?.metaData?.name ?: dir
    val version: String get() = ext?.metaData?.version ?: ""
    val isBlocker: Boolean get() = id.contains("uBlock", ignoreCase = true)
    val isAutomation: Boolean get() = id == AUTOMATION_ID
}

const val AUTOMATION_ID = "automation@swift.browser"

/**
 * Loads the extensions bundled in assets/extensions (uBlock Origin, ClearURLs, ... + our automation extension)
 * as GeckoView built-in extensions: no install prompt, always present, allowed in private tabs.
 */
class ExtensionHost(private val app: SwiftApp) {
    val entries = mutableStateListOf<ExtEntry>()

    fun install() {
        val list = try {
            JSONArray(app.assets.open("extensions/extensions.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) { Log.e(TAG, "extensions.json missing", e); return }

        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val e = ExtEntry(o.getString("dir"), o.getString("id"))
            entries.add(e)
            app.runtime.webExtensionController
                .ensureBuiltIn("resource://android/assets/extensions/${e.dir}/", e.id)
                .accept({ ext: WebExtension? -> DebugLog.d("ext", "loaded ${e.dir} ok=${ext != null}"); if (ext != null) onInstalled(e, ext) },
                        { t: Throwable? -> e.error = t?.message ?: "failed"; DebugLog.d("ext", "FAILED ${e.dir}: ${t?.message}"); Log.e(TAG, "ensureBuiltIn ${e.dir}", t) })
        }
    }

    private fun onInstalled(e: ExtEntry, ext: WebExtension) {
        e.ext = ext
        e.enabled = ext.metaData.enabled
        app.runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
        app.browser.tabs.forEach { wire(it, e, ext) }
    }

    fun attach(tab: Tab) {
        for (e in entries) { val x = e.ext ?: continue; wire(tab, e, x) }
    }

    private fun wire(tab: Tab, e: ExtEntry, ext: WebExtension) {
        if (e.isBlocker) tab.session.webExtensionController.setActionDelegate(ext, BadgeDelegate(tab))
        if (e.isAutomation) tab.session.webExtensionController.setMessageDelegate(ext, automationDelegate(tab), "browser")
    }

    fun setEnabled(e: ExtEntry, on: Boolean) {
        val x = e.ext ?: return
        val c = app.runtime.webExtensionController
        val r = if (on) c.enable(x, WebExtensionController.EnableSource.USER) else c.disable(x, WebExtensionController.EnableSource.USER)
        r.accept({ nx: WebExtension? -> if (nx != null) { e.ext = nx; e.enabled = on } }, { _: Throwable? -> })
    }

    /** uBlock Origin puts the per-tab "blocked" count in the toolbar badge: we read it from there. */
    private inner class BadgeDelegate(private val tab: Tab) : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            val n = parseBadge(action.badgeText)
            if (n > tab.lastBadge) app.prefs.addBlocked(n - tab.lastBadge)
            tab.lastBadge = n
            tab.blocked = n
        }
    }

    private fun automationDelegate(tab: Tab) = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            tab.autoPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) { app.browser.automation.onMessage(message) }
                override fun onDisconnect(port: WebExtension.Port) { if (tab.autoPort === port) tab.autoPort = null }
            })
        }
    }

    companion object {
        private const val TAG = "SwiftExt"
        fun parseBadge(s: String?): Int {
            val t = s?.trim()?.lowercase() ?: return 0
            if (t.isEmpty()) return 0
            return when {
                t.endsWith("k") -> ((t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000).toInt()
                t.endsWith("m") -> ((t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000).toInt()
                else -> t.toIntOrNull() ?: 0
            }
        }
    }
}

/**
 * Drives the page through the built-in "Swift Automation" extension.
 * Steps are JSON objects, e.g.  {"cmd":"open","url":"https://example.com"}, {"cmd":"click","selector":"a"},
 * {"cmd":"type","selector":"input[name=q]","value":"hello","enter":true}, {"cmd":"getText","selector":"h1"}.
 * This is the integration point for your own automation logic.
 */
class AutomationEngine(private val b: BrowserState) {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val ids = AtomicInteger(1)

    fun onMessage(msg: Any) {
        val o = msg as? JSONObject ?: return
        val id = o.optInt("id", -1)
        if (id > 0) pending.remove(id)?.complete(o)
    }

    suspend fun send(tab: Tab, cmd: JSONObject, timeoutMs: Long = 30_000): JSONObject {
        val port = withTimeoutOrNull(10_000) {
            while (tab.autoPort == null) delay(100)
            tab.autoPort
        } ?: throw IllegalStateException("no connection to the page (still loading, or extension missing)")
        val id = ids.getAndIncrement()
        cmd.put("id", id)
        val d = CompletableDeferred<JSONObject>()
        pending[id] = d
        try {
            withContext(Dispatchers.Main) { port.postMessage(cmd) }
            return withTimeout(timeoutMs) { d.await() }
        } finally { pending.remove(id) }
    }

    private suspend fun waitLoaded(tab: Tab) {
        delay(400)
        withTimeoutOrNull(45_000) { while (tab.loading) delay(100) }
    }

    /** Runs the steps in the currently selected tab; every step result is reported through [log]. */
    suspend fun run(steps: JSONArray, log: (String) -> Unit) {
        var tab = b.current ?: throw IllegalStateException("no tab")
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            val cmd = s.getString("cmd")
            log("${i + 1}. $cmd ${s.optString("selector", s.optString("url", ""))}")
            when (cmd) {
                "open" -> {
                    withContext(Dispatchers.Main) { tab.autoPort = null; b.load(tab, Omnibox.resolve(s.getString("url"), "https://duckduckgo.com/?q=%s")) }
                    waitLoaded(tab)
                }
                "newTab" -> {
                    tab = withContext(Dispatchers.Main) { b.newTab(null, s.optBoolean("private", false)) }
                    if (s.has("url")) {
                        withContext(Dispatchers.Main) { b.load(tab, s.getString("url")) }
                        waitLoaded(tab)
                    }
                }
                "back" -> withContext(Dispatchers.Main) { tab.autoPort = null; tab.session.goBack() }.also { waitLoaded(tab) }
                "wait" -> delay(s.optLong("ms", 1000))
                else -> {
                    val r = send(tab, s, s.optLong("timeoutMs", 30_000) + 12_000)
                    if (!r.optBoolean("ok")) throw IllegalStateException("step ${i + 1} ($cmd) failed: ${r.optString("error")}")
                    if (r.has("value")) log("   → " + r.optString("value").take(300))
                }
            }
        }
        log("done")
    }
}
