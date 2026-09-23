package io.github.zhora87.bezmen.ocr.paddle

import android.content.res.AssetManager

/** Models packed into the APK under `assets/models/` by the build (see models.lock). */
class AssetModelStore(private val assets: AssetManager) : ModelStore {
    override fun read(name: String): ByteArray = assets.open("models/$name").use { it.readBytes() }
}
