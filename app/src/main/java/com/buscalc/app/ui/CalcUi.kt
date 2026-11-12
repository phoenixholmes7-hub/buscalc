package com.buscalc.app.ui

import androidx.compose.runtime.Immutable
import com.buscalc.app.engine.CalcEngine
import com.buscalc.app.engine.NumFmt
import com.buscalc.app.engine.Prefix
import com.buscalc.app.engine.Shift
import com.buscalc.app.engine.Strip

enum class Tone { PLAIN, GOLD, AQUA }

@Immutable
data class Flag(val text: String, val tone: Tone = Tone.PLAIN)

/** Everything the calculator screen draws, captured from the engine after each key press. */
@Immutable
data class CalcUi(
    val display: String,
    val isError: Boolean,
    /** Small line above the number: what the number is, or the calculation in progress. */
    val caption: String,
    val shift: Shift,
    /** Standing modes, always shown. */
    val modes: List<String>,
    /** Things that are temporarily in effect, such as a shift, a pending STO, or INPUT. */
    val flags: List<Flag>,
    val strip: Strip,
    val muted: Boolean,
) {
    companion object {
        fun from(e: CalcEngine): CalcUi {
            val s = e.s
            val flags = buildList {
                when (s.shift) {
                    Shift.GOLD -> add(Flag("UPPER", Tone.GOLD))
                    Shift.AQUA -> add(Flag("LOWER", Tone.AQUA))
                    Shift.NONE -> {}
                }
                when (s.prefix) {
                    Prefix.STO -> add(Flag("STO _", Tone.AQUA))
                    Prefix.STO_DOT -> add(Flag("STO ._", Tone.AQUA))
                    Prefix.RCL -> add(Flag("RCL _"))
                    Prefix.RCL_DOT -> add(Flag("RCL ._"))
                    Prefix.DISP -> add(Flag("DISP _", Tone.AQUA))
                    Prefix.NONE -> {}
                }
                if (s.inv) add(Flag("INV", Tone.GOLD))
                if (s.hyp) add(Flag("HYP", Tone.GOLD))
                if (s.hasInput) add(Flag("INPUT"))
                val open = s.pending.count { it.op == '(' }
                if (open > 0) add(Flag("(".repeat(open.coerceAtMost(4))))
                if (s.kOp != null) add(Flag("K"))
                if (s.m != 0.0) add(Flag("M"))
            }
            val modes = listOf(
                NumFmt.format(s.pyr, NumFmt.FLOAT, s.commaRadix) + " P/YR",
                if (s.begin) "BEGIN" else "END",
                if (s.radians) "RAD" else "DEG",
                if (s.chainMode) "CHAIN" else "ALG",
                if (s.dmy) "D.MY" else "M.DY",
                if (s.cal360) "360" else "ACT",
                if (s.semiAnnual) "SEMI" else "ANN",
            )
            val expression = e.expressionText()
            return CalcUi(
                display = if (e.isError) e.displayText else e.displayText.replace('-', '−'),
                isError = e.isError,
                caption = s.label ?: expression,
                shift = s.shift,
                modes = modes,
                flags = flags,
                strip = e.strip().let { strip ->
                    strip.copy(cells = strip.cells.map { it.first to it.second.replace('-', '−') })
                },
                muted = s.mute,
            )
        }
    }
}
