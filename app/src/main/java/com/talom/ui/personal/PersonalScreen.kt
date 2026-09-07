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
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.source.whatsapp.WhatsAppConversation
import com.talom.ui.components.AppHeader
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.StatBar
import com.talom.ui.components.TalomCard
import java.util.concurrent.TimeUnit

private enum class InsightGroup { REMINDERS, PEOPLE, GENERAL }

private fun partitionInsights(
    items: List<ConversationInsightEntity>,
): Map<InsightGroup, List<ConversationInsightEntity>> {
    val reminders = mutableListOf<ConversationInsightEntity>()
    val people = mutableListOf<ConversationInsightEntity>()
    val general = mutableListOf<ConversationInsightEntity>()
    items.forEach { e ->
        when (runCatching { InsightType.valueOf(e.type) }.getOrNull()) {
            InsightType.PERSONAL_REMINDER, InsightType.PLAN -> reminders += e
            InsightType.FAMILY, InsightType.FRIEND -> people += e
            InsightType.GENERAL, null -> general += e
        }
    }
    return mapOf(
        InsightGroup.REMINDERS to reminders,
        InsightGroup.PEOPLE to people,
        InsightGroup.GENERAL to general,
    )
}

private fun timeBucket(storedAtMillis: Long, now: Long): String = when {
    now - storedAtMillis <= TimeUnit.DAYS.toMillis(1) -> "Today"
    now - storedAtMillis <= TimeUnit.DAYS.toMillis(7) -> "This week"
    else -> "Older"
}

private fun relativeTime(storedAtMillis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(
        storedAtMillis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

@Composable
fun PersonalScreen(
    insights: List<ConversationInsightEntity>,
    whitelist: List<WhatsAppWhitelist>,
    directory: List<WhatsAppConversation>,
) {
    val now = remember(insights) { System.currentTimeMillis() }
    val senderByJid = remember(whitelist, directory) {
        buildMap {
            whitelist.forEach { w -> put(w.jid, w.label ?: w.jid) }
            directory.forEach { c -> putIfAbsent(c.jid, c.label) }
        }
    }
    val groups = remember(insights) { partitionInsights(insights) }
    val totalInsights = insights.size
    val todayCount = insights.count { timeBucket(it.storedAtMillis, now) == "Today" }
    val weekCount = insights.count { timeBucket(it.storedAtMillis, now) == "This week" }
    val olderCount = totalInsights - todayCount - weekCount

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

        // Overview: time-bucket counts (or single empty-state line)
        if (totalInsights == 0) {
            TalomCard {
                Text(
                    "Pull with AI enabled to see what was found in your conversations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
        } else {
            StatBar(
                header = "Overview",
                stats = listOf(
                    todayCount.toString() to "Today",
                    weekCount.toString() to "This week",
                    olderCount.toString() to "Older",
                ),
            )
        }

        // Reminders & plans — hidden when empty
        val reminders = groups[InsightGroup.REMINDERS].orEmpty()
        if (reminders.isNotEmpty()) {
            InsightSection(
                title = "Reminders & plans",
                items = reminders,
                now = now,
                senderByJid = senderByJid,
            )
        }

        // Family & friends — hidden when empty
        val people = groups[InsightGroup.PEOPLE].orEmpty()
        if (people.isNotEmpty()) {
            InsightSection(
                title = "Family & friends",
                items = people,
                now = now,
                senderByJid = senderByJid,
            )
        }

        // General — hidden when empty
        val general = groups[InsightGroup.GENERAL].orEmpty()
        if (general.isNotEmpty()) {
            InsightSection(
                title = "General",
                items = general,
                now = now,
                senderByJid = senderByJid,
            )
        }
    }
}

@Composable
private fun InsightSection(
    title: String,
    items: List<ConversationInsightEntity>,
    now: Long,
    senderByJid: Map<String, String>,
) {
    val sorted = remember(items) { items.sortedByDescending { it.storedAtMillis } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        TalomCard {
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

@Composable
private fun InsightRow(
    item: ConversationInsightEntity,
    now: Long,
    senderByJid: Map<String, String>,
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
                if (sender != null) {
                    append("from ")
                    append(sender)
                    append(" · ")
                }
                append(relativeTime(item.storedAtMillis, now))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
