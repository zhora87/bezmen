import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Downloads the OCR models listed in models.lock, verifies their sha256 and lays them out as
 * `<outputDir>/models/<name>`, which the Android build adds as an assets root (asset path
 * `models/<name>`). Files already present with the right hash are kept, so a build with a warm
 * output directory needs no network.
 */
abstract class FetchModelsTask : DefaultTask() {
    @get:InputFile
    abstract val lockFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        @Suppress("UNCHECKED_CAST")
        val lock = JsonSlurper().parse(lockFile.get().asFile) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val models = lock["models"] as List<Map<String, String>>
        val target = modelsDirectory(outputDir.get().asFile).apply { mkdirs() }
        for (model in models) {
            val file = target.resolve(model.getValue("name"))
            val expected = model.getValue("sha256")
            if (file.exists() && sha256(file) == expected) {
                logger.info("model ${file.name} is up to date")
                continue
            }
            logger.lifecycle("downloading ${file.name} from ${model.getValue("url")}")
            URI(model.getValue("url")).toURL().openStream().use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            val actual = sha256(file)
            if (actual != expected) {
                file.delete()
                throw GradleException("sha256 mismatch for ${file.name}: expected $expected, got $actual")
            }
        }
    }

    companion object {
        const val MODELS_SUBDIR = "models"
        private const val BUFFER_SIZE = 1 shl 16

        /** Where the model files end up inside the task output directory. */
        fun modelsDirectory(outputDir: File): File = outputDir.resolve(MODELS_SUBDIR)

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
