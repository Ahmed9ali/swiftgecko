package com.swift.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

enum class Screen { None, Tabs, Bookmarks, History, Downloads, Settings, Extensions, Automation }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(app: SwiftApp) {
    val b = app.browser
    val tab = b.current
    val focus = LocalFocusManager.current
    var screen by remember { mutableStateOf(Screen.None) }
    var menu by remember { mutableStateOf(false) }
    var shields by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var focusTick by remember { mutableIntStateOf(0) }
    val requester = remember { FocusRequester() }
    val accent = if (tab?.isPrivate == true) PrivatePurple else MaterialTheme.colorScheme.primary

    BackHandler(enabled = screen != Screen.None || find || focused || tab?.canBack == true || b.tabs.size > 1) {
        when {
            screen != Screen.None -> screen = Screen.None
            find -> { find = false; tab?.session?.finder?.clear() }
            focused -> { focus.clearFocus(); focused = false }
            tab != null && tab.canBack -> tab.session.goBack()
            tab != null && b.tabs.size > 1 -> b.close(tab.id)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            val chrome = if (b.fullscreen) Modifier else Modifier.statusBarsPadding().navigationBarsPadding().imePadding()
            Column(Modifier.fillMaxSize().then(chrome)) {
                if (!b.fullscreen && tab != null) {
                    TopBar(
                        app, tab, query, { query = it }, focused,
                        onFocus = { f ->
                            focused = f
                            if (f) query = TextFieldValue(tab.url, TextRange(0, tab.url.length))
                        },
                        onGo = { text -> b.go(text); focus.clearFocus(); focused = false },
                        onShields = { shields = true },
                        accent = accent, requester = requester, focusTick = focusTick,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (tab != null) GeckoSurface(b, tab, Modifier.fillMaxSize())
                    if (tab != null && tab.isBlank) NewTabPage(app, tab.isPrivate, onSearch = { focusTick++ }, onOpen = { b.go(it) })
                    if (find && tab != null) FindBar(tab, { find = false; tab.session.finder.clear() }, Modifier.align(Alignment.BottomCenter))
                    if (focused && tab != null) Suggestions(app, query.text) { url -> b.go(url); focus.clearFocus(); focused = false }
                }
                if (!b.fullscreen) {
                    BottomBar(
                        tab, accent,
                        tabCount = b.tabs.count { it.isPrivate == b.privateMode },
                        onBack = { tab?.session?.goBack() },
                        onForward = { tab?.session?.goForward() },
                        onNew = { b.captureThumb(tab); b.newTab(priv = b.privateMode); focusTick++ },
                        onTabs = { b.captureThumb(tab); screen = Screen.Tabs },
                        onMenu = { menu = true },
                    )
                }
            }

            AnimatedVisibility(screen != Screen.None, enter = fadeIn(), exit = fadeOut()) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        when (screen) {
                            Screen.Tabs -> TabsScreen(b, onClose = { screen = Screen.None }, onNewFocus = { focusTick++ })
                            Screen.Bookmarks -> BookmarksScreen(app) { screen = Screen.None }
                            Screen.History -> HistoryScreen(app) { screen = Screen.None }
                            Screen.Downloads -> DownloadsScreen(app) { screen = Screen.None }
                            Screen.Settings -> SettingsScreen(app) { screen = Screen.None }
                            Screen.Extensions -> ExtensionsScreen(app, openTab = { b.newTab(it); screen = Screen.None }) { screen = Screen.None }
                            Screen.Automation -> AutomationScreen(app, hide = { screen = Screen.None }) { screen = Screen.None }
                            Screen.None -> {}
                        }
                    }
                }
            }
        }
    }

    if (menu && tab != null) MenuSheet(app, tab, onDismiss = { menu = false }, onScreen = { screen = it },
        onFind = { find = true }, onNewTab = { priv -> b.newTab(priv = priv); focusTick++ })
    if (shields && tab != null) ShieldsSheet(app, tab) { shields = false }
    b.context?.let { ContextSheet(app, it) { b.context = null } }
    b.prompt?.let { PromptDialog(b, it) }
}

@Composable
fun GeckoSurface(b: BrowserState, tab: Tab, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> GeckoView(ctx).also { it.setAutofillEnabled(true); b.geckoView = it } },
        update = { v ->
            if (tab.opened) {
                if (v.session !== tab.session) v.setSession(tab.session)
                tab.session.setActive(true)
            }
        },
    )
}

// ------------------------------------------------------------------ top / bottom bars
@Composable
fun TopBar(
    app: SwiftApp, tab: Tab, query: TextFieldValue, onQuery: (TextFieldValue) -> Unit, focused: Boolean,
    onFocus: (Boolean) -> Unit, onGo: (String) -> Unit, onShields: () -> Unit, accent: Color,
    requester: FocusRequester, focusTick: Int,
) {
    LaunchedEffect(focusTick) { if (focusTick > 0) { try { requester.requestFocus() } catch (_: Exception) { } } }
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BadgedShield(tab.blocked, accent, onShields)
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (tab.secure && !tab.isBlank) Icons.Default.Lock else Icons.Default.Search, null,
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    val display = if (focused) query else TextFieldValue(if (tab.isBlank) "" else hostOf(tab.url))
                    BasicTextField(
                        value = display,
                        onValueChange = { if (focused) onQuery(it) },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onGo = { onGo(query.text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { onFocus(it.isFocused) },
                    )
                    if (display.text.isEmpty()) {
                        Text(if (tab.isPrivate) "Search privately" else "Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    }
                }
                if (focused && query.text.isNotEmpty()) {
                    Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp).clickable { onQuery(TextFieldValue("")) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { if (tab.loading) tab.session.stop() else tab.session.reload() }) {
                Icon(if (tab.loading) Icons.Default.Close else Icons.Default.Refresh, "Reload")
            }
        }
        if (tab.loading) {
            LinearProgressIndicator(progress = { tab.progress / 100f }, color = accent, trackColor = Color.Transparent, modifier = Modifier.fillMaxWidth().height(2.dp))
        } else Spacer(Modifier.height(2.dp))
    }
}

@Composable
fun BadgedShield(count: Int, accent: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge(containerColor = accent) { Text(if (count > 99) "99+" else count.toString(), color = Color.White) } }) {
            Icon(Icons.Default.Security, "Shields", tint = accent)
        }
    }
}

@Composable
fun BottomBar(
    tab: Tab?, accent: Color, tabCount: Int, onBack: () -> Unit, onForward: () -> Unit,
    onNew: () -> Unit, onTabs: () -> Unit, onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).height(54.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = tab?.canBack == true) { Icon(Icons.Default.ArrowBack, "Back") }
        IconButton(onClick = onForward, enabled = tab?.canForward == true) { Icon(Icons.Default.ArrowForward, "Forward") }
        IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New tab") }
        IconButton(onClick = onTabs) {
            Box(
                Modifier.size(26.dp).border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(tabCount.toString(), fontSize = 12.sp, color = if (tab?.isPrivate == true) accent else MaterialTheme.colorScheme.onSurface) }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "Menu") }
    }
}

// ------------------------------------------------------------------ suggestions / new tab / find
@Composable
fun Suggestions(app: SwiftApp, text: String, onPick: (String) -> Unit) {
    val q = text.trim()
    val lower = q.lowercase()
    val hits = remember(lower) {
        if (lower.isEmpty()) emptyList()
        else (app.store.bookmarks + app.store.history).filter { it.url.lowercase().contains(lower) || it.title.lowercase().contains(lower) }.distinctBy { it.url }.take(6)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (q.isNotEmpty()) {
            SuggestionRow(Icons.Default.Search, "Search for \"$q\"", null) { onPick(q) }
        }
        hits.forEach { s -> SuggestionRow(Icons.Default.History, s.title, s.url) { onPick(s.url) } }
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NewTabPage(app: SwiftApp, priv: Boolean, onSearch: () -> Unit, onOpen: (String) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = when {
        priv -> if (dark) Color(0xFF2A1B4D) else Color(0xFFE9DDFF)
        else -> if (dark) Color(0xFF3A1A12) else Color(0xFFFFE6DC)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, MaterialTheme.colorScheme.background)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            if (priv) {
                Icon(Icons.Default.VisibilityOff, null, Modifier.size(56.dp), tint = PrivatePurple)
                Spacer(Modifier.height(12.dp))
                Text("Private tab", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text("History, cookies and site data from this tab are not kept when you close it.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatCard("Ads & trackers blocked", app.prefs.blockedTotal.toString(), Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    StatCard("Time saved (est.)", fmtSeconds(app.prefs.blockedTotal * 0.05), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onSearch).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!priv) {
                Spacer(Modifier.height(24.dp))
                val sites = remember(app.store.history.size, app.store.bookmarks.size) { app.store.topSites(8) }
                sites.chunked(4).forEach { rowSites ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        rowSites.forEach { s -> SiteTile(s, Modifier.weight(1f)) { onOpen(s.url) } }
                        repeat(4 - rowSites.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun fmtSeconds(s: Double): String = when {
    s < 60 -> "${s.toInt()}s"
    s < 3600 -> "${(s / 60).toInt()}m"
    else -> "${(s / 3600).toInt()}h ${((s % 3600) / 60).toInt()}m"
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text(value, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SiteTile(s: Site, modifier: Modifier, onClick: () -> Unit) {
    val host = hostOf(s.url)
    val colors = listOf(0xFFFB542B, 0xFF5B8DEF, 0xFF2EB67D, 0xFFB65BEF, 0xFFE8A33D, 0xFF3DC4E8)
    val c = Color(colors[(host.hashCode() and 0x7fffffff) % colors.size])
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(c.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            Text(host.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(host, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun FindBar(tab: Tab, onClose: () -> Unit, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    fun search(flags: Int) {
        if (text.isEmpty()) { tab.session.finder.clear(); info = ""; return }
        tab.session.finder.find(text, flags).accept({ r ->
            info = if (r == null || r.total == 0) "0" else "${r.current}/${r.total}"
        }, { _: Throwable? -> })
    }
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it; search(0) }, singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search(0) }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (text.isEmpty()) Text("Find in page", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } },
        )
        Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { search(GeckoSession.FINDER_FIND_BACKWARDS) }) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
        IconButton(onClick = { search(0) }) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
    }
}

// ------------------------------------------------------------------ sheets & dialogs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    app: SwiftApp, tab: Tab, onDismiss: () -> Unit, onScreen: (Screen) -> Unit,
    onFind: () -> Unit, onNewTab: (Boolean) -> Unit,
) {
    val b = app.browser
    val marked = app.store.isBookmarked(tab.url)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            fun act(f: () -> Unit): () -> Unit = { onDismiss(); f() }
            MenuRow(Icons.Default.Add, "New tab", act { onNewTab(false) })
            MenuRow(Icons.Default.VisibilityOff, "New private tab", act { onNewTab(true) })
            HorizontalDivider()
            MenuRow(Icons.Default.Bookmark, "Bookmarks", act { onScreen(Screen.Bookmarks) })
            MenuRow(Icons.Default.History, "History", act { onScreen(Screen.History) })
            MenuRow(Icons.Default.Download, "Downloads", act { onScreen(Screen.Downloads) })
            MenuRow(Icons.Default.Extension, "Extensions", act { onScreen(Screen.Extensions) })
            MenuRow(Icons.Default.PlayArrow, "Automation", act { onScreen(Screen.Automation) })
            HorizontalDivider()
            if (!tab.isBlank) {
                MenuRow(if (marked) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, if (marked) "Remove bookmark" else "Add bookmark",
                    act { app.store.toggleBookmark(tab.url, tab.title) })
                MenuRow(Icons.Default.Share, "Share", act { b.host?.share(tab.url) })
                MenuRow(Icons.Default.FindInPage, "Find in page", act { onFind() })
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DesktopWindows, null)
                    Spacer(Modifier.width(16.dp))
                    Text("Desktop site", Modifier.weight(1f))
                    Switch(checked = tab.desktop, onCheckedChange = { on ->
                        tab.desktop = on
                        tab.session.settings.userAgentMode = if (on) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                        tab.session.settings.viewportMode = if (on) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                        tab.session.reload()
                    })
                }
            }
            MenuRow(Icons.Default.Settings, "Settings", act { onScreen(Screen.Settings) })
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldsSheet(app: SwiftApp, tab: Tab, onDismiss: () -> Unit) {
    var up by remember { mutableStateOf(true) }
    LaunchedEffect(tab.id, tab.url) {
        Shields.isUp(app, tab) { up = it }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(if (tab.isBlank) "Shields" else hostOf(tab.url), fontSize = 20.sp)
            Text(if (up) "Shields are UP for this site" else "Shields are DOWN for this site", color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tracking protection for this site", Modifier.weight(1f))
                Switch(checked = up, onCheckedChange = { on ->
                    up = on
                    Shields.set(app, tab, on)
                    tab.session.reload()
                })
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row {
                Column(Modifier.weight(1f)) { Text(tab.blocked.toString(), fontSize = 28.sp, color = MaterialTheme.colorScheme.primary); Text("blocked on this page", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(Modifier.weight(1f)) { Text(app.prefs.blockedTotal.toString(), fontSize = 28.sp, color = MaterialTheme.colorScheme.primary); Text("blocked in total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict tracking protection")
                    Text("All sites. Blocks more, may break some pages.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = app.prefs.strict, onCheckedChange = { app.prefs.setStrict(it); app.applyProtection() })
            }
            Spacer(Modifier.height(8.dp))
            val active = app.extensions.entries.filter { it.ext != null && it.enabled && !it.isAutomation }.joinToString(", ") { it.name }
            if (active.isNotEmpty()) Text("Also active: $active", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(app: SwiftApp, info: ContextInfo, onDismiss: () -> Unit) {
    val b = app.browser
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            val title = info.link ?: info.src ?: ""
            Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fun act(f: () -> Unit): () -> Unit = { onDismiss(); f() }
            info.link?.let { link ->
                MenuRow(Icons.Default.Add, "Open in new tab", act { b.newTab(link, priv = info.tab.isPrivate) })
                MenuRow(Icons.Default.VisibilityOff, "Open in private tab", act { b.newTab(link, priv = true) })
                MenuRow(Icons.Default.ContentCopy, "Copy link", act { b.host?.copy(link) })
                MenuRow(Icons.Default.Share, "Share link", act { b.host?.share(link) })
            }
            info.src?.let { src ->
                if (info.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) {
                    MenuRow(Icons.Default.OpenInNew, "Open image in new tab", act { b.newTab(src, priv = info.tab.isPrivate) })
                    MenuRow(Icons.Default.Download, "Save image", act { b.downloads.saveUrl(src) })
                }
                MenuRow(Icons.Default.ContentCopy, "Copy image/media address", act { b.host?.copy(src) })
            }
        }
    }
}

@Composable
fun PromptDialog(b: BrowserState, p: PromptRequest) {
    fun finish(r: PromptResult?) { b.prompt = null; p.onResult(r) }
    var text by remember(p) { mutableStateOf(p.text) }
    var picked by remember(p) { mutableStateOf(p.choices.filter { it.selected }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = { finish(null) },
        title = { Text(p.title.ifBlank { "Message from the page" }) },
        text = {
            Column {
                if (!p.message.isNullOrBlank()) Text(p.message)
                if (p.kind == PromptKind.TEXT) OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (p.kind == PromptKind.CHOICE) {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(p.choices) { c ->
                            if (c.header) Text(c.label, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            else Row(
                                Modifier.fillMaxWidth().clickable(enabled = c.enabled) {
                                    if (p.multi) picked = if (c.id in picked) picked - c.id else picked + c.id
                                    else finish(PromptResult(true, ids = listOf(c.id)))
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (p.multi) Checkbox(checked = c.id in picked, onCheckedChange = null)
                                else if (c.selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) else Spacer(Modifier.width(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(c.label, color = if (c.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (p.kind != PromptKind.CHOICE || p.multi) TextButton(onClick = { finish(PromptResult(true, text, picked.toList())) }) { Text(p.positive) }
        },
        dismissButton = {
            if (p.negative != null) TextButton(onClick = { finish(PromptResult(false)) }) { Text(p.negative) }
        },
    )
}
