import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8080
                }
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8080
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

abstract class GenerateAppConfig : DefaultTask() {
    @get:Input
    abstract val appEnv: Property<String>

    @get:Input
    abstract val apiBaseUrl: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val envValue = appEnv.get().replace("\\", "\\\\").replace("\"", "\\\"")
        val apiValue = apiBaseUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val outputText = buildString {
            appendLine("package pl.dawidszczesniak.blockchain_platform")
            appendLine("")
            appendLine("internal const val APP_ENV = \"${envValue}\"")
            appendLine("internal const val API_BASE_URL = \"${apiValue}\"")
        }
        target.writeText(outputText)
    }
}

val appEnvProvider = providers.gradleProperty("appEnv")
    .map { it.lowercase() }
    .orElse("local")
val apiBaseUrlProvider = providers.gradleProperty("apiBaseUrl")
    .orElse(
        appEnvProvider.map { env ->
            when (env) {
                "prod", "production" -> "https://api.your-domain.com"
                "staging", "stage" -> "https://staging-api.your-domain.com"
                else -> "http://localhost:8081"
            }
        }
    )

val generatedConfigDir = layout.buildDirectory.dir("generated/appConfig")
val generateAppConfig by tasks.registering(GenerateAppConfig::class) {
    appEnv.set(appEnvProvider)
    apiBaseUrl.set(apiBaseUrlProvider)
    outputFile.set(
        generatedConfigDir.map {
            it.file("pl/dawidszczesniak/blockchain_platform/AppBuildConfig.kt")
        }
    )
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generatedConfigDir)
}

tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(generateAppConfig)
}

tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach {
    dependsOn(generateAppConfig)
}
