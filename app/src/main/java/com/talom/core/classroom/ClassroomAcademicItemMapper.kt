package com.talom.core.classroom

import com.talom.core.ai.AcademicItem
import com.talom.core.ai.AcademicItemType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val GOOGLE_CLASSROOM_SOURCE_PREFIX = "google-classroom://"

object ClassroomAcademicItemMapper {
    fun fromCoursework(
        coursework: ClassroomCoursework,
        subject: String? = null,
        extractionVersion: Int = 1,
    ): AcademicItem = AcademicItem(
        stableId = "$GOOGLE_CLASSROOM_SOURCE_PREFIX/coursework/${segment(coursework.courseId)}/${segment(coursework.id)}",
        type = AcademicItemType.ASSIGNMENT,
        title = coursework.title,
        details = coursework.description,
        subject = subject,
        dueAtMillis = coursework.dueAtMillis,
        sourceJid = "$GOOGLE_CLASSROOM_SOURCE_PREFIX/course/${segment(coursework.courseId)}",
        sourceMessageId = stableSourceMessageId(coursework),
        confidence = 1f,
        extractionVersion = extractionVersion,
    )

    private fun stableSourceMessageId(coursework: ClassroomCoursework): Long =
        (coursework.courseId + "\u0000" + coursework.id).hashCode().toLong() and Long.MAX_VALUE

    private fun segment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
