package com.swift.browser

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BraveOrange = Color(0xFFFB542B)
val PrivatePurple = Color(0xFF9B6BFF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7654), onPrimary = Color.White,
    background = Color(0xFF16171B), onBackground = Color(0xFFECEDF2),
    surface = Color(0xFF1E1F25), onSurface = Color(0xFFECEDF2),
    surfaceVariant = Color(0xFF2A2C34), onSurfaceVariant = Color(0xFFA9ACB8),
    outline = Color(0xFF3A3D47), error = Color(0xFFFF6B6B),
)
private val LightColors = lightColorScheme(
    primary = BraveOrange, onPrimary = Color.White,
    background = Color.White, onBackground = Color(0xFF1B1C20),
    surface = Color(0xFFF5F6F9), onSurface = Color(0xFF1B1C20),
    surfaceVariant = Color(0xFFE9EBF0), onSurfaceVariant = Color(0xFF5B5E6B),
    outline = Color(0xFFD3D6DE), error = Color(0xFFD32F2F),
)

fun isDark(mode: Int, system: Boolean) = when (mode) { 1 -> false; 2 -> true; else -> system }

@Composable
fun SwiftTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = isDark(mode, isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
