package com.talom.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TalomDatabaseProvider {
    @Volatile
    private var instance: TalomDatabase? = null

    fun get(context: Context): TalomDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TalomDatabase::class.java,
                "talom.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build().also { instance = it }
        }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pull_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceId TEXT NOT NULL,
                    startedAtMillis INTEGER NOT NULL,
                    completedAtMillis INTEGER,
                    state TEXT NOT NULL,
                    itemCount INTEGER NOT NULL,
                    detail TEXT
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS academic_items (
                    stableId TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    details TEXT,
                    subject TEXT,
                    dueAtMillis INTEGER,
                    sourceJid TEXT NOT NULL,
                    sourceMessageId INTEGER NOT NULL,
                    confidence REAL NOT NULL,
                    extractionVersion INTEGER NOT NULL,
                    providerId TEXT NOT NULL,
                    modelId TEXT NOT NULL,
                    storedAtMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_insights (
                    stableId TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    details TEXT,
                    sourceJid TEXT NOT NULL,
                    sourceMessageId INTEGER NOT NULL,
                    confidence REAL NOT NULL,
                    extractionVersion INTEGER NOT NULL,
                    providerId TEXT NOT NULL,
                    modelId TEXT NOT NULL,
                    storedAtMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE pull_log ADD COLUMN academicItemCount INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE pull_log ADD COLUMN insightCount INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE pull_log ADD COLUMN skippedCount INTEGER NOT NULL DEFAULT 0")
        }

    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS classroom_courses (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        section TEXT,
                        room TEXT,
                        description TEXT,
                        courseState TEXT NOT NULL,
                        alternateLink TEXT,
                        storedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS classroom_coursework (
                        id TEXT NOT NULL PRIMARY KEY,
                        courseId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        dueAtMillis INTEGER,
                        state TEXT NOT NULL,
                        alternateLink TEXT,
                        storedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS classroom_announcements (
                        id TEXT NOT NULL PRIMARY KEY,
                        courseId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        creationTimeMillis INTEGER NOT NULL,
                        alternateLink TEXT,
                        storedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS whatsapp_directory (
                    jid TEXT NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    isGroup INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE whatsapp_whitelist ADD COLUMN category TEXT NOT NULL DEFAULT 'GENERAL'")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Map the dropped GENERAL category to FAMILY for legacy rows.
            database.execSQL("UPDATE whatsapp_whitelist SET category = 'FAMILY' WHERE category = 'GENERAL'")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No schema change. The academic_items.type column is TEXT, so Room
            // accepts the new enum string values (CLASS_TEST, PRESENTATION,
            // VIVA, INTERVIEW, PRACTICAL) on next insert. Bumping the version
            // makes Room accept these without a destructive migration.
        }
    }
}
