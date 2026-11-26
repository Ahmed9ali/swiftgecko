package com.swift.browser

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity(), UiHost {
    private val app get() = application as SwiftApp
    private var pendingFiles: ((List<Uri>) -> Unit)? = null
    private var pendingPerms: ((Boolean) -> Unit)? = null

    private val filesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = pendingFiles; pendingFiles = null
        val data = res.data
        val uris = ArrayList<Uri>()
        if (res.resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            else data.data?.let { uris.add(it) }
        }
        cb?.invoke(uris)
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        val cb = pendingPerms; pendingPerms = null
        cb?.invoke(m.isNotEmpty() && m.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!app.ready) {
            setContent { SwiftTheme(0) { DiagnosticsDialog("The browser engine could not start", app.startupError ?: "unknown error", this, onClose = { finish() }) } }
            return
        }
        app.browser.host = this
        app.browser.startIfNeeded()
        handleIntent(intent)
        setContent { SwiftTheme(app.prefs.theme) { BrowserApp(app) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (app.ready) handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val url = i?.dataString
        if (i?.action == Intent.ACTION_VIEW && !url.isNullOrBlank()) {
            app.browser.newTab(url)
            i.data = null
        }
    }

    override fun onResume() { super.onResume(); if (app.ready) app.browser.host = this }
    override fun onPause() { super.onPause(); if (app.ready) app.browser.saveTabs() }

    // ------------------------------------------------------------ UiHost
    override fun pickFiles(multiple: Boolean, mimeTypes: Array<String>?, cb: (List<Uri>) -> Unit) {
        val mimes = mimeTypes?.filter { it.contains('/') }?.toTypedArray()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimes != null && mimes.size == 1) mimes[0] else "*/*"
            if (mimes != null && mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }
        pendingFiles = cb
        try { filesLauncher.launch(i) } catch (e: ActivityNotFoundException) { pendingFiles = null; cb(emptyList()) }
    }

    override fun requestAndroidPermissions(perms: Array<String>, cb: (Boolean) -> Unit) {
        pendingPerms = cb
        permLauncher.launch(perms)
    }

    override fun setFullscreen(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (on) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(i, null))
    }

    override fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", text))
        toast("Copied")
    }

    override fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    /** intent:, market:, tel:, mailto:, deep links. Returns false when nothing can handle it. */
    override fun openExternal(uri: String): Boolean {
        return try {
            val i = if (uri.startsWith("intent:")) Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.selector = null
            i.component = null
            startActivity(i)
            true
        } catch (e: Exception) {
            try {
                if (uri.startsWith("intent:")) {
                    val fb = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                    if (!fb.isNullOrBlank()) { app.browser.current?.let { app.browser.load(it, fb) }; return true }
                }
            } catch (_: Exception) { }
            false
        }
    }
}
