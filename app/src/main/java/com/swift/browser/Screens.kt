package com.swift.browser

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = actions,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        content = content,
    )
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

// ------------------------------------------------------------------ tabs
@Composable
fun TabsScreen(b: BrowserState, onClose: () -> Unit, onNewFocus: () -> Unit) {
    val list = b.tabs.filter { it.isPrivate == b.privateMode }
    val accent = if (b.privateMode) PrivatePurple else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !b.privateMode, onClick = { b.privateMode = false }, label = { Text("Tabs") })
            FilterChip(selected = b.privateMode, onClick = { b.privateMode = true }, label = { Text("Private") },
                leadingIcon = { Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp)) })
        }
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (b.privateMode) "No private tabs" else "No tabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { t ->
                    val sel = t.id == b.selectedId
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(if (sel) 2.dp else 1.dp, if (sel) accent else MaterialTheme.colorScheme.outline), RoundedCornerShape(14.dp))
                            .clickable { b.select(t.id); onClose() },
                    ) {
                        Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title.ifBlank { if (t.isBlank) "New tab" else hostOf(t.url) }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            IconButton(onClick = { b.close(t.id) }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Close", Modifier.size(16.dp)) }
                        }
                        Box(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            val th = t.thumb
                            if (th != null) Image(th, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                            else Text(if (t.isBlank) "+" else hostOf(t.url).take(1).uppercase(), fontSize = 34.sp, color = accent)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { b.closeAll(b.privateMode) }) { Text("Close all") }
            FilledIconButton(onClick = { b.newTab(priv = b.privateMode); onClose(); onNewFocus() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)) {
                Icon(Icons.Default.Add, "New tab")
            }
            TextButton(onClick = onClose) { Text("Done") }
        }
    }
}

// ------------------------------------------------------------------ bookmarks / history / downloads
@Composable
fun BookmarksScreen(app: SwiftApp, onBack: () -> Unit) {
    ScreenScaffold("Bookmarks", onBack) { pad ->
        if (app.store.bookmarks.isEmpty()) Empty("No bookmarks yet")
        else LazyColumn(Modifier.padding(pad)) {
            items(app.store.bookmarks.toList()) { s ->
                SiteRow(s, onClick = { app.browser.go(s.url); onBack() }, onDelete = { app.store.removeBookmark(s) })
            }
        }
    }
}

@Composable
fun HistoryScreen(app: SwiftApp, onBack: () -> Unit) {
    ScreenScaffold("History", onBack, actions = {
        IconButton(onClick = { app.store.clearHistory() }) { Icon(Icons.Default.Delete, "Clear history") }
    }) { pad ->
        if (app.store.history.isEmpty()) Empty("No history")
        else LazyColumn(Modifier.padding(pad)) {
            items(app.store.history.take(300)) { s ->
                SiteRow(s, onClick = { app.browser.go(s.url); onBack() }, onDelete = { app.store.removeHistory(s) })
            }
        }
    }
}

@Composable
private fun SiteRow(s: Site, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Delete") }
    }
}

@Composable
fun DownloadsScreen(app: SwiftApp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    ScreenScaffold("Downloads", onBack) { pad ->
        val all = app.browser.downloads.items
        if (all.isEmpty()) Empty("No downloads yet")
        else LazyColumn(Modifier.padding(pad)) {
            items(all.toList()) { d ->
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = d.done && d.uri != null) {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(d.uri, d.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { app.browser.host?.toast("No app can open this file") }
                    }.padding(16.dp)
                ) {
                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        d.failed -> Text("Failed", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        d.done -> Text("Saved to Downloads  (${d.bytes / 1024} KB)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ settings
@Composable
fun SettingsScreen(app: SwiftApp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    ScreenScaffold("Settings", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SectionTitle("Search engine")
            Prefs.ENGINES.forEach { (id, v) ->
                Row(Modifier.fillMaxWidth().clickable { app.prefs.setEngine(id) }.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = app.prefs.engine == id, onClick = { app.prefs.setEngine(id) })
                    Text(v.first)
                }
            }
            SectionTitle("Appearance")
            listOf("System default", "Light", "Dark").forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth().clickable { app.prefs.setTheme(i); app.applyTheme() }.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = app.prefs.theme == i, onClick = { app.prefs.setTheme(i); app.applyTheme() })
                    Text(label)
                }
            }
            SectionTitle("Privacy & shields")
            SwitchRow("Strict tracking protection", "Blocks more trackers; a few sites may break", app.prefs.strict) { app.prefs.setStrict(it); app.applyProtection() }
            SwitchRow("Restore tabs on start", null, app.prefs.restore) { app.prefs.setRestore(it) }
            TextButton(onClick = { confirmClear = true }, Modifier.padding(horizontal = 8.dp)) { Text("Clear browsing data…") }
            SectionTitle("Browser")
            TextButton(onClick = {
                try { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) { }
            }, Modifier.padding(horizontal = 8.dp)) { Text("Set as default browser") }
            TextButton(onClick = { showLog = true }, Modifier.padding(horizontal = 8.dp)) { Text("Diagnostics (event log)") }
            Text("Swift Browser 0.1  ·  Gecko engine", Modifier.padding(16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showLog) DiagnosticsDialog("Diagnostics", DebugLog.dump(), app.browser.host, onClose = { showLog = false }, onClear = { DebugLog.clear() })
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear browsing data?") },
        text = { Text("Deletes history, cookies, cache and site data in all tabs.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; app.browser.clearData() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------ extensions
@Composable
fun ExtensionsScreen(app: SwiftApp, openTab: (String) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Extensions", onBack) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text("Built into the app. They also run in private tabs. To change the list, edit scripts/fetch_extensions.py and rebuild.",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(app.extensions.entries.toList()) { e ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name)
                            Text(if (e.version.isNotEmpty()) "v${e.version}" else e.dir, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.ext != null) Switch(checked = e.enabled, onCheckedChange = { app.extensions.setEnabled(e, it) })
                    }
                    e.error?.let { Text("Could not load: $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    val opt = e.ext?.metaData?.optionsPageUrl
                    if (!opt.isNullOrEmpty()) TextButton(onClick = { openTab(opt) }) { Text("Open settings") }
                }
                HorizontalDivider()
            }
        }
    }
}

// ------------------------------------------------------------------ automation
private const val SAMPLE = """[
  {"cmd": "open", "url": "https://duckduckgo.com"},
  {"cmd": "type", "selector": "input[name=q]", "value": "swift browser", "enter": true},
  {"cmd": "waitFor", "selector": "article", "timeout": 15000},
  {"cmd": "getText", "selector": "article h2"}
]"""

@Composable
fun AutomationScreen(app: SwiftApp, hide: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var script by remember { mutableStateOf(SAMPLE) }
    var log by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    fun run(watch: Boolean) {
        val steps = try { JSONArray(script) } catch (e: Exception) { log = "Invalid JSON: ${e.message}"; return }
        log = ""; running = true
        if (watch) hide()
        scope.launch {
            try { app.browser.automation.run(steps) { log += it + "\n" } }
            catch (e: Exception) { log += "ERROR: ${e.message}\n" }
            running = false
            if (watch) app.browser.host?.toast("Automation finished")
        }
    }

    ScreenScaffold("Automation", onBack) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("Steps run in the current tab. Commands: open, newTab, back, wait, click, type, waitFor, getText, getHtml, getAttr, scroll, eval, info.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp)) {
                BasicTextField(
                    value = script, onValueChange = { script = it },
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run(false) }, enabled = !running) { Text("Run") }
                OutlinedButton(onClick = { run(true) }, enabled = !running) { Text("Run & watch") }
            }
            Text(log, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}
