package com.talom.data.whatsapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whatsapp_whitelist")
data class WhatsAppWhitelist(
    @PrimaryKey val jid: String,
    val label: String?,
    val addedAtMillis: Long,
    val category: RelationCategory = RelationCategory.FAMILY,
)

@Entity(tableName = "whatsapp_cursors")
data class WhatsAppCursorEntity(
    @PrimaryKey val sourceId: String,
    val timestampMillis: Long,
    val messageId: Long,
)
