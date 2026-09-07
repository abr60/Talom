package com.talom.source.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals

class WhatsAppExportParserTest {
    @Test
    fun parsesDayMonthAndMultilineMessagesWithoutIgnoringValidLines() {
        val result = WhatsAppExportParser.parse(
            """
            06/09/2026, 10:15 AM - Hasan Al Mahmud CU: Meeting tomorrow at 10.
            Please bring the documents.
            06/09/2026, 11:20 AM - Me: Okay.
            """.trimIndent(),
        )

        assertEquals(2, result.messages.size)
        assertEquals(0, result.ignoredLines)
        assertEquals("Meeting tomorrow at 10.\nPlease bring the documents.", result.messages[0].text)
        assertEquals("Okay.", result.messages[1].text)
    }

    @Test
    fun parsesMonthDayFormat() {
        val result = WhatsAppExportParser.parse(
            "09/06/2026, 8:30 PM - Contact: Dinner at 9",
        )

        assertEquals(1, result.messages.size)
        assertEquals("Dinner at 9", result.messages.single().text)
    }
}
