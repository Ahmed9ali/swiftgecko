package com.swift.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class Site(val url: String, val title: String, val time: Long = System.currentTimeMillis(), val visits: Int = 1)

/** Bookmarks + history, kept in SharedPreferences as JSON (small, simple, enough for a personal browser). */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("swift_store", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    val bookmarks = mutableStateListOf<Site>()
    val history = mutableStateListOf<Site>()

    init {
        load("bookmarks", bookmarks)
        load("history", history)
    }

    private fun load(key: String, into: MutableList<Site>) {
        try {
            val a = JSONArray(sp.getString(key, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                into.add(Site(o.getString("u"), o.optString("t"), o.optLong("d"), o.optInt("v", 1)))
            }
        } catch (_: Exception) { }
    }

    private fun toJson(list: List<Site>): String {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("d", it.time).put("v", it.visits)) }
        return a.toString()
    }

    private val saveRunnable = Runnable {
        sp.edit().putString("bookmarks", toJson(bookmarks)).putString("history", toJson(history)).apply()
    }
    private fun scheduleSave() { handler.removeCallbacks(saveRunnable); handler.postDelayed(saveRunnable, 3000) }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(url: String, title: String): Boolean {
        val i = bookmarks.indexOfFirst { it.url == url }
        val added = i < 0
        if (added) bookmarks.add(0, Site(url, title.ifBlank { url })) else bookmarks.removeAt(i)
        scheduleSave()
        return added
    }

    fun removeBookmark(site: Site) { bookmarks.remove(site); scheduleSave() }

    fun addHistory(url: String, title: String) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val i = history.indexOfFirst { it.url == url }
        val visits = if (i >= 0) history.removeAt(i).visits + 1 else 1
        history.add(0, Site(url, title.ifBlank { url }, System.currentTimeMillis(), visits))
        while (history.size > 3000) history.removeAt(history.size - 1)
        scheduleSave()
    }

    fun removeHistory(site: Site) { history.remove(site); scheduleSave() }
    fun clearHistory() { history.clear(); scheduleSave() }

    /** Most visited sites + bookmarks, for the new-tab page. */
    fun topSites(n: Int): List<Site> {
        val seen = HashSet<String>()
        val out = ArrayList<Site>()
        for (s in bookmarks.take(4) + history.sortedByDescending { it.visits }) {
            val host = hostOf(s.url)
            if (seen.add(host)) out.add(s)
            if (out.size >= n) break
        }
        return out
    }
}
