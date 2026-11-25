package com.swift.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("swift", Context.MODE_PRIVATE)

    var engine by mutableStateOf(sp.getString("engine", "brave") ?: "brave"); private set
    var theme by mutableIntStateOf(sp.getInt("theme", 0)); private set          // 0 system, 1 light, 2 dark
    var strict by mutableStateOf(sp.getBoolean("strict", false)); private set   // Gecko strict tracking protection
    var restore by mutableStateOf(sp.getBoolean("restore", true)); private set
    var blockedTotal by mutableIntStateOf(sp.getInt("blocked", 0)); private set

    val engineTemplate: String get() = (ENGINES[engine] ?: ENGINES.getValue("brave")).second
    val tabsJson: String get() = sp.getString("tabs", "[]") ?: "[]"
    val tabsSelected: Int get() = sp.getInt("tabsSel", 0)

    fun setEngine(v: String) { engine = v; sp.edit().putString("engine", v).apply() }
    fun setTheme(v: Int) { theme = v; sp.edit().putInt("theme", v).apply() }
    fun setStrict(v: Boolean) { strict = v; sp.edit().putBoolean("strict", v).apply() }
    fun setRestore(v: Boolean) { restore = v; sp.edit().putBoolean("restore", v).apply() }
    fun addBlocked(n: Int) { blockedTotal += n; sp.edit().putInt("blocked", blockedTotal).apply() }
    fun saveTabs(json: String, selected: Int) { sp.edit().putString("tabs", json).putInt("tabsSel", selected).apply() }

    companion object {
        val ENGINES = linkedMapOf(
            "brave" to ("Brave Search" to "https://search.brave.com/search?q=%s"),
            "ddg" to ("DuckDuckGo" to "https://duckduckgo.com/?q=%s"),
            "google" to ("Google" to "https://www.google.com/search?q=%s"),
            "startpage" to ("Startpage" to "https://www.startpage.com/do/search?q=%s"),
            "bing" to ("Bing" to "https://www.bing.com/search?q=%s"),
        )
    }
}
