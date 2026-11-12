package com.buscalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val title: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

/** Every user-adjustable option in one value, so the screen can be driven and tested without the engine. */
data class SettingsUi(
    val decimals: Int = 2,
    val commaRadix: Boolean = false,
    val chainMode: Boolean = false,
    val radians: Boolean = false,
    val dmy: Boolean = false,
    val begin: Boolean = false,
    val semiAnnual: Boolean = true,
    val cal360: Boolean = false,
    val keySound: Boolean = true,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val version: String = "",
)

@Composable
fun SettingsScreen(settings: SettingsUi, onChange: (SettingsUi) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Section("Display")
            Choice(
                title = "Decimal places",
                options = (0..9).map { it to it.toString() } + (-1 to "Floating (show all digits)"),
                selected = settings.decimals,
                onSelect = { onChange(settings.copy(decimals = it)) },
            )
            Choice(
                title = "Number format",
                options = listOf(false to "1,234.56", true to "1.234,56"),
                selected = settings.commaRadix,
                onSelect = { onChange(settings.copy(commaRadix = it)) },
            )
            Choice(
                title = "Theme",
                options = ThemeMode.entries.map { it to it.title },
                selected = settings.theme,
                onSelect = { onChange(settings.copy(theme = it)) },
            )

            Section("Calculation")
            Choice(
                title = "Order of operations",
                options = listOf(
                    false to "Algebraic: × and ÷ before + and −",
                    true to "Chain: strictly left to right",
                ),
                selected = settings.chainMode,
                onSelect = { onChange(settings.copy(chainMode = it)) },
            )
            Choice(
                title = "Payments fall due",
                options = listOf(false to "At the end of each period", true to "At the beginning of each period"),
                selected = settings.begin,
                onSelect = { onChange(settings.copy(begin = it)) },
            )
            Choice(
                title = "Angles",
                options = listOf(false to "Degrees", true to "Radians"),
                selected = settings.radians,
                onSelect = { onChange(settings.copy(radians = it)) },
            )

            Section("Dates and bonds")
            Choice(
                title = "Date entry",
                options = listOf(false to "Month first: MM.DDYYYY", true to "Day first: DD.MMYYYY"),
                selected = settings.dmy,
                onSelect = { onChange(settings.copy(dmy = it)) },
            )
            Choice(
                title = "Day count",
                options = listOf(false to "Actual calendar days", true to "30-day months, 360-day year"),
                selected = settings.cal360,
                onSelect = { onChange(settings.copy(cal360 = it)) },
            )
            Choice(
                title = "Bond coupons",
                options = listOf(true to "Twice a year", false to "Once a year"),
                selected = settings.semiAnnual,
                onSelect = { onChange(settings.copy(semiAnnual = it)) },
            )

            Section("Keys")
            Toggle("Key sound", settings.keySound) { onChange(settings.copy(keySound = it)) }
            Toggle("Vibrate on key press", settings.haptics) { onChange(settings.copy(haptics = it)) }
            Toggle("Keep screen on", settings.keepScreenOn) { onChange(settings.copy(keepScreenOn = it)) }

            if (settings.version.isNotEmpty()) {
                Text(
                    "Holmes Business Calculator ${settings.version}",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(title, fontSize = 16.sp)
        Text(
            options.firstOrNull { it.first == selected }?.second.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = value == selected, role = Role.RadioButton) {
                                    open = false
                                    onSelect(value)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
