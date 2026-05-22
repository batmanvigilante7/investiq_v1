package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = Color.Black,
    secondary = CyberAmber,
    onSecondary = Color.Black,
    tertiary = CyberCyan,
    background = HorizonBlack,
    onBackground = Color.White,
    surface = TerminalCoal,
    onSurface = Color.White,
    surfaceVariant = ModuleSlate,
    onSurfaceVariant = TextGray,
    error = CyberRed,
    outline = GridBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark-mode terminal
    dynamicColor: Boolean = false, // Enforce aesthetic consistency
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}
