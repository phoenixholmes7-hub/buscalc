package com.buscalc.app.engine

import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.serialization.Serializable

/** A calculation that cannot produce an answer. The message is what the display shows. */
class CalcError(message: String) : RuntimeException(message)

object Err {
    const val NO_SOLUTION = "No solution"
    const val DIV0 = "Divide by zero"
    const val INVALID = "Invalid input"
    const val OVERFLOW = "Overflow"
    const val PYR = "P/YR must be 1–999"
    const val DATE = "Invalid date"
    const val NO_DATE = "Dates not set"
    const val NO_DATA = "No data"
    const val NO_FLOWS = "No cash flows"
    const val FULL = "List is full"
}

/**
 * Root finding by scanning for a sign change and then bisecting.
 *
 * The rate equations here can have zero, one, or several roots, and Newton's method
 * is happy to wander off or land on a root nobody wanted. Scanning in u = ln(1 + i)
 * covers rates from about -100% up to +100,000% per period evenly in relative terms,
 * and when there are several roots it returns the smallest positive one (or the
 * negative one closest to zero), which is the answer a person expects.
 */
internal object RateSolver {
    private const val STEPS = 6000

    /** [f] takes u = ln(1 + i) and returns the equation residual. [horizon] is the largest exponent applied to u. */
    fun solve(horizon: Double, f: (Double) -> Double): Double {
        val span = if (horizon > 0) 700.0 / horizon else 700.0
        val uMin = max(ln(1e-6), -span)
        val uMax = min(ln(1e5), span)

        var bestNeg: Double? = null
        val step = (uMax - uMin) / STEPS
        // Walk outward from zero on the positive side first: the first root found is the smallest positive.
        scan(0.0, uMax, step, f)?.let { return toRate(it) }
        // Then the negative side, again starting at zero so the first hit is the closest to zero.
        scan(0.0, uMin, -step, f)?.let { bestNeg = it }
        return toRate(bestNeg ?: throw CalcError(Err.NO_SOLUTION))
    }

    private fun toRate(u: Double): Double {
        val i = expm1(u)
        return if (abs(i) < 1e-14) 0.0 else i
    }

    private fun scan(from: Double, to: Double, step: Double, f: (Double) -> Double): Double? {
        var u0 = from
        var f0 = f(u0)
        if (f0 == 0.0) return u0
        while (if (step > 0) u0 < to else u0 > to) {
            val u1 = if (step > 0) min(u0 + step, to) else max(u0 + step, to)
            val f1 = f(u1)
            if (f1 == 0.0) return u1
            if (f0.isFinite() && f1.isFinite() && (f0 < 0) != (f1 < 0)) return bisect(u0, u1, f0, f)
            u0 = u1
            f0 = f1
        }
        return null
    }

    private fun bisect(a0: Double, b0: Double, fa0: Double, f: (Double) -> Double): Double {
        var a = a0
        var b = b0
        var fa = fa0
        repeat(200) {
            val m = (a + b) / 2
            if (m == a || m == b) return m
            val fm = f(m)
            if (fm == 0.0) return m
            if ((fm < 0) == (fa < 0)) {
                a = m
                fa = fm
            } else {
                b = m
            }
        }
        return (a + b) / 2
    }
}

/** Time value of money. Rates are percentages per year; cash paid out is negative. */
object Tvm {
    data class Params(
        val n: Double,
        val iyr: Double,
        val pv: Double,
        val pmt: Double,
        val fv: Double,
        val pyr: Double,
        val begin: Boolean,
    ) {
        val i: Double get() = iyr / pyr / 100.0
        val k: Double get() = if (begin) 1.0 + i else 1.0
    }

    /** (1 + i)^-n */
    private fun discount(i: Double, n: Double): Double = exp(-n * ln1p(i))

    /** Present value of an ordinary annuity of 1: (1 - (1 + i)^-n) / i, with the i = 0 limit. */
    private fun annuity(i: Double, n: Double): Double =
        if (i == 0.0) n else -expm1(-n * ln1p(i)) / i

    private fun checkRate(p: Params) {
        if (p.pyr < 1 || p.pyr > 999) throw CalcError(Err.PYR)
        if (p.i <= -1.0) throw CalcError(Err.INVALID)
    }

    private fun finite(v: Double): Double {
        if (v.isNaN()) throw CalcError(Err.NO_SOLUTION)
        if (v.isInfinite()) throw CalcError(Err.OVERFLOW)
        return v
    }

    fun solvePv(p: Params): Double {
        checkRate(p)
        return finite(-(p.pmt * p.k * annuity(p.i, p.n) + p.fv * discount(p.i, p.n)))
    }

    fun solveFv(p: Params): Double {
        checkRate(p)
        return finite(-(p.pv + p.pmt * p.k * annuity(p.i, p.n)) / discount(p.i, p.n))
    }

    fun solvePmt(p: Params): Double {
        checkRate(p)
        val a = p.k * annuity(p.i, p.n)
        if (a == 0.0) throw CalcError(Err.NO_SOLUTION)
        return finite(-(p.pv + p.fv * discount(p.i, p.n)) / a)
    }

    fun solveN(p: Params): Double {
        checkRate(p)
        val i = p.i
        if (i == 0.0) {
            if (p.pmt == 0.0) throw CalcError(Err.NO_SOLUTION)
            val n = -(p.pv + p.fv) / p.pmt
            if (n < 0) throw CalcError(Err.NO_SOLUTION)
            return n
        }
        val c = p.pmt * p.k / i
        val ratio = (c - p.fv) / (c + p.pv)
        if (ratio.isNaN() || ratio <= 0.0 || ratio.isInfinite()) throw CalcError(Err.NO_SOLUTION)
        val n = ln(ratio) / ln1p(i)
        if (n < 0) throw CalcError(Err.NO_SOLUTION)
        return finite(n)
    }

    /** Returns I/YR as an annual nominal percentage. */
    fun solveIyr(p: Params): Double {
        if (p.pyr < 1 || p.pyr > 999) throw CalcError(Err.PYR)
        if (p.n <= 0.0) throw CalcError(Err.NO_SOLUTION)
        val s = if (p.begin) 1.0 else 0.0
        val i = RateSolver.solve(p.n) { u ->
            val rate = expm1(u)
            val d = exp(-p.n * u)
            val a = if (rate == 0.0) p.n else -expm1(-p.n * u) / rate
            p.pv + p.pmt * (1.0 + rate * s) * a + p.fv * d
        }
        return i * p.pyr * 100.0
    }

    fun effectiveFromNominal(nominal: Double, pyr: Double): Double {
        if (pyr < 1 || pyr > 999) throw CalcError(Err.PYR)
        return ((1.0 + nominal / 100.0 / pyr).pow(pyr) - 1.0) * 100.0
    }

    fun nominalFromEffective(effective: Double, pyr: Double): Double {
        if (pyr < 1 || pyr > 999) throw CalcError(Err.PYR)
        if (effective <= -100.0) throw CalcError(Err.INVALID)
        return ((1.0 + effective / 100.0).pow(1.0 / pyr) - 1.0) * pyr * 100.0
    }
}

data class AmortRow(val period: Int, val principal: Double, val interest: Double, val balance: Double)

data class AmortSchedule(
    val rows: List<AmortRow>,
    val totalPrincipal: Double,
    val totalInterest: Double,
) {
    val totalPaid: Double get() = totalPrincipal + totalInterest
}

object Amortization {
    const val MAX_PERIODS = 12000

    /**
     * Builds the schedule for payments [from]..[to] (1-based, inclusive).
     *
     * PV, PMT and each period's interest are rounded to [decimals] places first, the way
     * a lender's statement works, so the rows add up to the totals shown. Interest uses
     * banker's rounding (half to even), which is what loan servicing systems use and keeps
     * the half-cent cases from drifting in one direction over hundreds of payments.
     * Floating format ([decimals] < 0) skips the rounding. Signs follow the cash-flow
     * convention: with a positive PV and negative PMT, principal and interest are negative.
     */
    fun schedule(p: Tvm.Params, from: Int, to: Int, decimals: Int): AmortSchedule {
        if (p.pyr < 1 || p.pyr > 999) throw CalcError(Err.PYR)
        if (from < 1 || to < from || to > MAX_PERIODS) throw CalcError(Err.INVALID)
        val i = p.i
        val pmt = NumFmt.round(p.pmt, decimals)
        var balance = NumFmt.round(p.pv, decimals)
        val rows = ArrayList<AmortRow>(to - from + 1)
        var totalPrincipal = 0.0
        var totalInterest = 0.0
        for (period in 1..to) {
            val interest =
                if (p.begin && period == 1) 0.0 else NumFmt.round(-balance * i, decimals, RoundingMode.HALF_EVEN) + 0.0
            val principal = pmt - interest
            balance = NumFmt.round(balance + principal, decimals)
            if (period >= from) {
                rows += AmortRow(period, principal, interest, balance)
                totalPrincipal += principal
                totalInterest += interest
            }
        }
        return AmortSchedule(rows, totalPrincipal, totalInterest)
    }
}

@Serializable
data class CashFlow(val amount: Double, val count: Int = 1)

object CashFlows {
    const val MAX_FLOWS = 100
    const val MAX_COUNT = 9999

    private fun periods(flows: List<CashFlow>): Int = flows.sumOf { it.count }

    /** Net present value at periodic rate [i] (a decimal). The first flow sits at time zero. */
    fun npv(flows: List<CashFlow>, i: Double): Double {
        if (flows.isEmpty()) throw CalcError(Err.NO_FLOWS)
        if (i <= -1.0) throw CalcError(Err.INVALID)
        val v = npvAt(flows, ln1p(i))
        if (!v.isFinite()) throw CalcError(Err.OVERFLOW)
        return v
    }

    /** Net future value: the NPV carried forward to the time of the last flow. */
    fun nfv(flows: List<CashFlow>, i: Double): Double {
        val v = npv(flows, i) * (1.0 + i).pow(periods(flows) - 1)
        if (!v.isFinite()) throw CalcError(Err.OVERFLOW)
        return v
    }

    private fun npvAt(flows: List<CashFlow>, u: Double): Double {
        var total = 0.0
        var t = 0
        for (f in flows) {
            // amount * sum_{k=0}^{count-1} e^{-u (t + k)}
            val series = if (abs(u) < 1e-12) f.count.toDouble() else expm1(-u * f.count) / expm1(-u)
            total += f.amount * exp(-u * t) * series
            t += f.count
        }
        return total
    }

    /** Internal rate of return per period, as a decimal. */
    fun irr(flows: List<CashFlow>): Double {
        if (flows.isEmpty()) throw CalcError(Err.NO_FLOWS)
        if (flows.none { it.amount > 0 } || flows.none { it.amount < 0 }) throw CalcError(Err.NO_SOLUTION)
        return RateSolver.solve(periods(flows).toDouble()) { u -> npvAt(flows, u) }
    }

    fun total(flows: List<CashFlow>): Double = flows.sumOf { it.amount * it.count }
}

/** Running sums for one- and two-variable statistics and linear regression. */
@Serializable
data class StatSums(
    val n: Double = 0.0,
    val sx: Double = 0.0,
    val sy: Double = 0.0,
    val sxx: Double = 0.0,
    val syy: Double = 0.0,
    val sxy: Double = 0.0,
) {
    fun add(x: Double, y: Double, sign: Double = 1.0) = StatSums(
        n + sign, sx + sign * x, sy + sign * y, sxx + sign * x * x, syy + sign * y * y, sxy + sign * x * y,
    )

    private fun need(count: Int) {
        if (n < count) throw CalcError(Err.NO_DATA)
    }

    val meanX: Double get() { need(1); return sx / n }
    val meanY: Double get() { need(1); return sy / n }

    private fun variance(s: Double, ss: Double, sample: Boolean): Double {
        need(if (sample) 2 else 1)
        return max((ss - s * s / n) / (if (sample) n - 1 else n), 0.0)
    }

    fun sdX(sample: Boolean): Double = kotlin.math.sqrt(variance(sx, sxx, sample))
    fun sdY(sample: Boolean): Double = kotlin.math.sqrt(variance(sy, syy, sample))

    val slope: Double
        get() {
            need(2)
            val d = n * sxx - sx * sx
            if (d == 0.0) throw CalcError(Err.NO_SOLUTION)
            return (n * sxy - sx * sy) / d
        }

    val intercept: Double get() = (sy - slope * sx) / n

    val correlation: Double
        get() {
            need(2)
            val d = (n * sxx - sx * sx) * (n * syy - sy * sy)
            if (d <= 0.0) throw CalcError(Err.NO_SOLUTION)
            return (n * sxy - sx * sy) / kotlin.math.sqrt(d)
        }

    fun estimateY(x: Double): Double = slope * x + intercept

    fun estimateX(y: Double): Double {
        val m = slope
        if (m == 0.0) throw CalcError(Err.NO_SOLUTION)
        return (y - intercept) / m
    }

    /** Mean of x weighted by y. */
    val weightedMean: Double
        get() {
            need(1)
            if (sy == 0.0) throw CalcError(Err.DIV0)
            return sxy / sy
        }
}

object Depreciation {
    data class Result(val depreciation: Double, val remaining: Double)

    private fun check(life: Double, year: Double) {
        if (life <= 0.0 || year < 1.0 || year != floor(year) || year > 10000) throw CalcError(Err.INVALID)
    }

    fun straightLine(cost: Double, salvage: Double, life: Double, year: Double): Result {
        check(life, year)
        val base = cost - salvage
        if (year - 1 >= life) return Result(0.0, 0.0)
        val annual = base / life
        val before = annual * (year - 1)
        val after = if (year >= life) base else annual * year
        return Result(after - before, base - after)
    }

    fun sumOfYears(cost: Double, salvage: Double, life: Double, year: Double): Result {
        check(life, year)
        val base = cost - salvage
        val whole = floor(life)
        val frac = life - whole
        val soyd = (whole + 1) * (whole + 2 * frac) / 2
        var accumulated = 0.0
        var dep = 0.0
        var y = 1.0
        while (y <= year) {
            dep = if (y > ceil(life)) 0.0 else max(life - y + 1, 0.0) / soyd * base
            if (abs(accumulated + dep) > abs(base)) dep = base - accumulated
            accumulated += dep
            y += 1.0
        }
        return Result(dep, base - accumulated)
    }

    /** Declining balance at [factorPct] percent of the straight-line rate (200 = double declining). */
    fun decliningBalance(cost: Double, salvage: Double, life: Double, factorPct: Double, year: Double): Result {
        check(life, year)
        if (factorPct <= 0.0) throw CalcError(Err.INVALID)
        val rate = min(factorPct / 100.0 / life, 1.0)
        var book = cost
        var dep = 0.0
        var y = 1.0
        while (y <= year) {
            dep = if (y > ceil(life)) 0.0 else min(book * rate, max(book - salvage, 0.0))
            book -= dep
            y += 1.0
        }
        return Result(dep, book - salvage)
    }
}

object BreakEven {
    private fun safeDiv(a: Double, b: Double): Double = if (b == 0.0) throw CalcError(Err.DIV0) else a / b

    fun units(sp: Double, vc: Double, fc: Double, profit: Double) = safeDiv(fc + profit, sp - vc)
    fun price(units: Double, vc: Double, fc: Double, profit: Double) = safeDiv(fc + profit, units) + vc
    fun variableCost(units: Double, sp: Double, fc: Double, profit: Double) = sp - safeDiv(fc + profit, units)
    fun fixedCost(units: Double, sp: Double, vc: Double, profit: Double) = units * (sp - vc) - profit
    fun profit(units: Double, sp: Double, vc: Double, fc: Double) = units * (sp - vc) - fc
}
