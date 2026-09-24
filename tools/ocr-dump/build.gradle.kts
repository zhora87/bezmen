plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

sourceSets {
    main {
        resources.srcDir(rootProject.layout.projectDirectory.dir("locale-packs"))
        resources.include("*.json")
    }
}

dependencies {
    implementation(projects.core.ocr)
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("io.github.zhora87.bezmen.tools.OcrDumpKt")
}

// Runs from the repository root with the models fetched by :fetchModels (models.lock).
tasks.named<JavaExec>("run") {
    dependsOn(rootProject.tasks.named("fetchModels"))
    workingDir = rootProject.layout.projectDirectory.asFile
    val modelsDir = FetchModelsTask.modelsDirectory(rootProject.layout.buildDirectory.dir("models").get().asFile)
    systemProperty("bezmen.models.dir", modelsDir.absolutePath)
    systemProperty("java.awt.headless", "true")
}
