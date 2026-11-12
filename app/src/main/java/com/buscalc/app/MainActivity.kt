package com.buscalc.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import com.buscalc.app.engine.CalDate
import com.buscalc.app.engine.CalcEngine
import com.buscalc.app.engine.Dates
import com.buscalc.app.engine.NumFmt
import com.buscalc.app.ui.AmortizationScreen
import com.buscalc.app.ui.BusCalcTheme
import com.buscalc.app.ui.CalculatorScreen
import com.buscalc.app.ui.CashFlowScreen
import com.buscalc.app.ui.HelpScreen
import com.buscalc.app.ui.HistoryScreen
import com.buscalc.app.ui.LoanSummary
import com.buscalc.app.ui.MenuAction
import com.buscalc.app.ui.RegisterGroup
import com.buscalc.app.ui.RegisterRow
import com.buscalc.app.ui.RegistersScreen
import com.buscalc.app.ui.SettingsScreen
import com.buscalc.app.ui.SettingsUi
import com.buscalc.app.ui.ThemeMode
import com.buscalc.app.ui.parseNumber

class MainActivity : ComponentActivity() {
    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onStop() {
        super.onStop()
        vm.saveNow()
    }

    @Composable
    private fun App() {
        val dark = when (vm.prefs.theme) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        LaunchedEffect(dark) {
            val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        val keepOn = vm.prefs.keepScreenOn
        DisposableEffect(keepOn) {
            if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        BusCalcTheme(dark = dark) {
            BackHandler(enabled = vm.screen != Screen.CALCULATOR) { vm.screen = Screen.CALCULATOR }
            val back = { vm.screen = Screen.CALCULATOR }
            // Reading the revision here makes every screen below refresh after an engine change.
            vm.revision
            val e = vm.engine
            when (vm.screen) {
                Screen.CALCULATOR -> {
                    val view = LocalView.current
                    CalculatorScreen(
                        ui = vm.ui,
                        onKey = { key ->
                            if (vm.prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (!e.s.mute) click()
                            vm.press(key)
                        },
                        onStripTap = { vm.update { cycleContext() } },
                        onMenu = ::onMenu,
                    )
                }
                Screen.AMORTIZATION -> AmortizationScreen(
                    loan = LoanSummary(
                        n = plain(e.s.n),
                        iyr = plain(e.s.iyr),
                        pyr = plain(e.s.pyr),
                        pv = e.fmt(NumFmt.round(e.s.pv, e.s.decimals)),
                        pmt = e.fmt(NumFmt.round(e.s.pmt, e.s.decimals)),
                        fv = e.fmt(e.s.fv),
                        begin = e.s.begin,
                    ),
                    initialFrom = vm.amortRange.first,
                    initialTo = vm.amortRange.second,
                    compute = e::amortize,
                    format = e::fmt,
                    onShare = ::share,
                    onBack = back,
                )
                Screen.CASH_FLOWS -> CashFlowScreen(
                    flows = e.s.flows.toList(),
                    iyr = e.s.iyr,
                    pyr = e.s.pyr,
                    commaRadix = e.s.commaRadix,
                    format = e::fmt,
                    onSet = { i, flow -> vm.update { setFlow(i, flow) } },
                    onRemove = { i -> vm.update { removeFlow(i) } },
                    onClear = { vm.update { clearFlows() } },
                    onRate = { rate -> vm.update { s.iyr = rate } },
                    onBack = back,
                )
                Screen.REGISTERS -> RegistersScreen(
                    groups = registerGroups(e),
                    onUse = { v ->
                        vm.update { loadValue(v) }
                        back()
                    },
                    onBack = back,
                )
                Screen.HISTORY -> HistoryScreen(
                    entries = e.s.history.toList(),
                    onUse = { v ->
                        vm.update { loadValue(v) }
                        back()
                    },
                    onClear = { vm.update { clearHistory() } },
                    onBack = back,
                )
                Screen.SETTINGS -> SettingsScreen(
                    settings = SettingsUi(
                        decimals = e.s.decimals,
                        commaRadix = e.s.commaRadix,
                        chainMode = e.s.chainMode,
                        radians = e.s.radians,
                        dmy = e.s.dmy,
                        begin = e.s.begin,
                        semiAnnual = e.s.semiAnnual,
                        cal360 = e.s.cal360,
                        keySound = !e.s.mute,
                        haptics = vm.prefs.haptics,
                        keepScreenOn = vm.prefs.keepScreenOn,
                        theme = vm.prefs.theme,
                        version = BuildConfig.VERSION_NAME,
                    ),
                    onChange = ::applySettings,
                    onBack = back,
                )
                Screen.HELP -> HelpScreen(onBack = back)
            }
        }
    }

    private fun plain(v: Double) = NumFmt.format(v, NumFmt.FLOAT, vm.engine.s.commaRadix)

    private fun onMenu(action: MenuAction) {
        when (action) {
            MenuAction.AMORTIZATION -> vm.openAmortization()
            MenuAction.CASH_FLOWS -> vm.screen = Screen.CASH_FLOWS
            MenuAction.REGISTERS -> vm.screen = Screen.REGISTERS
            MenuAction.HISTORY -> vm.screen = Screen.HISTORY
            MenuAction.SETTINGS -> vm.screen = Screen.SETTINGS
            MenuAction.HELP -> vm.screen = Screen.HELP
            MenuAction.COPY -> copy()
            MenuAction.PASTE -> paste()
        }
    }

    private fun applySettings(n: SettingsUi) {
        vm.update {
            s.decimals = n.decimals
            s.commaRadix = n.commaRadix
            s.chainMode = n.chainMode
            s.radians = n.radians
            s.dmy = n.dmy
            s.begin = n.begin
            s.semiAnnual = n.semiAnnual
            s.cal360 = n.cal360
            s.mute = !n.keySound
        }
        vm.updatePrefs(vm.prefs.copy(theme = n.theme, haptics = n.haptics, keepScreenOn = n.keepScreenOn))
    }

    private fun click() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.4f)
    }

    private fun copy() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val e = vm.engine
        // Copy the full value, not the rounded one on screen, so nothing is lost on the way out.
        val text = if (e.isError) e.displayText else NumFmt.format(e.s.x, NumFmt.FLOAT, e.s.commaRadix)
        clipboard.setPrimaryClip(ClipData.newPlainText("Calculator value", text))
        // Android 13 and later show their own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast("Copied $text")
    }

    private fun paste() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        val cleaned = text?.filter { it.isDigit() || it in ".,-−Ee" }.orEmpty()
        val value = parseNumber(cleaned, vm.engine.s.commaRadix)
        if (value == null) toast("Nothing to paste: the clipboard does not hold a number") else vm.update { loadValue(value) }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Amortization schedule")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share schedule"))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun registerGroups(e: CalcEngine): List<RegisterGroup> {
        val s = e.s
        fun row(name: String, v: Double) = RegisterRow(name, e.fmt(v), v)
        fun date(name: String, day: Long?) =
            RegisterRow(name, day?.let { Dates.format(CalDate.fromEpochDay(it), s.dmy) } ?: "not set")
        val used = s.reg.withIndex().filter { it.value != 0.0 }.map { row("R${it.index}", it.value) }
        return listOf(
            RegisterGroup(
                "Loan values",
                listOf(
                    row("N", s.n), row("I/YR", s.iyr), row("PV", s.pv), row("PMT", s.pmt), row("FV", s.fv),
                    row("P/YR", s.pyr), row("EFF%", s.eff),
                ),
                onClear = { vm.update { clearTvm() } },
            ),
            RegisterGroup(
                "Memory",
                listOf(row("M", s.m)) + used.ifEmpty { listOf(RegisterRow("R0 – R19", "all empty")) },
                onClear = {
                    vm.update {
                        this.s.m = 0.0
                        this.s.reg.indices.forEach { this.s.reg[it] = 0.0 }
                    }
                },
            ),
            RegisterGroup(
                "Margin and markup",
                listOf(row("Cost", s.cst), row("Price", s.prc), row("Margin %", s.mar), row("Markup %", s.mu)),
            ),
            RegisterGroup(
                "Break-even",
                listOf(
                    row("Units", s.units), row("Selling price", s.sp), row("Variable cost", s.vc),
                    row("Fixed cost", s.fc), row("Profit", s.profit),
                ),
            ),
            RegisterGroup(
                "Bond",
                listOf(
                    date("Settlement", s.settleDay), date("Maturity", s.maturityDay),
                    row("Coupon %", s.cpn), row("Call value", s.call), row("Yield %", s.ytm),
                    row("Price", s.price), row("Accrued interest", s.accInt),
                ),
            ),
            RegisterGroup(
                "Statistics",
                listOf(
                    row("n", s.sums.n), row("Σx", s.sums.sx), row("Σy", s.sums.sy),
                    row("Σx²", s.sums.sxx), row("Σy²", s.sums.syy), row("Σxy", s.sums.sxy),
                ),
                onClear = { vm.update { clearStats() } },
            ),
        )
    }
}
