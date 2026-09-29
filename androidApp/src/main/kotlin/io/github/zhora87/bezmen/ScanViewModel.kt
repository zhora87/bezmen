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
import io.github.zhora87.bezmen.domain.Script
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
import io.github.zhora87.bezmen.ui.settings.JsonSettingsStore
import io.github.zhora87.bezmen.ui.settings.SettingsController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the scan pipeline for the activity's lifetime: the locale pack, the OCR engine (loaded in the
 * background right away, it takes over a second on a budget phone), the camera and the controller.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    val frame = ViewfinderFrame.PRICE_TAG
    val camera = TagCamera(app, frame)

    /** The pack the phone's country picks; settings may override it. */
    val autoPackId: String = LocalePacks.defaultId(app)

    val settings = SettingsController(
        viewModelScope,
        JsonSettingsStore(fileReader("settings.json"), fileWriter("settings.json"))
    )

    /** Packs are small JSON assets, so the current one is simply reloaded when settings change it. */
    val pack: StateFlow<LocalePack> = settings.state
        .map { it.packId ?: autoPackId }
        .distinctUntilChanged()
        .map { LocalePacks.load(app.assets, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalePacks.load(app.assets, autoPackId))

    private val models = AssetModelStore(app.assets)

    /** One engine per script, loaded on first use; the current pack's script is loaded right away. */
    private val engines = mutableMapOf<Script, Deferred<PaddleOnnxOcrEngine>>()

    val controller = ScanController(
        viewModelScope,
        parser = { PriceTagParser(pack.value) },
        currency = { pack.value.currency.code },
        capture = ::capture,
    )

    val comparison = ComparisonController(
        viewModelScope,
        JsonComparisonStore(fileReader("comparison.json"), fileWriter("comparison.json")),
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
                engineFor(pack.value.script).await()
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
        Log.d(TAG, "state ${state::class.simpleName}${detail.orEmpty()} pack=${pack.value.id}")
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
        val engine = engineFor(pack.value.script)
        val lines = withContext(Dispatchers.Default) { engine.await().recognize(frame.image) }
        if (BuildConfig.DEBUG) {
            DebugShots.save(getApplication(), frame.image)
            val ms = (System.nanoTime() - started) / NANOS_PER_MS
            Log.d(TAG, "frame ${frame.image.width}x${frame.image.height} sharpness ${frame.sharpness}, OCR $ms ms")
            lines.forEach { Log.d(TAG, "  line ${it.box} ${it.confidence}: ${it.text}") }
        }
        return CaptureOutcome.Recognized(lines)
    }

    private fun engineFor(script: Script): Deferred<PaddleOnnxOcrEngine> = engines.getOrPut(script) {
        viewModelScope.async(Dispatchers.Default) { PaddleOnnxOcrEngine(models, script) }
    }

    private fun fileReader(name: String): suspend () -> String? = {
        withContext(
            Dispatchers.IO
        ) { File(getApplication<Application>().filesDir, name).takeIf { it.isFile }?.readText() }
    }

    private fun fileWriter(name: String): suspend (String) -> Unit = { text ->
        withContext(Dispatchers.IO) { File(getApplication<Application>().filesDir, name).writeText(text) }
    }

    /** viewModelScope is already cancelled here, so loaded engines are closed synchronously. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCleared() {
        camera.shutdown()
        engines.values.filter {
            it.isCompleted && !it.isCancelled
        }.forEach { runCatching { it.getCompleted() }.getOrNull()?.close() }
    }

    private companion object {
        const val TAG = "ScanViewModel"
        const val NANOS_PER_MS = 1_000_000L
    }
}
