package com.buscalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buscalc.app.engine.CashFlow
import com.buscalc.app.engine.CashFlows
import kotlin.math.abs

@Composable
fun CashFlowScreen(
    flows: List<CashFlow>,
    iyr: Double,
    pyr: Double,
    commaRadix: Boolean,
    format: (Double) -> String,
    onSet: (Int, CashFlow) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onRate: (Double) -> Unit,
    onBack: () -> Unit,
) {
    // -1 = closed, flows.size = adding a new one, otherwise editing that index.
    var editing by remember { mutableStateOf(-1) }
    var editingRate by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val rate = iyr / pyr / 100.0
    val npv = runCatching { format(CashFlows.npv(flows, rate)) }.getOrDefault("–")
    val nfv = runCatching { format(CashFlows.nfv(flows, rate)) }.getOrDefault("–")
    val irr = runCatching { format(CashFlows.irr(flows) * pyr * 100.0) + " %" }.getOrDefault("–")

    ScreenScaffold(
        title = "Cash flows",
        onBack = onBack,
        actions = {
            IconButton(enabled = flows.isNotEmpty(), onClick = { confirmClear = true }) {
                Icon(AppIcons.Delete, contentDescription = "Clear all cash flows")
            }
        },
        floatingActionButton = {
            if (flows.size < CashFlows.MAX_FLOWS) {
                ExtendedFloatingActionButton(
                    onClick = { editing = flows.size },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text("Add flow") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Discount rate (I/YR)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    format(iyr).withMinus() + " %   ·   " + format(pyr).trimZeros() + " per year",
                                    style = NumberStyle,
                                    fontSize = 16.sp,
                                )
                            }
                            TextButton(onClick = { editingRate = true }) { Text("Change") }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Result("Net present value", npv.withMinus(), emphasised = true)
                        Result("Internal rate of return", irr.withMinus(), emphasised = true)
                        Result("Net future value", nfv.withMinus())
                        Result("Sum of flows", format(CashFlows.total(flows)).withMinus())
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (flows.isEmpty()) {
                    Text(
                        "Add the initial outlay first, as a negative amount. Then add each later flow in order. " +
                            "A flow that repeats can be entered once with a count.",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Header("Flow", Modifier.width(56.dp), TextAlign.Start)
                        Header("Amount", Modifier.weight(1f), TextAlign.End)
                        Header("Times", Modifier.width(72.dp), TextAlign.End)
                    }
                    HorizontalDivider()
                }
            }
            itemsIndexed(flows) { index, flow ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Edit") { editing = index }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "CF$index",
                        Modifier.width(56.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = NumberStyle,
                    )
                    Text(
                        format(flow.amount).withMinus(),
                        Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        style = NumberStyle,
                        fontSize = 17.sp,
                        color = if (flow.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "× ${flow.count}",
                        Modifier.width(72.dp),
                        textAlign = TextAlign.End,
                        style = NumberStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (editing >= 0) {
        FlowDialog(
            index = editing,
            existing = flows.getOrNull(editing),
            commaRadix = commaRadix,
            onSave = {
                onSet(editing, it)
                editing = -1
            },
            onDelete = {
                onRemove(editing)
                editing = -1
            },
            onDismiss = { editing = -1 },
        )
    }
    if (editingRate) {
        RateDialog(
            commaRadix = commaRadix,
            onSave = {
                onRate(it)
                editingRate = false
            },
            onDismiss = { editingRate = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all cash flows?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun String.trimZeros(): String =
    if (contains('.') || contains(',')) trimEnd('0').trimEnd('.', ',') else this

@Composable
private fun Header(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text,
        modifier,
        textAlign = align,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Result(name: String, value: String, emphasised: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(name, Modifier.weight(1f))
        Text(
            value,
            style = NumberStyle,
            fontSize = 16.sp,
            fontWeight = if (emphasised) FontWeight.SemiBold else null,
            color = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FlowDialog(
    index: Int,
    existing: CashFlow?,
    commaRadix: Boolean,
    onSave: (CashFlow) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember {
        mutableStateOf(existing?.let { plainNumber(abs(it.amount), commaRadix) } ?: "")
    }
    // The opening flow of an investment is nearly always money going out.
    var outflow by remember { mutableStateOf(existing?.let { it.amount < 0 } ?: (index == 0)) }
    var count by remember { mutableStateOf((existing?.count ?: 1).toString()) }
    val parsedAmount = parseNumber(amount, commaRadix)
    val parsedCount = count.toIntOrNull()?.takeIf { it in 1..CashFlows.MAX_COUNT }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add cash flow $index" else "Cash flow $index") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !outflow, onClick = { outflow = false }, label = { Text("Money in  +") })
                    FilterChip(selected = outflow, onClick = { outflow = true }, label = { Text("Money out  −") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && parsedAmount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = count,
                    onValueChange = { v -> count = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Times in a row") },
                    singleLine = true,
                    isError = parsedCount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedAmount != null && parsedCount != null,
                onClick = {
                    val magnitude = abs(parsedAmount ?: 0.0)
                    onSave(CashFlow(if (outflow) -magnitude else magnitude, parsedCount ?: 1))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun RateDialog(commaRadix: Boolean, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = parseNumber(text, commaRadix)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discount rate") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Percent per year") },
                singleLine = true,
                isError = text.isNotEmpty() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A number as editable text: no grouping, and no trailing zeros. */
private fun plainNumber(v: Double, commaRadix: Boolean): String {
    val s = java.math.BigDecimal(v).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
    return if (commaRadix) s.replace('.', ',') else s
}
