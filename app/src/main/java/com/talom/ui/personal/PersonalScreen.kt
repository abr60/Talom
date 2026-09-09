package com.talom.ui.personal

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talom.core.ai.InsightType
import com.talom.data.academic.ConversationInsightEntity
import com.talom.data.source.PullLogEntity
import com.talom.data.whatsapp.RelationCategory
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.source.whatsapp.WhatsAppConversation
import com.talom.ui.components.AppHeader
import com.talom.ui.components.PullStatusBanner
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.StatBar
import com.talom.ui.components.TalomCard

private enum class InsightGroup { REMINDERS, FRIENDS, FAMILY, GENERAL }

private fun messageKey(jid: String, messageId: Long): String = "$jid|$messageId"

/**
 * Route insights by whitelist relationship for Friends/Family/General.
 * Reminders stay type-driven (PERSONAL_REMINDER / PLAN).
 */
private fun partitionInsights(
    items: List<ConversationInsightEntity>,
    categoryByJid: Map<String, RelationCategory>,
    fromMeKeys: Set<String>,
): Map<InsightGroup, List<ConversationInsightEntity>> {
    val reminders = mutableListOf<ConversationInsightEntity>()
    val friends = mutableListOf<ConversationInsightEntity>()
    val family = mutableListOf<ConversationInsightEntity>()
    val general = mutableListOf<ConversationInsightEntity>()
    items.forEach { e ->
        val type = runCatching { InsightType.valueOf(e.type) }.getOrNull()
        val fromMe = messageKey(e.sourceJid, e.sourceMessageId) in fromMeKeys
        // Hide standard sent messages; keep user-originated plan/reminder confirmations.
        if (fromMe && type != InsightType.PERSONAL_REMINDER && type != InsightType.PLAN) {
            return@forEach
        }
        when (type) {
            InsightType.PERSONAL_REMINDER, InsightType.PLAN -> reminders += e
            else -> when (categoryByJid[e.sourceJid]) {
                RelationCategory.FRIENDS -> friends += e
                RelationCategory.FAMILY -> family += e
                RelationCategory.WORK, RelationCategory.ACADEMIC, null -> general += e
            }
        }
    }
    return mapOf(
        InsightGroup.REMINDERS to reminders,
        InsightGroup.FRIENDS to friends,
        InsightGroup.FAMILY to family,
        InsightGroup.GENERAL to general,
    )
}

private fun displayMillis(item: ConversationInsightEntity): Long =
    item.receivedAtMillis ?: item.storedAtMillis

private fun relativeTime(millis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

@Composable
fun PersonalScreen(
    insights: List<ConversationInsightEntity>,
    whitelist: List<WhatsAppWhitelist>,
    directory: List<WhatsAppConversation>,
    fromMeKeys: Set<String> = emptySet(),
    latestPullLog: PullLogEntity? = null,
) {
    val now = remember(insights) { System.currentTimeMillis() }
    val senderByJid = remember(whitelist, directory) {
        buildMap {
            whitelist.forEach { w -> put(w.jid, w.label ?: w.jid) }
            directory.forEach { c -> putIfAbsent(c.jid, c.label) }
        }
    }
    val categoryByJid = remember(whitelist) {
        whitelist.associate { it.jid to it.category }
    }
    val groups = remember(insights, categoryByJid, fromMeKeys) {
        partitionInsights(insights, categoryByJid, fromMeKeys)
    }
    val totalInsights = groups.values.sumOf { it.size }

    val overviewStats = remember(groups) {
        listOf(
            (groups[InsightGroup.REMINDERS]?.size ?: 0) to "Reminders",
            (groups[InsightGroup.FRIENDS]?.size ?: 0) to "Friends",
            (groups[InsightGroup.FAMILY]?.size ?: 0) to "Family",
            (groups[InsightGroup.GENERAL]?.size ?: 0) to "General",
        ).filter { it.first > 0 }.map { it.first.toString() to it.second }
    }

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        AppHeader(
            secondary = when (totalInsights) {
                0 -> "No insights yet"
                1 -> "1 insight"
                else -> "$totalInsights insights"
            },
        )
        Text(
            text = "Personal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        PullStatusBanner(latestPullLog)

        if (overviewStats.isNotEmpty()) {
            StatBar(
                header = "Overview",
                stats = overviewStats,
            )
        }

        ReminderSection(
            title = "Reminders & plans",
            items = groups[InsightGroup.REMINDERS].orEmpty(),
            emptyCaption = "No reminders or plans yet — they'll show up as your chats come in.",
            now = now,
            senderByJid = senderByJid,
        )

        ContactGroupedSection(
            title = "Friends",
            items = groups[InsightGroup.FRIENDS].orEmpty(),
            emptyCaption = "Nothing from friends yet — hang tight.",
            now = now,
            senderByJid = senderByJid,
        )

        ContactGroupedSection(
            title = "Family",
            items = groups[InsightGroup.FAMILY].orEmpty(),
            emptyCaption = "No family updates yet.",
            now = now,
            senderByJid = senderByJid,
        )

        ContactGroupedSection(
            title = "General",
            items = groups[InsightGroup.GENERAL].orEmpty(),
            emptyCaption = "Other bits from conversations will land here.",
            now = now,
            senderByJid = senderByJid,
        )
    }
}

@Composable
private fun ReminderSection(
    title: String,
    items: List<ConversationInsightEntity>,
    emptyCaption: String,
    now: Long,
    senderByJid: Map<String, String>,
) {
    val sorted = remember(items) { items.sortedByDescending { displayMillis(it) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        TalomCard {
            if (sorted.isEmpty()) {
                Text(
                    text = emptyCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    sorted.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                        InsightRow(item = item, now = now, senderByJid = senderByJid)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactGroupedSection(
    title: String,
    items: List<ConversationInsightEntity>,
    emptyCaption: String,
    now: Long,
    senderByJid: Map<String, String>,
) {
    val byContact = remember(items, senderByJid) {
        items
            .groupBy { senderByJid[it.sourceJid] ?: it.sourceJid }
            .toList()
            .sortedBy { it.first.lowercase() }
            .map { (contact, list) ->
                contact to list.sortedByDescending { displayMillis(it) }
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        TalomCard {
            if (byContact.isEmpty()) {
                Text(
                    text = emptyCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    byContact.forEachIndexed { groupIdx, (contact, contactItems) ->
                        if (groupIdx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = contact,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            contactItems.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                }
                                InsightRow(
                                    item = item,
                                    now = now,
                                    senderByJid = senderByJid,
                                    showSender = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightRow(
    item: ConversationInsightEntity,
    now: Long,
    senderByJid: Map<String, String>,
    showSender: Boolean = true,
) {
    val sender = senderByJid[item.sourceJid]
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!item.details.isNullOrBlank()) {
            Text(
                text = item.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                if (showSender && sender != null) {
                    append("from ")
                    append(sender)
                    append(" · ")
                }
                append(relativeTime(displayMillis(item), now))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
