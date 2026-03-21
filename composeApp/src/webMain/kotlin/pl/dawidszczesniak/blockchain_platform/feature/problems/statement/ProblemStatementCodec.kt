package pl.dawidszczesniak.blockchain_platform.feature.problems.statement

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample

data class ProblemStatementContent(
    val statement: String,
    val constraints: String,
    val examples: List<ProblemExample>,
)

private const val PROBLEM_METADATA_MARKER = "\n\n[[BP_META]]"
private val codecJson = Json {
    ignoreUnknownKeys = true
}

fun encodeProblemDescription(
    statement: String,
    constraints: String,
    examples: List<ProblemExample>,
): String {
    val normalizedStatement = statement.trim()
    val normalizedConstraints = constraints.trim()
    val normalizedExamples = examples.map {
        ProblemExample(
            input = it.input.trim(),
            output = it.output.trim(),
            explanation = it.explanation.trim(),
        )
    }
    if (normalizedConstraints.isEmpty() && normalizedExamples.isEmpty()) {
        return normalizedStatement
    }
    val metadata = ProblemStatementMetadataDto(
        constraints = normalizedConstraints.ifBlank { null },
        examples = normalizedExamples.map {
            ProblemStatementExampleDto(
                input = it.input,
                output = it.output,
                explanation = it.explanation,
            )
        },
    )
    val encodedMetadata = codecJson.encodeToString(metadata)
    return "$normalizedStatement$PROBLEM_METADATA_MARKER$encodedMetadata"
}

fun decodeProblemDescription(description: String): ProblemStatementContent {
    val markerIndex = description.lastIndexOf(PROBLEM_METADATA_MARKER)
    if (markerIndex < 0) {
        return ProblemStatementContent(
            statement = description.trim(),
            constraints = "",
            examples = emptyList(),
        )
    }

    val statement = description.substring(0, markerIndex).trimEnd()
    val metadataRaw = description.substring(markerIndex + PROBLEM_METADATA_MARKER.length).trim()
    val metadata = runCatching {
        codecJson.decodeFromString<ProblemStatementMetadataDto>(metadataRaw)
    }.getOrNull()

    if (metadata == null) {
        return ProblemStatementContent(
            statement = description.trim(),
            constraints = "",
            examples = emptyList(),
        )
    }

    return ProblemStatementContent(
        statement = statement,
        constraints = metadata.constraints?.trim().orEmpty(),
        examples = metadata.examples.map {
            ProblemExample(
                input = it.input.trim(),
                output = it.output.trim(),
                explanation = it.explanation.trim(),
            )
        },
    )
}

@Serializable
private data class ProblemStatementMetadataDto(
    val constraints: String? = null,
    val examples: List<ProblemStatementExampleDto> = emptyList(),
)

@Serializable
private data class ProblemStatementExampleDto(
    val input: String,
    val output: String,
    val explanation: String,
)
