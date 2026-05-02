package pl.dawidszczesniak.blockchain_platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal object RuntimeEnvironment {
    fun load(
        workingDirectory: Path = Paths.get("").toAbsolutePath(),
        secretValueResolver: SecretValueResolver = GoogleCloudSecretManagerResolver,
    ): Map<String, String> {
        val localEnvPath = findLocalEnvPath(workingDirectory)
        if (!Files.isRegularFile(localEnvPath)) {
            error(
                "APP_ENV=local requires .env.local in project root. " +
                    "Create it and configure local DB/Redis/Auth/Ethereum/Sandbox values."
            )
        }

        val fileEnv = parseDotEnv(localEnvPath)
        val resolvedEnv = resolveGoogleSecretReferences(fileEnv, secretValueResolver)
        val resolvedAppEnv = resolvedEnv["APP_ENV"]
            ?.trim()
            ?.lowercase()
            .orEmpty()
            .ifBlank { AppEnvironment.Local.id }

        require(resolvedAppEnv == AppEnvironment.Local.id) {
            ".env.local must keep APP_ENV=local."
        }

        return resolvedEnv + mapOf("APP_ENV" to resolvedAppEnv)
    }

    internal fun parseDotEnv(path: Path): Map<String, String> {
        val values = linkedMapOf<String, String>()
        Files.readAllLines(path).forEachIndexed { index, rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                return@forEachIndexed
            }

            val content = trimmedLine.removePrefix("export ").trimStart()
            val separatorIndex = content.indexOf('=')
            if (separatorIndex <= 0) {
                error("Invalid .env.local entry at line ${index + 1}. Expected KEY=value.")
            }

            val key = content.substring(0, separatorIndex).trim()
            if (!key.matches(Regex("[A-Z0-9_]+"))) {
                error("Invalid env key '$key' in .env.local at line ${index + 1}.")
            }

            val value = content.substring(separatorIndex + 1).trim().unwrapMatchingQuotes()
            values[key] = value
        }
        return values
    }

    internal fun resolveGoogleSecretReferences(
        env: Map<String, String>,
        secretValueResolver: SecretValueResolver,
    ): Map<String, String> {
        val resolved = env.toMutableMap()
        val projectId = env["GCP_PROJECT_ID"]
            ?.trim()
            ?.ifBlank { null }
            ?: env["GOOGLE_CLOUD_PROJECT"]?.trim()?.ifBlank { null }

        env.forEach { (key, value) ->
            if (!key.startsWith(GCP_SECRET_PREFIX)) return@forEach
            if (value.isBlank()) return@forEach

            val targetKey = key.removePrefix(GCP_SECRET_PREFIX)
            val currentValue = resolved[targetKey]?.trim().orEmpty()
            if (currentValue.isNotBlank()) return@forEach
            require(projectId != null || value.trim().startsWith("projects/")) {
                "GCP_PROJECT_ID or GOOGLE_CLOUD_PROJECT must be configured when using GCP_SECRET_* references."
            }

            resolved[targetKey] = secretValueResolver.resolve(
                secretReference = value,
                projectId = projectId,
            )
        }

        return resolved
    }

    private fun findLocalEnvPath(startDirectory: Path): Path {
        var current: Path? = startDirectory
        while (current != null) {
            val candidate = current.resolve(".env.local")
            if (Files.isRegularFile(candidate)) {
                return candidate
            }
            current = current.parent
        }
        return startDirectory.resolve(".env.local")
    }
}

internal const val GCP_SECRET_PREFIX = "GCP_SECRET_"

private fun String.unwrapMatchingQuotes(): String {
    if (length < 2) return this
    return when {
        startsWith('"') && endsWith('"') -> substring(1, lastIndex)
        startsWith('\'') && endsWith('\'') -> substring(1, lastIndex)
        else -> this
    }
}
