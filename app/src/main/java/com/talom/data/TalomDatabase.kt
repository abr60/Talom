package com.talom.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.talom.data.whatsapp.WhatsAppCursorDao
import com.talom.data.whatsapp.WhatsAppCursorEntity
import com.talom.data.whatsapp.WhatsAppDirectoryDao
import com.talom.data.whatsapp.WhatsAppDirectoryEntity
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.data.whatsapp.WhatsAppWhitelistDao
import com.talom.data.whatsapp.WhatsAppMessageDao
import com.talom.data.whatsapp.WhatsAppMessageEntity
import com.talom.data.source.SourceStatusDao
import com.talom.data.source.SourceStatusEntity
import com.talom.data.source.PullLogDao
import com.talom.data.source.PullLogEntity
import com.talom.data.academic.AcademicItemDao
import com.talom.data.academic.AcademicItemEntity
import com.talom.data.academic.ConversationInsightDao
import com.talom.data.academic.ConversationInsightEntity
import com.talom.data.classroom.ClassroomAnnouncementEntity
import com.talom.data.classroom.ClassroomCourseEntity
import com.talom.data.classroom.ClassroomCourseworkEntity

@Database(
    entities = [
        WhatsAppWhitelist::class,
        WhatsAppCursorEntity::class,
        WhatsAppMessageEntity::class,
        WhatsAppDirectoryEntity::class,
        SourceStatusEntity::class,
        PullLogEntity::class,
        AcademicItemEntity::class,
        ConversationInsightEntity::class,
        ClassroomCourseEntity::class,
        ClassroomCourseworkEntity::class,
        ClassroomAnnouncementEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class TalomDatabase : RoomDatabase() {
    abstract fun whatsappWhitelistDao(): WhatsAppWhitelistDao
    abstract fun whatsappCursorDao(): WhatsAppCursorDao
    abstract fun whatsappMessageDao(): WhatsAppMessageDao
    abstract fun whatsappDirectoryDao(): WhatsAppDirectoryDao
    abstract fun sourceStatusDao(): SourceStatusDao
    abstract fun pullLogDao(): PullLogDao
    abstract fun academicItemDao(): AcademicItemDao
    abstract fun conversationInsightDao(): ConversationInsightDao
    abstract fun classroomDao(): com.talom.data.classroom.ClassroomDao
}
