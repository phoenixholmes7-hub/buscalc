package com.buscalc.app.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min

/**
 * Number formatting for the calculator display.
 *
 * Values are held as doubles, but everything shown is first rounded to 12 significant
 * digits (half-up), so binary noise such as 0.1 + 0.2 never reaches the screen.
 * `decimals < 0` means floating format (as many digits as needed, up to 12).
 * All output uses an ASCII '-' for negatives; the UI swaps in a typographic minus.
 */
object NumFmt {
    const val MAX_DIGITS = 12
    const val FLOAT = -1

    private val MC = MathContext(MAX_DIGITS, RoundingMode.HALF_UP)
    private val LIMIT = BigDecimal("1E12")
    private val SMALL = BigDecimal("0.001")

    /** Rounds [x] to [decimals] places the way the display would. Floating format leaves it untouched. */
    fun round(x: Double, decimals: Int, mode: RoundingMode = RoundingMode.HALF_UP): Double {
        if (decimals < 0 || x.isNaN() || x.isInfinite()) return x
        return BigDecimal(x).round(MC).setScale(decimals, mode).toDouble()
    }

    fun format(x: Double, decimals: Int, commaRadix: Boolean = false): String {
        if (x.isNaN() || x.isInfinite()) return "Error"
        val bd = BigDecimal(x).round(MC)
        if (bd.signum() == 0) {
            return localize(BigDecimal.ZERO.setScale(max(decimals, 0)).toPlainString(), commaRadix)
        }
        val abs = bd.abs()
        if (abs >= LIMIT) return sci(bd, decimals, commaRadix)
        val intDigits = max(bd.precision() - bd.scale(), 1)
        val maxDec = MAX_DIGITS - intDigits
        if (decimals < 0) {
            val stripped = bd.stripTrailingZeros()
            val scale = max(stripped.scale(), 0)
            return when {
                scale <= maxDec -> localize(stripped.setScale(scale).toPlainString(), commaRadix)
                abs >= SMALL -> {
                    val r = bd.setScale(maxDec, RoundingMode.HALF_UP).stripTrailingZeros()
                    localize(r.setScale(max(r.scale(), 0)).toPlainString(), commaRadix)
                }
                else -> sci(bd, decimals, commaRadix)
            }
        }
        val r = bd.setScale(min(decimals, maxDec), RoundingMode.HALF_UP)
        if (r.signum() == 0 || r.abs() >= LIMIT) return sci(bd, decimals, commaRadix)
        return localize(r.toPlainString(), commaRadix)
    }

    private fun sci(bd: BigDecimal, decimals: Int, commaRadix: Boolean): String {
        var exp = bd.precision() - bd.scale() - 1
        val dec = if (decimals < 0) 9 else min(decimals, 9)
        var mant = bd.movePointLeft(exp).setScale(dec, RoundingMode.HALF_UP)
        if (mant.abs() >= BigDecimal.TEN) {
            mant = mant.movePointLeft(1).setScale(dec, RoundingMode.HALF_UP)
            exp += 1
        }
        if (decimals < 0) {
            mant = mant.stripTrailingZeros()
            if (mant.scale() < 0) mant = mant.setScale(0)
        }
        return localize(mant.toPlainString(), commaRadix) + "E" + exp
    }

    /** Formats a number while it is being typed, keeping a trailing decimal point and zeros. */
    fun groupEntry(mantissa: String, exponent: String?, commaRadix: Boolean = false): String {
        val body = localize(mantissa, commaRadix)
        return if (exponent == null) body else body + "E" + exponent
    }

    /** A typed number exactly as keyed, without thousands separators: how it appears in the history tape. */
    fun plainEntry(mantissa: String, exponent: String?, commaRadix: Boolean = false): String {
        val body = if (commaRadix) mantissa.replace('.', ',') else mantissa
        return if (exponent == null) body else body + "E" + exponent
    }

    /** Adds thousands separators to a plain "-1234.5" style string and applies the radix choice. */
    private fun localize(plain: String, commaRadix: Boolean): String {
        val negative = plain.startsWith("-")
        val unsigned = if (negative) plain.substring(1) else plain
        val dot = unsigned.indexOf('.')
        val intPart = (if (dot >= 0) unsigned.substring(0, dot) else unsigned).ifEmpty { "0" }
        val fracPart = if (dot >= 0) unsigned.substring(dot + 1) else null
        val group = if (commaRadix) '.' else ','
        val mark = if (commaRadix) ',' else '.'
        val sb = StringBuilder()
        if (negative) sb.append('-')
        for ((i, ch) in intPart.withIndex()) {
            if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(group)
            sb.append(ch)
        }
        if (fracPart != null) sb.append(mark).append(fracPart)
        return sb.toString()
    }
}
