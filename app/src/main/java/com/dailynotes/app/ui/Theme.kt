package com.dailynotes.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCCEFEA), onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFDDE8E6), background = Color(0xFFF7FAF9), surface = Color(0xFFF7FAF9)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5FD4C8), onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0B4F49), onPrimaryContainer = Color(0xFFCCEFEA),
    secondaryContainer = Color(0xFF2B3634), background = Color(0xFF101414), surface = Color(0xFF101414)
)

@Composable
fun NotesTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
