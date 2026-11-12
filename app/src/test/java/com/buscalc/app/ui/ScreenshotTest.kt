package com.buscalc.app.ui

import androidx.compose.runtime.Composable
import com.buscalc.app.engine.CalcEngine
import com.buscalc.app.engine.Fn
import com.buscalc.app.engine.Key
import com.buscalc.app.engine.NumFmt
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen off-device and saves it under app/build/screenshots.
 * These double as smoke tests: a screen that crashes while composing fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/$name.png") {
            BusCalcTheme(dark = dark) { content() }
        }
    }

    private fun CalcEngine.type(number: String): CalcEngine {
        for (ch in number) apply(if (ch == '.') Fn.DOT else Fn.entries[ch - '0'])
        return this
    }

    private fun CalcEngine.store(number: String, fn: Fn): CalcEngine {
        type(number)
        apply(fn)
        return this
    }

    private fun loan(): CalcEngine = CalcEngine().apply {
        store("360", Fn.N)
        store("4.75", Fn.IYR)
        store("135000", Fn.PV)
        store("0", Fn.FV)
        apply(Fn.PMT)
    }

    @Composable
    private fun Calculator(e: CalcEngine) = CalculatorScreen(CalcUi.from(e), {}, {}, {})

    @Test
    fun calculator() {
        val e = loan()
        shot("calculator") { Calculator(e) }
        shot("calculator_light", dark = false) { Calculator(e) }
    }

    @Test
    fun calculatorWithShiftAndExpression() {
        val e = loan()
        e.type("1250.5").apply(Fn.ADD)
        e.type("42").apply(Fn.MUL)
        e.type("3")
        e.press(Key.GOLD)
        shot("calculator_shift") { Calculator(e) }
    }

    @Test
    fun calculatorShowingBond() {
        val e = CalcEngine()
        e.store("4.282004", Fn.SETDATE)
        e.store("6.042018", Fn.MATDATE)
        e.store("6.75", Fn.CPN)
        e.store("4.75", Fn.YTM)
        e.apply(Fn.PRICE)
        shot("calculator_bond", dark = false) { Calculator(e) }
    }

    @Test
    fun calculatorShowingAnError() {
        val e = CalcEngine()
        e.type("5").apply(Fn.DIV)
        e.type("0").apply(Fn.EQ)
        shot("calculator_error") { Calculator(e) }
    }

    @Test
    fun amortization() {
        val e = loan()
        val summary = LoanSummary(
            n = "360", iyr = "4.75", pyr = "12",
            pv = e.fmt(e.s.pv), pmt = e.fmt(NumFmt.round(e.s.pmt, 2)), fv = e.fmt(e.s.fv), begin = false,
        )
        shot("amortization") { AmortizationScreen(summary, 1, 360, e::amortize, e::fmt, {}, {}) }
        shot("amortization_light", dark = false) { AmortizationScreen(summary, 1, 360, e::amortize, e::fmt, {}, {}) }
    }

    @Test
    fun cashFlows() {
        val e = CalcEngine()
        e.store("1", Fn.PYR)
        for (amount in listOf("80000", "500")) {
            e.type(amount)
            e.apply(Fn.CHS)
            e.apply(Fn.CFJ)
        }
        for (amount in listOf("4500", "5500", "4500", "130000")) e.store(amount, Fn.CFJ)
        e.store("13", Fn.IYR)
        shot("cash_flows") {
            CashFlowScreen(e.s.flows.toList(), e.s.iyr, e.s.pyr, false, e::fmt, { _, _ -> }, {}, {}, {}, {})
        }
        shot("cash_flows_empty") {
            CashFlowScreen(emptyList(), 0.0, 12.0, false, e::fmt, { _, _ -> }, {}, {}, {}, {})
        }
    }

    @Test
    fun history() {
        val e = loan()
        e.type("3.14159").apply(Fn.ADD)
        e.type("42").apply(Fn.MUL)
        e.type("1337").apply(Fn.EQ)
        e.type("25000").apply(Fn.ADD)
        e.type("8").apply(Fn.PCT)
        e.apply(Fn.EQ)
        e.store("9.60", Fn.CST)
        e.store("15", Fn.PRC)
        e.apply(Fn.MAR)
        shot("history") { HistoryScreen(e.s.history.toList(), {}, {}, {}) }
        shot("history_empty") { HistoryScreen(emptyList(), {}, {}, {}) }
    }

    @Test
    fun registers() {
        val e = loan()
        val groups = listOf(
            RegisterGroup(
                "Loan values",
                listOf(
                    RegisterRow("N", e.fmt(e.s.n), e.s.n), RegisterRow("I/YR", e.fmt(e.s.iyr), e.s.iyr),
                    RegisterRow("PV", e.fmt(e.s.pv), e.s.pv), RegisterRow("PMT", e.fmt(e.s.pmt), e.s.pmt),
                ),
                onClear = {},
            ),
            RegisterGroup("Bond", listOf(RegisterRow("Settlement", "not set"), RegisterRow("Coupon %", "0.00", 0.0))),
        )
        shot("registers") { RegistersScreen(groups, {}, {}) }
    }

    @Test
    fun settings() {
        shot("settings") { SettingsScreen(SettingsUi(version = "1.0"), {}, {}) }
    }

    @Test
    fun help() {
        shot("help") { HelpScreen({}, initiallyOpen = "Find a loan payment") }
        shot("help_light", dark = false) { HelpScreen({}, initiallyOpen = "How the keys work") }
    }
}
