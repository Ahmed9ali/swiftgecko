package com.swift.browser

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory event log (shown in Settings > Diagnostics) so problems can be diagnosed without a PC. */
object DebugLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun d(tag: String, msg: String) {
        val l = "${fmt.format(Date())} [$tag] $msg"
        lines.addLast(l)
        while (lines.size > 300) lines.removeFirst()
        Log.d("Swift", l)
    }

    @Synchronized fun dump(): String = lines.joinToString("\n")
    @Synchronized fun clear() = lines.clear()
}

/** Writes uncaught Java exceptions (plus the last events) to a file; shown on the next start. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { write(ctx, "Uncaught exception in thread '${t.name}'", e) } catch (_: Throwable) { }
            previous?.uncaughtException(t, e)
        }
    }

    fun write(ctx: Context, what: String, e: Throwable) {
        file(ctx).writeText("$what\n${Log.getStackTraceString(e)}\n\n--- recent events ---\n${DebugLog.dump()}")
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }
}
