package com.buscalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One stored value. [value] is null for rows that are not numbers you can recall, such as dates. */
data class RegisterRow(val name: String, val text: String, val value: Double? = null)

data class RegisterGroup(val title: String, val rows: List<RegisterRow>, val onClear: (() -> Unit)? = null)

@Composable
fun RegistersScreen(groups: List<RegisterGroup>, onUse: (Double) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = "Registers & memory", onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    "Everything the calculator is holding. Tap a value to put it in the display.",
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            groups.forEach { group ->
                item(key = group.title) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group.title,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (group.onClear != null) {
                            TextButton(onClick = group.onClear) { Text("Clear") }
                        } else {
                            TextButton(onClick = {}, enabled = false) { Text("") }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            group.rows.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val base = Modifier.fillMaxWidth()
                                val v = row.value
                                Row(
                                    (if (v != null) base.clickable(onClickLabel = "Use ${row.name}") { onUse(v) } else base)
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                ) {
                                    Text(row.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(row.text.withMinus(), style = NumberStyle, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
