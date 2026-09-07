package com.talom.data.classroom

import com.talom.core.ai.AcademicItemValidator
import com.talom.core.classroom.ClassroomAcademicItemMapper
import com.talom.core.classroom.ClassroomCoursework
import com.talom.data.academic.AcademicItemDao
import com.talom.data.academic.AcademicItemEntity

class ClassroomAcademicItemRepository(
    private val dao: AcademicItemDao,
    private val extractionVersion: Int = 1,
) {
    suspend fun persist(
        coursework: List<ClassroomCoursework>,
        subjectsByCourseId: Map<String, String> = emptyMap(),
    ): Result<Int> = runCatching {
        val validated = AcademicItemValidator.validateAll(
            coursework.map {
                ClassroomAcademicItemMapper.fromCoursework(
                    coursework = it,
                    subject = subjectsByCourseId[it.courseId],
                    extractionVersion = extractionVersion,
                )
            },
            expectedVersion = extractionVersion,
        ).getOrThrow()
        dao.upsertAll(
            validated.map {
                AcademicItemEntity.fromDomain(
                    item = it,
                    providerId = "google-classroom",
                    modelId = "google-classroom-api-v1",
                )
            },
        )
        validated.size
    }
}
