package com.talom.data.classroom

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassroomDao {
    @Query("SELECT * FROM classroom_courses ORDER BY name")
    fun observeCourses(): Flow<List<ClassroomCourseEntity>>

    @Query("SELECT * FROM classroom_coursework ORDER BY dueAtMillis")
    fun observeCoursework(): Flow<List<ClassroomCourseworkEntity>>

    @Query("SELECT * FROM classroom_announcements ORDER BY creationTimeMillis DESC")
    fun observeAnnouncements(): Flow<List<ClassroomAnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(items: List<ClassroomCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoursework(items: List<ClassroomCourseworkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnouncements(items: List<ClassroomAnnouncementEntity>)
}
