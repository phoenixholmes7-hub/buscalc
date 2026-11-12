package com.buscalc.app.engine

import kotlinx.serialization.Serializable

/** A pending left operand and its operator, or an open parenthesis ('('). */
@Serializable
data class Pending(val value: Double, val op: Char)

@Serializable
data class StatPoint(val x: Double, val y: Double)

@Serializable
data class HistoryField(val name: String, val value: String)

@Serializable
data class HistoryEntry(
    val time: Long,
    /** What was calculated, e.g. "3 + 4 =" or "PMT". */
    val title: String,
    val resultText: String,
    val value: Double,
    val fields: List<HistoryField> = emptyList(),
)

enum class Prefix { NONE, STO, STO_DOT, RCL, RCL_DOT, DISP }

/** Which group of registers the strip above the keypad is showing. */
enum class Context { TVM, BOND, BREAK_EVEN, MARGIN, CASH_FLOW, STATS }

/**
 * The calculator's entire memory. It is saved as JSON whenever the app goes to the
 * background, so every field here survives a restart.
 */
@Serializable
class CalcState {
    // Display and number entry
    var x = 0.0
    var y = 0.0
    var entry: String? = null
    var entryExp: String? = null
    var typedText: String? = null
    var xFresh = true
    var pairReady = false
    var hasInput = false
    var label: String? = null
    var label2: String? = null
    var dateText: String? = null
    var dateText2: String? = null
    var showFull = false
    var error: String? = null
    var shift = Shift.NONE
    var prefix = Prefix.NONE
    var inv = false
    var hyp = false
    var lastFn: Fn? = null

    // Arithmetic
    val pending: MutableList<Pending> = mutableListOf()
    var lastWasOperator = false
    var kOp: Char? = null
    var kValue = 0.0
    var kPercent = false
    var pctSource = 0.0
    val tokens: MutableList<String> = mutableListOf()
    val parenTokenIdx: MutableList<Int> = mutableListOf()
    var exprNeedsOperand = true
    var exprOperandStart = 0

    // Settings
    var decimals = 2
    var commaRadix = false
    var chainMode = false
    var radians = false
    var dmy = false
    var mute = false

    // Memory
    var m = 0.0
    val reg: MutableList<Double> = MutableList(20) { 0.0 }

    // Time value of money
    var n = 0.0
    var iyr = 0.0
    var pv = 0.0
    var pmt = 0.0
    var fv = 0.0
    var pyr = 12.0
    var begin = false
    var eff = 0.0

    // Cost, price, margin, markup
    var cst = 0.0
    var prc = 0.0
    var mar = 0.0
    var mu = 0.0
    var lastPctIsMarkup = false

    // Cash flows
    val flows: MutableList<CashFlow> = mutableListOf()
    var flowCursor = 0

    // Statistics
    var sums = StatSums()
    val statData: MutableList<StatPoint> = mutableListOf()

    // Bonds (dates as days since 1970-01-01)
    var settleDay: Long? = null
    var maturityDay: Long? = null
    var cpn = 0.0
    var call = 100.0
    var ytm = 0.0
    var price = 0.0
    var accInt = 0.0
    var semiAnnual = true
    var cal360 = false

    // Break-even
    var units = 0.0
    var sp = 0.0
    var vc = 0.0
    var fc = 0.0
    var profit = 0.0

    var context = Context.TVM
    val history: MutableList<HistoryEntry> = mutableListOf()
}
