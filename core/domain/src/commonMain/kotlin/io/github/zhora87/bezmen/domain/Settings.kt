package io.github.zhora87.bezmen.domain

import kotlinx.serialization.Serializable

/** Which amount unit prices are shown for. Comparison does not depend on it, only the displayed number. */
@Serializable
enum class DisplayUnits {
    /** 1 kg, 1 l, 1 piece: the amounts people price goods by. */
    STANDARD,

    /** 100 g, 100 ml, 1 piece: what nutrition labels and deli counters use. */
    PER_HUNDRED,
    ;

    fun referenceFor(dimension: Dimension): Quantity = when (dimension) {
        Dimension.COUNT -> References.PER_PIECE
        Dimension.MASS -> if (this == STANDARD) References.PER_1_KG else References.PER_100_G
        Dimension.VOLUME -> if (this == STANDARD) References.PER_1_L else References.PER_100_ML
    }
}

/** User settings. [packId] null means the recognition pack follows the phone's country. */
@Serializable
data class Settings(val units: DisplayUnits = DisplayUnits.STANDARD, val packId: String? = null)
