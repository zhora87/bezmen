import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        testRuns.named("test") {
            executionTask.configure { useJUnitPlatform() }
        }
    }

    android {
        namespace = "io.github.zhora87.bezmen.ocr"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // ONNX Runtime has one Java API for desktop and Android; the engine is written once against it.
        val jvmAndroidMain = create("jvmAndroidMain") {
            dependsOn(commonMain.get())
            dependencies {
                compileOnly(libs.onnxruntime.jvm)
            }
        }
        jvmMain {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(libs.onnxruntime.jvm)
            }
        }
        androidMain {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(libs.onnxruntime.android)
            }
        }
    }
}

// JVM tests run the real models from the fetched directory.
tasks.named<Test>("jvmTest") {
    dependsOn(rootProject.tasks.named("fetchModels"))
    val modelsDir = FetchModelsTask.modelsDirectory(rootProject.layout.buildDirectory.dir("models").get().asFile)
    systemProperty("bezmen.models.dir", modelsDir.absolutePath)
    systemProperty("bezmen.corpus.dir", rootProject.layout.projectDirectory.dir("corpus").asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
}
