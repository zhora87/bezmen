package io.github.zhora87.bezmen.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zhora87.bezmen.ui.resources.Res
import io.github.zhora87.bezmen.ui.resources.action_retry
import io.github.zhora87.bezmen.ui.resources.action_shoot
import io.github.zhora87.bezmen.ui.resources.error_camera
import io.github.zhora87.bezmen.ui.resources.error_models
import io.github.zhora87.bezmen.ui.resources.status_blurry
import io.github.zhora87.bezmen.ui.resources.status_loading
import io.github.zhora87.bezmen.ui.resources.status_nothing
import io.github.zhora87.bezmen.ui.resources.status_ready
import io.github.zhora87.bezmen.ui.resources.status_recognizing
import org.jetbrains.compose.resources.stringResource

/** Portrait camera image is 3:4; the preview box has exactly that shape so the frame maps 1:1. */
private const val PREVIEW_ASPECT = 3f / 4f
private const val DIM_ALPHA = 0.55f
private val SHUTTER_SIZE = 76.dp

/**
 * Viewfinder with the tag frame, a status line and the shutter. [preview] is the platform camera
 * view; [frameWidth] and [frameHeight] are the frame's share of the camera image.
 */
@Composable
fun ScanScreen(
    state: ScanState,
    frameWidth: Float,
    frameHeight: Float,
    currencySymbol: String,
    actions: ScanActions,
    preview: @Composable (Modifier) -> Unit,
) {
    if (state is ScanState.Result) {
        ResultCard(state.draft, currencySymbol, onEdit = actions.onEdit, onRetake = actions.onRetake)
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(PREVIEW_ASPECT)) {
            preview(Modifier.fillMaxSize())
            FrameOverlay(frameWidth, frameHeight, Modifier.fillMaxSize())
        }
        StatusLine(state, Modifier.padding(horizontal = 24.dp))
        BottomAction(state, actions, Modifier.padding(bottom = 24.dp))
    }
}

/** Callbacks of the scan screen, bundled to keep signatures short. */
class ScanActions(
    val onShutter: () -> Unit,
    val onRetake: () -> Unit,
    val onEdit: (TagDraft) -> Unit,
)

@Composable
private fun FrameOverlay(widthFraction: Float, heightFraction: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width * widthFraction
        val h = size.height * heightFraction
        val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
        val corner = CornerRadius(16.dp.toPx())
        val hole = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h, corner)
            )
        }
        clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = DIM_ALPHA)) }
        drawRoundRect(Color.White, topLeft, Size(w, h), corner, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun StatusLine(state: ScanState, modifier: Modifier) {
    val text = when (state) {
        ScanState.LoadingModels -> stringResource(Res.string.status_loading)
        ScanState.Ready -> stringResource(Res.string.status_ready)
        ScanState.Recognizing -> stringResource(Res.string.status_recognizing)
        ScanState.Blurry -> stringResource(Res.string.status_blurry)
        ScanState.NothingFound -> stringResource(Res.string.status_nothing)
        is ScanState.Error -> when (state.error) {
            ScanError.CAMERA -> stringResource(Res.string.error_camera)
            ScanError.MODELS -> stringResource(Res.string.error_models)
        }
        is ScanState.Result -> ""
    }
    Text(
        text,
        modifier,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BottomAction(state: ScanState, actions: ScanActions, modifier: Modifier) {
    when {
        state == ScanState.Error(ScanError.MODELS) -> Unit
        state is ScanState.Error -> Button(onClick = actions.onRetake, modifier) {
            Text(stringResource(Res.string.action_retry))
        }
        state == ScanState.LoadingModels || state == ScanState.Recognizing ->
            CircularProgressIndicator(modifier.size(SHUTTER_SIZE), color = Color.White)
        else -> ShutterButton(actions.onShutter, modifier)
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(Res.string.action_shoot)
    Button(
        onClick = onClick,
        modifier = modifier.size(SHUTTER_SIZE).semantics { contentDescription = label },
        shape = CircleShape,
    ) {}
}
