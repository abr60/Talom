package com.talom.core.classroom

data class ClassroomCourse(
    val id: String,
    val name: String,
    val section: String?,
    val room: String?,
    val description: String?,
    val courseState: String,
    val alternateLink: String?,
)

data class ClassroomCoursework(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String?,
    val dueAtMillis: Long?,
    val state: String,
    val alternateLink: String?,
)

data class ClassroomAnnouncement(
    val id: String,
    val courseId: String,
    val text: String,
    val creationTimeMillis: Long,
    val alternateLink: String?,
)

interface ClassroomReadOnlyProvider {
    suspend fun listCourses(): Result<List<ClassroomCourse>>
    suspend fun listCoursework(courseId: String): Result<List<ClassroomCoursework>>
    suspend fun listAnnouncements(courseId: String): Result<List<ClassroomAnnouncement>>
}
