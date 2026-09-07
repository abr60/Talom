package com.talom.source.whatsapp

import com.talom.core.source.MessageSource
import com.talom.core.source.MessageSourceRequest
import com.talom.core.source.MessageSourceResult
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Instant

class WhatsAppMessageSource(
    private val snapshotProvider: WhatsAppSnapshotProvider,
) : MessageSource {
    override suspend fun extract(request: MessageSourceRequest): MessageSourceResult {
        if (request.allowedJids.isEmpty()) {
            return MessageSourceResult.Failure(
                code = MessageSourceResult.Failure.Code.INVALID_WHITELIST,
                message = "At least one WhatsApp JID must be selected.",
            )
        }
        if (request.limit !in 1..1_000) {
            return MessageSourceResult.Failure(
                code = MessageSourceResult.Failure.Code.EXTRACTION_FAILED,
                message = "Batch size must be between 1 and 1000.",
            )
        }

        val snapshot = snapshotProvider.createSnapshot().getOrElse {
            return MessageSourceResult.Failure(
                code = MessageSourceResult.Failure.Code.SNAPSHOT_FAILED,
                message = "Could not create a WhatsApp snapshot.",
                cause = it,
            )
        }
        return runCatching {
            extractFromSnapshot(snapshot.database, request, snapshot.capturedAt)
        }.getOrElse {
            MessageSourceResult.Failure(
                code = if (it is UnsupportedSchemaException) {
                    MessageSourceResult.Failure.Code.UNSUPPORTED_SCHEMA
                } else {
                    MessageSourceResult.Failure.Code.EXTRACTION_FAILED
                },
                message = it.message ?: "WhatsApp extraction failed.",
                cause = it,
            )
        }
    }

    private fun extractFromSnapshot(
        databaseFile: File,
        request: MessageSourceRequest,
        capturedAt: Instant,
    ): MessageSourceResult.Success {
        val database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            requireColumns(database, "message", setOf("_id", "chat_row_id", "from_me", "timestamp", "message_type", "text_data"))
            requireColumns(database, "chat", setOf("_id", "jid_row_id", "subject"))
            requireColumns(database, "jid", setOf("_id", "raw_string"))

            val jids = request.allowedJids.toList()
            val jidPlaceholders = jids.joinToString(",") { "?" }
            val selection = buildString {
                append("j.raw_string IN ($jidPlaceholders)")
                request.cursor?.let {
                    append(" AND (m.timestamp > ? OR (m.timestamp = ? AND m._id > ?))")
                }
            }
            val args = buildList {
                addAll(jids)
                request.cursor?.let {
                    add(it.timestampMillis.toString())
                    add(it.timestampMillis.toString())
                    add(it.messageId.toString())
                }
            }
            val textColumn = if (request.includeText) "m.text_data" else "NULL"
            val messages = mutableListOf<com.talom.core.source.SourceMessage>()
            database.rawQuery(
                """
                SELECT m._id, m.chat_row_id, j.raw_string, c.subject, m.from_me,
                       m.timestamp, m.message_type, $textColumn
                FROM message m
                JOIN chat c ON c._id = m.chat_row_id
                JOIN jid j ON j._id = c.jid_row_id
                WHERE $selection
                ORDER BY m.timestamp ASC, m._id ASC
                LIMIT ?
                """.trimIndent(),
                (args + request.limit.toString()).toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    messages += com.talom.core.source.SourceMessage(
                        messageId = cursor.getLong(0),
                        chatId = cursor.getLong(1),
                        jid = cursor.getString(2),
                        chatSubject = cursor.getStringOrNull(3),
                        fromMe = cursor.getInt(4) != 0,
                        timestampMillis = cursor.getLong(5),
                        messageType = cursor.getInt(6),
                        text = cursor.getStringOrNull(7),
                    )
                }
            }
            val nextCursor = messages.lastOrNull()?.let {
                com.talom.core.source.MessageCursor(it.timestampMillis, it.messageId)
            }
            return MessageSourceResult.Success(
                com.talom.core.source.MessageSourceBatch(
                    messages = messages,
                    nextCursor = nextCursor,
                    snapshotTimestampMillis = capturedAt.toEpochMilli(),
                ),
            )
        } finally {
            database.close()
        }
    }

    private fun requireColumns(database: SQLiteDatabase, table: String, required: Set<String>) {
        val found = mutableSetOf<String>()
        database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) found += cursor.getString(1)
        }
        check(required.all(found::contains)) {
            throw UnsupportedSchemaException("WhatsApp table $table is missing required columns")
        }
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}

private class UnsupportedSchemaException(message: String) : IllegalStateException(message)
