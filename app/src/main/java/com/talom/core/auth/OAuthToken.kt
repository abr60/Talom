package com.talom.core.auth

/**
 * A provider-neutral access token. Refresh and interactive consent are deliberately
 * outside this abstraction so integrations can fail closed when OAuth is not configured.
 */
data class OAuthAccessToken(
    val value: String,
    val expiresAtMillis: Long? = null,
    val tokenType: String = "Bearer",
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis != null && expiresAtMillis <= nowMillis
}

interface OAuthTokenProvider {
    suspend fun getAccessToken(): Result<OAuthAccessToken>
}

class MissingOAuthTokenException(
    message: String = "No OAuth access token is configured.",
) : IllegalStateException(message)

class ExpiredOAuthTokenException(
    message: String = "The OAuth access token is missing or expired.",
) : IllegalStateException(message)

class MissingOAuthClientIdException(
    message: String = "Google Classroom OAuth client ID is not configured.",
) : IllegalStateException(message)

data class GoogleClassroomOAuthConfiguration(
    val clientId: String?,
) {
    fun requireClientId(): Result<String> = runCatching {
        clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw MissingOAuthClientIdException()
    }
}
