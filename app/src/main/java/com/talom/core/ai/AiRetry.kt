package com.talom.core.ai

import java.net.HttpURLConnection
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

object AiRetry {
    fun isRetryableStatus(code: Int): Boolean = code == 429 || code in 500..504

    fun parseRetryAfterSeconds(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        header.trim().toLongOrNull()?.let { return it.coerceAtLeast(1) }
        // HTTP-date form not supported; treat as no hint.
        return null
    }

    fun backoffDelayMs(attempt: Int, retryAfterHeader: String?): Long {
        parseRetryAfterSeconds(retryAfterHeader)?.let {
            return min(it * 1000L, 60_000L)
        }
        val exp = 500L * (1L shl attempt.coerceAtMost(6)) // 500,1000,2000,4000,8000...
        val jitter = Random.nextLong(0, 250)
        return min(exp + jitter, 60_000L)
    }

    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        isRetryable: (code: Int) -> Boolean = ::isRetryableStatus,
        block: suspend (attempt: Int) -> Pair<Int, T>,
    ): T {
        var last: Throwable? = null
        var lastCode: Int? = null
        repeat(maxAttempts) { attempt ->
            val (code, value) = try {
                block(attempt)
            } catch (t: Throwable) {
                last = t
                // Network exception => retryable
                if (attempt + 1 < maxAttempts) {
                    delay(backoffDelayMs(attempt, null))
                    return@repeat
                }
                throw t
            }
            if (code in 200..299) return value
            lastCode = code
            if (!isRetryable(code) || attempt + 1 >= maxAttempts) return value
            // Will retry; caller must have provided retryAfter via block side-effect if needed.
            // For HTTP path we handle delay inside providers where header is available.
        }
        error("Retry exhausted (last code $lastCode): ${last?.message}")
    }

    fun retryAfterFromConnection(conn: HttpURLConnection): String? =
        conn.getHeaderField("Retry-After")
}
