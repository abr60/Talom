package com.talom.data.classroom

import com.talom.core.classroom.ClassroomReadOnlyProvider
import com.talom.data.academic.AcademicItemDao
import com.talom.data.source.SourceStatusDao
import com.talom.data.source.SourceStatusEntity

class ClassroomRepository(
    private val provider: ClassroomReadOnlyProvider,
    private val dao: ClassroomDao,
    academicItemDao: AcademicItemDao? = null,
    extractionVersion: Int = 1,
    private val statusDao: SourceStatusDao? = null,
) {
    private val academicRepository = academicItemDao?.let {
        ClassroomAcademicItemRepository(it, extractionVersion)
    }

    suspend fun refresh(): Result<Unit> {
        statusDao?.save(
            SourceStatusEntity(
                sourceId = SOURCE_ID,
                state = "RUNNING",
                lastSuccessfulPullMillis = statusDao.get(SOURCE_ID)?.lastSuccessfulPullMillis,
                lastSnapshotMillis = System.currentTimeMillis(),
                detail = "Refreshing Google Classroom.",
            ),
        )
        return runCatching {
            val courses = provider.listCourses().getOrThrow()
            dao.upsertCourses(courses.map(ClassroomCourseEntity::fromDomain))
            val allCoursework = mutableListOf<com.talom.core.classroom.ClassroomCoursework>()
            courses.forEach { course ->
                val coursework = provider.listCoursework(course.id).getOrThrow()
                allCoursework += coursework
                dao.upsertCoursework(coursework.map(ClassroomCourseworkEntity::fromDomain))
                dao.upsertAnnouncements(
                    provider.listAnnouncements(course.id).getOrThrow()
                        .map(ClassroomAnnouncementEntity::fromDomain),
                )
            }
            academicRepository?.persist(
                coursework = allCoursework,
                subjectsByCourseId = courses.associate { it.id to it.name },
            )?.getOrThrow()
            Unit
        }.onSuccess {
            statusDao?.save(
                SourceStatusEntity(
                    sourceId = SOURCE_ID,
                    state = "READY",
                    lastSuccessfulPullMillis = System.currentTimeMillis(),
                    lastSnapshotMillis = System.currentTimeMillis(),
                    detail = "Google Classroom refresh completed.",
                ),
            )
        }.onFailure { error ->
            statusDao?.save(
                SourceStatusEntity(
                    sourceId = SOURCE_ID,
                    state = "FAILED",
                    lastSuccessfulPullMillis = statusDao.get(SOURCE_ID)?.lastSuccessfulPullMillis,
                    lastSnapshotMillis = System.currentTimeMillis(),
                    detail = error.message ?: "Google Classroom refresh failed.",
                ),
            )
        }

    }

    companion object {
        const val SOURCE_ID = "google-classroom"
    }
}
