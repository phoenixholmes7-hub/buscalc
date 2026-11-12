package com.buscalc.app.engine

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** A plain Gregorian calendar date. Kept free of java.time so it runs on every supported Android version. */
data class CalDate(val year: Int, val month: Int, val day: Int) {

    /** Days since 1970-01-01. */
    fun toEpochDay(): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int get() = (Math.floorMod(toEpochDay() + 3, 7L) + 1).toInt()

    val weekdayName: String get() = WEEKDAYS[dayOfWeek - 1]

    fun plusDays(days: Long): CalDate = fromEpochDay(toEpochDay() + days)

    /** Moves by whole months, pinning to [anchorDay] where the target month is long enough. */
    fun plusMonths(months: Int, anchorDay: Int = day): CalDate {
        val total = year * 12 + (month - 1) + months
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        return CalDate(y, m, minOf(anchorDay, daysInMonth(y, m)))
    }

    companion object {
        private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun isLeap(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

        fun daysInMonth(year: Int, month: Int) = when (month) {
            2 -> if (isLeap(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        fun isValid(year: Int, month: Int, day: Int) =
            year in 1583..9999 && month in 1..12 && day in 1..daysInMonth(year, month)

        fun fromEpochDay(epochDay: Long): CalDate {
            val z = epochDay + 719468
            val era = Math.floorDiv(z, 146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
            return CalDate(y, m, d)
        }
    }
}

object Dates {
    /**
     * Reads a date keyed in as a number: MM.DDYYYY, or DD.MMYYYY when [dmy] is set.
     * April 28, 2010 is typed 4.282010 (or 28.042010).
     */
    fun parse(x: Double, dmy: Boolean): CalDate? {
        if (x.isNaN() || x < 1.0 || x >= 100.0) return null
        val whole = floor(x).toInt()
        val rest = ((x - whole) * 1e6).roundToLong()
        val second = (rest / 10000).toInt()
        val year = (rest % 10000).toInt()
        val month = if (dmy) second else whole
        val day = if (dmy) whole else second
        return if (CalDate.isValid(year, month, day)) CalDate(year, month, day) else null
    }

    fun encode(d: CalDate, dmy: Boolean): Double {
        val whole = if (dmy) d.day else d.month
        val second = if (dmy) d.month else d.day
        return whole + second / 100.0 + d.year / 1e6
    }

    fun format(d: CalDate, dmy: Boolean): String {
        val mm = d.month.toString().padStart(2, '0')
        val dd = d.day.toString().padStart(2, '0')
        return if (dmy) "$dd-$mm-${d.year}" else "$mm-$dd-${d.year}"
    }

    fun daysActual(a: CalDate, b: CalDate): Long = b.toEpochDay() - a.toEpochDay()

    /** 30/360 day count, treating every month as 30 days. */
    fun days360(a: CalDate, b: CalDate): Long {
        val d1 = if (a.day == 31) 30 else a.day
        val d2 = if (b.day == 31 && d1 == 30) 30 else b.day
        return 360L * (b.year - a.year) + 30L * (b.month - a.month) + (d2 - d1)
    }
}

/** Fixed-coupon bond pricing on a settlement date, per 100 of face value. */
object Bond {
    data class Params(
        val settlement: CalDate,
        val maturity: CalDate,
        /** Annual coupon rate, percent. */
        val coupon: Double,
        /** Redemption (call) value per 100 of face. */
        val call: Double,
        val semiAnnual: Boolean,
        /** Actual/actual day count when true, 30/360 otherwise. */
        val actual: Boolean,
    )

    private class Schedule(val accruedDays: Double, val periodDays: Double, val remainingCoupons: Int) {
        val daysToNext: Double get() = periodDays - accruedDays
    }

    private fun schedule(p: Params): Schedule {
        if (p.maturity.toEpochDay() <= p.settlement.toEpochDay()) throw CalcError(Err.DATE)
        val step = if (p.semiAnnual) 6 else 12
        // Coupon dates run backward from maturity; find the pair that brackets settlement.
        var k = 1
        var prev = p.maturity.plusMonths(-step)
        while (prev.toEpochDay() > p.settlement.toEpochDay()) {
            k += 1
            prev = p.maturity.plusMonths(-step * k)
            if (k > 4000) throw CalcError(Err.DATE)
        }
        val next = p.maturity.plusMonths(-step * (k - 1))
        return if (p.actual) {
            Schedule(
                Dates.daysActual(prev, p.settlement).toDouble(),
                Dates.daysActual(prev, next).toDouble(),
                k,
            )
        } else {
            Schedule(Dates.days360(prev, p.settlement).toDouble(), 360.0 / (12 / step), k)
        }
    }

    fun accrued(p: Params): Double {
        val s = schedule(p)
        val perPeriod = p.coupon / (if (p.semiAnnual) 2 else 1)
        return perPeriod * s.accruedDays / s.periodDays
    }

    /** Clean price for a yield given as an annual percentage. */
    fun price(p: Params, yieldPct: Double): Double = priceWith(p, schedule(p), yieldPct)

    private fun priceWith(p: Params, s: Schedule, yieldPct: Double): Double {
        val m = if (p.semiAnnual) 2.0 else 1.0
        val c = p.coupon / m
        val y = yieldPct / 100.0 / m
        val frac = s.daysToNext / s.periodDays
        val accrued = c * s.accruedDays / s.periodDays
        if (y <= -1.0) throw CalcError(Err.INVALID)
        if (s.remainingCoupons <= 1) {
            val denom = 1.0 + frac * y
            if (denom <= 0.0) throw CalcError(Err.INVALID)
            return (p.call + c) / denom - accrued
        }
        val n = s.remainingCoupons
        val v = 1.0 / (1.0 + y)
        // c * (v^frac + v^(frac+1) + … + v^(frac+n-1)) + call * v^(frac+n-1)
        val series = if (y == 0.0) n.toDouble() else (1.0 - v.pow(n)) / (1.0 - v)
        return c * v.pow(frac) * series + p.call * v.pow(frac + n - 1) - accrued
    }

    /** Yield to maturity (or to call), as an annual percentage, for a clean [price]. */
    fun yieldFor(p: Params, price: Double): Double {
        if (price <= 0.0) throw CalcError(Err.INVALID)
        val s = schedule(p)
        val m = if (p.semiAnnual) 2.0 else 1.0
        // Price falls as yield rises, so a simple bracket and bisection always converges.
        var lo = -99.0 * m
        var hi = 100.0
        fun f(y: Double) = priceWith(p, s, y) - price
        while (f(hi) > 0.0) {
            hi *= 2
            if (hi > 1e7) throw CalcError(Err.NO_SOLUTION)
        }
        if (f(lo) < 0.0) throw CalcError(Err.NO_SOLUTION)
        repeat(200) {
            val mid = (lo + hi) / 2
            if (mid == lo || mid == hi) return mid
            if (f(mid) > 0.0) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
}
