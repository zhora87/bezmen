package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.parser.ParseResult
import io.github.zhora87.bezmen.domain.parser.PriceTagParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the camera side hands back after the shutter: recognised lines, a blurred burst or a failure. */
sealed interface CaptureOutcome {
    data class Recognized(val lines: List<OcrLine>) : CaptureOutcome

    data object Blurry : CaptureOutcome

    data class Failed(val reason: String) : CaptureOutcome
}

enum class ScanError { MODELS, CAMERA }

sealed interface ScanState {
    data object LoadingModels : ScanState

    data object Ready : ScanState

    data object Recognizing : ScanState

    data object Blurry : ScanState

    data object NothingFound : ScanState

    data class Result(val draft: TagDraft) : ScanState

    data class Error(val error: ScanError) : ScanState
}

/**
 * The scan screen's state machine. Capture and OCR happen behind [capture] (camera, crop, focus
 * check, engine); this class only sequences them and turns lines into an editable [TagDraft].
 */
class ScanController(
    private val scope: CoroutineScope,
    private val parser: PriceTagParser,
    private val currency: String,
    private val capture: suspend () -> CaptureOutcome,
) {
    private val mutable = MutableStateFlow<ScanState>(ScanState.LoadingModels)
    val state: StateFlow<ScanState> = mutable.asStateFlow()

    fun onEngineReady() {
        if (mutable.value == ScanState.LoadingModels) mutable.value = ScanState.Ready
    }

    fun onEngineFailed() {
        mutable.value = ScanState.Error(ScanError.MODELS)
    }

    fun onShutter() {
        val current = mutable.value
        val canShoot = current == ScanState.Ready || current == ScanState.Blurry || current == ScanState.NothingFound
        if (!canShoot) return
        mutable.value = ScanState.Recognizing
        scope.launch { mutable.value = toState(capture()) }
    }

    /** Back to the viewfinder from a result, a hint or a camera error. A model error stays. */
    fun onRetake() {
        if (mutable.value != ScanState.Error(ScanError.MODELS)) mutable.value = ScanState.Ready
    }

    fun onEdit(draft: TagDraft) {
        if (mutable.value is ScanState.Result) mutable.value = ScanState.Result(draft)
    }

    private fun toState(outcome: CaptureOutcome): ScanState = when (outcome) {
        CaptureOutcome.Blurry -> ScanState.Blurry
        is CaptureOutcome.Failed -> ScanState.Error(ScanError.CAMERA)
        is CaptureOutcome.Recognized -> when (val result = parser.parse(outcome.lines)) {
            ParseResult.Nothing -> ScanState.NothingFound
            else -> ScanState.Result(TagDraft.from(result, currency))
        }
    }
}
