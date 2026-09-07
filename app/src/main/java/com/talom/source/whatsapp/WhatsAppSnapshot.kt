package com.talom.source.whatsapp

import android.content.Context
import java.io.File
import java.time.Instant

data class WhatsAppSnapshot(
    val database: File,
    val capturedAt: Instant,
)

interface WhatsAppSnapshotProvider {
    suspend fun createSnapshot(): Result<WhatsAppSnapshot>
}

/**
 * Root snapshot boundary. The actual command is kept behind this interface so
 * extraction never reads WhatsApp's live database directly.
 */
class RootWhatsAppSnapshotProvider(
    private val context: Context,
    private val commandRunner: RootCommandRunner = ShellRootCommandRunner(),
    private val ownerUid: Int = android.os.Process.myUid(),
) : WhatsAppSnapshotProvider {
    override suspend fun createSnapshot(): Result<WhatsAppSnapshot> {
        val output = File(context.cacheDir, "whatsapp-snapshot")
        if (!output.mkdirs() && !output.isDirectory) {
            return Result.failure(IllegalStateException("Could not create snapshot directory"))
        }

        val database = File(output, "msgstore.db")
        val command = """
            set -eu
            test "`id -u`" = "0"
            rm -rf '${output.absolutePath}'
            mkdir -p '${output.absolutePath}'
            cp -a /data/user/0/com.whatsapp/databases/msgstore.db '${database.absolutePath}'
            if [ -f /data/user/0/com.whatsapp/databases/msgstore.db-wal ]; then
              cp -a /data/user/0/com.whatsapp/databases/msgstore.db-wal '${database.absolutePath}-wal'
            fi
            if [ -f /data/user/0/com.whatsapp/databases/msgstore.db-shm ]; then
              cp -a /data/user/0/com.whatsapp/databases/msgstore.db-shm '${database.absolutePath}-shm'
            fi
            chown -R $ownerUid:$ownerUid '${output.absolutePath}'
            chmod 700 '${output.absolutePath}'
            chmod 600 '${database.absolutePath}' '${database.absolutePath}-wal' '${database.absolutePath}-shm' 2>/dev/null || true
        """.trimIndent()

        return commandRunner.run(command).map {
            if (!database.isFile || database.length() == 0L) {
                throw IllegalStateException("WhatsApp snapshot database is missing or empty")
            }
            WhatsAppSnapshot(database, Instant.now())
        }
    }
}

fun interface RootCommandRunner {
    fun run(command: String): Result<Unit>
}

class ShellRootCommandRunner : RootCommandRunner {
    override fun run(command: String): Result<Unit> {
        return runCatching {
            val process = ProcessBuilder("su", "--mount-master", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "Root snapshot failed with exit code $exitCode: ${output.take(500)}"
            }
        }
    }
}
