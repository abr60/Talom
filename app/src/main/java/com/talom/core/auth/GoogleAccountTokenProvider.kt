package com.talom.core.auth

import android.content.Context
import android.os.Bundle
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device-account OAuth for Google Classroom.
 *
 * Tokens come from Google Play Services (GoogleAuthUtil.getToken), so there is
 * no client secret, no redirect URI, and no PKCE in the app. GMS mints and
 * refreshes short-lived tokens on demand; only the account email is persisted.
 *
 * The matching Android OAuth client (package com.talom + debug SHA-1) must
 * exist in Google Cloud Console, otherwise GMS returns an auth error.
 */
class GoogleAccountTokenProvider(
    private val context: Context,
    private val accountEmail: String,
) : OAuthTokenProvider {

    companion object {
        const val SCOPE_CLASSROOM_COURSES =
            "oauth2:https://www.googleapis.com/auth/classroom.courses.readonly"
        const val SCOPE_CLASSROOM_COURSEWORK =
            "oauth2:https://www.googleapis.com/auth/classroom.coursework.me.readonly"
        const val SCOPE_CLASSROOM_ANNOUNCEMENTS =
            "oauth2:https://www.googleapis.com/auth/classroom.announcements.readonly"

        /** Space-separated bundle passed to getToken in one consent screen. */
        const val CLASSROOM_SCOPES =
            "$SCOPE_CLASSROOM_COURSES $SCOPE_CLASSROOM_COURSEWORK $SCOPE_CLASSROOM_ANNOUNCEMENTS"
    }

    @Suppress("DEPRECATION")
    override suspend fun getAccessToken(): Result<OAuthAccessToken> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = GoogleAuthUtil.getToken(context, accountEmail, CLASSROOM_SCOPES, Bundle())
                    ?: throw MissingOAuthTokenException("Google Play Services returned no token.")
                OAuthAccessToken(token, expiresAtMillis = null)
            }.recoverCatching { error ->
                when (error) {
                    is UserRecoverableAuthException ->
                        throw MissingOAuthTokenException(
                            "Google sign-in needs approval: ${error.message}",
                        )
                    is GoogleAuthException ->
                        throw MissingOAuthTokenException("Google auth failed: ${error.message}")
                    else -> throw error
                }
            }
        }
}
