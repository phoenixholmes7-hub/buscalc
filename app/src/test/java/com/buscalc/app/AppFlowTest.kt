package com.buscalc.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real activity by tapping keys, the way someone using the app would. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class AppFlowTest {

    /** Saved state from one test must not leak into the next, so wipe it before the activity starts. */
    @get:Rule(order = 0)
    val cleanState = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.getApplication().filesDir.listFiles()?.forEach { it.delete() }
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun tap(vararg keys: String) {
        for (key in keys) rule.onNodeWithContentDescription(key).performClick()
    }

    private fun shows(text: String) = rule.onNodeWithContentDescription("Display $text").assertExists()

    private fun enterLoan() {
        tap("3", "6", "0", "N")
        tap("4", "Decimal point", "7", "5", "I/YR")
        tap("1", "3", "5", "0", "0", "0", "PV")
        tap("0", "FV", "PMT")
    }

    @Test
    fun startsAtZero() {
        shows("0.00")
    }

    @Test
    fun arithmeticOnTheKeypad() {
        tap("3", "Plus", "4", "Times", "5", "Equals")
        shows("23.00")
    }

    @Test
    fun loanPaymentThenAmortizationSchedule() {
        enterLoan()
        shows("−704.22")
        tap("Lower shift", "FV")
        rule.onNodeWithText("Amortization").assertExists()
        rule.onNodeWithText("−253,519.20").assertExists()
        rule.onNodeWithText("134,830.16").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/flow_amortization.png")
        rule.onNodeWithContentDescription("Back").performClick()
        shows("−704.22")
    }

    @Test
    fun shiftedKeyChangesPaymentsPerYear() {
        tap("1", "Lower shift", "PMT")
        rule.onNodeWithText("1 P/YR", substring = true).assertExists()
        assertEquals(1.0, rule.activity.let { activityViewModel(it).engine.s.pyr }, 0.0)
    }

    @Test
    fun helpKeyOpensHelp() {
        tap("HELP")
        rule.onNodeWithText("Why is my answer wrong?").assertExists()
    }

    @Test
    fun menuReachesEveryScreen() {
        enterLoan()
        val screens = listOf(
            "Cash flows" to "Discount rate (I/YR)",
            "Registers & memory" to "Loan values",
            "Recent calculations" to "PMT",
            "Settings" to "Decimal places",
            "Help" to "How the keys work",
        )
        for ((item, landmark) in screens) {
            rule.onNodeWithContentDescription("Menu").performClick()
            rule.onNodeWithText(item).performClick()
            rule.onNodeWithText(landmark).assertExists()
            rule.onNodeWithContentDescription("Back").performClick()
            shows("−704.22")
        }
    }

    @Test
    fun tappingAHistoryEntryPutsItBackInTheDisplay() {
        tap("6", "Times", "7", "Equals", "Clear")
        shows("0.00")
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText("Recent calculations").performClick()
        rule.onNodeWithText("42.00").performClick()
        shows("42.00")
    }

    @Test
    fun settingsChangeTheDisplayFormat() {
        tap("2", "Divide", "3", "Equals")
        shows("0.67")
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Decimal places").performClick()
        rule.onNodeWithText("4").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        shows("0.6667")
    }

    private fun activityViewModel(activity: MainActivity): CalcViewModel =
        androidx.lifecycle.ViewModelProvider(activity)[CalcViewModel::class.java]
}
