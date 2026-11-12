package com.buscalc.app.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the engine the way a person would: a string of keys separated by spaces.
 * Numbers are typed digit by digit; everything else is a key name from [names].
 */
class CalcEngineTest {

    private val names: Map<String, Fn> = mapOf(
        "+" to Fn.ADD, "-" to Fn.SUB, "*" to Fn.MUL, "/" to Fn.DIV, "^" to Fn.POW, "=" to Fn.EQ,
        "(" to Fn.LPAREN, ")" to Fn.RPAREN, "%" to Fn.PCT, "%CHG" to Fn.PCT_CHG,
        "I/YR" to Fn.IYR, "P/YR" to Fn.PYR, "xP/YR" to Fn.XPYR, "NOM%" to Fn.NOM, "EFF%" to Fn.EFF,
        "BEG/END" to Fn.BEG_END, "CHS" to Fn.CHS, "C" to Fn.CLR, "CALL_C" to Fn.CLR_ALL,
        "S+" to Fn.SIGMA_PLUS, "S-" to Fn.SIGMA_MINUS, "1/x" to Fn.INV_X, "x^2" to Fn.SQUARE,
        "->M" to Fn.TO_M, "M+" to Fn.M_PLUS, "360/ACT" to Fn.CAL_360, "SEMI/ANN" to Fn.SEMI_ANN,
        "DMY/MDY" to Fn.DMY_MDY, "ALG/CHAIN" to Fn.ALG_CHAIN,
    ) + Fn.entries.associateBy { it.name }

    private fun engine() = CalcEngine(clock = { 0L }, random = { 0.25 })

    private fun CalcEngine.keys(sequence: String): CalcEngine {
        for (token in sequence.trim().split(Regex("\\s+"))) {
            val fn = names[token]
            if (fn != null) {
                apply(fn)
                continue
            }
            require(token.all { it.isDigit() || it == '.' }) { "Unknown key: $token" }
            for (c in token) apply(if (c == '.') Fn.DOT else Fn.entries[c - '0'])
        }
        return this
    }

    private fun CalcEngine.shows(expected: String) = assertEquals(expected, displayText)

    private fun near(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertEquals(expected, actual, tolerance)

    // ------------------------------------------------------------ arithmetic

    @Test
    fun multiplicationBindsTighterThanAddition() {
        engine().keys("3.14159 + 42 * 1337 =").shows("56,157.14")
    }

    @Test
    fun chainModeWorksLeftToRight() {
        val e = engine().keys("ALG/CHAIN 3.14159 + 42 * 1337 =")
        near(60354.30583, e.s.x, 1e-4)
    }

    @Test
    fun runningResultIsCarriedIntoNextCalculation() {
        engine().keys("25000 + 8 % =").shows("27,000.00")
        engine().keys("25000 + 8 % = * 0.05 / 12 =").shows("112.50")
    }

    @Test
    fun percentOfAProductDividesByOneHundred() {
        engine().keys("200 * 25 % =").shows("50.00")
        engine().keys("200 - 25 % =").shows("150.00")
    }

    @Test
    fun parenthesesGroupAndUnclosedOnesAreClosedByEquals() {
        engine().keys("( 2 + 3 ) * 4 =").shows("20.00")
        engine().keys("2 * ( 3 + 4 =").shows("14.00")
        engine().keys("10 - ( 2 + 3 ) * 2 =").shows("0.00")
    }

    @Test
    fun secondOperatorReplacesFirst() {
        engine().keys("10 - 3 * - 2 =").shows("5.00")
        engine().keys("8 + * 2 =").shows("16.00")
    }

    @Test
    fun powerAndUnaryFunctions() {
        engine().keys("2 ^ 10 =").shows("1,024.00")
        engine().keys("81 SQRT").shows("9.00")
        engine().keys("4 1/x").shows("0.25")
        engine().keys("12 x^2").shows("144.00")
        engine().keys("5 FACT").shows("120.00")
        engine().keys("1 EXP LN").shows("1.00")
        engine().keys("2 + 9 SQRT =").shows("5.00")
    }

    @Test
    fun constantRepeatsAnOperation() {
        val e = engine().keys("5 + 2 K =")
        e.shows("7.00")
        e.keys("6 =").shows("8.00")
        e.keys("7 =").shows("9.00")
    }

    @Test
    fun constantRemembersAPercentage() {
        val e = engine().keys("10 + 10 % K =")
        e.shows("11.00")
        e.keys("25 =").shows("27.50")
    }

    @Test
    fun swapExchangesOperands() {
        engine().keys("9 / 3 SWAP =").shows("0.33")
    }

    @Test
    fun divisionByZeroShowsAnErrorThatClears() {
        val e = engine().keys("5 / 0 =")
        assertTrue(e.isError)
        e.shows(Err.DIV0)
        e.keys("C").shows("0.00")
        assertFalse(e.isError)
        e.keys("2 + 2 =").shows("4.00")
    }

    @Test
    fun entryEditing() {
        engine().keys("123 BACK").shows("12")
        engine().keys("12.5 CHS").shows("-12.5")
        engine().keys("1 EEX 3 =").shows("1,000.00")
        engine().keys("2.5 EEX CHS 2 + 0 =").shows("0.03")
        engine().keys(".5").shows("0.5")
        engine().keys("1234567.5").shows("1,234,567.5")
        engine().keys("5 BACK").shows("0.00")
    }

    @Test
    fun percentChange() {
        engine().keys("80 INPUT 100 %CHG").shows("25.00")
    }

    @Test
    fun trigInDegreesAndRadians() {
        engine().keys("30 SIN").shows("0.50")
        engine().keys("180 COS").shows("-1.00")
        engine().keys("45 TAN").shows("1.00")
        engine().keys(".5 INV SIN").shows("30.00")
        val e = engine().keys("RAD_DEG PI SIN")
        near(0.0, e.s.x, 1e-12)
        near(1.1752011936, engine().keys("1 HYP SIN").s.x, 1e-9)
        assertTrue(engine().keys("90 TAN").isError)
    }

    @Test
    fun permutationsAndCombinations() {
        engine().keys("5 INPUT 2 NPR").shows("20.00")
        engine().keys("52 INPUT 5 NCR").shows("2,598,960.00")
    }

    // ------------------------------------------------------------ display

    @Test
    fun displayFormats() {
        val e = engine().keys("2 / 3 =")
        e.shows("0.67")
        e.keys("DISP 4").shows("0.6667")
        e.keys("DISP DOT").shows("0.66666666667")
        e.keys("DISP 2 RADIX").shows("0,67")
        engine().keys("1234567.891 RADIX =").shows("1.234.567,89")
        engine().keys("2 / 3 = DISP EQ").shows("0.66666666667")
    }

    @Test
    fun largeAndSmallNumbersUseScientificNotation() {
        assertEquals("1.00E12", NumFmt.format(1e12, 2))
        assertEquals("1.23E-7", NumFmt.format(0.000000123, 2))
        assertEquals("999,999,999,999", NumFmt.format(999_999_999_999.4, 2))
        assertEquals("0.30", NumFmt.format(0.1 + 0.2, 2))
        assertEquals("0.3", NumFmt.format(0.1 + 0.2, NumFmt.FLOAT))
        assertEquals("2.68", NumFmt.format(2.675, 2))
        assertEquals("-0.01", NumFmt.format(-0.005, 2))
        assertEquals("100", NumFmt.format(100.0, NumFmt.FLOAT))
    }

    @Test
    fun roundKeyRoundsTheStoredValue() {
        val e = engine().keys("2 / 3 = RND")
        near(0.67, e.s.x, 0.0)
    }

    // ------------------------------------------------------------ memory

    @Test
    fun memoryAndRegisters() {
        engine().keys("5 ->M 3 M+ C RM").shows("8.00")
        engine().keys("42 STO 3 C RCL 3").shows("42.00")
        engine().keys("7 STO DOT 2 C RCL DOT 2").shows("7.00")
        engine().keys("100 PV 5 RCL PV").shows("100.00")
    }

    // ------------------------------------------------------------ time value of money

    @Test
    fun mortgagePayment() {
        val e = engine().keys("360 N 4.75 I/YR 135000 PV 0 FV PMT")
        e.shows("-704.22")
        // Each of the other four can be recovered from the rest.
        near(4.75, e.keys("I/YR").s.x, 1e-9)
        near(360.0, e.keys("N").s.x, 1e-7)
        near(135000.0, e.keys("PV").s.x, 1e-5)
        near(0.0, e.keys("FV").s.x, 1e-4)
    }

    @Test
    fun yearsTimesPaymentsPerYear() {
        val e = engine().keys("30 xP/YR")
        e.shows("360.00")
        near(360.0, e.s.n, 0.0)
    }

    @Test
    fun futureValueWithAnnualCompounding() {
        engine().keys("1 P/YR 10 N 5 I/YR 1000 CHS PV 0 PMT FV").shows("1,628.89")
    }

    @Test
    fun beginModeAnnuity() {
        val e = engine().keys("BEG/END 36 N 10 I/YR 0 PV 100 CHS PMT FV")
        near(4213.000293267812, e.s.x, 1e-6)
    }

    @Test
    fun zeroInterestLoan() {
        engine().keys("12 N 0 I/YR 1200 PV 0 FV PMT").shows("-100.00")
        engine().keys("12 N 1200 PV 100 CHS PMT 0 FV I/YR").shows("0.00")
    }

    @Test
    fun negativeInterestRateIsFound() {
        val e = engine().keys("1 P/YR 10 N 1000 PV 90 CHS PMT 0 FV I/YR")
        assertTrue(e.s.x < 0)
        val check = Tvm.solvePmt(Tvm.Params(10.0, e.s.x, 1000.0, 0.0, 0.0, 1.0, false))
        near(-90.0, check, 1e-7)
    }

    @Test
    fun impossibleRateReportsNoSolution() {
        val e = engine().keys("10 N 1000 PV 100 PMT 0 FV I/YR")
        e.shows(Err.NO_SOLUTION)
    }

    @Test
    fun nominalAndEffectiveRates() {
        engine().keys("5.25 NOM% 12 P/YR EFF%").shows("5.38")
        val e = engine().keys("12 P/YR 5.378188672746131 EFF% NOM%")
        near(5.25, e.s.x, 1e-9)
    }

    @Test
    fun paymentsPerYearMustBeInRange() {
        assertTrue(engine().keys("0 P/YR").isError)
    }

    @Test
    fun storedValueIsNotRecomputedAfterArithmetic() {
        // A result from "=" is a new number, so PMT stores it rather than solving.
        val e = engine().keys("360 N 4.75 I/YR 135000 PV 0 FV 700 + 50 = CHS PMT")
        near(-750.0, e.s.pmt, 0.0)
    }

    // ------------------------------------------------------------ amortization

    @Test
    fun amortizationScheduleMatchesALenderStatement() {
        val e = engine().keys("360 N 4.75 I/YR 135000 PV 0 FV PMT")
        val all = e.amortize(1, 360)
        val first = all.rows[0]
        near(-169.84, first.principal, 1e-9)
        near(-534.38, first.interest, 1e-9)
        near(134830.16, first.balance, 1e-9)
        val second = all.rows[1]
        near(-170.52, second.principal, 1e-9)
        near(-533.70, second.interest, 1e-9)
        near(134659.64, second.balance, 1e-9)
        val seventeenth = all.rows[16]
        near(-180.93, seventeenth.principal, 1e-9)
        near(-523.29, seventeenth.interest, 1e-9)
        near(132019.37, seventeenth.balance, 1e-9)
        near(-118522.15, all.totalInterest, 1e-6)
        near(-134997.05, all.totalPrincipal, 1e-6)
        near(-253519.20, all.totalPaid, 1e-6)
    }

    @Test
    fun amortizationRangeStartsFromTheRightBalance() {
        val e = engine().keys("360 N 4.75 I/YR 135000 PV 0 FV PMT")
        val part = e.amortize(13, 24)
        assertEquals(12, part.rows.size)
        assertEquals(13, part.rows.first().period)
        near(e.amortize(1, 24).rows.last().balance, part.rows.last().balance, 0.0)
    }

    @Test
    fun amortKeyOpensTheScheduleForTheTypedRange() {
        val e = engine().keys("360 N 4.75 I/YR 135000 PV 0 FV PMT")
        assertEquals(CalcEngine.Effect.OpenAmortization(1, 360), e.apply(Fn.AMORT))
        e.keys("13 INPUT 24")
        assertEquals(CalcEngine.Effect.OpenAmortization(13, 24), e.apply(Fn.AMORT))
    }

    // ------------------------------------------------------------ cash flows

    @Test
    fun netPresentValueAndInternalRateOfReturn() {
        val e = engine().keys("1 P/YR 80000 CHS CFJ 500 CHS CFJ 4500 CFJ 5500 CFJ 4500 CFJ 130000 CFJ")
        assertEquals(6, e.s.flows.size)
        e.keys("13 I/YR NPV").shows("212.18")
        e.keys("IRR").shows("13.06")
        near(e.s.x, e.s.iyr, 0.0)
    }

    @Test
    fun repeatedCashFlows() {
        // 5 monthly inflows of 1,000 then 3 of 2,000 against an initial 6,000 outlay.
        val e = engine().keys("12 P/YR 6000 CHS CFJ 1000 CFJ 5 NJ 2000 INPUT 3 CFJ")
        assertEquals(listOf(CashFlow(-6000.0, 1), CashFlow(1000.0, 5), CashFlow(2000.0, 3)), e.s.flows)
        val monthly = CashFlows.irr(e.s.flows)
        near(0.0, CashFlows.npv(e.s.flows, monthly), 1e-6)
        near(monthly * 1200, e.keys("IRR").s.x, 1e-9)
    }

    @Test
    fun cashFlowsWithOneSignHaveNoRate() {
        assertTrue(engine().keys("100 CFJ 200 CFJ IRR").isError)
    }

    // ------------------------------------------------------------ margin, break-even, depreciation

    @Test
    fun marginAndMarkup() {
        val e = engine().keys("9.60 CST 15 PRC MAR")
        e.shows("36.00")
        e.keys("MU").shows("56.25")
        engine().keys("20 CST 25 MAR PRC").shows("26.67")
        engine().keys("20 CST 25 MU PRC").shows("25.00")
        engine().keys("50 PRC 20 MAR CST").shows("40.00")
    }

    @Test
    fun breakEven() {
        engine().keys("300 SP 250 VC 150000 FC 10000 PROFIT UNITS").shows("3,200.00")
        engine().keys("3200 UNITS 300 SP 250 VC 150000 FC PROFIT").shows("10,000.00")
        engine().keys("3200 UNITS 250 VC 150000 FC 10000 PROFIT SP").shows("300.00")
    }

    @Test
    fun depreciationMethods() {
        val setup = "5 N 10000 PV 500 FV 200 I/YR"
        val sl = engine().keys("$setup 1 SL")
        sl.shows("1,900.00")
        sl.keys("SWAP").shows("7,600.00")
        val soyd = engine().keys("$setup 2 SOYD")
        soyd.shows("2,533.33")
        soyd.keys("SWAP").shows("3,800.00")
        val db1 = engine().keys("$setup 1 DB")
        db1.shows("4,000.00")
        db1.keys("SWAP").shows("5,500.00")
        val db2 = engine().keys("$setup 2 DB")
        db2.shows("2,400.00")
        db2.keys("SWAP").shows("3,100.00")
        engine().keys("$setup 5 DB SWAP").shows("277.60")
        // Declining balance stops at salvage value rather than depreciating past it.
        val floor = engine().keys("5 N 10000 PV 4000 FV 200 I/YR 2 DB")
        floor.shows("2,000.00")
        floor.keys("SWAP").shows("0.00")
        engine().keys("5 N 10000 PV 4000 FV 200 I/YR 3 DB").shows("0.00")
    }

    // ------------------------------------------------------------ statistics

    @Test
    fun twoVariableStatisticsAndRegression() {
        val e = engine().keys("1 INPUT 2 S+ 2 INPUT 4 S+ 3 INPUT 5 S+ 4 INPUT 4 S+ 5 INPUT 5 S+")
        e.shows("5.00")
        e.keys("MEAN").shows("3.00")
        e.keys("SWAP").shows("4.00")
        near(1.5811388300841898, e.keys("SDEV").s.x, 1e-12)
        near(1.224744871391589, e.keys("SWAP").s.x, 1e-12)
        near(1.4142135623730951, e.keys("PSDEV").s.x, 1e-12)
        near(4.0, e.keys("3 EST_Y").s.x, 1e-12)
        near(0.6, e.keys("SWAP").s.x, 1e-12)
        near(3.0, e.keys("4 EST_X").s.x, 1e-12)
        near(0.7745966692414834, e.keys("SWAP").s.x, 1e-12)
        near(2.2, e.keys("WMEAN SWAP").s.x, 1e-12)
        e.keys("SUM_XY").shows("66.00")
        e.keys("5 INPUT 5 S-").shows("4.00")
        e.keys("CLR_STAT")
        assertEquals(0.0, e.s.sums.n, 0.0)
        assertTrue(e.keys("MEAN").isError)
    }

    @Test
    fun weightedMean() {
        engine().keys("70 INPUT 2 S+ 90 INPUT 3 S+ WMEAN").shows("82.00")
    }

    // ------------------------------------------------------------ dates and bonds

    @Test
    fun dateArithmetic() {
        val e = engine().keys("4.282010 INPUT 6.042020 DDAYS")
        e.shows("3,690.00")
        e.keys("SWAP").shows("3,636.00")
        val d = engine().keys("4.282010 INPUT 100 DATE")
        d.shows("08-06-2010")
        assertTrue(d.s.label!!.endsWith("Fri"))
        engine().keys("DMY/MDY 28.042010 INPUT 100 DATE").shows("06-08-2010")
        assertTrue(engine().keys("2.302010 INPUT 1 DATE").isError)
    }

    @Test
    fun treasuryBondPriceAndYield() {
        val e = engine().keys("4.282004 SETDATE 6.042018 MATDATE 6.75 CPN 4.75 YTM PRICE")
        e.shows("120.38")
        e.keys("ACCINT").shows("2.69")
        e.keys("122.125 PRICE YTM").shows("4.60")
    }

    @Test
    fun bondYieldOnACouponDate() {
        val e = engine().keys("SEMI/ANN 1.012026 SETDATE 1.012036 MATDATE 6 CPN 110 PRICE YTM")
        e.shows("4.72")
        near(0.0, e.s.accInt, 0.0)
        // Solving price back from that yield returns the price we started with.
        near(110.0, e.keys("PRICE").s.x, 1e-9)
    }

    @Test
    fun bondWithAnnualCouponsOn30360Basis() {
        val e = engine().keys("SEMI/ANN 360/ACT 3.152024 SETDATE 9.152030 MATDATE 5 CPN 5 YTM PRICE")
        // Priced at its coupon rate, an annual bond sits just under par between coupon dates.
        assertTrue(e.s.x > 99.9 && e.s.x < 100.0)
        near(2.5, e.keys("ACCINT").s.x, 1e-12)
    }

    @Test
    fun bondNeedsBothDates() {
        engine().keys("6 CPN 5 YTM PRICE").shows(Err.NO_DATE)
    }

    @Test
    fun calendarHelpers() {
        val d = CalDate(2024, 2, 29)
        assertEquals(d, CalDate.fromEpochDay(d.toEpochDay()))
        assertEquals(4, d.dayOfWeek)
        assertEquals(CalDate(2023, 8, 29), d.plusMonths(-6))
        assertEquals(CalDate(2025, 2, 28), CalDate(2024, 8, 31).plusMonths(6))
        assertEquals(0L, CalDate(1970, 1, 1).toEpochDay())
        assertNull(Dates.parse(13.012024, false))
    }

    // ------------------------------------------------------------ keypad, history, persistence

    @Test
    fun shiftKeysSelectTheSecondaryFunctions() {
        val e = engine()
        e.press(Key.D1)
        e.press(Key.AQUA)
        assertEquals(Shift.AQUA, e.s.shift)
        e.press(Key.PMT)
        assertEquals(Shift.NONE, e.s.shift)
        near(1.0, e.s.pyr, 0.0)
        e.press(Key.GOLD)
        e.press(Key.GOLD)
        assertEquals(Shift.NONE, e.s.shift)
        e.press(Key.D6)
        e.press(Key.GOLD)
        e.press(Key.PMT)
        near(6.0, e.s.cpn, 0.0)
    }

    @Test
    fun everyKeyAndShiftCombinationIsSafeToPress() {
        val e = engine()
        for (shift in listOf(Key.AQUA, Key.GOLD, null)) {
            for (def in KeyMap.keys) {
                if (shift != null) e.press(shift)
                e.press(Key.D2)
                if (shift != null) e.press(shift)
                e.press(def.key)
                e.displayText
                e.strip()
            }
        }
        assertEquals(40, KeyMap.keys.size)
    }

    @Test
    fun historyRecordsExpressionsAndSolves() {
        val e = engine().keys("3.14159 + 42 * 1337 =")
        val calc = e.s.history.single()
        assertEquals("3.14159 + 42 × 1337 =", calc.title)
        assertEquals("56,157.14", calc.resultText)
        e.keys("25000 + 8 % =")
        assertEquals("25000 + 8% =", e.s.history.last().title)
        e.keys("* 0.05 / 12 =")
        assertEquals("27,000.00 × 0.05 ÷ 12 =", e.s.history.last().title)
        assertEquals("112.50", e.s.history.last().resultText)
        e.keys("( 2 + 3 ) * 4 =")
        assertEquals("(2 + 3) × 4 =", e.s.history.last().title)
        e.keys("360 N 4.75 I/YR 135000 PV 0 FV PMT")
        val solve = e.s.history.last()
        assertEquals("PMT", solve.title)
        assertEquals("-704.22", solve.resultText)
        assertEquals("135,000.00", solve.fields.first { it.name == "PV" }.value)
    }

    @Test
    fun expressionLineTracksTheCalculationInProgress() {
        val e = engine().keys("12 + 3 *")
        assertEquals("12 + 3 ×", e.expressionText())
        e.keys("4 =")
        assertEquals("", e.expressionText())
    }

    @Test
    fun clearAllKeepsSettingsAndHistory() {
        val e = engine().keys("1 + 1 = 4 P/YR DISP 4 360 N 5 ->M CALL_C")
        assertEquals(0.0, e.s.n, 0.0)
        assertEquals(0.0, e.s.m, 0.0)
        assertEquals(4.0, e.s.pyr, 0.0)
        assertEquals(4, e.s.decimals)
        assertEquals(1, e.s.history.size)
    }

    @Test
    fun stateSurvivesSerialization() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val e = engine().keys("360 N 4.75 I/YR 135000 PV 0 FV PMT 100 CFJ 5 INPUT 6 S+ 4.282004 SETDATE 2 + 3 *")
        val restored = CalcEngine(json.decodeFromString<CalcState>(json.encodeToString(e.s)))
        assertEquals(e.displayText, restored.displayText)
        assertEquals(e.s.pmt, restored.s.pmt, 0.0)
        assertEquals(e.s.flows, restored.s.flows)
        assertEquals(e.s.sums, restored.s.sums)
        assertEquals(e.s.settleDay, restored.s.settleDay)
        restored.keys("4 =").shows("14.00")
    }
}
