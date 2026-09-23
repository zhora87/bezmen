plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.detekt)
}

// Static analysis for the whole repository from the root project: one config, one report.
detekt {
    source.setFrom(files(rootDir))
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/build/**", "**/.gradle/**", "**/resources/**")
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}
