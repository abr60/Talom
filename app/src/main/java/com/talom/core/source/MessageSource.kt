package com.talom.core.source

data class MessageCursor(
    val timestampMillis: Long,
    val messageId: Long,
)

data class MessageSourceRequest(
    val allowedJids: Set<String>,
    val cursor: MessageCursor? = null,
    val limit: Int = 100,
    val includeText: Boolean = false,
)

data class SourceMessage(
    val messageId: Long,
    val chatId: Long,
    val jid: String,
    val chatSubject: String?,
    val fromMe: Boolean,
    val timestampMillis: Long,
    val messageType: Int,
    val text: String?,
)

data class MessageSourceBatch(
    val messages: List<SourceMessage>,
    val nextCursor: MessageCursor?,
    val snapshotTimestampMillis: Long,
)

sealed interface MessageSourceResult {
    data class Success(val batch: MessageSourceBatch) : MessageSourceResult

    data class Failure(
        val code: Code,
        val message: String,
        val cause: Throwable? = null,
    ) : MessageSourceResult {
        enum class Code {
            SOURCE_UNAVAILABLE,
            SNAPSHOT_FAILED,
            UNSUPPORTED_SCHEMA,
            INVALID_WHITELIST,
            EXTRACTION_FAILED,
        }
    }
}

interface MessageSource {
    suspend fun extract(request: MessageSourceRequest): MessageSourceResult
}
