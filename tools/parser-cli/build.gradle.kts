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
        // Ship the locale packs inside the CLI so it runs from any directory.
        resources.srcDir(rootProject.layout.projectDirectory.dir("locale-packs"))
        resources.include("*.json")
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("io.github.zhora87.bezmen.tools.ParserCliKt")
}

// Paths on the command line are relative to the repository root, not to this module.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.layout.projectDirectory.asFile
}
