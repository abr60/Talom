package com.talom.core.ai

import com.talom.core.source.SourceMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AiPrompt {
    const val MAX_TEXT_CHARS = 400
    const val TARGET_BATCH_TOKENS = 3000
    const val MAX_MESSAGES_PER_BATCH = 30
    const val ESTIMATED_OVERHEAD_TOKENS = 750
    private const val BENGALI_START = '\u0980'
    private const val BENGALI_END = '\u09FF'

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE dd MMM HH:mm", Locale.ENGLISH)

    fun formatTimestamp(millis: Long): String = try {
        timeFormatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
    } catch (_: Throwable) {
        Instant.ofEpochMilli(millis).toString()
    }

    fun shortSender(jid: String, chatSubject: String?): String {
        chatSubject?.takeIf { it.isNotBlank() }?.let { return it.trim().take(40) }
        val local = jid.substringBefore('@')
        return if (local.length > 20) local.takeLast(20) else local.ifBlank { jid.take(20) }
    }

    fun truncate(text: String): String {
        val t = text.trim()
        return if (t.length <= MAX_TEXT_CHARS) t else t.take(MAX_TEXT_CHARS).trimEnd() + "…"
    }

    fun formatMessage(msg: SourceMessage): String {
        val time = formatTimestamp(msg.timestampMillis)
        val sender = if (msg.fromMe) {
            "You"
        } else {
            shortSender(msg.jid, msg.chatSubject)
        }
        val body = truncate(msg.text.orEmpty())
        return "[$time] $sender (${msg.jid}/${msg.messageId}): $body"
    }

    private val noiseExact = setOf(
        "ok", "okay", "thanks", "thank you", "thx", "hmm", "accha", "acha", "ha", "haha", "hehe", "lol",
        "seen", "typing", "👍", "👌", "🙏", "😂", "😊", "❤️", "✅",
    )
    fun isNoise(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return true
        if (t in noiseExact) return true
        if (t.length <= 2) return true
        if (t.none { it.isLetterOrDigit() }) return true
        return false
    }

    fun estimateTokens(text: String): Int {
        val bengaliCount = text.count { it in BENGALI_START..BENGALI_END }
        val nonBengali = (text.length - bengaliCount).coerceAtLeast(0)
        // Bengali scripts tokenize denser (~1.8 chars/token) than English (~3.5).
        val avgCharsPerToken = if (bengaliCount > nonBengali) 1.8 else 3.5
        return (text.length / avgCharsPerToken).toInt().coerceAtLeast(1)
    }

    fun chunkByTokens(
        messages: List<SourceMessage>,
        targetTokens: Int = TARGET_BATCH_TOKENS,
        maxPerBatch: Int = MAX_MESSAGES_PER_BATCH,
        overheadTokens: Int = ESTIMATED_OVERHEAD_TOKENS,
    ): List<List<SourceMessage>> {
        if (messages.isEmpty()) return emptyList()
        val chunks = mutableListOf<List<SourceMessage>>()
        var current = mutableListOf<SourceMessage>()
        var currentTokens = overheadTokens
        for (msg in messages) {
            val line = formatMessage(msg)
            val tokens = estimateTokens(line) + 4
            val wouldExceed = current.isNotEmpty() &&
                (current.size >= maxPerBatch || currentTokens + tokens > targetTokens)
            if (wouldExceed) {
                chunks += current.toList()
                current = mutableListOf()
                currentTokens = overheadTokens
            }
            current += msg
            currentTokens += tokens
        }
        if (current.isNotEmpty()) chunks += current.toList()
        return chunks
    }

    fun buildPrompt(
        messages: List<SourceMessage>,
        schemaVersion: Int,
        useExamples: Boolean = true,
    ): String {
        val rendered = messages.joinToString("\n") { formatMessage(it) }
        val today = try {
            java.time.LocalDate.now(ZoneId.systemDefault()).toString()
        } catch (_: Throwable) { Instant.now().toString().substring(0, 10) }
        val examples = if (useExamples) {
            """

            Few-shot examples (follow this exact JSON shape, never use "string" literally):

            Example 1 — Bengali class schedule:
            Messages:
            [Mon 01 Sep 09:00] Fahim CR (120363404881593486@g.us/101): Agamikal sokal 9 tay Microeconomics class hobe, Room 302. Sobai attend koro.
            JSON:
            {"items":[{"stableId":"120363404881593486@g.us_101_class_schedule","type":"CLASS_SCHEDULE","title":"Microeconomics class","details":"Tomorrow 9 AM, Room 302","subject":"Microeconomics","dueAtMillis":1725171600000,"sourceJid":"120363404881593486@g.us","sourceMessageId":101,"confidence":0.92,"extractionVersion":$schemaVersion}],"insights":[]}

            Example 2 — assignment deadline:
            Messages:
            [Tue 02 Sep 14:30] CR (120363404881593486@g.us/102): Assignment — submit History of Economic Thought hand-written copy by Friday 5 PM. Late not accepted.
            JSON:
            {"items":[{"stableId":"120363404881593486@g.us_102_assignment","type":"ASSIGNMENT","title":"History of Economic Thought assignment","details":"Hand-written copy due Friday 5 PM","subject":"History of Economic Thought","dueAtMillis":1725637200000,"sourceJid":"120363404881593486@g.us","sourceMessageId":102,"confidence":0.95,"extractionVersion":$schemaVersion}],"insights":[]}

            Example 3 — personal reminder:
            Messages:
            [Tue 02 Sep 10:00] Hasan (8801515207903@s.whatsapp.net/201): Bhai kalke bikele dekha korbo?
            [Tue 02 Sep 10:01] You (8801515207903@s.whatsapp.net/202): ok
            JSON:
            {"items":[],"insights":[{"stableId":"8801515207903@s.whatsapp.net_201_plan","type":"PLAN","title":"Meet Hasan tomorrow afternoon","details":"Confirmed for tomorrow afternoon","sourceJid":"8801515207903@s.whatsapp.net","sourceMessageId":201,"confidence":0.88,"extractionVersion":$schemaVersion}]}
            """.trimIndent()
        } else {
            ""
        }
        return """
            Today is $today (system timezone). Use this to resolve relative dates like "tomorrow", "next Sunday", "agamikal", "kalke".

            PRIORITY: Extract academic items FIRST and thoroughly. Personal insights are secondary — only when clearly useful and not academic.

            Preserve the original language of titles and details. Do not translate.
            Titles/details: concise assistant-voice summaries — never paste raw message text. Merge related URLs into one entry.

            CRITICAL: Copy sourceJid and sourceMessageId EXACTLY from the message header in parentheses (jid/messageId). Never invent IDs. Never output the literal word "string". stableId MUST be "<sourceJid>_<sourceMessageId>_<type_lowercase>".

            Message speaker labels: "You" = user-sent. Prefer named contacts for incoming messages.

            SKIP NOISE — empty arrays for acks, bare emoji, seen/typing, stickers, or no actionable info.

            dueAtMillis MUST be when the EVENT OCCURS / is DUE — NEVER the WhatsApp send timestamp.
            - CLASS_SCHEDULE: next upcoming occurrence (today or future). "tomorrow 9 AM" → tomorrow 09:00. Day-of-week only → next that weekday. Weekly recurring → next upcoming instance. If the class already happened today, still include today's instance; if past, pick the next week.
            - ASSIGNMENT / DEADLINE: submission deadline from text. Prefer ASSIGNMENT (not ANNOUNCEMENT) for anything the student must submit.
            - EXAM / CLASS_TEST / VIVA / PRACTICAL / INTERVIEW / PRESENTATION: event date+time.
            - ANNOUNCEMENT / CANCELLATION: dueAtMillis null unless a clear event date is stated.
            - If ambiguous, null is OK — still emit the item. Do not invent ancient/past years.

            Type vocabulary for ITEMS:
            CLASS_SCHEDULE | ASSIGNMENT | EXAM | DEADLINE | ANNOUNCEMENT | CANCELLATION |
            CLASS_TEST | PRESENTATION | VIVA | INTERVIEW | PRACTICAL
            Insights ONLY: PERSONAL_REMINDER | PLAN | FAMILY | FRIEND | GENERAL
            GENERAL: actionable non-family/non-friend within ~24–48h. Do not dump long academic content into insights — put it in items.
            $examples

            Respond with ONLY one JSON object — no prose, no markdown fences:
            {"items":[{"stableId":"<jid>_<id>_<type_lowercase>","type":"CLASS_SCHEDULE|ASSIGNMENT|EXAM|DEADLINE|ANNOUNCEMENT|CANCELLATION|CLASS_TEST|PRESENTATION|VIVA|INTERVIEW|PRACTICAL","title":"<short title>","details":"<details or null>","subject":"<subject or null>","dueAtMillis":1234567890000,"sourceJid":"<jid from header>","sourceMessageId":12345,"confidence":0.95,"extractionVersion":$schemaVersion}],"insights":[{"stableId":"<jid>_<id>_<type_lowercase>","type":"PERSONAL_REMINDER|PLAN|FAMILY|FRIEND|GENERAL","title":"<short title>","details":"<details or null>","sourceJid":"<jid from header>","sourceMessageId":12345,"confidence":0.88,"extractionVersion":$schemaVersion}]}
            Prefer non-empty items when academic content exists. Never output literal "string".
            Messages:
            $rendered
        """.trimIndent()
    }
}
