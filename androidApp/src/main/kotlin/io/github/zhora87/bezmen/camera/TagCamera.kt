package io.github.zhora87.bezmen.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.zhora87.bezmen.ocr.image.FrameOps
import io.github.zhora87.bezmen.ocr.image.ImageOps
import io.github.zhora87.bezmen.ocr.image.RgbImage
import io.github.zhora87.bezmen.ocr.image.ViewfinderFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** A cropped, upright shot of the frame and how sharp it is. */
class SharpFrame(val image: RgbImage, val sharpness: Float)

/**
 * CameraX for one job: keep a price tag in the viewfinder frame in focus and hand over a full
 * resolution still cropped to the frame. The small print ("грн / 180 г") is only a few dozen pixels
 * tall even in a still; preview-sized analysis frames make it unreadable. Focus and exposure are
 * metered on the frame only, so bright packaging around the tag does not pull the autofocus away.
 */
class TagCamera(private val context: Context, private val frame: ViewfinderFrame) {
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    private val selector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()

    suspend fun bind(owner: LifecycleOwner, view: PreviewView) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().setResolutionSelector(selector).build()
        preview.surfaceProvider = view.surfaceProvider
        val capture = ImageCapture.Builder()
            .setResolutionSelector(selector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        provider.unbindAll()
        val bound = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        camera = bound
        imageCapture = capture
        bound.cameraInfo.zoomState.value?.let { zoom ->
            bound.cameraControl.setZoomRatio(DEFAULT_ZOOM.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
        }
        focusOnFrame()
    }

    /** Refocuses on the frame and takes a still, cropped to the frame and upright; null if the camera is not ready. */
    suspend fun captureSharpest(): SharpFrame? {
        val capture = imageCapture ?: return null
        focusOnFrame()
        val started = System.nanoTime()
        val proxy = takePicture(capture)
        val shot = withContext(Dispatchers.Default) { proxy.use { decodeFrame(it) } }
        val sharpness = withContext(Dispatchers.Default) { FrameOps.sharpness(shot) }
        val ms = (System.nanoTime() - started) / NANOS_PER_MS
        Log.d(TAG, "still ${shot.width}x${shot.height} sharpness ${sharpness.toInt()} in $ms ms")
        return SharpFrame(shot, sharpness)
    }

    fun shutdown() {
        captureExecutor.shutdown()
    }

    private suspend fun takePicture(capture: ImageCapture): ImageProxy = suspendCancellableCoroutine { cont ->
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)

                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            },
        )
    }

    /**
     * JPEG still to the upright frame crop. Decoding is subsampled so the crop is at most
     * [MAX_CROP_WIDTH] wide: more pixels only cost time, the recogniser works on 48 px lines.
     */
    private fun decodeFrame(proxy: ImageProxy): RgbImage {
        val buffer = proxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val rotation = proxy.imageInfo.rotationDegrees
        val sideways = rotation == QUARTER || rotation == THREE_QUARTERS
        val uprightWidth = if (sideways) proxy.height else proxy.width
        val cropWidth = uprightWidth * frame.widthFraction
        var sample = 1
        while (cropWidth / (sample * 2) >= MAX_CROP_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("could not decode the still")
        val sensorFrame = if (sideways) ViewfinderFrame(frame.heightFraction, frame.widthFraction) else frame
        val w = (bitmap.width * sensorFrame.widthFraction).roundToInt()
        val h = (bitmap.height * sensorFrame.heightFraction).roundToInt()
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, (bitmap.width - w) / 2, (bitmap.height - h) / 2, w, h)
        bitmap.recycle()
        for (i in pixels.indices) pixels[i] = pixels[i] and RGB_MASK
        return ImageOps.rotate(RgbImage(w, h, pixels), rotation)
    }

    /** Autofocus and exposure metered on the frame only; kept there instead of drifting back to the scene. */
    private suspend fun focusOnFrame() {
        val control = camera?.cameraControl ?: return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
            .createPoint(CENTER, CENTER, frame.widthFraction * METERING_SHARE)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        val future = control.startFocusAndMetering(action)
        val settled = withTimeoutOrNull(FOCUS_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                future.addListener({ cont.resume(true) }, ContextCompat.getMainExecutor(context))
            }
        }
        if (settled == null) Log.i(TAG, "focus did not report within $FOCUS_TIMEOUT_MS ms, shooting anyway")
    }

    private companion object {
        const val TAG = "TagCamera"
        const val DEFAULT_ZOOM = 1.5f
        const val FOCUS_TIMEOUT_MS = 1_000L
        const val MAX_CROP_WIDTH = 2_048f
        const val CENTER = 0.5f
        const val METERING_SHARE = 0.5f
        const val QUARTER = 90
        const val THREE_QUARTERS = 270
        const val RGB_MASK = 0xFFFFFF
        const val NANOS_PER_MS = 1_000_000L
    }
}
