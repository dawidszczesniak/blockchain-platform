import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localHost = "localhost"
val frontendPort = 8081
val backendPort = 8080
val localApiBaseUrl = "http://$localHost:$backendPort"
val localEnv = loadDotEnv(rootDir.resolve(".env.local"))

kotlin {
    js {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = frontendPort
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
                    port = frontendPort
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
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":shared"))
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

val appEnvProvider = providers.provider {
    localEnv["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
}
val defaultApiBaseUrlProvider = appEnvProvider.map { env ->
    when (env) {
        "prod", "production" -> "https://api.your-domain.com"
        "staging", "stage" -> "https://staging-api.your-domain.com"
        else -> localApiBaseUrl
    }
}

val freeLocalFrontendPort by tasks.registering(Exec::class) {
    description = "Kills the process listening on port 8081 before a local frontend run."

    commandLine(
        "zsh",
        "-ic",
        """
        pids=$(lsof -tiTCP:$frontendPort -sTCP:LISTEN)
        if [ -n "${'$'}pids" ]; then
          kill ${'$'}pids
        fi
        """.trimIndent(),
    )
}

tasks.named("wasmJsBrowserDevelopmentRun") {
    mustRunAfter(freeLocalFrontendPort)
}

tasks.register("runLocalForce8081") {
    group = "application"
    description = "Frees port 8081 and starts the WasmJS frontend dev server."

    dependsOn(freeLocalFrontendPort, "wasmJsBrowserDevelopmentRun")
}

val generatedConfigDir = layout.buildDirectory.dir("generated/appConfig")
val generateAppConfig by tasks.registering(GenerateAppConfig::class) {
    appEnv.set(appEnvProvider)
    apiBaseUrl.set(defaultApiBaseUrlProvider)
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

fun loadDotEnv(path: File): Map<String, String> {
    require(path.isFile) {
        ".env.local must exist in project root because frontend build config is read only from this file."
    }
    val values = linkedMapOf<String, String>()
    path.readLines().forEachIndexed { index, rawLine ->
        val trimmedLine = rawLine.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return@forEachIndexed
        }
        val content = trimmedLine.removePrefix("export ").trimStart()
        val separatorIndex = content.indexOf('=')
        require(separatorIndex > 0) {
            "Invalid .env.local entry at line ${index + 1}. Expected KEY=value."
        }
        val key = content.substring(0, separatorIndex).trim()
        require(key.matches(Regex("[A-Z0-9_]+"))) {
            "Invalid env key '$key' in .env.local at line ${index + 1}."
        }
        val value = content.substring(separatorIndex + 1).trim()
        values[key] = unwrapMatchingQuotes(value)
    }
    return values
}

fun unwrapMatchingQuotes(value: String): String {
    if (value.length < 2) return value
    return when {
        value.startsWith('"') && value.endsWith('"') -> value.substring(1, value.lastIndex)
        value.startsWith('\'') && value.endsWith('\'') -> value.substring(1, value.lastIndex)
        else -> value
    }
}
