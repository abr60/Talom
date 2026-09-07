package com.talom.data.classroom

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.talom.core.classroom.ClassroomAnnouncement
import com.talom.core.classroom.ClassroomCourse
import com.talom.core.classroom.ClassroomCoursework

@Entity(tableName = "classroom_courses")
data class ClassroomCourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val section: String?,
    val room: String?,
    val description: String?,
    val courseState: String,
    val alternateLink: String?,
    val storedAtMillis: Long,
) {
    fun toDomain() = ClassroomCourse(id, name, section, room, description, courseState, alternateLink)

    companion object {
        fun fromDomain(value: ClassroomCourse) = ClassroomCourseEntity(
            value.id, value.name, value.section, value.room, value.description,
            value.courseState, value.alternateLink, System.currentTimeMillis(),
        )
    }
}

@Entity(tableName = "classroom_coursework")
data class ClassroomCourseworkEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val description: String?,
    val dueAtMillis: Long?,
    val state: String,
    val alternateLink: String?,
    val storedAtMillis: Long,
) {
    fun toDomain() = ClassroomCoursework(
        id, courseId, title, description, dueAtMillis, state, alternateLink,
    )

    companion object {
        fun fromDomain(value: ClassroomCoursework) = ClassroomCourseworkEntity(
            value.id, value.courseId, value.title, value.description, value.dueAtMillis,
            value.state, value.alternateLink, System.currentTimeMillis(),
        )
    }
}

@Entity(tableName = "classroom_announcements")
data class ClassroomAnnouncementEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val text: String,
    val creationTimeMillis: Long,
    val alternateLink: String?,
    val storedAtMillis: Long,
) {
    fun toDomain() = ClassroomAnnouncement(id, courseId, text, creationTimeMillis, alternateLink)

    companion object {
        fun fromDomain(value: ClassroomAnnouncement) = ClassroomAnnouncementEntity(
            value.id, value.courseId, value.text, value.creationTimeMillis,
            value.alternateLink, System.currentTimeMillis(),
        )
    }
}
