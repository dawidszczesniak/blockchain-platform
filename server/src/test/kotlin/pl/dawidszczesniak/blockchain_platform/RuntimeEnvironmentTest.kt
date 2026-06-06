package pl.dawidszczesniak.blockchain_platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeEnvironmentTest {
    @Test
    fun `loads dot env file for local`() {
        val tempDir = Files.createTempDirectory("runtime-env-test")
        Files.writeString(
            tempDir.resolve(".env.local"),
            """
            APP_ENV=local
            DATABASE_URL=jdbc:postgresql://localhost:5432/from-file
            ETH_RPC_URL=https://file-rpc.example.com
            """.trimIndent(),
        )

        val resolved = RuntimeEnvironment.load(
            workingDirectory = tempDir,
        )

        assertEquals("local", resolved["APP_ENV"])
        assertEquals("jdbc:postgresql://localhost:5432/from-file", resolved["DATABASE_URL"])
        assertEquals("https://file-rpc.example.com", resolved["ETH_RPC_URL"])
    }

    @Test
    fun `resolves env values from google secret references`() {
        val tempDir = Files.createTempDirectory("runtime-env-secret")
        Files.writeString(
            tempDir.resolve(".env.local"),
            """
            APP_ENV=local
            GCP_PROJECT_ID=test-project
            GCP_SECRET_ETH_RPC_URL=eth-rpc-url
            """.trimIndent(),
        )

        var requestedSecretReference: String? = null
        var requestedProjectId: String? = null
        val resolved = RuntimeEnvironment.load(
            workingDirectory = tempDir,
            secretValueResolver = SecretValueResolver { secretReference, projectId ->
                requestedSecretReference = secretReference
                requestedProjectId = projectId
                "https://secret-rpc.example.com"
            },
        )

        assertEquals("https://secret-rpc.example.com", resolved["ETH_RPC_URL"])
        assertEquals("eth-rpc-url", requestedSecretReference)
        assertEquals("test-project", requestedProjectId)
    }

    @Test
    fun `plain env value wins over google secret reference`() {
        val tempDir = Files.createTempDirectory("runtime-env-secret-override")
        Files.writeString(
            tempDir.resolve(".env.local"),
            """
            APP_ENV=local
            ETH_RPC_URL=https://file-rpc.example.com
            GCP_PROJECT_ID=test-project
            GCP_SECRET_ETH_RPC_URL=eth-rpc-url
            """.trimIndent(),
        )

        var resolverCalled = false
        val resolved = RuntimeEnvironment.load(
            workingDirectory = tempDir,
            secretValueResolver = SecretValueResolver { _, _ ->
                resolverCalled = true
                "https://secret-rpc.example.com"
            },
        )

        assertEquals("https://file-rpc.example.com", resolved["ETH_RPC_URL"])
        assertEquals(false, resolverCalled)
    }

    @Test
    fun `fails when google secret reference has no project id`() {
        val tempDir = Files.createTempDirectory("runtime-env-secret-missing-project")
        Files.writeString(
            tempDir.resolve(".env.local"),
            """
            APP_ENV=local
            GCP_SECRET_ETH_RPC_URL=eth-rpc-url
            """.trimIndent(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            RuntimeEnvironment.load(
                workingDirectory = tempDir,
                secretValueResolver = SecretValueResolver { _, _ ->
                    error("secret resolver should not be called without project id")
                },
            )
        }

        assertEquals(
            "GCP_PROJECT_ID or GOOGLE_CLOUD_PROJECT must be configured when using GCP_SECRET_* references.",
            error.message,
        )
    }

    @Test
    fun `finds dot env file in parent directories`() {
        val tempDir = Files.createTempDirectory("runtime-env-parent")
        Files.writeString(
            tempDir.resolve(".env.local"),
            """
            APP_ENV=local
            DATABASE_URL=jdbc:postgresql://localhost:5432/from-parent
            """.trimIndent(),
        )
        val nestedDir = Files.createDirectories(tempDir.resolve("server").resolve("build"))

        val resolved = RuntimeEnvironment.load(
            workingDirectory = nestedDir,
        )

        assertEquals("local", resolved["APP_ENV"])
        assertEquals("jdbc:postgresql://localhost:5432/from-parent", resolved["DATABASE_URL"])
    }

    @Test
    fun `fails fast when local env file is missing`() {
        val tempDir = Files.createTempDirectory("runtime-env-missing")

        val error = assertFailsWith<IllegalStateException> {
            RuntimeEnvironment.load(
                workingDirectory = tempDir,
            )
        }

        assertEquals(
            "APP_ENV=local requires .env.local in project root. Create it and configure local DB/Redis/Auth/Ethereum/Sandbox values.",
            error.message,
        )
    }
}
