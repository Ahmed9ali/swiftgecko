package com.swift.browser

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/**
 * Navigation, progress and content callbacks for one tab.
 * If a GeckoView update changes a signature, the compiler error will point at one of the overrides below.
 */
class TabDelegates(
    private val tab: Tab,
    private val b: BrowserState,
    private val app: SwiftApp,
) : GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate {

    // ---------------------------------------------------------------- navigation
    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
    ) {
        tab.opened = true
        tab.url = if (url == null || url == "about:blank") "" else url
        if (tab.url.isNotEmpty()) tab.pendingUrl = null
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canBack = canGoBack }
    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canForward = canGoForward }

    private val internalSchemes = setOf(
        "http", "https", "about", "data", "blob", "file", "view-source",
        "moz-extension", "resource", "javascript", "ws", "wss", "ftp", "jar", "chrome",
    )

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? {
        val scheme = request.uri.substringBefore(':', "").lowercase()
        if (scheme in internalSchemes) return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        // intent:, market:, tel:, mailto:, app deep links ... only when the user actually tapped something
        if (request.hasUserGesture) {
            if (b.host?.openExternal(request.uri) == false) { /* nothing can handle it: just drop it */ }
        }
        return GeckoResult.fromValue(AllowOrDeny.DENY)
    }

    /** target=_blank / window.open: give Gecko a new, not yet opened session. */
    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        val t = b.newTab(url = null, priv = tab.isPrivate, select = true, open = false)
        return GeckoResult.fromValue(t.session)
    }

    override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
        val u = (uri ?: "").replace("<", "&lt;")
        val html = """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font-family:sans-serif;background:#16171b;color:#ecedf2;padding:32px}
            h2{color:#ff7654}a{color:#ff7654}</style></head><body>
            <h2>This page can't be opened</h2><p>$u</p>
            <p>Error code ${error.code} (category ${error.category})</p>
            <p><a href="javascript:location.reload()">Try again</a></p></body></html>
        """.trimIndent()
        return GeckoResult.fromValue("data:text/html;charset=utf-8," + android.net.Uri.encode(html))
    }

    // ---------------------------------------------------------------- progress
    override fun onPageStart(session: GeckoSession, url: String) {
        tab.opened = true
        tab.loading = true
        tab.progress = 5
        tab.blocked = 0
        tab.lastBadge = 0
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        tab.loading = false
        tab.progress = 100
        if (success && !tab.isPrivate && tab.url.isNotEmpty()) app.store.addHistory(tab.url, tab.title)
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) { tab.progress = progress }

    override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
        tab.secure = securityInfo.isSecure
    }

    // ---------------------------------------------------------------- content
    override fun onTitleChange(session: GeckoSession, title: String?) { tab.title = title ?: "" }

    override fun onCloseRequest(session: GeckoSession) { b.close(tab.id) }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        b.fullscreen = fullScreen
        b.host?.setFullscreen(fullScreen)
    }

    override fun onContextMenu(
        session: GeckoSession,
        screenX: Int,
        screenY: Int,
        element: GeckoSession.ContentDelegate.ContextElement,
    ) {
        if (element.linkUri == null && element.srcUri == null) return
        b.context = ContextInfo(tab, element.linkUri, element.srcUri, element.type, element.title)
    }

    /** Downloads: Gecko hands us the response (with cookies/auth already applied). */
    override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
        b.downloads.save(response)
    }

    override fun onCrash(session: GeckoSession) = recover(session)
    override fun onKill(session: GeckoSession) = recover(session)

    private fun recover(session: GeckoSession) {
        session.open(app.runtime)
        tab.opened = true
        if (tab.url.isNotEmpty()) session.loadUri(tab.url)
    }
}
