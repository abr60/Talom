package com.talom.source.whatsapp

import java.security.MessageDigest

data class ExportedWhatsAppMessage(
    val stableId: String,
    val timestampMillis: Long,
    val sender: String?,
    val text: String,
)

data class WhatsAppExportResult(
    val messages: List<ExportedWhatsAppMessage>,
    val ignoredLines: Int,
)

/**
 * Parses the common WhatsApp text export format without retaining the source
 * file. Multiline bodies are attached to the preceding message.
 */
object WhatsAppExportParser {
    private val header = Regex(
        pattern = """^\uFEFF?\[?(\d{1,2})[/-](\d{1,2})[/-](\d{2,4}),\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?\]?\s+-\s+(.*)$""",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    fun parse(input: String): WhatsAppExportResult {
        val messages = mutableListOf<ExportedWhatsAppMessage>()
        var ignoredLines = 0
        var currentTimestamp: Long? = null
        var currentSender: String? = null
        val currentBody = StringBuilder()

        fun flush() {
            val timestamp = currentTimestamp ?: return
            val text = currentBody.toString().trim()
            if (text.isNotEmpty()) {
                messages += ExportedWhatsAppMessage(
                    stableId = stableId(timestamp, currentSender, text),
                    timestampMillis = timestamp,
                    sender = currentSender,
                    text = text,
                )
            }
            currentTimestamp = null
            currentSender = null
            currentBody.clear()
        }

        input.lineSequence().forEach { line ->
            val match = header.matchEntire(line)
            if (match == null) {
                if (currentTimestamp != null) {
                    currentBody.append('\n').append(line)
                } else if (line.isNotBlank()) {
                    ignoredLines++
                }
                return@forEach
            }

            flush()
            val values = match.groupValues
            val firstDatePart = values[1].toInt()
            val secondDatePart = values[2].toInt()
            currentTimestamp = runCatching {
                parseTimestamp(
                firstDatePart = firstDatePart,
                secondDatePart = secondDatePart,
                year = values[3].toInt().let { if (it < 100) 2000 + it else it },
                hour = values[4].toInt(),
                minute = values[5].toInt(),
                second = values[6].ifEmpty { "0" }.toInt(),
                meridiem = values[7].ifEmpty { null },
                )
            }.getOrNull()
            if (currentTimestamp == null) {
                ignoredLines++
                return@forEach
            }
            val payload = values[8]
            val separator = payload.indexOf(": ")
            if (separator > 0) {
                currentSender = payload.substring(0, separator)
                currentBody.append(payload.substring(separator + 2))
            } else {
                currentSender = null
                currentBody.append(payload)
            }
        }
        flush()
        return WhatsAppExportResult(messages, ignoredLines)
    }

    private fun parseTimestamp(
        firstDatePart: Int,
        secondDatePart: Int,
        year: Int,
        hour: Int,
        minute: Int,
        second: Int,
        meridiem: String?,
    ): Long {
        val normalizedHour = when {
            meridiem.equals("PM", ignoreCase = true) && hour < 12 -> hour + 12
            meridiem.equals("AM", ignoreCase = true) && hour == 12 -> 0
            else -> hour
        }
        // WhatsApp exports use either day/month or month/day depending on locale.
        val (day, month) = when {
            firstDatePart > 12 && secondDatePart <= 12 ->
                firstDatePart to secondDatePart
            secondDatePart > 12 && firstDatePart <= 12 ->
                secondDatePart to firstDatePart
            else ->
                firstDatePart to secondDatePart
        }
        return java.time.LocalDateTime.of(year, month, day, normalizedHour, minute, second)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun stableId(timestamp: Long, sender: String?, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$timestamp\u0000$sender\u0000$text".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
