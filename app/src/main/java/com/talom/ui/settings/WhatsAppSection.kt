package com.talom.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talom.data.source.PullLogEntity
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.source.whatsapp.WhatsAppConversation
import com.talom.ui.components.ActionRow
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SettingsGroup

/** Minimal WhatsApp source management — whitelist, collapsible JID, sync with anchored directory. */
@Composable
fun WhatsAppSection(
    whitelist: List<WhatsAppWhitelist>,
    directory: List<WhatsAppConversation>,
    directoryState: String,
    directoryLoading: Boolean,
    directoryQuery: String,
    pullState: String,
    pulling: Boolean,
    importState: String,
    jidInput: String,
    pullLog: List<PullLogEntity>,
    formatTime: (Long) -> String,
    isJidValid: Boolean,
    onDirectoryQueryChange: (String) -> Unit,
    onLoadDirectory: () -> Unit,
    onWhitelistConversation: (WhatsAppConversation) -> Unit,
    onRemoveWhitelist: (WhatsAppWhitelist) -> Unit,
    onJidInputChange: (String) -> Unit,
    onAddJid: () -> Unit,
    onPull: () -> Unit,
    onImport: () -> Unit,
) {
    var jidExpanded by remember { mutableStateOf(false) }
    var directoryExpanded by remember(directory) { mutableStateOf(directory.isNotEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("WhatsApp")
        Text(
            "Only whitelisted conversations are ever read.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Whitelisted conversations
        if (whitelist.isEmpty()) {
            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        "No conversations whitelisted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            SettingsGroup {
                whitelist.forEachIndexed { index, item ->
                    ActionRow(
                        label = item.label ?: item.jid,
                        actionLabel = "Remove",
                        onAction = { onRemoveWhitelist(item) },
                        showDivider = index < whitelist.lastIndex,
                    )
                }
            }
        }

        // Collapsible JID
        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { jidExpanded = !jidExpanded }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add by JID", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "120363...@g.us for groups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (jidExpanded) "▴" else "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (jidExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = jidInput,
                        onValueChange = onJidInputChange,
                        label = { Text("WhatsApp JID") },
                        placeholder = { Text("120363...@g.us") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
                ActionRow(
                    label = "Add conversation by JID",
                    actionLabel = "Add",
                    onAction = {
                        onAddJid()
                        if (isJidValid) jidExpanded = false
                    },
                    actionEnabled = isJidValid,
                )
            }
        }

        // Sync card — Load row anchors the directory list
        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = directory.isNotEmpty()) { directoryExpanded = !directoryExpanded }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Load contacts & groups", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        directoryState,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (directory.isNotEmpty()) {
                        Text(
                            if (directoryExpanded) "▴" else "▾",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clickable(enabled = !directoryLoading, onClick = {
                            onLoadDirectory()
                            directoryExpanded = true
                        }),
                    ) {
                        Text(
                            if (directoryLoading) "..." else "Load",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Anchored directory list — directly under Load row
            if (directory.isNotEmpty() && directoryExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = directoryQuery,
                        onValueChange = onDirectoryQueryChange,
                        label = { Text("Search") },
                        placeholder = { Text("Filter contacts…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    val shown = directory
                        .filter {
                            directoryQuery.isBlank() ||
                                it.label.contains(directoryQuery, ignoreCase = true) ||
                                it.jid.contains(directoryQuery, ignoreCase = true)
                        }
                        .take(20)
                    shown.forEachIndexed { index, conversation ->
                        ActionRow(
                            label = "${conversation.label}${if (conversation.isGroup) " (group)" else ""}",
                            actionLabel = if (whitelist.any { it.jid == conversation.jid }) "Added" else "+ Add",
                            onAction = { onWhitelistConversation(conversation) },
                            actionEnabled = whitelist.none { it.jid == conversation.jid },
                            showDivider = index < shown.lastIndex,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            } else if (directory.isEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            ActionRow(
                label = "Pull WhatsApp",
                caption = pullState,
                actionLabel = if (pulling) "..." else "Pull",
                onAction = onPull,
                actionEnabled = whitelist.isNotEmpty() && !pulling,
                showDivider = true,
            )
            ActionRow(
                label = "Import exported chat",
                caption = importState,
                actionLabel = "Import",
                onAction = onImport,
            )
        }

        if (pullLog.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeader("Pull history")
                pullLog.take(3).forEach { log ->
                    Text(
                        "${formatTime(log.startedAtMillis)}  ${log.state}  " +
                            "${log.itemCount} msgs, ${log.academicItemCount} acad, " +
                            "${log.insightCount} pers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
