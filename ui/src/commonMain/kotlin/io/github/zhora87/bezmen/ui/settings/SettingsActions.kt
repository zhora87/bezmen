package io.github.zhora87.bezmen.ui.settings

import io.github.zhora87.bezmen.domain.DisplayUnits

/** Callbacks of the settings screen. */
class SettingsActions(
    val onUnits: (DisplayUnits) -> Unit,
    val onPack: (String?) -> Unit,
    val onBack: () -> Unit,
)
