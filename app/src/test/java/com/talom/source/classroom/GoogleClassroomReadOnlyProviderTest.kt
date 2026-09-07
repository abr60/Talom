package com.talom.source.classroom

import com.talom.core.auth.OAuthAccessToken
import com.talom.core.auth.OAuthTokenProvider
import com.talom.core.classroom.ClassroomAcademicItemMapper
import com.talom.core.classroom.ClassroomCoursework
import com.talom.core.classroom.GOOGLE_CLASSROOM_SOURCE_PREFIX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleClassroomReadOnlyProviderTest {
    @Test
    fun parserKeepsValidRecordsAndHandlesOptionalFields() {
        val result = GoogleClassroomJsonParser.parseCoursework(
            """
            {
              "courseWork": [
                {
                  "id": "cw-1",
                  "courseId": "course-1",
                  "title": "Essay",
                  "description": "Read chapter 2",
                  "dueDate": {"year": 2026, "month": 9, "day": 7},
                  "state": "PUBLISHED",
                  "unknownField": true
                },
                {"id": "malformed", "title": ""}
              ]
            }
            """.trimIndent(),
            requestedCourseId = "course-1",
        ).getOrThrow()

        assertEquals(1, result.size)
        assertEquals("cw-1", result.single().id)
        assertEquals("course-1", result.single().courseId)
        assertTrue(result.single().dueAtMillis != null)
    }

    @Test
    fun mappingIsStableAndUsesASeparateSourceNamespace() {
        val coursework = ClassroomCoursework(
            id = "cw-1",
            courseId = "course-1",
            title = "Essay",
            description = "Details",
            dueAtMillis = 1234L,
            state = "PUBLISHED",
            alternateLink = null,
        )

        val first = ClassroomAcademicItemMapper.fromCoursework(coursework, subject = "History")
        val second = ClassroomAcademicItemMapper.fromCoursework(coursework, subject = "History")

        assertEquals(first, second)
        assertEquals("ASSIGNMENT", first.type.name)
        assertTrue(first.stableId.startsWith(GOOGLE_CLASSROOM_SOURCE_PREFIX))
        assertTrue(first.sourceJid.startsWith(GOOGLE_CLASSROOM_SOURCE_PREFIX))
        assertTrue(first.sourceMessageId >= 0)
    }

    @Test
    fun parserReadsAnnouncementsAndRfc3339CreationTime() {
        val result = GoogleClassroomJsonParser.parseAnnouncements(
            """
            {
              "announcements": [{
                "id": "announcement-1",
                "text": "Class moved online",
                "creationTime": "2026-09-07T00:00:00Z",
                "alternateLink": "https://classroom.google.com/c/1"
              }]
            }
            """.trimIndent(),
            requestedCourseId = "course-1",
        ).getOrThrow()

        assertEquals("course-1", result.single().courseId)
        assertEquals(1788739200000L, result.single().creationTimeMillis)
    }

    @Test
    fun missingTokenFailsClosedBeforeMakingAnHttpRequest() {
        val provider = GoogleClassroomReadOnlyProvider(
            tokenProvider = object : OAuthTokenProvider {
                override suspend fun getAccessToken(): Result<OAuthAccessToken> =
                    Result.success(OAuthAccessToken(""))
            },
            httpClient = object : GoogleClassroomHttpClient {
                override fun get(
                    url: String,
                    headers: Map<String, String>,
                ): GoogleClassroomHttpResponse = error("HTTP must not be called without a token")
            },
        )

        val error = kotlinx.coroutines.runBlocking {
            provider.listCourses().exceptionOrNull()
        }

        assertFailsWith<com.talom.core.auth.MissingOAuthTokenException> {
            throw (error ?: AssertionError("Expected missing-token failure"))
        }
    }
}
