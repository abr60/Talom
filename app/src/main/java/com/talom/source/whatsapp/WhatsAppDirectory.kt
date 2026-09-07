package com.talom.source.whatsapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class WhatsAppConversation(
    val jid: String,
    val label: String,
    val isGroup: Boolean,
)

interface WhatsAppDirectoryProvider {
    suspend fun listConversations(): Result<List<WhatsAppConversation>>
}

/**
 * Reads only WhatsApp conversation/contact metadata. Message bodies are never
 * read by this directory provider.
 */
class RootWhatsAppDirectoryProvider(
    private val context: Context,
    private val commandRunner: RootCommandRunner = ShellRootCommandRunner(),
    private val ownerUid: Int = android.os.Process.myUid(),
) : WhatsAppDirectoryProvider {
    override suspend fun listConversations(): Result<List<WhatsAppConversation>> {
        val output = File(context.cacheDir, "whatsapp-directory")
        val msgstore = File(output, "msgstore.db")
        val waDb = File(output, "wa.db")
        val command = """
            set -eu
            rm -rf '${output.absolutePath}'
            mkdir -p '${output.absolutePath}'
            cp -a /data/user/0/com.whatsapp/databases/msgstore.db '${msgstore.absolutePath}'
            cp -a /data/user/0/com.whatsapp/databases/wa.db '${waDb.absolutePath}'
            chown -R $ownerUid:$ownerUid '${output.absolutePath}'
            chmod 700 '${output.absolutePath}'
            chmod 600 '${msgstore.absolutePath}' '${waDb.absolutePath}'
        """.trimIndent()

        return commandRunner.run(command).map {
            check(msgstore.isFile && waDb.isFile) {
                "WhatsApp contact databases are missing."
            }
            readConversations(msgstore, waDb)
        }
    }

    private fun readConversations(msgstoreFile: File, waDbFile: File): List<WhatsAppConversation> {
        val contacts = readContactNames(waDbFile)
        val result = linkedMapOf<String, WhatsAppConversation>()
        SQLiteDatabase.openDatabase(
            msgstoreFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery(
                """
                SELECT j.raw_string, c.subject
                FROM chat c
                JOIN jid j ON j._id = c.jid_row_id
                WHERE j.raw_string LIKE '%@g.us'
                   OR j.raw_string LIKE '%@s.whatsapp.net'
                ORDER BY COALESCE(c.subject, j.raw_string)
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val jid = cursor.getString(0)
                    val subject = cursor.getStringOrNull(1)
                    val label = if (jid.endsWith("@g.us")) {
                        subject?.takeIf { it.isNotBlank() }
                    } else {
                        contacts[jid]?.takeIf { it.isNotBlank() }
                    }
                    if (label != null) {
                        result[jid] = WhatsAppConversation(
                            jid = jid,
                            label = label,
                            isGroup = jid.endsWith("@g.us"),
                        )
                    }
                }
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    private fun readContactNames(file: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val columns = database.rawQuery("PRAGMA table_info(wa_contacts)", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            val jidColumn = columns.firstOrNull {
                it.equals("jid", true) || it.equals("raw_string", true)
            } ?: return result
            val nameColumn = columns.firstOrNull {
                it.equals("display_name", true) ||
                    it.equals("wa_name", true) ||
                    it.equals("given_name", true)
            } ?: return result
            database.rawQuery(
                "SELECT $jidColumn, $nameColumn FROM wa_contacts " +
                    "WHERE $jidColumn LIKE '%@s.whatsapp.net'",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(0) && !cursor.isNull(1)) {
                        result[cursor.getString(0)] = cursor.getString(1)
                    }
                }
            }
        }
        return result
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
