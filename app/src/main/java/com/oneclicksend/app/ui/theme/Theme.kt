package com.oneclicksend.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF3DDC97)
private val Red = Color(0xFFFF3B30)
private val Background = Color(0xFF0B0F14)
private val Surface = Color(0xFF161C24)
private val OnSurface = Color(0xFFF4F7FA)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF003822),
    secondary = Red,
    background = Background,
    surface = Surface,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = Color(0xFFB3BCC8),
    error = Red,
)

@Composable
fun OneClickSendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
