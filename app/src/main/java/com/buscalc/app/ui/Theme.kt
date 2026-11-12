package com.buscalc.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours for the calculator face. The rest of the app uses the Material scheme derived from them. */
@Immutable
data class CalcColors(
    val isDark: Boolean,
    val background: Color,
    val display: Color,
    val displayText: Color,
    val muted: Color,
    val outline: Color,
    /** Upper shift: the labels printed above the keys. */
    val gold: Color,
    /** Lower shift: the labels printed on the bottom edge of the keys. */
    val aqua: Color,
    val onShift: Color,
    val keyFinance: Color,
    val keyFunction: Color,
    val keyDigit: Color,
    val keyOperator: Color,
    val keyText: Color,
    val keyEquals: Color,
    val onEquals: Color,
    val keyClear: Color,
    val onClear: Color,
    val error: Color,
)

val DarkCalcColors = CalcColors(
    isDark = true,
    background = Color(0xFF0E1419),
    display = Color(0xFF151D24),
    displayText = Color(0xFFF2F6F8),
    muted = Color(0xFF8B99A6),
    outline = Color(0xFF26323C),
    gold = Color(0xFFFFB547),
    aqua = Color(0xFF4FD6E6),
    onShift = Color(0xFF0E1419),
    keyFinance = Color(0xFF1F3344),
    keyFunction = Color(0xFF1D262E),
    keyDigit = Color(0xFF2C3742),
    keyOperator = Color(0xFF3B4957),
    keyText = Color(0xFFF2F6F8),
    keyEquals = Color(0xFFE8EEF2),
    onEquals = Color(0xFF0E1419),
    keyClear = Color(0xFF45262A),
    onClear = Color(0xFFFF9A8F),
    error = Color(0xFFFF9A8F),
)

val LightCalcColors = CalcColors(
    isDark = false,
    background = Color(0xFFE6EBEF),
    display = Color(0xFFFFFFFF),
    displayText = Color(0xFF111A21),
    muted = Color(0xFF5C6B78),
    outline = Color(0xFFCBD4DB),
    gold = Color(0xFF9A5A00),
    aqua = Color(0xFF006E7F),
    onShift = Color(0xFFFFFFFF),
    keyFinance = Color(0xFFD3E0EA),
    keyFunction = Color(0xFFF4F7F9),
    keyDigit = Color(0xFFFFFFFF),
    keyOperator = Color(0xFFC9D3DB),
    keyText = Color(0xFF111A21),
    keyEquals = Color(0xFF16242F),
    onEquals = Color(0xFFFFFFFF),
    keyClear = Color(0xFFF9DAD6),
    onClear = Color(0xFFA5271B),
    error = Color(0xFFB3261E),
)

val LocalCalcColors = staticCompositionLocalOf { DarkCalcColors }

@Composable
fun BusCalcTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkCalcColors else LightCalcColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.aqua,
            onPrimary = c.onShift,
            primaryContainer = Color(0xFF123F48),
            onPrimaryContainer = Color(0xFFBDF3FA),
            secondary = c.gold,
            onSecondary = c.onShift,
            secondaryContainer = Color(0xFF4A3410),
            onSecondaryContainer = Color(0xFFFFDDA8),
            tertiary = c.gold,
            background = c.background,
            onBackground = c.displayText,
            surface = c.background,
            onSurface = c.displayText,
            surfaceVariant = c.keyFunction,
            onSurfaceVariant = c.muted,
            surfaceContainerLowest = Color(0xFF0A0F13),
            surfaceContainerLow = c.display,
            surfaceContainer = Color(0xFF19222A),
            surfaceContainerHigh = c.keyFunction,
            surfaceContainerHighest = c.keyDigit,
            outline = Color(0xFF5B6975),
            outlineVariant = c.outline,
            error = c.error,
            onError = c.onShift,
        )
    } else {
        lightColorScheme(
            primary = c.aqua,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC4EEF4),
            onPrimaryContainer = Color(0xFF00363F),
            secondary = c.gold,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE2B5),
            onSecondaryContainer = Color(0xFF4A2B00),
            tertiary = c.gold,
            background = c.background,
            onBackground = c.displayText,
            surface = c.background,
            onSurface = c.displayText,
            surfaceVariant = Color(0xFFDCE3E9),
            onSurfaceVariant = c.muted,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F9FA),
            surfaceContainer = Color(0xFFF1F4F6),
            surfaceContainerHigh = Color(0xFFEAEEF1),
            surfaceContainerHighest = Color(0xFFDFE5EA),
            outline = Color(0xFF7B8893),
            outlineVariant = c.outline,
            error = c.error,
            onError = Color.White,
        )
    }
    CompositionLocalProvider(LocalCalcColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
