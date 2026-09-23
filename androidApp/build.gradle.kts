import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.ui)
    implementation(projects.core.domain)
    implementation(projects.core.ocr)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.onnxruntime.android) // the HardSwish probe talks to ONNX Runtime directly
}

android {
    namespace = "io.github.zhora87.bezmen"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.zhora87.bezmen"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Distribution channels. `foss` carries no Google dependencies and is what F-Droid builds.
    // `play` may add an optional ML Kit OCR engine for Latin-script locale packs (docs/architecture.md).
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // ONNX Runtime ships native code for every ABI; one APK per ABI keeps the download honest.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/**
 * The app must never request network access. The manifest strips INTERNET from any
 * dependency with tools:node="remove"; this task proves it on the merged manifest of every
 * variant and is wired into `check`, so CI fails the moment a library sneaks it back in.
 */
abstract class VerifyNoInternetPermission : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val file = mergedManifest.get().asFile
        if (file.readText().contains("android.permission.INTERNET")) {
            throw GradleException("INTERNET permission found in merged manifest: $file")
        }
        logger.lifecycle("OK: no INTERNET permission in ${file.parentFile.name}/${file.name}")
    }
}

/**
 * Assets for on-device tests: locale packs, the local corpus (photos never enter git, they are read
 * from the working tree) and optional probe models. Everything lands in the test APK only.
 */
abstract class PrepareDeviceCorpusTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localePacks: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val expected: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val images: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val probeModels: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun prepare() {
        fs.sync {
            into(outputDir)
            from(localePacks) { into("locale-packs") }
            from(expected) { into("corpus/expected") }
            from(images) { into("corpus/images") }
            from(probeModels) { into("probe") }
        }
    }
}

val prepareDeviceCorpus = tasks.register<PrepareDeviceCorpusTask>("prepareDeviceCorpus") {
    localePacks.from(fileTree(rootProject.file("locale-packs")) { include("*.json") })
    expected.from(fileTree(rootProject.file("corpus/expected")) { include("uk-atb-*.json") })
    images.from(fileTree(rootProject.file("corpus/images/uk/atb")) { include("*.jpg") })
    probeModels.from(fileTree(rootProject.file("build/models-original")) { include("*.onnx") })
    outputDir.set(layout.buildDirectory.dir("deviceCorpus"))
}

androidComponents {
    onVariants { variant ->
        // OCR models are fetched by :fetchModels (models.lock) and packed as assets/models/*.onnx.
        val fetchModels = rootProject.tasks.named("fetchModels", FetchModelsTask::class.java)
        variant.sources.assets?.addGeneratedSourceDirectory(fetchModels, FetchModelsTask::outputDir)
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(
            prepareDeviceCorpus,
            PrepareDeviceCorpusTask::outputDir,
        )

        val taskName = "verifyNoInternet" + variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoInternetPermission>(taskName) {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.named("check") { dependsOn(verify) }
    }
}
