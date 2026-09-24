package io.github.zhora87.bezmen

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import io.github.zhora87.bezmen.ocr.image.RgbImage
import java.io.File

/**
 * Debug builds only: keeps the cropped frame that went into OCR in the app's own external files
 * directory (shots/), so a bad recognition can be replayed on the desktop with tools/ocr-dump.
 * Nothing leaves the phone; the directory is removed with the app.
 */
internal object DebugShots {
    private const val MAX_SHOTS = 50
    private const val JPEG_QUALITY = 92
    private const val OPAQUE = 0xFF000000.toInt()

    fun save(context: Context, image: RgbImage) {
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        dir.listFiles()?.sortedBy { it.name }?.dropLast(MAX_SHOTS - 1)?.forEach { it.delete() }
        val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
        val argb = IntArray(image.pixels.size) { image.pixels[it] or OPAQUE }
        val bitmap = Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } }
            .onFailure { Log.w("DebugShots", "could not save $file", it) }
        bitmap.recycle()
    }
}
