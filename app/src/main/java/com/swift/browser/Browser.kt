package com.swift.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

/** Things only an Activity can do. MainActivity implements this. */
interface UiHost {
    fun pickFiles(multiple: Boolean, mimeTypes: Array<String>?, cb: (List<Uri>) -> Unit)
    fun requestAndroidPermissions(perms: Array<String>, cb: (Boolean) -> Unit)
    fun setFullscreen(on: Boolean)
    fun share(text: String)
    fun openExternal(uri: String): Boolean
    fun copy(text: String)
    fun toast(msg: String)
}

class Tab(val id: Int, val isPrivate: Boolean, val session: GeckoSession) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var secure by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var opened by mutableStateOf(false)
    var pendingUrl: String? = null
    var autoPort: WebExtension.Port? = null
    var lastBadge = 0
    val isBlank: Boolean get() = url.isEmpty()
}

enum class PromptKind { ALERT, CONFIRM, TEXT, CHOICE }
data class ChoiceItem(val id: String, val label: String, val enabled: Boolean, val selected: Boolean, val header: Boolean = false)
class PromptResult(val ok: Boolean, val text: String = "", val ids: List<String> = emptyList())
class PromptRequest(
    val kind: PromptKind,
    val title: String,
    val message: String? = null,
    val text: String = "",
    val choices: List<ChoiceItem> = emptyList(),
    val multi: Boolean = false,
    val positive: String = "OK",
    val negative: String? = null,
    val onResult: (PromptResult?) -> Unit,
)
class ContextInfo(val tab: Tab, val link: String?, val src: String?, val type: Int, val title: String?)

fun hostOf(url: String): String {
    val h = Uri.parse(url).host ?: return url
    return h.removePrefix("www.")
}

object Omnibox {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
    private val host = Regex("^(localhost|([\\w-]+\\.)+[a-zA-Z]{2,}|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?([/?#].*)?$")

    fun resolve(input: String, template: String): String {
        val t = input.trim()
        if (t.isEmpty()) return t
        val lower = t.lowercase()
        if (lower.startsWith("javascript:")) return template.replace("%s", Uri.encode(t))
        if (scheme.matches(t) && (t.contains("://") || lower.startsWith("about:") || lower.startsWith("data:") ||
                lower.startsWith("view-source:") || lower.startsWith("mailto:") || lower.startsWith("tel:"))) return t
        if (!t.contains(' ') && host.matches(t)) return "https://$t"
        return template.replace("%s", Uri.encode(t))
    }
}

class BrowserState(private val app: SwiftApp) {
    val tabs = mutableStateListOf<Tab>()
    var selectedId by mutableIntStateOf(-1); private set
    var privateMode by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var prompt by mutableStateOf<PromptRequest?>(null)
    var context by mutableStateOf<ContextInfo?>(null)

    var geckoView: GeckoView? = null
    var host: UiHost? = null
    val downloads = Downloads(app)
    val automation = AutomationEngine(this)

    private var nextId = 1
    private val handler = Handler(Looper.getMainLooper())

    val current: Tab? get() = tabs.firstOrNull { it.id == selectedId }

    fun newTab(url: String? = null, priv: Boolean = false, select: Boolean = true, open: Boolean = true): Tab {
        val settings = GeckoSessionSettings.Builder().usePrivateMode(priv).build()
        val session = GeckoSession(settings)
        val tab = Tab(nextId++, priv, session)
        val d = TabDelegates(tab, this, app)
        session.navigationDelegate = d
        session.progressDelegate = d
        session.contentDelegate = d
        session.permissionDelegate = PermissionsImpl(this)
        session.promptDelegate = PromptsImpl(this)
        app.extensions.attach(tab)
        if (open) session.open(app.runtime)
        tab.opened = open
        tabs.add(tab)
        if (select) select(tab.id)
        if (url != null) load(tab, url)
        return tab
    }

    fun load(tab: Tab, url: String) {
        tab.pendingUrl = null
        tab.url = url
        tab.session.loadUri(url)
    }

    /** Address-bar input: URL or search terms. */
    fun go(input: String) {
        val t = current ?: newTab()
        load(t, Omnibox.resolve(input, app.prefs.engineTemplate))
    }

    fun select(id: Int) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        val old = current
        if (old != null && old !== t && old.session.isOpen) old.session.setActive(false)
        selectedId = id
        privateMode = t.isPrivate
        if (t.session.isOpen) t.session.setActive(true)
        t.pendingUrl?.let { t.pendingUrl = null; t.session.loadUri(it) }
    }

    fun close(id: Int) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        val idx = tabs.indexOf(t)
        val wasCurrent = t.id == selectedId
        if (geckoView?.session === t.session) geckoView?.releaseSession()
        tabs.remove(t)
        t.session.close()
        if (wasCurrent) {
            val same = tabs.filter { it.isPrivate == t.isPrivate }
            val next = same.getOrNull((idx - 1).coerceIn(0, (same.size - 1).coerceAtLeast(0))) ?: tabs.lastOrNull()
            if (next != null) select(next.id) else newTab()
        }
    }

    fun closeAll(priv: Boolean) {
        tabs.filter { it.isPrivate == priv }.map { it.id }.forEach { close(it) }
        if (tabs.isEmpty()) newTab()
    }

    /** Snapshot of the visible page for the tab switcher. Call BEFORE the visible tab changes. */
    fun captureThumb(t: Tab?) {
        val v = geckoView ?: return
        if (t == null || t.isBlank || v.session !== t.session) return
        try {
            v.capturePixels().accept({ bmp: Bitmap? ->
                if (bmp != null) {
                    val w = 360
                    val h = (bmp.height * (w.toFloat() / bmp.width)).toInt().coerceAtLeast(1)
                    t.thumb = Bitmap.createScaledBitmap(bmp, w, h, true).asImageBitmap()
                }
            }, { _: Throwable? -> })
        } catch (_: Exception) { }
    }

    fun saveTabs() {
        if (!app.prefs.restore) return
        val normal = tabs.filter { !it.isPrivate }
        val arr = JSONArray()
        normal.forEach { arr.put(JSONObject().put("u", it.pendingUrl ?: it.url).put("t", it.title)) }
        app.prefs.saveTabs(arr.toString(), normal.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
    }

    fun startIfNeeded() {
        if (tabs.isNotEmpty()) return
        var restored = false
        if (app.prefs.restore) {
            try {
                val arr = JSONArray(app.prefs.tabsJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val u = o.optString("u")
                    val t = newTab(null, false, select = false)
                    t.title = o.optString("t")
                    if (u.isNotEmpty()) { t.url = u; t.pendingUrl = u }
                    restored = true
                }
                if (restored) select(tabs[app.prefs.tabsSelected.coerceIn(0, tabs.size - 1)].id)
            } catch (_: Exception) { }
        }
        if (tabs.isEmpty()) newTab()
    }

    fun clearData() {
        app.runtime.storageController.clearData(StorageController.ClearFlags.ALL)
        app.store.clearHistory()
        tabs.forEach { it.thumb = null }
    }

    fun post(r: Runnable) = handler.post(r)
}
