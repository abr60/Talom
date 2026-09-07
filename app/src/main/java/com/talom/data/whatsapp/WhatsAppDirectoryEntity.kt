package com.talom.data.whatsapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whatsapp_directory")
data class WhatsAppDirectoryEntity(
    @PrimaryKey val jid: String,
    val label: String,
    val isGroup: Boolean,
    val updatedAtMillis: Long,
)
