package com.buscalc.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.buscalc.app.engine.Key
import com.buscalc.app.engine.KeyDef
import com.buscalc.app.engine.KeyKind
import com.buscalc.app.engine.KeyMap
import com.buscalc.app.engine.Shift
import com.buscalc.app.engine.Strip

enum class MenuAction(val title: String) {
    AMORTIZATION("Amortization schedule"),
    CASH_FLOWS("Cash flows"),
    REGISTERS("Registers & memory"),
    HISTORY("Recent calculations"),
    COPY("Copy value"),
    PASTE("Paste value"),
    SETTINGS("Settings"),
    HELP("Help"),
}

private val Tight = PlatformTextStyle(includeFontPadding = false)

@Composable
fun CalculatorScreen(
    ui: CalcUi,
    onKey: (Key) -> Unit,
    onStripTap: () -> Unit,
    onMenu: (MenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCalcColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            DisplayPanel(ui, onMenu, Modifier.weight(0.23f))
            Spacer(Modifier.height(6.dp))
            RegisterStrip(ui.strip, onStripTap)
            Spacer(Modifier.height(4.dp))
            Keypad(ui.shift, onKey, Modifier.weight(0.77f))
        }
    }
}

// --------------------------------------------------------------------------- display

@Composable
private fun DisplayPanel(ui: CalcUi, onMenu: (MenuAction) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalCalcColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.display)
            .border(1.dp, c.outline, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.flags.forEach { FlagChip(it) }
            }
            MenuButton(onMenu)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = ui.caption,
                color = if (ui.isError) c.error else c.muted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                style = TextStyle(platformStyle = Tight, letterSpacing = 0.03.em),
            )
            BasicText(
                text = ui.display,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Display ${ui.display}" },
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 54.sp, stepSize = 1.sp),
                style = TextStyle(
                    color = if (ui.isError) c.error else c.displayText,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    fontFeatureSettings = "tnum",
                    platformStyle = Tight,
                ),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = ui.modes.joinToString("   "),
            modifier = Modifier.padding(end = 10.dp),
            color = c.muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = Tight, letterSpacing = 0.04.em, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun FlagChip(flag: Flag) {
    val c = LocalCalcColors.current
    val color = when (flag.tone) {
        Tone.GOLD -> c.gold
        Tone.AQUA -> c.aqua
        Tone.PLAIN -> c.displayText
    }
    Text(
        text = flag.text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        style = TextStyle(platformStyle = Tight, letterSpacing = 0.05.em),
    )
}

@Composable
private fun MenuButton(onMenu: (MenuAction) -> Unit) {
    val c = LocalCalcColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(AppIcons.Menu, contentDescription = "Menu", tint = c.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuAction.entries.forEach { action ->
                if (action == MenuAction.COPY || action == MenuAction.SETTINGS) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(action.title) },
                    onClick = {
                        open = false
                        onMenu(action)
                    },
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- register strip

@Composable
private fun RegisterStrip(strip: Strip, onTap: () -> Unit) {
    val c = LocalCalcColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Show next group of registers", role = Role.Button, onClick = onTap)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strip.title,
                color = c.aqua,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = Tight, letterSpacing = 0.12.em),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = strip.note ?: "tap to switch",
                color = c.muted,
                fontSize = 10.sp,
                maxLines = 1,
                style = TextStyle(platformStyle = Tight, letterSpacing = 0.04.em),
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            strip.cells.forEach { (name, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = value,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                        style = TextStyle(
                            color = c.displayText,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = "tnum",
                            platformStyle = Tight,
                        ),
                    )
                    Text(
                        text = name,
                        color = c.muted,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        style = TextStyle(platformStyle = Tight, letterSpacing = 0.06.em),
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- keypad

/** Font sizes that follow the key size instead of the system font scale, so labels always fit their keys. */
private class KeyMetrics(val goldBand: Dp, val main: TextUnit, val digit: TextUnit, val small: TextUnit)

@Composable
private fun Keypad(shift: Shift, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rows = KeyMap.keys.chunked(KeyMap.COLUMNS)
        val rowHeight = maxHeight / rows.size
        val keyWidth = maxWidth / KeyMap.COLUMNS
        val unit = minOf(rowHeight.value, keyWidth.value * 0.95f)
        val metrics = with(LocalDensity.current) {
            KeyMetrics(
                goldBand = (rowHeight.value * 0.24f).coerceIn(11f, 20f).dp,
                main = (unit * 0.27f).coerceIn(12f, 24f).dp.toSp(),
                digit = (unit * 0.36f).coerceIn(15f, 32f).dp.toSp(),
                small = (unit * 0.155f).coerceIn(8f, 13.5f).dp.toSp(),
            )
        }
        Column(Modifier.fillMaxSize()) {
            rows.forEach { row ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { def ->
                        KeyCell(
                            def, shift, metrics, onKey,
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCell(def: KeyDef, shift: Shift, m: KeyMetrics, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalCalcColors.current
    val face = when (def.kind) {
        KeyKind.FINANCE -> c.keyFinance
        KeyKind.FUNCTION -> c.keyFunction
        KeyKind.DIGIT -> c.keyDigit
        KeyKind.OPERATOR -> if (def.key == Key.EQ) c.keyEquals else c.keyOperator
        KeyKind.CLEAR -> if (def.key == Key.CLR) c.keyClear else c.keyFunction
        KeyKind.SHIFT_GOLD -> c.gold
        KeyKind.SHIFT_AQUA -> c.aqua
    }
    val text = when {
        def.key == Key.EQ -> c.onEquals
        def.key == Key.CLR -> c.onClear
        else -> c.keyText
    }
    // While a shift is active its labels stay bright and everything else steps back.
    val mainAlpha = if (shift == Shift.NONE) 1f else 0.45f
    val goldAlpha = if (shift == Shift.AQUA) 0.35f else 1f
    val aquaAlpha = if (shift == Shift.GOLD) 0.35f else 1f
    val isActiveShift =
        (def.kind == KeyKind.SHIFT_GOLD && shift == Shift.GOLD) || (def.kind == KeyKind.SHIFT_AQUA && shift == Shift.AQUA)

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(m.goldBand),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (def.goldLabel != null) {
                Text(
                    text = keyLabel(def.goldLabel),
                    color = c.gold.copy(alpha = goldAlpha),
                    fontSize = m.small,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(platformStyle = Tight),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(face)
                .then(if (isActiveShift) Modifier.border(2.5.dp, c.displayText, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(role = Role.Button) { onKey(def.key) }
                .semantics { contentDescription = describe(def) },
            contentAlignment = Alignment.Center,
        ) {
            when (def.kind) {
                KeyKind.SHIFT_GOLD -> ShiftGlyph(pointUp = true, color = c.onShift)
                KeyKind.SHIFT_AQUA -> ShiftGlyph(pointUp = false, color = c.onShift)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (def.key == Key.BACK) {
                        Icon(
                            AppIcons.Backspace,
                            contentDescription = null,
                            tint = text.copy(alpha = mainAlpha),
                            modifier = Modifier.size(with(LocalDensity.current) { m.main.toDp() } * 1.15f),
                        )
                    } else {
                        Text(
                            text = keyLabel(def.label),
                            color = text.copy(alpha = mainAlpha),
                            fontSize = if (def.kind == KeyKind.DIGIT || def.kind == KeyKind.OPERATOR) m.digit else m.main,
                            fontWeight = if (def.kind == KeyKind.DIGIT) FontWeight.Normal else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(platformStyle = Tight),
                        )
                    }
                    if (def.aquaLabel != null) {
                        Text(
                            text = keyLabel(def.aquaLabel),
                            color = (if (def.key == Key.EQ) c.onEquals else c.aqua).copy(alpha = aquaAlpha),
                            fontSize = m.small,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(platformStyle = Tight),
                        )
                    }
                }
            }
        }
    }
}

/** An arrow toward the labels that the shift key selects: up for the ones above the keys, down for the ones below. */
@Composable
private fun ShiftGlyph(pointUp: Boolean, color: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 14.dp)) {
        val tip = if (pointUp) 0f else size.height
        val base = if (pointUp) size.height else 0f
        val path = Path().apply {
            moveTo(size.width / 2, tip)
            lineTo(size.width, base)
            lineTo(0f, base)
            close()
        }
        drawPath(path, color)
    }
}

/** Renders "e^x" style labels with a real superscript. */
private fun keyLabel(text: String): AnnotatedString {
    val caret = text.indexOf('^')
    if (caret < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, caret))
        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)) {
            append(text.substring(caret + 1))
        }
    }
}

private fun describe(def: KeyDef): String = when (def.kind) {
    KeyKind.SHIFT_GOLD -> "Upper shift"
    KeyKind.SHIFT_AQUA -> "Lower shift"
    else -> when (def.key) {
        Key.BACK -> "Backspace"
        Key.CHS -> "Change sign"
        Key.DOT -> "Decimal point"
        Key.EQ -> "Equals"
        Key.ADD -> "Plus"
        Key.SUB -> "Minus"
        Key.MUL -> "Times"
        Key.DIV -> "Divide"
        Key.CLR -> "Clear"
        else -> def.label
    }
}
