package com.buscalc.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buscalc.app.engine.CalcEngine
import com.buscalc.app.engine.CalcState
import com.buscalc.app.engine.Key
import com.buscalc.app.engine.Prefix
import com.buscalc.app.engine.Shift
import com.buscalc.app.ui.CalcUi
import com.buscalc.app.ui.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Screen { CALCULATOR, AMORTIZATION, CASH_FLOWS, REGISTERS, HISTORY, SETTINGS, HELP }

/** App-level preferences that are not part of the calculator's own state. */
@Serializable
data class AppPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
)

/** Reads and writes the calculator state and preferences as small JSON files. */
class StateStore(private val dir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }
    private val stateFile get() = File(dir, "calculator.json")
    private val prefsFile get() = File(dir, "prefs.json")

    fun loadState(): CalcState {
        val s = runCatching { json.decodeFromString<CalcState>(stateFile.readText()) }.getOrElse { CalcState() }
        // Half-finished key sequences and error messages should not survive a restart.
        s.shift = Shift.NONE
        s.prefix = Prefix.NONE
        s.error = null
        s.showFull = false
        while (s.reg.size < 20) s.reg += 0.0
        return s
    }

    fun encode(s: CalcState): String = json.encodeToString(s)

    fun writeState(text: String) = write(stateFile, text)

    fun loadPrefs(): AppPrefs =
        runCatching { json.decodeFromString<AppPrefs>(prefsFile.readText()) }.getOrElse { AppPrefs() }

    fun savePrefs(p: AppPrefs) = write(prefsFile, json.encodeToString(p))

    private fun write(file: File, text: String) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }
}

class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StateStore(app.filesDir)
    val engine = CalcEngine(store.loadState())

    var ui by mutableStateOf(CalcUi.from(engine))
        private set

    /** Bumped on every change so screens that read the engine directly recompose. */
    var revision by mutableIntStateOf(0)
        private set

    var prefs by mutableStateOf(store.loadPrefs())
        private set

    var screen by mutableStateOf(Screen.CALCULATOR)
    var amortRange by mutableStateOf(1 to 12)
        private set

    private var saveJob: Job? = null

    fun press(key: Key) {
        val effect = engine.press(key)
        changed()
        when (effect) {
            CalcEngine.Effect.OpenHelp -> screen = Screen.HELP
            is CalcEngine.Effect.OpenAmortization -> openAmortization(effect.from, effect.to)
            null -> {}
        }
    }

    fun openAmortization(from: Int? = null, to: Int? = null) {
        val last = kotlin.math.ceil(engine.s.n).toInt().coerceIn(1, com.buscalc.app.engine.Amortization.MAX_PERIODS)
        amortRange = (from ?: 1) to (to ?: last)
        screen = Screen.AMORTIZATION
    }

    /** Runs a change against the engine and refreshes everything that depends on it. */
    fun update(block: CalcEngine.() -> Unit) {
        engine.block()
        changed()
    }

    fun updatePrefs(p: AppPrefs) {
        prefs = p
        store.savePrefs(p)
    }

    private fun changed() {
        ui = CalcUi.from(engine)
        revision++
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            val text = store.encode(engine.s)
            withContext(Dispatchers.IO) { store.writeState(text) }
        }
    }

    /** Called when the app leaves the foreground, where a delayed save might never run. */
    fun saveNow() {
        saveJob?.cancel()
        store.writeState(store.encode(engine.s))
    }
}
