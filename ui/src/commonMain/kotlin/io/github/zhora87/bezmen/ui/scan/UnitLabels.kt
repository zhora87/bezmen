package io.github.zhora87.bezmen.ui.scan

import androidx.compose.runtime.Composable
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.ui.resources.Res
import io.github.zhora87.bezmen.ui.resources.result_per
import io.github.zhora87.bezmen.ui.resources.unit_cl
import io.github.zhora87.bezmen.ui.resources.unit_dozen
import io.github.zhora87.bezmen.ui.resources.unit_g
import io.github.zhora87.bezmen.ui.resources.unit_kg
import io.github.zhora87.bezmen.ui.resources.unit_l
import io.github.zhora87.bezmen.ui.resources.unit_lb
import io.github.zhora87.bezmen.ui.resources.unit_mg
import io.github.zhora87.bezmen.ui.resources.unit_ml
import io.github.zhora87.bezmen.ui.resources.unit_pair
import io.github.zhora87.bezmen.ui.resources.unit_pc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Units offered in the quantity picker, in the order people meet them on tags. */
val PICKER_UNITS =
    listOf(MeasureUnit.GRAM, MeasureUnit.KILOGRAM, MeasureUnit.MILLILITRE, MeasureUnit.LITRE, MeasureUnit.PIECE)

private fun MeasureUnit.label(): StringResource = when (this) {
    MeasureUnit.MILLIGRAM -> Res.string.unit_mg
    MeasureUnit.GRAM -> Res.string.unit_g
    MeasureUnit.KILOGRAM -> Res.string.unit_kg
    MeasureUnit.POUND -> Res.string.unit_lb
    MeasureUnit.MILLILITRE -> Res.string.unit_ml
    MeasureUnit.CENTILITRE -> Res.string.unit_cl
    MeasureUnit.LITRE -> Res.string.unit_l
    MeasureUnit.PIECE -> Res.string.unit_pc
    MeasureUnit.PAIR -> Res.string.unit_pair
    MeasureUnit.DOZEN -> Res.string.unit_dozen
}

@Composable
fun unitLabel(unit: MeasureUnit): String = stringResource(unit.label())

/** "за 100 г", "за 1 шт". */
@Composable
fun perReference(reference: Quantity): String =
    stringResource(Res.string.result_per, QuantityInput.format(reference.value), unitLabel(reference.unit))

fun Money.withSymbol(symbol: String): String = "${format()} $symbol"
