package io.github.zhora87.bezmen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zhora87.bezmen.ui.resources.Res
import io.github.zhora87.bezmen.ui.resources.permission_grant
import io.github.zhora87.bezmen.ui.resources.permission_settings
import io.github.zhora87.bezmen.ui.resources.permission_text
import io.github.zhora87.bezmen.ui.resources.permission_title
import org.jetbrains.compose.resources.stringResource

/** Explains why the camera is needed; after a refusal also offers the system settings. */
@Composable
fun PermissionScreen(askedBefore: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(
                16.dp,
                alignment = androidx.compose.ui.Alignment.CenterVertically
            ),
        ) {
            Text(stringResource(Res.string.permission_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.permission_text), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.permission_grant))
            }
            if (askedBefore) {
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.permission_settings))
                }
            }
        }
    }
}
