package com.buscalc.app

import com.buscalc.app.engine.CalcEngine
import com.buscalc.app.engine.Fn
import com.buscalc.app.engine.Key
import com.buscalc.app.ui.ThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun calculatorStateComesBackAfterARestart() {
        val store = StateStore(folder.root)
        val e = CalcEngine()
        "360".forEach { e.apply(Fn.entries[it - '0']) }
        e.apply(Fn.N)
        e.apply(Fn.D5)
        e.apply(Fn.DIV)
        e.apply(Fn.D0)
        e.apply(Fn.EQ) // leaves an error on screen
        e.press(Key.GOLD) // and a shift half-pressed
        store.writeState(store.encode(e.s))

        val restored = StateStore(folder.root).loadState()
        assertEquals(360.0, restored.n, 0.0)
        assertNull(restored.error)
        assertEquals(com.buscalc.app.engine.Shift.NONE, restored.shift)
    }

    @Test
    fun missingOrDamagedFilesFallBackToDefaults() {
        val store = StateStore(folder.root)
        assertEquals(12.0, store.loadState().pyr, 0.0)
        File(folder.root, "calculator.json").writeText("{ not json")
        assertEquals(12.0, store.loadState().pyr, 0.0)
        assertEquals(AppPrefs(), store.loadPrefs())
    }

    @Test
    fun preferencesRoundTrip() {
        val store = StateStore(folder.root)
        store.savePrefs(AppPrefs(theme = ThemeMode.DARK, haptics = false, keepScreenOn = true))
        assertEquals(AppPrefs(ThemeMode.DARK, false, true), store.loadPrefs())
    }
}
