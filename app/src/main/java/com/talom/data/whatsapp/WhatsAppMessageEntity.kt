package com.talom.data.whatsapp

import androidx.room.Entity

@Entity(
    tableName = "whatsapp_messages",
    primaryKeys = ["messageId", "jid"],
)
data class WhatsAppMessageEntity(
    val messageId: Long,
    val chatId: Long,
    val jid: String,
    val chatSubject: String?,
    val fromMe: Boolean,
    val timestampMillis: Long,
    val messageType: Int,
    val hasText: Boolean,
)
