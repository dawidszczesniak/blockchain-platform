package pl.dawidszczesniak.blockchain_platform.feature.login.datasource

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyResponseDto
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient

interface LoginRemoteDataSource {
    suspend fun requestChallenge(request: AuthChallengeRequestDto): AuthChallengeResponseDto
    suspend fun verifyChallenge(request: AuthVerifyRequestDto): AuthVerifyResponseDto
    suspend fun getSession(): AuthVerifyResponseDto
    suspend fun logout()
}

class LoginRemoteDataSourceImpl(
    private val apiBaseUrl: String,
    private val httpTextClient: HttpTextClient,
) : LoginRemoteDataSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestChallenge(request: AuthChallengeRequestDto): AuthChallengeResponseDto {
        val body = json.encodeToString(AuthChallengeRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(endpoint(apiBaseUrl, "/auth/challenge"), body)
        return json.decodeFromString(AuthChallengeResponseDto.serializer(), payload)
    }

    override suspend fun verifyChallenge(request: AuthVerifyRequestDto): AuthVerifyResponseDto {
        val body = json.encodeToString(AuthVerifyRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(endpoint(apiBaseUrl, "/auth/verify"), body)
        return json.decodeFromString(AuthVerifyResponseDto.serializer(), payload)
    }

    override suspend fun getSession(): AuthVerifyResponseDto {
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/auth/session"))
        return json.decodeFromString(AuthVerifyResponseDto.serializer(), payload)
    }

    override suspend fun logout() {
        httpTextClient.postJson(endpoint(apiBaseUrl, "/auth/logout"), "{}")
    }
}

private fun endpoint(apiBaseUrl: String, path: String): String {
    val base = apiBaseUrl.trimEnd('/')
    return if (base.isBlank()) {
        path
    } else {
        "$base$path"
    }
}
