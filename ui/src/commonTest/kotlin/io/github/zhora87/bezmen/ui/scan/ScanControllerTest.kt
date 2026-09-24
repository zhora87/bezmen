package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.parser.PriceTagParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ScanControllerTest {
    private val pack = LocalePack.fromJson(
        """
        { "id": "uk", "version": 1, "script": "CYRILLIC", "ocrModel": "cyrillic",
          "currency": { "code": "UAH", "symbols": ["₴", "грн"] },
          "units": { "g": ["г"], "kg": ["кг"], "ml": ["мл"], "l": ["л"], "pc": ["шт"] } }
        """.trimIndent(),
    )
    private val tagLines = listOf(
        OcrLine("Молоко 900 мл", Box(0.05f, 0.05f, 0.6f, 0.2f), 0.95f),
        OcrLine("49,90 грн", Box(0.3f, 0.4f, 0.95f, 0.9f), 0.99f),
    )

    private fun TestScope.controller(capture: suspend () -> CaptureOutcome) =
        ScanController(this, PriceTagParser(pack), pack.currency.code, capture)

    @Test
    fun `starts loading models and becomes ready`() = runTest {
        val c = controller { CaptureOutcome.Recognized(tagLines) }
        assertEquals(ScanState.LoadingModels, c.state.value)

        c.onEngineReady()

        assertEquals(ScanState.Ready, c.state.value)
    }

    @Test
    fun `shutter recognises the tag and shows the result`() = runTest {
        val gate = CompletableDeferred<CaptureOutcome>()
        val c = controller { gate.await() }
        c.onEngineReady()

        c.onShutter()
        advanceUntilIdle()
        assertEquals(ScanState.Recognizing, c.state.value)
        gate.complete(CaptureOutcome.Recognized(tagLines))
        advanceUntilIdle()

        val result = assertIs<ScanState.Result>(c.state.value)
        assertEquals("49,90", result.draft.priceText)
    }

    @Test
    fun `blurry frame asks to hold steady and allows another shot`() = runTest {
        var outcome: CaptureOutcome = CaptureOutcome.Blurry
        val c = controller { outcome }
        c.onEngineReady()

        c.onShutter()
        advanceUntilIdle()
        assertEquals(ScanState.Blurry, c.state.value)

        outcome = CaptureOutcome.Recognized(tagLines)
        c.onShutter()
        advanceUntilIdle()
        assertIs<ScanState.Result>(c.state.value)
    }

    @Test
    fun `no text on the frame is reported as nothing found`() = runTest {
        val c = controller { CaptureOutcome.Recognized(emptyList()) }
        c.onEngineReady()

        c.onShutter()
        advanceUntilIdle()

        assertEquals(ScanState.NothingFound, c.state.value)
    }

    @Test
    fun `shutter is ignored while models load or while recognising`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<CaptureOutcome>()
        val c = controller {
            calls++
            gate.await()
        }
        c.onShutter()
        advanceUntilIdle()
        assertEquals(0, calls)

        c.onEngineReady()
        c.onShutter()
        c.onShutter()
        advanceUntilIdle()

        assertEquals(1, calls)
        gate.complete(CaptureOutcome.Blurry)
    }

    @Test
    fun `camera failure is an error and retake returns to ready`() = runTest {
        val c = controller { CaptureOutcome.Failed("camera closed") }
        c.onEngineReady()

        c.onShutter()
        advanceUntilIdle()
        assertEquals(ScanState.Error(ScanError.CAMERA), c.state.value)

        c.onRetake()
        assertEquals(ScanState.Ready, c.state.value)
    }

    @Test
    fun `model load failure is a permanent error`() = runTest {
        val c = controller { CaptureOutcome.Recognized(tagLines) }

        c.onEngineFailed()
        c.onRetake()

        assertEquals(ScanState.Error(ScanError.MODELS), c.state.value)
    }

    @Test
    fun `edits replace the draft in the result`() = runTest {
        val c = controller { CaptureOutcome.Recognized(tagLines) }
        c.onEngineReady()
        c.onShutter()
        advanceUntilIdle()
        val draft = assertIs<ScanState.Result>(c.state.value).draft

        c.onEdit(draft.copy(priceText = "45,00"))

        assertEquals("45,00", assertIs<ScanState.Result>(c.state.value).draft.priceText)
    }
}
