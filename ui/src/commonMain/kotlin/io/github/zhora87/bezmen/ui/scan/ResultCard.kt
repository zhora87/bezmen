package io.github.zhora87.bezmen.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zhora87.bezmen.domain.parser.FieldKind
import io.github.zhora87.bezmen.ui.resources.Res
import io.github.zhora87.bezmen.ui.resources.action_retake
import io.github.zhora87.bezmen.ui.resources.field_card_price
import io.github.zhora87.bezmen.ui.resources.field_name
import io.github.zhora87.bezmen.ui.resources.field_price
import io.github.zhora87.bezmen.ui.resources.field_quantity
import io.github.zhora87.bezmen.ui.resources.result_card_price
import io.github.zhora87.bezmen.ui.resources.result_check
import io.github.zhora87.bezmen.ui.resources.result_need_price
import io.github.zhora87.bezmen.ui.resources.result_need_quantity
import io.github.zhora87.bezmen.ui.resources.result_old_price
import io.github.zhora87.bezmen.ui.resources.result_weighted
import org.jetbrains.compose.resources.stringResource

/** The recognised tag: the unit price in large type, then every field editable in place. */
@Composable
fun ResultCard(draft: TagDraft, currencySymbol: String, onEdit: (TagDraft) -> Unit, onRetake: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UnitPriceHeader(draft, currencySymbol)
            if (draft.needsCheck) CheckBanner()
            draft.oldPrice?.let { Text(stringResource(Res.string.result_old_price, it.withSymbol(currencySymbol))) }
            Fields(draft, currencySymbol, onEdit)
            Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_retake))
            }
        }
    }
}

@Composable
private fun UnitPriceHeader(draft: TagDraft, currencySymbol: String) {
    val shown = draft.displayPrice()
    if (shown == null) {
        val hint = when (FieldKind.PRICE) {
            in draft.missing -> Res.string.result_need_price
            else -> Res.string.result_need_quantity
        }
        Text(stringResource(hint), style = MaterialTheme.typography.titleLarge)
        return
    }
    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(shown.amount.withSymbol(currencySymbol), style = MaterialTheme.typography.displayMedium)
        Text(perReference(shown.reference), style = MaterialTheme.typography.titleLarge)
    }
    draft.displayCardPrice()?.let { card ->
        Text(
            stringResource(
                Res.string.result_card_price,
                card.amount.withSymbol(currencySymbol),
                perReference(card.reference),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (draft.isWeighted) {
        Text(
            stringResource(
                Res.string.result_weighted,
                QuantityInput.format(shown.reference.value),
                unitLabel(shown.reference.unit),
            ),
        )
    }
}

@Composable
private fun CheckBanner() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            stringResource(Res.string.result_check),
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun Fields(draft: TagDraft, currencySymbol: String, onEdit: (TagDraft) -> Unit) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onEdit(draft.copy(name = it)) },
        label = { Text(stringResource(Res.string.field_name)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.priceText,
        onValueChange = { onEdit(draft.copy(priceText = it)) },
        label = { Text(stringResource(Res.string.field_price)) },
        suffix = { Text(currencySymbol) },
        isError = FieldKind.PRICE in draft.missing,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.cardPriceText.isNotEmpty()) {
        OutlinedTextField(
            value = draft.cardPriceText,
            onValueChange = { onEdit(draft.copy(cardPriceText = it)) },
            label = { Text(stringResource(Res.string.field_card_price)) },
            suffix = { Text(currencySymbol) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = draft.quantityText,
        onValueChange = { onEdit(draft.copy(quantityText = it)) },
        label = { Text(stringResource(Res.string.field_quantity)) },
        isError = FieldKind.QUANTITY in draft.missing,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PICKER_UNITS.forEach { unit ->
            FilterChip(
                selected = draft.unit == unit,
                onClick = { onEdit(draft.copy(unit = unit)) },
                label = { Text(unitLabel(unit)) },
            )
        }
    }
}
