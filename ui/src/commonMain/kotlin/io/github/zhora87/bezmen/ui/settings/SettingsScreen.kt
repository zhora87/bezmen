package io.github.zhora87.bezmen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.zhora87.bezmen.domain.DisplayUnits
import io.github.zhora87.bezmen.domain.Settings
import io.github.zhora87.bezmen.ui.resources.Res
import io.github.zhora87.bezmen.ui.resources.action_back
import io.github.zhora87.bezmen.ui.resources.pack_auto
import io.github.zhora87.bezmen.ui.resources.pack_en_generic
import io.github.zhora87.bezmen.ui.resources.pack_ru
import io.github.zhora87.bezmen.ui.resources.pack_uk
import io.github.zhora87.bezmen.ui.resources.settings_pack
import io.github.zhora87.bezmen.ui.resources.settings_title
import io.github.zhora87.bezmen.ui.resources.settings_units
import io.github.zhora87.bezmen.ui.resources.units_per_hundred
import io.github.zhora87.bezmen.ui.resources.units_standard
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Packs the app ships, in the order shown. */
val SHIPPED_PACKS = listOf("uk", "ru", "en-generic")

private fun packName(id: String): StringResource = when (id) {
    "uk" -> Res.string.pack_uk
    "ru" -> Res.string.pack_ru
    else -> Res.string.pack_en_generic
}

/** Unit for prices and the recognition pack; [autoPackId] is what the phone's country picks. */
@Composable
fun SettingsScreen(settings: Settings, autoPackId: String, actions: SettingsActions) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp),
            )
            Section(stringResource(Res.string.settings_units))
            Option(stringResource(Res.string.units_standard), settings.units == DisplayUnits.STANDARD) {
                actions.onUnits(DisplayUnits.STANDARD)
            }
            Option(stringResource(Res.string.units_per_hundred), settings.units == DisplayUnits.PER_HUNDRED) {
                actions.onUnits(DisplayUnits.PER_HUNDRED)
            }
            Section(stringResource(Res.string.settings_pack))
            Option(
                stringResource(Res.string.pack_auto, stringResource(packName(autoPackId))),
                settings.packId == null
            ) {
                actions.onPack(null)
            }
            SHIPPED_PACKS.forEach { id ->
                Option(stringResource(packName(id)), settings.packId == id) { actions.onPack(id) }
            }
            OutlinedButton(onClick = actions.onBack, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(Res.string.action_back))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect
        ).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
