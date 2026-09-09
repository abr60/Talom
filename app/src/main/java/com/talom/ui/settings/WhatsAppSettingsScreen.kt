package com.talom.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talom.data.source.PullLogEntity
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.ui.components.ActionRow
import com.talom.ui.components.NavRow
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SettingsGroup
import com.talom.ui.components.SettingsSubpageScaffold

@Composable
fun WhatsAppSettingsScreen(
    whitelist: List<WhatsAppWhitelist>,
    pulling: Boolean,
    pullState: String,
    importState: String,
    onPull: () -> Unit,
    onForcePull: () -> Unit,
    onImport: () -> Unit,
    pullLog: List<PullLogEntity>,
    formatTime: (Long) -> String,
    pullHour: Int,
    pullMinute: Int,
    onPullTimeChange: (Int, Int) -> Unit,
    onManageConversations: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pullLabel = remember(pullHour, pullMinute) {
        "%02d:%02d".format(pullHour, pullMinute)
    }
    SettingsSubpageScaffold(title = "WhatsApp", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Conversations")
            SettingsGroup {
                NavRow(
                    label = "Manage conversations",
                    caption = "${whitelist.size} whitelisted",
                    onClick = onManageConversations,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Pull schedule")
            SettingsGroup {
                NavRow(
                    label = "Daily pull time",
                    caption = "Runs at $pullLabel local time",
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onPullTimeChange(hour, minute) },
                            pullHour,
                            pullMinute,
                            true,
                        ).show()
                    },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Sync")
            SettingsGroup {
                ActionRow(
                    label = "Pull WhatsApp",
                    caption = pullState,
                    actionLabel = if (pulling) "..." else "Pull",
                    onAction = onPull,
                    actionEnabled = !pulling && whitelist.isNotEmpty(),
                    showDivider = true,
                )
                ActionRow(
                    label = "Re-extract last N days",
                    caption = "Re-runs AI over the current message window even with no new messages.",
                    actionLabel = if (pulling) "..." else "Re-run",
                    onAction = onForcePull,
                    actionEnabled = !pulling && whitelist.isNotEmpty(),
                    showDivider = true,
                )
                ActionRow(
                    label = "Import exported chat",
                    caption = importState,
                    actionLabel = "Import",
                    onAction = onImport,
                )
            }
        }

        if (pullLog.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Pull history")
                SettingsGroup {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        pullLog.take(3).forEach { log ->
                            val ac = log.academicItemCount ?: 0
                            val ins = log.insightCount ?: 0
                            Text(
                                "${formatTime(log.startedAtMillis)} ${log.state} ${log.itemCount} msgs, $ac acad, $ins pers",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            if (log.state != "SUCCESS" && !log.detail.isNullOrBlank()) {
                                Text(
                                    log.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (log.state) {
                                        "FAILED" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (whitelist.isEmpty()) {
            Text(
                "No whitelisted conversations. Open Manage conversations to add from your directory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
