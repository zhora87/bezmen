package io.github.zhora87.bezmen.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.zhora87.bezmen.domain.References
import io.github.zhora87.bezmen.domain.comparison.RankedItem
import io.github.zhora87.bezmen.ui.resources.Res
import io.github.zhora87.bezmen.ui.resources.action_cancel
import io.github.zhora87.bezmen.ui.resources.action_clear
import io.github.zhora87.bezmen.ui.resources.action_delete
import io.github.zhora87.bezmen.ui.resources.action_shoot_more
import io.github.zhora87.bezmen.ui.resources.compare_cheapest
import io.github.zhora87.bezmen.ui.resources.compare_clear_text
import io.github.zhora87.bezmen.ui.resources.compare_clear_title
import io.github.zhora87.bezmen.ui.resources.compare_dearer
import io.github.zhora87.bezmen.ui.resources.compare_empty
import io.github.zhora87.bezmen.ui.resources.compare_not_comparable
import io.github.zhora87.bezmen.ui.resources.compare_title
import io.github.zhora87.bezmen.ui.resources.compare_unnamed
import io.github.zhora87.bezmen.ui.scan.QuantityInput
import io.github.zhora87.bezmen.ui.scan.perReference
import io.github.zhora87.bezmen.ui.scan.unitLabel
import io.github.zhora87.bezmen.ui.scan.withSymbol
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Scanned products ranked by unit price, cheapest first; items of another dimension at the end. */
@Composable
fun ComparisonScreen(state: ComparisonState, currencySymbol: String, actions: ComparisonActions) {
    var confirmClear by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
            Header(state.items.size, onClear = { confirmClear = true })
            if (state.items.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.compare_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.ranked, key = { it.item.id }) { row ->
                        ItemRow(row, currencySymbol, onRemove = { actions.onRemove(row.item.id) })
                    }
                }
            }
            Button(onClick = actions.onShootMore, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(stringResource(Res.string.action_shoot_more))
            }
        }
    }
    if (confirmClear) {
        ClearDialog(
            onConfirm = {
                confirmClear = false
                actions.onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Header(count: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.compare_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (count > 0) TextButton(onClick = onClear) { Text(stringResource(Res.string.action_clear)) }
    }
}

@Composable
private fun ItemRow(row: RankedItem, currencySymbol: String, onRemove: () -> Unit) {
    val item = row.item
    val reference = References.defaultFor(item.quantity.dimension)
    val colors = if (row.rank == 1) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(colors = colors) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.rank?.let { "$it. " }.orEmpty() + (item.name ?: stringResource(Res.string.compare_unnamed)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text(stringResource(Res.string.action_delete)) }
            }
            Text(
                item.unitPrice.per(reference).withSymbol(currencySymbol) + " " + perReference(reference),
                style = MaterialTheme.typography.headlineSmall,
            )
            val quantity = QuantityInput.format(item.quantity.value) + " " + unitLabel(item.quantity.unit)
            Text(
                item.price.withSymbol(currencySymbol) + " за " + quantity + " · " + verdict(row),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun verdict(row: RankedItem): String = when {
    !row.isComparable -> stringResource(Res.string.compare_not_comparable)
    row.rank == 1 -> stringResource(Res.string.compare_cheapest)
    else -> stringResource(Res.string.compare_dearer, (row.deltaPercent ?: 0.0).roundToInt())
}

@Composable
private fun ClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.compare_clear_title)) },
        text = { Text(stringResource(Res.string.compare_clear_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.action_clear)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
