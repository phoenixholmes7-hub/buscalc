package com.buscalc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buscalc.app.engine.AmortSchedule
import com.buscalc.app.engine.Amortization

/** The loan terms shown at the top of the schedule, already formatted for display. */
data class LoanSummary(
    val n: String,
    val iyr: String,
    val pyr: String,
    val pv: String,
    val pmt: String,
    val fv: String,
    val begin: Boolean,
)

private const val SHARE_ROW_LIMIT = 1500

@Composable
fun AmortizationScreen(
    loan: LoanSummary,
    initialFrom: Int,
    initialTo: Int,
    compute: (Int, Int) -> AmortSchedule,
    format: (Double) -> String,
    onShare: (String) -> Unit,
    onBack: () -> Unit,
) {
    var fromText by rememberSaveable { mutableStateOf(initialFrom.toString()) }
    var toText by rememberSaveable { mutableStateOf(initialTo.toString()) }
    var from by rememberSaveable { mutableStateOf(initialFrom) }
    var to by rememberSaveable { mutableStateOf(initialTo) }
    var rangeError by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val result = remember(from, to) { runCatching { compute(from, to) } }
    val schedule = result.getOrNull()

    fun update() {
        val a = fromText.trim().toIntOrNull()
        val b = toText.trim().toIntOrNull()
        if (a == null || b == null || a < 1 || b < a || b > Amortization.MAX_PERIODS) {
            rangeError = true
        } else {
            rangeError = false
            from = a
            to = b
            focus.clearFocus()
        }
    }

    ScreenScaffold(
        title = "Amortization",
        onBack = onBack,
        actions = {
            IconButton(
                enabled = schedule != null,
                onClick = { schedule?.let { onShare(shareText(loan, from, to, it, format)) } },
            ) { Icon(AppIcons.Share, contentDescription = "Share schedule") }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Term("N", loan.n)
                            Term("I/YR", loan.iyr + " %")
                            Term("P/YR", loan.pyr + if (loan.begin) "  ·  Begin" else "  ·  End")
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Term("PV", loan.pv)
                            Term("PMT", loan.pmt)
                            Term("FV", loan.fv)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    if (schedule != null) {
                        Text(
                            "Payments $from to $to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Total("Interest", format(schedule.totalInterest).withMinus())
                        Total("Principal", format(schedule.totalPrincipal).withMinus())
                        Total("Total paid", format(schedule.totalPaid).withMinus(), emphasised = true)
                    } else {
                        Text(
                            result.exceptionOrNull()?.message ?: "Cannot build a schedule from these values",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { v -> fromText = v.filter { it.isDigit() }.take(5) },
                    label = { Text("From payment") },
                    singleLine = true,
                    isError = rangeError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { v -> toText = v.filter { it.isDigit() }.take(5) },
                    label = { Text("To payment") },
                    singleLine = true,
                    isError = rangeError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { update() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { update() }) { Text("Update") }
            }

            TableRow("#", "Principal", "Interest", "Balance", header = true)
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(schedule?.rows.orEmpty(), key = { _, row -> row.period }) { index, row ->
                    TableRow(
                        row.period.toString(),
                        format(row.principal).withMinus(),
                        format(row.interest).withMinus(),
                        format(row.balance).withMinus(),
                        striped = index % 2 == 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Term(name: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value.withMinus(),
            modifier = Modifier.weight(1f),
            style = NumberStyle,
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun Total(name: String, value: String, emphasised: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(name, modifier = Modifier.weight(1f), fontWeight = if (emphasised) FontWeight.SemiBold else null)
        Text(
            value,
            style = NumberStyle,
            fontSize = 16.sp,
            fontWeight = if (emphasised) FontWeight.SemiBold else null,
            color = if (emphasised) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}

@Composable
private fun TableRow(
    number: String,
    principal: String,
    interest: String,
    balance: String,
    header: Boolean = false,
    striped: Boolean = false,
) {
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (striped) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = if (header) 8.dp else 9.dp),
    ) {
        Text(number, Modifier.weight(0.13f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = NumberStyle, fontSize = 14.sp, fontWeight = weight)
        for ((text, w) in listOf(principal to 0.27f, interest to 0.27f, balance to 0.33f)) {
            Text(
                text,
                Modifier.weight(w),
                color = color,
                style = NumberStyle,
                fontSize = 14.sp,
                fontWeight = weight,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

private fun shareText(loan: LoanSummary, from: Int, to: Int, s: AmortSchedule, format: (Double) -> String): String =
    buildString {
        appendLine("Amortization schedule, payments $from to $to")
        appendLine("N ${loan.n}   I/YR ${loan.iyr}%   P/YR ${loan.pyr}   ${if (loan.begin) "Begin" else "End"} mode")
        appendLine("PV ${loan.pv}   PMT ${loan.pmt}   FV ${loan.fv}")
        appendLine()
        appendLine("Interest: ${format(s.totalInterest)}")
        appendLine("Principal: ${format(s.totalPrincipal)}")
        appendLine("Total paid: ${format(s.totalPaid)}")
        appendLine()
        appendLine("#\tPrincipal\tInterest\tBalance")
        for (row in s.rows.take(SHARE_ROW_LIMIT)) {
            appendLine("${row.period}\t${format(row.principal)}\t${format(row.interest)}\t${format(row.balance)}")
        }
        if (s.rows.size > SHARE_ROW_LIMIT) appendLine("… ${s.rows.size - SHARE_ROW_LIMIT} more payments not shown")
    }
