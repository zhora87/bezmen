package io.github.zhora87.bezmen

import ai.onnxruntime.OrtException
import android.app.Application
import android.util.Log
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zhora87.bezmen.camera.SharpFrame
import io.github.zhora87.bezmen.camera.TagCamera
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.parser.PriceTagParser
import io.github.zhora87.bezmen.ocr.image.FrameOps
import io.github.zhora87.bezmen.ocr.image.ViewfinderFrame
import io.github.zhora87.bezmen.ocr.paddle.AssetModelStore
import io.github.zhora87.bezmen.ocr.paddle.PaddleOnnxOcrEngine
import io.github.zhora87.bezmen.ui.AppScreen
import io.github.zhora87.bezmen.ui.compare.ComparisonController
import io.github.zhora87.bezmen.ui.compare.JsonComparisonStore
import io.github.zhora87.bezmen.ui.scan.CaptureOutcome
import io.github.zhora87.bezmen.ui.scan.ScanController
import io.github.zhora87.bezmen.ui.scan.ScanState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the scan pipeline for the activity's lifetime: the locale pack, the OCR engine (loaded in the
 * background right away, it takes over a second on a budget phone), the camera and the controller.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    val frame = ViewfinderFrame.PRICE_TAG
    val pack: LocalePack = LocalePacks.load(app.assets, LocalePacks.defaultId(app))
    val currencySymbol: String = pack.currency.symbols.first()
    val camera = TagCamera(app, frame)

    private val engine = viewModelScope.async(Dispatchers.Default) {
        PaddleOnnxOcrEngine(AssetModelStore(app.assets), pack.script)
    }

    val controller = ScanController(viewModelScope, PriceTagParser(pack), pack.currency.code, ::capture)

    private val comparisonFile = File(app.filesDir, "comparison.json")
    val comparison = ComparisonController(
        viewModelScope,
        JsonComparisonStore(
            read = { withContext(Dispatchers.IO) { comparisonFile.takeIf { it.isFile }?.readText() } },
            write = { text -> withContext(Dispatchers.IO) { comparisonFile.writeText(text) } },
        ),
    )

    private val mutableScreen = MutableStateFlow(AppScreen.SCAN)
    val screen: StateFlow<AppScreen> = mutableScreen.asStateFlow()

    fun open(screen: AppScreen) {
        mutableScreen.value = screen
    }

    /** Puts the reviewed tag into the comparison list and returns to the viewfinder for the next one. */
    fun addToComparison() {
        val draft = (controller.state.value as? ScanState.Result)?.draft ?: return
        if (comparison.add(draft)) controller.onRetake()
    }

    init {
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                controller.state.collect(::logState)
            }
        }
        viewModelScope.launch {
            try {
                engine.await()
                controller.onEngineReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: OrtException) {
                Log.e(TAG, "OCR models failed to load", e)
                controller.onEngineFailed()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "OCR models failed to load", e)
                controller.onEngineFailed()
            }
        }
    }

    private fun logState(state: ScanState) {
        val detail = (state as? ScanState.Result)?.draft?.let {
            " price=${it.priceText} card=${it.cardPriceText} qty=${it.quantityText} ${it.unit} " +
                "check=${it.needsCheck} name=${it.name}"
        }
        Log.d(TAG, "state ${state::class.simpleName}${detail.orEmpty()} pack=${pack.id}")
    }

    private suspend fun capture(): CaptureOutcome = try {
        val best = camera.captureSharpest()
        when {
            best == null -> CaptureOutcome.Failed("no frames from the camera")
            best.sharpness < FrameOps.BLUR_THRESHOLD -> CaptureOutcome.Blurry
            else -> recognize(best)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ImageCaptureException) {
        Log.e(TAG, "capture failed", e)
        CaptureOutcome.Failed(e.message ?: "capture failed")
    } catch (e: OrtException) {
        Log.e(TAG, "recognition failed", e)
        CaptureOutcome.Failed(e.message ?: "recognition failed")
    } catch (e: IllegalStateException) {
        Log.e(TAG, "capture failed", e)
        CaptureOutcome.Failed(e.message ?: "capture failed")
    }

    private suspend fun recognize(frame: SharpFrame): CaptureOutcome {
        val started = System.nanoTime()
        val lines = withContext(Dispatchers.Default) { engine.await().recognize(frame.image) }
        if (BuildConfig.DEBUG) {
            DebugShots.save(getApplication(), frame.image)
            val ms = (System.nanoTime() - started) / NANOS_PER_MS
            Log.d(TAG, "frame ${frame.image.width}x${frame.image.height} sharpness ${frame.sharpness}, OCR $ms ms")
            lines.forEach { Log.d(TAG, "  line ${it.box} ${it.confidence}: ${it.text}") }
        }
        return CaptureOutcome.Recognized(lines)
    }

    /** viewModelScope is already cancelled here, so a loaded engine is closed synchronously. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCleared() {
        camera.shutdown()
        if (engine.isCompleted && !engine.isCancelled) runCatching { engine.getCompleted() }.getOrNull()?.close()
    }

    private companion object {
        const val TAG = "ScanViewModel"
        const val NANOS_PER_MS = 1_000_000L
    }
}
