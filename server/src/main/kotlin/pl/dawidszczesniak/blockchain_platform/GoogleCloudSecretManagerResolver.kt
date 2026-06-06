package pl.dawidszczesniak.blockchain_platform

import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient

internal fun interface SecretValueResolver {
    fun resolve(secretReference: String, projectId: String?): String
}

internal object GoogleCloudSecretManagerResolver : SecretValueResolver {
    override fun resolve(secretReference: String, projectId: String?): String {
        val secretVersionName = secretReference.toSecretVersionName(projectId)
        SecretManagerServiceClient.create().use { client ->
            val request = AccessSecretVersionRequest.newBuilder()
                .setName(secretVersionName)
                .build()
            return client.accessSecretVersion(request).payload.data.toStringUtf8()
        }
    }
}

private fun String.toSecretVersionName(projectId: String?): String {
    val trimmed = trim()
    require(trimmed.isNotBlank()) { "Google Secret Manager reference cannot be blank." }

    if (trimmed.startsWith("projects/")) {
        return if (trimmed.contains("/versions/")) trimmed else "$trimmed/versions/latest"
    }

    require(!projectId.isNullOrBlank()) {
        "GCP_PROJECT_ID or GOOGLE_CLOUD_PROJECT must be configured when using GCP_SECRET_* references."
    }

    return "projects/$projectId/secrets/$trimmed/versions/latest"
}
