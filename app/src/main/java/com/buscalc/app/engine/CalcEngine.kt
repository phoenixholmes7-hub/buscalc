package com.buscalc.app.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/** The row of live registers shown above the keypad. */
data class Strip(val context: Context, val title: String, val cells: List<Pair<String, String>>, val note: String? = null)

/**
 * The calculator itself: feed it key presses, read back the display.
 *
 * It has no Android dependencies, so the whole thing runs in plain unit tests.
 *
 * Store-or-solve keys (N, I/YR, PV, PMT, FV, the margin, break-even and bond registers)
 * follow one rule: if a number was just keyed in or calculated, the key stores it;
 * if the previous key was another one of those register keys, it solves for that value.
 */
class CalcEngine(
    var s: CalcState = CalcState(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: () -> Double = { Math.random() },
) {
    sealed interface Effect {
        data object OpenHelp : Effect
        data class OpenAmortization(val from: Int, val to: Int) : Effect
    }

    // ---------------------------------------------------------------- key input

    fun press(key: Key): Effect? {
        when (key) {
            Key.GOLD -> {
                s.shift = if (s.shift == Shift.GOLD) Shift.NONE else Shift.GOLD
                return null
            }
            Key.AQUA -> {
                s.shift = if (s.shift == Shift.AQUA) Shift.NONE else Shift.AQUA
                return null
            }
            else -> {}
        }
        val def = KeyMap.of(key)
        val fn = when (s.shift) {
            Shift.GOLD -> def.gold
            Shift.AQUA -> def.aqua
            Shift.NONE -> def.main
        }
        s.shift = Shift.NONE
        return if (fn == null) null else apply(fn)
    }

    fun apply(fn: Fn): Effect? {
        if (s.error != null) {
            s.error = null
            if (fn == Fn.CLR || fn == Fn.BACK) return null
        }
        val oldLabel = s.label
        val oldLabel2 = s.label2
        val oldDate = s.dateText
        val oldDate2 = s.dateText2
        s.label = null
        s.label2 = null
        s.dateText = null
        s.dateText2 = null
        s.showFull = false
        try {
            val prefix = s.prefix
            if (prefix != Prefix.NONE) {
                s.prefix = Prefix.NONE
                val consumed = when (prefix) {
                    Prefix.STO, Prefix.STO_DOT -> handleStore(fn, prefix == Prefix.STO_DOT)
                    Prefix.RCL, Prefix.RCL_DOT -> handleRecall(fn, prefix == Prefix.RCL_DOT)
                    Prefix.DISP -> handleDisp(fn)
                    Prefix.NONE -> false
                }
                if (consumed) return null
            }
            if (fn == Fn.SWAP) {
                swap(oldLabel, oldLabel2, oldDate, oldDate2)
                return null
            }
            return dispatch(fn)
        } catch (e: CalcError) {
            s.error = e.message
            s.entry = null
            s.entryExp = null
            return null
        } finally {
            s.lastFn = fn
        }
    }

    /** Puts a value in the display as if it had been keyed in (history taps, paste). */
    fun loadValue(v: Double) {
        s.error = null
        s.entry = null
        s.entryExp = null
        clearTransient()
        if (v.isFinite()) setX(v)
    }

    private fun clearTransient() {
        s.label = null
        s.label2 = null
        s.dateText = null
        s.dateText2 = null
        s.showFull = false
        s.prefix = Prefix.NONE
        s.shift = Shift.NONE
    }

    private fun dispatch(fn: Fn): Effect? {
        val wasOp = s.lastWasOperator
        s.lastWasOperator = false
        val inv = s.inv
        val hyp = s.hyp
        if (fn != Fn.HYP && fn != Fn.INV) {
            s.inv = false
            s.hyp = false
        }
        when (fn) {
            Fn.D0, Fn.D1, Fn.D2, Fn.D3, Fn.D4, Fn.D5, Fn.D6, Fn.D7, Fn.D8, Fn.D9 -> digit('0' + fn.ordinal)
            Fn.DOT -> dot()
            Fn.EEX -> exponent()
            Fn.CHS -> changeSign()
            Fn.BACK -> backspace()
            Fn.CLR -> clear()
            Fn.CLR_ALL -> clearAll()

            Fn.ADD -> operator('+', wasOp)
            Fn.SUB -> operator('-', wasOp)
            Fn.MUL -> operator('*', wasOp)
            Fn.DIV -> operator('/', wasOp)
            Fn.POW -> operator('^', wasOp)
            Fn.EQ -> equals()
            Fn.LPAREN -> openParen()
            Fn.RPAREN -> closeParen()
            Fn.PCT -> percent()
            Fn.PCT_CHG -> percentChange()
            Fn.K -> constant()
            Fn.SWAP -> {}
            Fn.INPUT -> {
                commitEntry()
                s.y = s.x
                s.hasInput = true
                s.pairReady = true
                s.label = "INPUT"
            }
            Fn.RND -> {
                commitEntry()
                setX(NumFmt.round(s.x, s.decimals))
            }

            Fn.INV_X -> unary { if (it == 0.0) throw CalcError(Err.DIV0) else 1.0 / it }
            Fn.SQRT -> unary { if (it < 0.0) throw CalcError(Err.INVALID) else sqrt(it) }
            Fn.SQUARE -> unary { it * it }
            Fn.EXP -> unary { exp(it) }
            Fn.LN -> unary { if (it <= 0.0) throw CalcError(Err.INVALID) else ln(it) }
            Fn.FACT -> unary { factorial(it) }
            Fn.PI -> {
                commitEntry()
                setX(PI)
            }
            Fn.RAND -> {
                commitEntry()
                setX(random())
            }
            Fn.NPR, Fn.NCR -> countArrangements(fn == Fn.NCR)
            Fn.SIN, Fn.COS, Fn.TAN -> trig(fn, inv, hyp)
            Fn.HYP -> {
                s.hyp = !hyp
                s.lastWasOperator = wasOp
            }
            Fn.INV -> {
                s.inv = !inv
                s.lastWasOperator = wasOp
            }
            Fn.RAD_DEG -> {
                s.radians = !s.radians
                s.label = if (s.radians) "RADIANS" else "DEGREES"
                s.lastWasOperator = wasOp
            }

            Fn.STO -> {
                commitEntry()
                s.prefix = Prefix.STO
                s.lastWasOperator = wasOp
            }
            Fn.RCL -> {
                commitEntry()
                s.prefix = Prefix.RCL
                s.lastWasOperator = wasOp
            }
            Fn.TO_M -> {
                commitEntry()
                s.m = s.x
                s.label = "M"
            }
            Fn.RM -> {
                commitEntry()
                setX(s.m)
                s.label = "M"
            }
            Fn.M_PLUS -> {
                commitEntry()
                s.m = checked(s.m + s.x)
                s.label = "M+"
            }

            Fn.N, Fn.IYR, Fn.PV, Fn.PMT, Fn.FV -> tvmKey(fn)
            Fn.XPYR -> {
                commitEntry()
                s.n = checked(s.x * s.pyr)
                registerResult(s.n, "N", Context.TVM)
            }
            Fn.PYR -> {
                if (takeFresh()) {
                    if (s.x < 1 || s.x > 999) throw CalcError(Err.PYR)
                    s.pyr = s.x
                    s.xFresh = false
                    s.label = "P/YR"
                } else {
                    registerResult(s.pyr, "P/YR", Context.TVM)
                }
                s.context = Context.TVM
            }
            Fn.NOM -> {
                if (takeFresh()) {
                    s.iyr = s.x
                    s.xFresh = false
                    s.label = "NOM%"
                } else {
                    s.iyr = Tvm.nominalFromEffective(s.eff, s.pyr)
                    registerResult(s.iyr, "NOM%", Context.TVM)
                    addHistory("NOM%", s.iyr, "EFF%" to fmt(s.eff), "P/YR" to fmtPlain(s.pyr))
                }
                s.context = Context.TVM
            }
            Fn.EFF -> {
                if (takeFresh()) {
                    s.eff = s.x
                    s.xFresh = false
                    s.label = "EFF%"
                } else {
                    s.eff = Tvm.effectiveFromNominal(s.iyr, s.pyr)
                    registerResult(s.eff, "EFF%", Context.TVM)
                    addHistory("EFF%", s.eff, "NOM%" to fmt(s.iyr), "P/YR" to fmtPlain(s.pyr))
                }
                s.context = Context.TVM
            }
            Fn.BEG_END -> {
                s.begin = !s.begin
                s.label = if (s.begin) "BEGIN MODE" else "END MODE"
                s.lastWasOperator = wasOp
            }
            Fn.AMORT -> return amortization()

            Fn.MU, Fn.CST, Fn.PRC, Fn.MAR -> marginKey(fn)

            Fn.CFJ -> cashFlowEntry()
            Fn.NJ -> cashFlowCount()
            Fn.NPV -> {
                commitEntry()
                val v = CashFlows.npv(s.flows, s.iyr / s.pyr / 100.0)
                setX(v)
                s.label = "NPV"
                s.context = Context.CASH_FLOW
                addHistory("NPV", v, "I/YR" to fmt(s.iyr), "Flows" to s.flows.size.toString())
            }
            Fn.IRR -> {
                commitEntry()
                val v = CashFlows.irr(s.flows) * s.pyr * 100.0
                s.iyr = v
                setX(v)
                s.label = "IRR/YR"
                s.context = Context.CASH_FLOW
                addHistory("IRR/YR", v, "Flows" to s.flows.size.toString(), "P/YR" to fmtPlain(s.pyr))
            }

            Fn.SIGMA_PLUS -> statEntry(1.0)
            Fn.SIGMA_MINUS -> statEntry(-1.0)
            Fn.CLR_STAT -> {
                clearStats()
                s.label = "STATS CLEARED"
            }
            Fn.MEAN -> statPair(s.sums.meanX, s.sums.meanY, "x̄", "ȳ")
            Fn.SDEV -> statPair(s.sums.sdX(true), s.sums.sdY(true), "Sx", "Sy")
            Fn.PSDEV -> statPair(s.sums.sdX(false), s.sums.sdY(false), "σx", "σy")
            Fn.EST_X -> {
                commitEntry()
                statPair(s.sums.estimateX(s.x), s.sums.correlation, "x̂", "r")
            }
            Fn.EST_Y -> {
                commitEntry()
                statPair(s.sums.estimateY(s.x), s.sums.slope, "ŷ", "m")
            }
            Fn.WMEAN -> statPair(s.sums.weightedMean, runCatching { s.sums.intercept }.getOrDefault(0.0), "x̄w", "b")
            Fn.SUM_N -> statRecall(s.sums.n, "n")
            Fn.SUM_X -> statRecall(s.sums.sx, "Σx")
            Fn.SUM_Y -> statRecall(s.sums.sy, "Σy")
            Fn.SUM_XX -> statRecall(s.sums.sxx, "Σx²")
            Fn.SUM_YY -> statRecall(s.sums.syy, "Σy²")
            Fn.SUM_XY -> statRecall(s.sums.sxy, "Σxy")

            Fn.DATE -> dateAdd()
            Fn.DDAYS -> dateDiff()
            Fn.DMY_MDY -> {
                s.dmy = !s.dmy
                s.label = if (s.dmy) "DATES: DD.MMYYYY" else "DATES: MM.DDYYYY"
                s.lastWasOperator = wasOp
            }
            Fn.CAL_360 -> {
                s.cal360 = !s.cal360
                s.label = if (s.cal360) "30/360 CALENDAR" else "ACTUAL CALENDAR"
                s.lastWasOperator = wasOp
            }
            Fn.SEMI_ANN -> {
                s.semiAnnual = !s.semiAnnual
                s.label = if (s.semiAnnual) "SEMIANNUAL COUPONS" else "ANNUAL COUPONS"
                s.lastWasOperator = wasOp
            }

            Fn.SETDATE -> dateRegister("SETTLEMENT", { s.settleDay }, { s.settleDay = it })
            Fn.MATDATE -> dateRegister("MATURITY", { s.maturityDay }, { s.maturityDay = it })
            Fn.CPN -> simpleRegister("CPN%", Context.BOND, { s.cpn }, { s.cpn = it })
            Fn.CALL -> simpleRegister("CALL", Context.BOND, { s.call }, { s.call = it })
            Fn.YTM -> bondYield()
            Fn.PRICE -> bondPrice()
            Fn.ACCINT -> {
                commitEntry()
                s.accInt = Bond.accrued(bondParams())
                registerResult(s.accInt, "ACCRUED INTEREST", Context.BOND)
            }

            Fn.UNITS, Fn.SP, Fn.VC, Fn.FC, Fn.PROFIT -> breakEvenKey(fn)
            Fn.SL, Fn.SOYD, Fn.DB -> depreciation(fn)

            Fn.DISP -> {
                s.prefix = Prefix.DISP
                s.lastWasOperator = wasOp
            }
            Fn.RADIX -> {
                s.commaRadix = !s.commaRadix
                s.lastWasOperator = wasOp
            }
            Fn.MUTE -> {
                s.mute = !s.mute
                s.label = if (s.mute) "KEY SOUND OFF" else "KEY SOUND ON"
                s.lastWasOperator = wasOp
            }
            Fn.ALG_CHAIN -> {
                s.chainMode = !s.chainMode
                s.label = if (s.chainMode) "CHAIN MODE" else "ALGEBRAIC MODE"
                s.lastWasOperator = wasOp
            }
            Fn.HELP -> {
                s.lastWasOperator = wasOp
                return Effect.OpenHelp
            }
        }
        return null
    }

    // ---------------------------------------------------------------- number entry

    private fun beginEntry() {
        if (s.entry == null) {
            s.entry = ""
            s.entryExp = null
            s.typedText = null
            dropStaleOperand()
        }
    }

    private fun digit(c: Char) {
        beginEntry()
        val exp = s.entryExp
        if (exp != null) {
            if (exp.count { it.isDigit() } < 3) s.entryExp = exp + c
            return
        }
        val e = s.entry!!
        if (e.count { it.isDigit() } >= NumFmt.MAX_DIGITS) return
        s.entry = when (e) {
            "0" -> c.toString()
            "-0" -> "-$c"
            else -> e + c
        }
    }

    private fun dot() {
        beginEntry()
        val e = s.entry!!
        if (s.entryExp != null || '.' in e) return
        s.entry = if (e.isEmpty() || e == "-") e + "0." else "$e."
    }

    private fun exponent() {
        beginEntry()
        if (s.entryExp != null) return
        val e = s.entry!!
        if (e.isEmpty() || e == "-") s.entry = e + "1"
        s.entryExp = ""
    }

    private fun changeSign() {
        val e = s.entry
        val exp = s.entryExp
        when {
            e == null -> setX(-s.x + 0.0)
            exp != null -> s.entryExp = if (exp.startsWith("-")) exp.substring(1) else "-$exp"
            else -> s.entry = if (e.startsWith("-")) e.substring(1) else "-$e"
        }
    }

    private fun backspace() {
        val e = s.entry
        val exp = s.entryExp
        when {
            e == null -> setX(0.0)
            exp != null -> s.entryExp = if (exp.isEmpty()) null else exp.dropLast(1)
            else -> {
                val shorter = e.dropLast(1)
                if (shorter.isEmpty() || shorter == "-") {
                    s.entry = null
                    setX(0.0)
                } else {
                    s.entry = shorter
                }
            }
        }
    }

    private fun parseEntry(): Double {
        var mant = s.entry ?: return s.x
        if (mant.isEmpty() || mant == "-") mant += "0"
        if (mant.endsWith(".")) mant += "0"
        val exp = s.entryExp
        val text = if (exp == null || exp.none { it.isDigit() }) mant else mant + "E" + exp
        return text.toDouble()
    }

    private fun commitEntry() {
        val e = s.entry ?: return
        val v = parseEntry()
        val exp = s.entryExp?.takeIf { x -> x.any { it.isDigit() } }
        s.typedText = NumFmt.plainEntry(e.trimEnd('.').ifEmpty { "0" }, exp, s.commaRadix)
        s.x = v
        s.entry = null
        s.entryExp = null
        s.xFresh = true
    }

    /** Commits any number being typed and reports whether the display holds a new value to store. */
    private fun takeFresh(): Boolean {
        commitEntry()
        return s.xFresh
    }

    private fun checked(v: Double): Double {
        if (v.isNaN()) throw CalcError(Err.INVALID)
        if (v.isInfinite()) throw CalcError(Err.OVERFLOW)
        return v
    }

    private fun setX(v: Double, fresh: Boolean = true) {
        checked(v)
        dropStaleOperand()
        s.x = v
        s.typedText = null
        s.entry = null
        s.entryExp = null
        s.xFresh = fresh
        s.pairReady = false
    }

    private fun setPair(x: Double, y: Double, label: String, label2: String) {
        checked(y)
        setX(x)
        s.y = y
        s.pairReady = true
        s.label = label
        s.label2 = label2
    }

    /** Shows the value of a store-or-solve register; the next register key will then solve. */
    private fun registerResult(v: Double, label: String, context: Context) {
        setX(v, fresh = false)
        s.label = label
        s.context = context
    }

    private fun clear() {
        s.entry = null
        s.entryExp = null
        resetArithmetic()
        s.x = 0.0
        s.typedText = null
        s.xFresh = true
        s.pairReady = false
        s.hasInput = false
        s.inv = false
        s.hyp = false
    }

    private fun clearAll() {
        val old = s
        s = CalcState().apply {
            decimals = old.decimals
            commaRadix = old.commaRadix
            chainMode = old.chainMode
            radians = old.radians
            dmy = old.dmy
            mute = old.mute
            pyr = old.pyr
            begin = old.begin
            semiAnnual = old.semiAnnual
            cal360 = old.cal360
            history += old.history
        }
        s.label = "ALL CLEAR  ·  P/YR ${fmtPlain(s.pyr)}"
    }

    // ---------------------------------------------------------------- arithmetic

    private fun precedence(op: Char) = when (op) {
        '+', '-' -> 1
        '*', '/' -> 2
        '^' -> 3
        else -> 0
    }

    private fun symbol(op: Char) = when (op) {
        '+' -> "+"
        '-' -> "−"
        '*' -> "×"
        '/' -> "÷"
        '^' -> "^"
        else -> ""
    }

    private fun binary(a: Double, op: Char, b: Double): Double = checked(
        when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (b == 0.0) throw CalcError(Err.DIV0) else a / b
            '^' -> a.pow(b)
            else -> b
        },
    )

    private fun operandText(): String = s.typedText ?: fmt(s.x)

    /**
     * The expression line can end with a finished operand (after `%` or `)`). If a new
     * value then replaces that operand, the tokens describing it are stale and are dropped.
     */
    private fun dropStaleOperand() {
        if (!s.exprNeedsOperand) {
            while (s.tokens.size > s.exprOperandStart) s.tokens.removeAt(s.tokens.size - 1)
            s.exprNeedsOperand = true
        }
    }

    private fun resetExpression() {
        s.tokens.clear()
        s.parenTokenIdx.clear()
        s.exprNeedsOperand = true
        s.exprOperandStart = 0
    }

    private fun resetArithmetic() {
        s.pending.clear()
        s.lastWasOperator = false
        resetExpression()
    }

    private fun popPending(): Pending = s.pending.removeAt(s.pending.size - 1)

    private fun operator(op: Char, wasOp: Boolean) {
        commitEntry()
        val top = s.pending.lastOrNull()
        if (wasOp && top != null && top.op != '(') {
            // Two operators in a row: the second replaces the first.
            popPending()
            if (s.tokens.isNotEmpty()) s.tokens.removeAt(s.tokens.size - 1)
        } else if (s.exprNeedsOperand) {
            s.tokens += operandText()
        }
        s.tokens += symbol(op)
        s.exprNeedsOperand = true
        s.kOp = null
        var acc = s.x
        try {
            while (true) {
                val t = s.pending.lastOrNull() ?: break
                if (t.op == '(') break
                if (!s.chainMode && precedence(t.op) < precedence(op)) break
                popPending()
                acc = binary(t.value, t.op, acc)
            }
        } catch (e: CalcError) {
            resetArithmetic()
            throw e
        }
        setX(acc)
        s.pending += Pending(acc, op)
        s.lastWasOperator = true
    }

    private fun equals() {
        commitEntry()
        if (s.pending.isEmpty()) {
            val op = s.kOp
            if (op != null) {
                val a = s.x
                val b = when {
                    !s.kPercent -> s.kValue
                    op == '+' || op == '-' -> a * s.kValue / 100.0
                    else -> s.kValue / 100.0
                }
                val expr = "${operandText()} ${symbol(op)} ${fmtPlain(s.kValue)}${if (s.kPercent) "%" else ""} ="
                val r = binary(a, op, b)
                resetExpression()
                setX(r)
                addHistory(expr, r)
            } else {
                resetExpression()
            }
            return
        }
        if (s.exprNeedsOperand) s.tokens += operandText()
        s.exprNeedsOperand = true
        var acc = s.x
        try {
            while (s.pending.isNotEmpty()) {
                val t = popPending()
                if (t.op == '(') s.tokens += ")" else acc = binary(t.value, t.op, acc)
            }
        } catch (e: CalcError) {
            resetArithmetic()
            throw e
        }
        val expr = expressionText() + " ="
        val worthKeeping = s.tokens.size >= 3
        resetExpression()
        setX(acc)
        if (worthKeeping) addHistory(expr, acc)
    }

    private fun openParen() {
        commitEntry()
        dropStaleOperand()
        s.parenTokenIdx += s.tokens.size
        s.tokens += "("
        s.pending += Pending(0.0, '(')
        s.exprNeedsOperand = true
    }

    private fun closeParen() {
        commitEntry()
        if (s.pending.none { it.op == '(' }) return
        if (s.exprNeedsOperand) s.tokens += operandText()
        s.tokens += ")"
        s.exprNeedsOperand = true
        var acc = s.x
        try {
            while (true) {
                val t = popPending()
                if (t.op == '(') break
                acc = binary(t.value, t.op, acc)
            }
        } catch (e: CalcError) {
            resetArithmetic()
            throw e
        }
        setX(acc)
        s.exprNeedsOperand = false
        s.exprOperandStart = if (s.parenTokenIdx.isEmpty()) 0 else s.parenTokenIdx.removeAt(s.parenTokenIdx.size - 1)
    }

    /**
     * `%` after + or − turns the number into that percentage of the left operand
     * (200 + 15 % = 230); otherwise it just divides by 100 (200 × 15 % = 30).
     */
    private fun percent() {
        commitEntry()
        val src = s.x
        val top = s.pending.lastOrNull()
        val r = if (top != null && (top.op == '+' || top.op == '-')) top.value * src / 100.0 else src / 100.0
        if (s.exprNeedsOperand) {
            s.exprOperandStart = s.tokens.size
            s.tokens += operandText() + "%"
        } else if (s.tokens.isNotEmpty()) {
            s.tokens[s.tokens.size - 1] = s.tokens.last() + "%"
        }
        s.exprNeedsOperand = true
        setX(r)
        s.exprNeedsOperand = false
        s.pctSource = src
    }

    private fun percentChange() {
        commitEntry()
        val old = s.y
        val new = s.x
        if (old == 0.0) throw CalcError(Err.DIV0)
        val r = (new - old) / old * 100.0
        s.hasInput = false
        setX(r)
        s.label = "%CHG"
        addHistory("%CHG", r, "Old" to fmt(old), "New" to fmt(new))
    }

    /** K keeps the pending operator and right-hand number so `=` can reapply them to new values. */
    private fun constant() {
        commitEntry()
        val top = s.pending.lastOrNull()
        if (top != null && top.op != '(') {
            s.kOp = top.op
            s.kPercent = s.lastFn == Fn.PCT
            s.kValue = if (s.kPercent) s.pctSource else s.x
            s.label = "K  ${symbol(top.op)} ${fmtPlain(s.kValue)}${if (s.kPercent) "%" else ""}"
        }
    }

    private fun swap(label: String?, label2: String?, date: String?, date2: String?) {
        commitEntry()
        val top = s.pending.lastOrNull()
        if (!s.pairReady && top != null && top.op != '(') {
            val left = top.value
            s.pending[s.pending.size - 1] = Pending(s.x, top.op)
            if (s.tokens.size >= 2 && s.exprNeedsOperand) s.tokens[s.tokens.size - 2] = operandText()
            s.x = left
            s.typedText = null
            s.xFresh = true
            return
        }
        val t = s.x
        s.x = s.y
        s.y = t
        s.typedText = null
        s.xFresh = true
        s.label = label2
        s.label2 = label
        s.dateText = date2
        s.dateText2 = date
    }

    private fun unary(f: (Double) -> Double) {
        commitEntry()
        setX(f(s.x))
    }

    private fun factorial(v: Double): Double {
        if (v < 0 || v != floor(v)) throw CalcError(Err.INVALID)
        if (v > 170) throw CalcError(Err.OVERFLOW)
        var r = 1.0
        for (i in 2..v.toInt()) r *= i
        return r
    }

    private fun countArrangements(combinations: Boolean) {
        commitEntry()
        val n = s.y
        val r = s.x
        if (n < 0 || r < 0 || r > n || n != floor(n) || r != floor(r)) throw CalcError(Err.INVALID)
        s.hasInput = false
        val k = if (combinations) minOf(r, n - r) else r
        var result = 1.0
        var i = 0
        while (i < k) {
            result *= (n - i)
            if (combinations) result /= (i + 1)
            i++
        }
        setX(if (combinations) result.roundToLong().toDouble().takeIf { result < 9e15 } ?: result else result)
        s.label = if (combinations) "nCr" else "nPr"
    }

    private fun trig(fn: Fn, inv: Boolean, hyp: Boolean) {
        commitEntry()
        val v = s.x
        val r = when {
            hyp && inv -> when (fn) {
                Fn.SIN -> asinh(v)
                Fn.COS -> if (v < 1.0) throw CalcError(Err.INVALID) else acosh(v)
                else -> if (abs(v) >= 1.0) throw CalcError(Err.INVALID) else atanh(v)
            }
            hyp -> when (fn) {
                Fn.SIN -> sinh(v)
                Fn.COS -> cosh(v)
                else -> tanh(v)
            }
            inv -> {
                val rad = when (fn) {
                    Fn.SIN -> if (abs(v) > 1.0) throw CalcError(Err.INVALID) else asin(v)
                    Fn.COS -> if (abs(v) > 1.0) throw CalcError(Err.INVALID) else acos(v)
                    else -> atan(v)
                }
                if (s.radians) rad else Math.toDegrees(rad)
            }
            s.radians -> when (fn) {
                Fn.SIN -> sin(v)
                Fn.COS -> cos(v)
                else -> tan(v)
            }
            else -> trigDegrees(fn, v)
        }
        setX(r)
        s.label = buildString {
            if (inv) append("A")
            append(fn.name)
            if (hyp) append("H")
        }
    }

    /** Degree-mode trig that returns exact values at multiples of 90°, where floating point would not. */
    private fun trigDegrees(fn: Fn, deg: Double): Double {
        val a = ((deg % 360.0) + 360.0) % 360.0
        val quarter = a / 90.0
        if (quarter == floor(quarter)) {
            val q = quarter.toInt() % 4
            return when (fn) {
                Fn.SIN -> listOf(0.0, 1.0, 0.0, -1.0)[q]
                Fn.COS -> listOf(1.0, 0.0, -1.0, 0.0)[q]
                else -> if (q % 2 == 1) throw CalcError(Err.INVALID) else 0.0
            }
        }
        val rad = Math.toRadians(a)
        return when (fn) {
            Fn.SIN -> sin(rad)
            Fn.COS -> cos(rad)
            else -> tan(rad)
        }
    }

    // ---------------------------------------------------------------- STO / RCL / DISP prefixes

    private fun handleStore(fn: Fn, dotted: Boolean): Boolean {
        if (fn.ordinal <= Fn.D9.ordinal) {
            val index = fn.ordinal + if (dotted) 10 else 0
            s.reg[index] = s.x
            s.label = "STO R$index"
            return true
        }
        if (fn == Fn.DOT && !dotted) {
            s.prefix = Prefix.STO_DOT
            return true
        }
        return false
    }

    private fun handleRecall(fn: Fn, dotted: Boolean): Boolean {
        if (fn.ordinal <= Fn.D9.ordinal) {
            val index = fn.ordinal + if (dotted) 10 else 0
            recall(s.reg[index], "R$index")
            return true
        }
        if (fn == Fn.DOT && !dotted) {
            s.prefix = Prefix.RCL_DOT
            return true
        }
        if (dotted) return false
        when (fn) {
            Fn.N -> recall(s.n, "N")
            Fn.IYR, Fn.NOM -> recall(s.iyr, "I/YR")
            Fn.PV -> recall(s.pv, "PV")
            Fn.PMT -> recall(s.pmt, "PMT")
            Fn.FV -> recall(s.fv, "FV")
            Fn.PYR -> recall(s.pyr, "P/YR")
            Fn.XPYR -> recall(s.n / s.pyr, "N ÷ P/YR")
            Fn.EFF -> recall(s.eff, "EFF%")
            Fn.MU -> recall(s.mu, "MU")
            Fn.CST -> recall(s.cst, "CST")
            Fn.PRC -> recall(s.prc, "PRC")
            Fn.MAR -> recall(s.mar, "MAR")
            Fn.UNITS -> recall(s.units, "UNITS")
            Fn.SP -> recall(s.sp, "SP")
            Fn.VC -> recall(s.vc, "VC")
            Fn.FC -> recall(s.fc, "FC")
            Fn.PROFIT -> recall(s.profit, "PROFIT")
            Fn.CPN -> recall(s.cpn, "CPN%")
            Fn.CALL -> recall(s.call, "CALL")
            Fn.YTM -> recall(s.ytm, "YTM")
            Fn.PRICE -> recall(s.price, "PRICE")
            Fn.ACCINT -> recall(s.accInt, "ACCRUED INTEREST")
            Fn.SETDATE -> showDate(CalDate.fromEpochDay(s.settleDay ?: throw CalcError(Err.NO_DATE)), "SETTLEMENT")
            Fn.MATDATE -> showDate(CalDate.fromEpochDay(s.maturityDay ?: throw CalcError(Err.NO_DATE)), "MATURITY")
            Fn.TO_M, Fn.RM, Fn.M_PLUS -> recall(s.m, "M")
            Fn.K -> recall(s.kValue, "K")
            Fn.SIGMA_PLUS -> recall(s.sums.n, "n")
            Fn.CFJ -> {
                if (s.flows.isEmpty()) throw CalcError(Err.NO_FLOWS)
                val j = s.flowCursor.coerceIn(0, s.flows.size - 1)
                recall(s.flows[j].amount, "CF $j")
                s.y = s.flows[j].count.toDouble()
                s.pairReady = true
                s.label2 = "N $j"
                s.flowCursor = (j + 1) % s.flows.size
                s.context = Context.CASH_FLOW
            }
            Fn.NJ -> {
                if (s.flows.isEmpty()) throw CalcError(Err.NO_FLOWS)
                val j = (s.flowCursor - 1 + s.flows.size) % s.flows.size
                recall(s.flows[j].count.toDouble(), "N $j")
            }
            else -> return false
        }
        return true
    }

    private fun recall(v: Double, label: String) {
        setX(v)
        s.label = label
    }

    private fun handleDisp(fn: Fn): Boolean {
        when {
            fn.ordinal <= Fn.D9.ordinal -> s.decimals = fn.ordinal
            fn == Fn.DOT -> s.decimals = NumFmt.FLOAT
            fn == Fn.EQ -> s.showFull = true
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- time value of money

    private fun tvmParams() = Tvm.Params(s.n, s.iyr, s.pv, s.pmt, s.fv, s.pyr, s.begin)

    private fun tvmKey(fn: Fn) {
        val name = when (fn) {
            Fn.N -> "N"
            Fn.IYR -> "I/YR"
            Fn.PV -> "PV"
            Fn.PMT -> "PMT"
            else -> "FV"
        }
        if (takeFresh()) {
            when (fn) {
                Fn.N -> s.n = s.x
                Fn.IYR -> s.iyr = s.x
                Fn.PV -> s.pv = s.x
                Fn.PMT -> s.pmt = s.x
                else -> s.fv = s.x
            }
            s.xFresh = false
            s.label = name
            s.context = Context.TVM
            return
        }
        val p = tvmParams()
        val v = when (fn) {
            Fn.N -> Tvm.solveN(p).also { s.n = it }
            Fn.IYR -> Tvm.solveIyr(p).also { s.iyr = it }
            Fn.PV -> Tvm.solvePv(p).also { s.pv = it }
            Fn.PMT -> Tvm.solvePmt(p).also { s.pmt = it }
            else -> Tvm.solveFv(p).also { s.fv = it }
        }
        registerResult(v, name, Context.TVM)
        addHistory(
            name, v,
            "N" to fmt(s.n), "I/YR" to fmt(s.iyr), "PV" to fmt(s.pv), "PMT" to fmt(s.pmt), "FV" to fmt(s.fv),
            "P/YR" to fmtPlain(s.pyr), "Mode" to if (s.begin) "Begin" else "End",
        )
    }

    private fun amortization(): Effect {
        commitEntry()
        val last = ceil(s.n).toInt().coerceIn(1, Amortization.MAX_PERIODS)
        var from = 1
        var to = last
        if (s.hasInput) {
            s.hasInput = false
            if (s.y < 1 || s.x < s.y || s.x > Amortization.MAX_PERIODS) throw CalcError(Err.INVALID)
            from = s.y.toInt()
            to = s.x.toInt()
        }
        s.context = Context.TVM
        return Effect.OpenAmortization(from, to)
    }

    fun amortize(from: Int, to: Int): AmortSchedule = Amortization.schedule(tvmParams(), from, to, s.decimals)

    // ---------------------------------------------------------------- margin and markup

    private fun marginKey(fn: Fn) {
        val name = fn.name
        if (takeFresh()) {
            when (fn) {
                Fn.CST -> s.cst = s.x
                Fn.PRC -> s.prc = s.x
                Fn.MAR -> {
                    s.mar = s.x
                    s.lastPctIsMarkup = false
                }
                else -> {
                    s.mu = s.x
                    s.lastPctIsMarkup = true
                }
            }
            s.xFresh = false
            s.label = name
            s.context = Context.MARGIN
            return
        }
        val v = when (fn) {
            Fn.CST -> if (s.lastPctIsMarkup) div(s.prc, 1 + s.mu / 100) else s.prc * (1 - s.mar / 100)
            Fn.PRC -> if (s.lastPctIsMarkup) s.cst * (1 + s.mu / 100) else div(s.cst, 1 - s.mar / 100)
            Fn.MAR -> div(s.prc - s.cst, s.prc) * 100
            else -> div(s.prc - s.cst, s.cst) * 100
        }
        checked(v)
        when (fn) {
            Fn.CST -> s.cst = v
            Fn.PRC -> s.prc = v
            Fn.MAR -> s.mar = v
            else -> s.mu = v
        }
        // Keep the other percentage in step so the strip never shows a contradictory pair.
        if (s.prc != 0.0 && s.cst != 0.0) {
            if (fn != Fn.MAR) s.mar = (s.prc - s.cst) / s.prc * 100
            if (fn != Fn.MU) s.mu = (s.prc - s.cst) / s.cst * 100
        }
        registerResult(v, name, Context.MARGIN)
        addHistory(name, v, "CST" to fmt(s.cst), "PRC" to fmt(s.prc), "MAR" to fmt(s.mar), "MU" to fmt(s.mu))
    }

    private fun div(a: Double, b: Double): Double = if (b == 0.0) throw CalcError(Err.DIV0) else a / b

    // ---------------------------------------------------------------- cash flows

    private fun flowCount(v: Double): Int {
        if (v < 1 || v > CashFlows.MAX_COUNT || v != floor(v)) throw CalcError(Err.INVALID)
        return v.toInt()
    }

    private fun cashFlowEntry() {
        commitEntry()
        if (s.flows.size >= CashFlows.MAX_FLOWS) throw CalcError(Err.FULL)
        val amount = if (s.hasInput) s.y else s.x
        val count = if (s.hasInput) flowCount(s.x) else 1
        s.hasInput = false
        s.flows += CashFlow(amount, count)
        s.flowCursor = 0
        val j = s.flows.size - 1
        setX(amount)
        s.label = if (count > 1) "CF $j  ×$count" else "CF $j"
        s.context = Context.CASH_FLOW
    }

    private fun cashFlowCount() {
        commitEntry()
        if (s.flows.isEmpty()) throw CalcError(Err.NO_FLOWS)
        val j = s.flows.size - 1
        s.flows[j] = s.flows[j].copy(count = flowCount(s.x))
        s.label = "N $j"
        s.context = Context.CASH_FLOW
    }

    // ---------------------------------------------------------------- statistics

    private fun statEntry(sign: Double) {
        commitEntry()
        val xv = if (s.hasInput) s.y else s.x
        val yv = if (s.hasInput) s.x else 0.0
        s.hasInput = false
        if (sign > 0) {
            if (s.statData.size >= MAX_STAT_POINTS) throw CalcError(Err.FULL)
            s.statData += StatPoint(xv, yv)
        } else {
            if (s.sums.n < 1) throw CalcError(Err.NO_DATA)
            val i = s.statData.indexOfLast { it.x == xv && it.y == yv }
            if (i >= 0) s.statData.removeAt(i)
        }
        s.sums = s.sums.add(xv, yv, sign)
        setX(s.sums.n)
        s.label = "n"
        s.context = Context.STATS
    }

    private fun statPair(a: Double, b: Double, label: String, label2: String) {
        commitEntry()
        setPair(a, b, label, label2)
        s.context = Context.STATS
    }

    private fun statRecall(v: Double, label: String) {
        commitEntry()
        setX(v)
        s.label = label
        s.context = Context.STATS
    }

    // ---------------------------------------------------------------- dates and bonds

    private fun showDate(d: CalDate, label: String) {
        setX(Dates.encode(d, s.dmy), fresh = false)
        s.dateText = Dates.format(d, s.dmy)
        s.label = "$label  ·  ${d.weekdayName}"
    }

    private fun dateAdd() {
        commitEntry()
        val start = Dates.parse(s.y, s.dmy) ?: throw CalcError(Err.DATE)
        val days = s.x
        if (days != floor(days) || abs(days) > 4_000_000) throw CalcError(Err.INVALID)
        val result = start.plusDays(days.toLong())
        if (!CalDate.isValid(result.year, result.month, result.day)) throw CalcError(Err.DATE)
        s.hasInput = false
        showDate(result, "DATE")
        s.xFresh = true
        addHistory(
            "DATE", s.x, "From" to Dates.format(start, s.dmy), "Days" to fmtPlain(days),
            resultText = Dates.format(result, s.dmy),
        )
    }

    private fun dateDiff() {
        commitEntry()
        val a = Dates.parse(s.y, s.dmy) ?: throw CalcError(Err.DATE)
        val b = Dates.parse(s.x, s.dmy) ?: throw CalcError(Err.DATE)
        val actual = Dates.daysActual(a, b).toDouble()
        val d360 = Dates.days360(a, b).toDouble()
        s.hasInput = false
        if (s.cal360) setPair(d360, actual, "DAYS (30/360)", "DAYS (ACTUAL)") else setPair(actual, d360, "DAYS (ACTUAL)", "DAYS (30/360)")
        addHistory(
            "ΔDAYS", s.x, "From" to Dates.format(a, s.dmy), "To" to Dates.format(b, s.dmy),
            "Actual" to fmtPlain(actual), "30/360" to fmtPlain(d360),
        )
    }

    private fun dateRegister(label: String, get: () -> Long?, set: (Long) -> Unit) {
        if (takeFresh()) {
            val d = Dates.parse(s.x, s.dmy) ?: throw CalcError(Err.DATE)
            set(d.toEpochDay())
            showDate(d, label)
        } else {
            showDate(CalDate.fromEpochDay(get() ?: throw CalcError(Err.NO_DATE)), label)
        }
        s.context = Context.BOND
    }

    private fun simpleRegister(label: String, context: Context, get: () -> Double, set: (Double) -> Unit) {
        if (takeFresh()) {
            set(s.x)
            s.xFresh = false
            s.label = label
            s.context = context
        } else {
            registerResult(get(), label, context)
        }
    }

    private fun bondParams(): Bond.Params {
        val settle = s.settleDay ?: throw CalcError(Err.NO_DATE)
        val maturity = s.maturityDay ?: throw CalcError(Err.NO_DATE)
        return Bond.Params(
            CalDate.fromEpochDay(settle), CalDate.fromEpochDay(maturity), s.cpn, s.call, s.semiAnnual, !s.cal360,
        )
    }

    private fun bondHistory(title: String, v: Double) {
        val p = bondParams()
        addHistory(
            title, v,
            "Settle" to Dates.format(p.settlement, s.dmy), "Mature" to Dates.format(p.maturity, s.dmy),
            "CPN%" to fmt(s.cpn), "CALL" to fmt(s.call), "YTM" to fmt(s.ytm), "PRICE" to fmt(s.price),
            "Accrued" to fmt(s.accInt),
            "Basis" to (if (s.semiAnnual) "Semi" else "Annual") + (if (s.cal360) " 30/360" else " Act/Act"),
        )
    }

    private fun bondPrice() {
        if (takeFresh()) {
            s.price = s.x
            s.xFresh = false
            s.label = "PRICE"
            s.context = Context.BOND
            return
        }
        val p = bondParams()
        s.price = checked(Bond.price(p, s.ytm))
        s.accInt = Bond.accrued(p)
        registerResult(s.price, "PRICE", Context.BOND)
        bondHistory("PRICE", s.price)
    }

    private fun bondYield() {
        if (takeFresh()) {
            s.ytm = s.x
            s.xFresh = false
            s.label = "YTM"
            s.context = Context.BOND
            return
        }
        val p = bondParams()
        s.ytm = checked(Bond.yieldFor(p, s.price))
        s.accInt = Bond.accrued(p)
        registerResult(s.ytm, "YTM", Context.BOND)
        bondHistory("YTM", s.ytm)
    }

    // ---------------------------------------------------------------- break-even and depreciation

    private fun breakEvenKey(fn: Fn) {
        val name = fn.name
        if (takeFresh()) {
            when (fn) {
                Fn.UNITS -> s.units = s.x
                Fn.SP -> s.sp = s.x
                Fn.VC -> s.vc = s.x
                Fn.FC -> s.fc = s.x
                else -> s.profit = s.x
            }
            s.xFresh = false
            s.label = name
            s.context = Context.BREAK_EVEN
            return
        }
        val v = checked(
            when (fn) {
                Fn.UNITS -> BreakEven.units(s.sp, s.vc, s.fc, s.profit)
                Fn.SP -> BreakEven.price(s.units, s.vc, s.fc, s.profit)
                Fn.VC -> BreakEven.variableCost(s.units, s.sp, s.fc, s.profit)
                Fn.FC -> BreakEven.fixedCost(s.units, s.sp, s.vc, s.profit)
                else -> BreakEven.profit(s.units, s.sp, s.vc, s.fc)
            },
        )
        when (fn) {
            Fn.UNITS -> s.units = v
            Fn.SP -> s.sp = v
            Fn.VC -> s.vc = v
            Fn.FC -> s.fc = v
            else -> s.profit = v
        }
        registerResult(v, name, Context.BREAK_EVEN)
        addHistory(
            name, v,
            "UNITS" to fmt(s.units), "SP" to fmt(s.sp), "VC" to fmt(s.vc), "FC" to fmt(s.fc), "PROFIT" to fmt(s.profit),
        )
    }

    /** Depreciation for the year in the display, using N = life, PV = cost, FV = salvage, I/YR = DB factor. */
    private fun depreciation(fn: Fn) {
        commitEntry()
        val year = s.x
        val r = when (fn) {
            Fn.SL -> Depreciation.straightLine(s.pv, s.fv, s.n, year)
            Fn.SOYD -> Depreciation.sumOfYears(s.pv, s.fv, s.n, year)
            else -> Depreciation.decliningBalance(s.pv, s.fv, s.n, s.iyr, year)
        }
        val name = fn.name
        setPair(r.depreciation, r.remaining, "$name  ·  YEAR ${fmtPlain(year)}", "REMAINING VALUE")
        val fields = mutableListOf(
            "Year" to fmtPlain(year), "Cost" to fmt(s.pv), "Salvage" to fmt(s.fv), "Life" to fmtPlain(s.n),
        )
        if (fn == Fn.DB) fields += "Factor" to fmtPlain(s.iyr) + "%"
        fields += "Remaining" to fmt(r.remaining)
        addHistory("$name depreciation", r.depreciation, *fields.toTypedArray())
    }

    // ---------------------------------------------------------------- history and helpers for the UI

    fun fmt(v: Double): String = NumFmt.format(v, s.decimals, s.commaRadix)

    /** Formats without padding zeros: 12 rather than 12.00. Used for counts, years and typed constants. */
    private fun fmtPlain(v: Double): String = NumFmt.format(v, NumFmt.FLOAT, s.commaRadix)

    private fun addHistory(
        title: String,
        value: Double,
        vararg fields: Pair<String, String>,
        resultText: String = fmt(value),
    ) {
        s.history += HistoryEntry(clock(), title, resultText, value, fields.map { HistoryField(it.first, it.second) })
        while (s.history.size > MAX_HISTORY) s.history.removeAt(0)
    }

    val displayText: String
        get() {
            s.error?.let { return it }
            s.entry?.let { return NumFmt.groupEntry(it, s.entryExp, s.commaRadix) }
            s.dateText?.let { return it }
            return if (s.showFull) fmtPlain(s.x) else fmt(s.x)
        }

    val isError: Boolean get() = s.error != null

    /** The calculation in progress, e.g. "( 2 + 3 ) ×". */
    fun expressionText(): String = s.tokens.joinToString(" ").replace("( ", "(").replace(" )", ")")

    fun strip(): Strip = when (s.context) {
        Context.TVM -> Strip(
            s.context, "TVM",
            listOf("N" to fmt(s.n), "I/YR" to fmt(s.iyr), "PV" to fmt(s.pv), "PMT" to fmt(s.pmt), "FV" to fmt(s.fv)),
        )
        Context.BOND -> Strip(
            s.context, "BOND",
            listOf(
                "ACCR" to fmt(s.accInt), "YTM" to fmt(s.ytm), "PRICE" to fmt(s.price),
                "CPN%" to fmt(s.cpn), "CALL" to fmt(s.call),
            ),
            note = dayText(s.settleDay) + "  →  " + dayText(s.maturityDay),
        )
        Context.BREAK_EVEN -> Strip(
            s.context, "BREAK-EVEN",
            listOf(
                "UNITS" to fmt(s.units), "SP" to fmt(s.sp), "VC" to fmt(s.vc),
                "FC" to fmt(s.fc), "PROFIT" to fmt(s.profit),
            ),
        )
        Context.MARGIN -> Strip(
            s.context, "MARGIN",
            listOf("MU%" to fmt(s.mu), "CST" to fmt(s.cst), "PRC" to fmt(s.prc), "MAR%" to fmt(s.mar)),
        )
        Context.CASH_FLOW -> Strip(
            s.context, "CASH FLOWS",
            listOf(
                "FLOWS" to s.flows.size.toString(),
                "PERIODS" to s.flows.sumOf { it.count }.toString(),
                "TOTAL" to fmt(CashFlows.total(s.flows)),
                "I/YR" to fmt(s.iyr),
            ),
        )
        Context.STATS -> Strip(
            s.context, "STATISTICS",
            listOf(
                "n" to fmtPlain(s.sums.n), "Σx" to fmt(s.sums.sx), "Σy" to fmt(s.sums.sy),
                "x̄" to (if (s.sums.n >= 1) fmt(s.sums.meanX) else "–"),
                "ȳ" to (if (s.sums.n >= 1) fmt(s.sums.meanY) else "–"),
            ),
        )
    }

    private fun dayText(day: Long?): String = if (day == null) "– – –" else Dates.format(CalDate.fromEpochDay(day), s.dmy)

    fun cycleContext() {
        val all = Context.entries
        s.context = all[(s.context.ordinal + 1) % all.size]
    }

    fun clearStats() {
        s.sums = StatSums()
        s.statData.clear()
    }

    fun clearFlows() {
        s.flows.clear()
        s.flowCursor = 0
    }

    fun clearTvm() {
        s.n = 0.0
        s.iyr = 0.0
        s.pv = 0.0
        s.pmt = 0.0
        s.fv = 0.0
        s.eff = 0.0
    }

    fun clearHistory() = s.history.clear()

    fun setFlow(index: Int, flow: CashFlow) {
        if (index == s.flows.size && index < CashFlows.MAX_FLOWS) s.flows += flow else if (index in s.flows.indices) s.flows[index] = flow
    }

    fun removeFlow(index: Int) {
        if (index in s.flows.indices) s.flows.removeAt(index)
        s.flowCursor = 0
    }

    companion object {
        const val MAX_HISTORY = 200
        const val MAX_STAT_POINTS = 2000
    }
}
