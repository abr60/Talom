package com.talom.source.classroom

import com.talom.core.auth.ExpiredOAuthTokenException
import com.talom.core.auth.MissingOAuthTokenException
import com.talom.core.auth.OAuthAccessToken
import com.talom.core.auth.OAuthTokenProvider
import com.talom.core.classroom.ClassroomAnnouncement
import com.talom.core.classroom.ClassroomCourse
import com.talom.core.classroom.ClassroomCoursework
import com.talom.core.classroom.ClassroomReadOnlyProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GoogleClassroomHttpResponse(
    val statusCode: Int,
    val body: String,
)

interface GoogleClassroomHttpClient {
    fun get(url: String, headers: Map<String, String>): GoogleClassroomHttpResponse
}

class UrlConnectionGoogleClassroomHttpClient(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : GoogleClassroomHttpClient {
    override fun get(url: String, headers: Map<String, String>): GoogleClassroomHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            GoogleClassroomHttpResponse(status, body)
        } catch (error: IOException) {
            throw error
        } finally {
            connection.disconnect()
        }
    }
}

class GoogleClassroomApiException(
    val statusCode: Int,
    message: String,
) : IOException(message)

object GoogleClassroomJsonParser {
    fun parseCourses(payload: String): Result<List<ClassroomCourse>> = runCatching {
        val root = ClassroomJson.parseObject(payload)
        root.arrayOfObjects("courses").mapNotNull { item ->
            val id = item.string("id")
            val name = item.string("name")
            if (id.isEmpty() || name.isEmpty()) {
                null
            } else {
                ClassroomCourse(
                    id = id,
                    name = name,
                    section = item.optionalString("section"),
                    room = item.optionalString("room"),
                    description = item.optionalString("description"),
                    courseState = item.string("courseState").ifEmpty { "UNKNOWN" },
                    alternateLink = item.optionalString("alternateLink"),
                )
            }
        }.distinctBy { it.id }
    }

    fun parseCoursework(payload: String, requestedCourseId: String): Result<List<ClassroomCoursework>> =
        runCatching {
            val root = ClassroomJson.parseObject(payload)
            root.arrayOfObjects("courseWork").mapNotNull { item ->
                val id = item.string("id")
                val title = item.string("title")
                val courseId = item.string("courseId").ifEmpty { requestedCourseId }.trim()
                if (id.isEmpty() || title.isEmpty() || courseId.isEmpty()) {
                    null
                } else {
                    ClassroomCoursework(
                        id = id,
                        courseId = courseId,
                        title = title,
                        description = item.optionalString("description"),
                        dueAtMillis = parseDueAtMillis(item),
                        state = item.string("state").ifEmpty { "UNKNOWN" },
                        alternateLink = item.optionalString("alternateLink"),
                    )
                }
            }.distinctBy { it.id }
        }

    fun parseAnnouncements(payload: String, requestedCourseId: String): Result<List<ClassroomAnnouncement>> =
        runCatching {
            val root = ClassroomJson.parseObject(payload)
            root.arrayOfObjects("announcements").mapNotNull { item ->
                val id = item.string("id")
                val text = item.string("text")
                val courseId = item.string("courseId").ifEmpty { requestedCourseId }.trim()
                val creationTimeMillis = parseTimestampMillis(item.string("creationTime"))
                if (id.isEmpty() || text.isEmpty() || courseId.isEmpty() || creationTimeMillis == null) {
                    null
                } else {
                    ClassroomAnnouncement(
                        id = id,
                        courseId = courseId,
                        text = text,
                        creationTimeMillis = creationTimeMillis,
                        alternateLink = item.optionalString("alternateLink"),
                    )
                }
            }.distinctBy { it.id }
        }

    private fun parseDueAtMillis(item: Map<String, Any?>): Long? {
        val date = item.objectValue("dueDate") ?: return null
        val year = date.int("year", -1)
        val month = date.int("month", -1)
        val day = date.int("day", -1)
        if (year < 1 || month !in 1..12 || day !in 1..31) return null
        val time = item.objectValue("dueTime")
        val hours = time?.int("hours", 0) ?: 0
        val minutes = time?.int("minutes", 0) ?: 0
        val seconds = time?.int("seconds", 0) ?: 0
        val nanos = time?.int("nanos", 0) ?: 0
        return runCatching {
            java.time.LocalDateTime.of(year, month, day, hours, minutes, seconds)
                .plusNanos(nanos.toLong())
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()
    }

    private fun parseTimestampMillis(value: String): Long? =
        runCatching { java.time.Instant.parse(value) }.getOrNull()?.toEpochMilli()

    private fun Map<String, Any?>.string(name: String): String =
        (this[name] as? String)?.trim().orEmpty()

    private fun Map<String, Any?>.optionalString(name: String): String? =
        string(name).takeIf { it.isNotEmpty() }

    private fun Map<String, Any?>.int(name: String, default: Int): Int =
        (this[name] as? Number)?.toInt() ?: default

    private fun Map<String, Any?>.objectValue(name: String): Map<String, Any?>? =
        this[name] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.arrayOfObjects(name: String): List<Map<String, Any?>> =
        (this[name] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
}

class GoogleClassroomReadOnlyProvider(
    private val tokenProvider: OAuthTokenProvider,
    baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: GoogleClassroomHttpClient = UrlConnectionGoogleClassroomHttpClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ClassroomReadOnlyProvider {
    private val apiBaseUrl = baseUrl.trimEnd('/').let {
        if (it.endsWith("/v1")) it else "$it/v1"
    }

    init {
        require(apiBaseUrl != "/v1") { "Google Classroom API base URL must not be blank." }
    }

    override suspend fun listCourses(): Result<List<ClassroomCourse>> =
        requestPaged("courses") { payload ->
            GoogleClassroomJsonParser.parseCourses(payload).getOrThrow()
        }

    override suspend fun listCoursework(courseId: String): Result<List<ClassroomCoursework>> {
        if (courseId.isBlank()) return Result.failure(IllegalArgumentException("Course ID must not be blank."))
        return requestPaged("courses/${encode(courseId)}/courseWork") { payload ->
            GoogleClassroomJsonParser.parseCoursework(payload, courseId).getOrThrow()
        }
    }

    override suspend fun listAnnouncements(courseId: String): Result<List<ClassroomAnnouncement>> {
        if (courseId.isBlank()) return Result.failure(IllegalArgumentException("Course ID must not be blank."))
        return requestPaged("courses/${encode(courseId)}/announcements") { payload ->
            GoogleClassroomJsonParser.parseAnnouncements(payload, courseId).getOrThrow()
        }
    }

    private suspend fun <T> requestPaged(
        path: String,
        parse: (String) -> List<T>,
    ): Result<List<T>> = runCatching {
        val token = tokenProvider.getAccessToken().getOrElse { throw it }
            .also { validateToken(it) }
        val values = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val query = buildString {
                append("pageSize=100")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(encode(pageToken!!))
                }
            }
            val response = httpClient.get(
                "$apiBaseUrl/$path?$query",
                mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "${token.tokenType.ifBlank { "Bearer" }} ${token.value}",
                ),
            )
            if (response.statusCode !in 200..299) {
                if (response.statusCode == 401) {
                    throw ExpiredOAuthTokenException(
                        "Google Classroom authorization expired or was rejected (HTTP 401).",
                    )
                }
                val detail = runCatching {
                    val error = ClassroomJson.parseObject(response.body)["error"]
                    when (error) {
                        is String -> error
                        is Map<*, *> -> (error["message"] as? String).orEmpty()
                        else -> ""
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                throw GoogleClassroomApiException(
                    response.statusCode,
                    "Google Classroom request failed with HTTP ${response.statusCode}" +
                        (detail?.let { ": $it" } ?: "."),
                )
            }
            val root = ClassroomJson.parseObject(response.body)
            values += parse(response.body)
            pageToken = (root["nextPageToken"] as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        values
    }

    private fun validateToken(token: OAuthAccessToken) {
        if (token.value.isBlank()) throw MissingOAuthTokenException()
        if (token.isExpired(nowMillis())) {
            throw ExpiredOAuthTokenException()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        const val DEFAULT_BASE_URL = "https://classroom.googleapis.com/v1"
    }
}

private object ClassroomJson {
    fun parseObject(payload: String): Map<String, Any?> =
        JsonParser(payload).parse().let {
            @Suppress("UNCHECKED_CAST")
            it as? Map<String, Any?>
                ?: throw IllegalArgumentException("Expected a JSON object.")
        }

    private class JsonParser(
        private val input: String,
    ) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != input.length) fail("Unexpected trailing data.")
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (index >= input.length) fail("Unexpected end of JSON.")
            return when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> fail("Unexpected character '${input[index]}'.")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                if (index >= input.length || input[index] != '"') fail("Expected an object key.")
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (consume('}')) return result
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (consume(']')) return result
            while (true) {
                result += parseValue()
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < input.length) {
                when (val character = input[index++]) {
                    '"' -> return result.toString()
                    '\\' -> {
                        if (index >= input.length) fail("Unterminated escape sequence.")
                        when (val escaped = input[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                if (index + 4 > input.length) fail("Invalid unicode escape.")
                                val code = input.substring(index, index + 4).toIntOrNull(16)
                                    ?: fail("Invalid unicode escape.")
                                result.append(code.toChar())
                                index += 4
                            }
                            else -> fail("Invalid escape character '$escaped'.")
                        }
                    }
                    else -> result.append(character)
                }
            }
            fail("Unterminated string.")
        }

        private fun parseNumber(): Number {
            val start = index
            if (consume('-')) Unit
            consumeDigits()
            if (consume('.')) consumeDigits(required = true)
            if (index < input.length && (input[index] == 'e' || input[index] == 'E')) {
                index++
                if (index < input.length && (input[index] == '+' || input[index] == '-')) index++
                consumeDigits(required = true)
            }
            val value = input.substring(start, index)
            return value.toLongOrNull() ?: value.toDoubleOrNull()
            ?: fail("Invalid number.")
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (!input.startsWith(literal, index)) fail("Invalid literal.")
            index += literal.length
            return value
        }

        private fun consumeDigits(required: Boolean = false) {
            val start = index
            while (index < input.length && input[index] in '0'..'9') index++
            if (required && start == index) fail("Expected a digit.")
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun consume(expected: Char): Boolean =
            if (index < input.length && input[index] == expected) {
                index++
                true
            } else {
                false
            }

        private fun expect(expected: Char) {
            if (!consume(expected)) fail("Expected '$expected'.")
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("$message (at character $index)")
    }
}
