package com.talom.ui.academic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talom.data.academic.AcademicItemEntity
import com.talom.data.classroom.ClassroomAnnouncementEntity
import com.talom.data.classroom.ClassroomCourseEntity
import com.talom.data.classroom.ClassroomCourseworkEntity
import com.talom.data.source.SourceStatusEntity
import com.talom.ui.components.ActionRow
import com.talom.ui.components.AppHeader
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.StatBar
import com.talom.ui.components.TalomCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// 36 hours covers the rest of today + all of tomorrow, so the user sees
// both "what's left today" and "what's tomorrow morning" in one card.
private const val NEXT_36H_MS = 36L * 60 * 60 * 1000
private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

private val ASSESSMENT_TYPES = setOf(
    "EXAM", "CLASS_TEST", "VIVA", "PRACTICAL", "INTERVIEW", "PRESENTATION",
)

@Composable
fun AcademicScreen(
    academicItems: List<AcademicItemEntity>,
    courses: List<ClassroomCourseEntity>,
    coursework: List<ClassroomCourseworkEntity>,
    announcements: List<ClassroomAnnouncementEntity>,
    classroomStatus: SourceStatusEntity?,
    formatTime: (Long) -> String,
    onClearAll: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val upcoming = remember(academicItems, now) {
        academicItems
            .filter { it.dueAtMillis != null && it.dueAtMillis >= now }
            .sortedBy { it.dueAtMillis }
    }
    val next36h = remember(upcoming, now) {
        upcoming.filter { it.dueAtMillis!! - now <= NEXT_36H_MS }
    }
    val thisWeek = remember(upcoming, now) {
        upcoming.filter {
            val delta = it.dueAtMillis!! - now
            delta in (NEXT_36H_MS + 1)..WEEK_MS
        }
    }
    val byType = remember(academicItems, now) {
        // Already-past items are still in this view (for "what I missed"
        // context) but only within the past day. Pruning handles older.
        academicItems
            .filter { it.dueAtMillis == null || it.dueAtMillis >= now - 24L * 60 * 60 * 1000 }
            .groupBy { it.type }
    }

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        AppHeader(secondary = "${academicItems.size} items")
        Text(
            text = "Academic",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatBar(
            header = "Overview",
            stats = listOf(
                next36h.size.toString() to "Next 36h",
                thisWeek.size.toString() to "This week",
                academicItems.size.toString() to "All",
            ),
        )

        GoogleClassroomCard(
            courses = courses,
            coursework = coursework,
            announcements = announcements,
            classroomStatus = classroomStatus,
            formatTime = formatTime,
        )

        WindowCard(
            title = "Next 36 hours",
            caption = if (next36h.isEmpty()) "Nothing scheduled in the next day and a half."
                else "${next36h.size} ${if (next36h.size == 1) "item" else "items"} happening soon.",
            items = next36h,
            formatTime = formatTime,
        )

        WindowCard(
            title = "This week",
            caption = if (thisWeek.isEmpty()) "Nothing else on the radar this week."
                else "${thisWeek.size} more in the next ${(WEEK_MS / (24 * 60 * 60 * 1000))} days.",
            items = thisWeek,
            formatTime = formatTime,
        )

        SectionHeader("By type")
        CollapsibleGroup(
            title = "Assessments",
            count = byType.filterKeys { it in ASSESSMENT_TYPES }.values.sumOf { it.size },
            items = byType.filterKeys { it in ASSESSMENT_TYPES }
                .flatMap { it.value }
                .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE },
            formatTime = formatTime,
        )
        CollapsibleGroup(
            title = "Assignments",
            count = byType["ASSIGNMENT"]?.size ?: 0,
            items = byType["ASSIGNMENT"].orEmpty()
                .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE },
            formatTime = formatTime,
        )
        CollapsibleGroup(
            title = "Class schedule",
            count = byType["CLASS_SCHEDULE"]?.size ?: 0,
            items = byType["CLASS_SCHEDULE"].orEmpty()
                .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE },
            formatTime = formatTime,
        )
        CollapsibleGroup(
            title = "Deadlines",
            count = byType["DEADLINE"]?.size ?: 0,
            items = byType["DEADLINE"].orEmpty()
                .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE },
            formatTime = formatTime,
        )
        CollapsibleGroup(
            title = "Announcements",
            count = byType["ANNOUNCEMENT"]?.size ?: 0,
            items = byType["ANNOUNCEMENT"].orEmpty()
                .sortedBy { it.storedAtMillis },
            formatTime = formatTime,
        )
        CollapsibleGroup(
            title = "Cancellations",
            count = byType["CANCELLATION"]?.size ?: 0,
            items = byType["CANCELLATION"].orEmpty()
                .sortedBy { it.storedAtMillis },
            formatTime = formatTime,
        )

        if (academicItems.isNotEmpty()) {
            ActionRow(
                label = "Clear all academic items",
                caption = "Removes every cached item. Next pull will refill.",
                actionLabel = "Clear",
                onAction = onClearAll,
            )
        }
    }
}

@Composable
private fun GoogleClassroomCard(
    courses: List<ClassroomCourseEntity>,
    coursework: List<ClassroomCourseworkEntity>,
    announcements: List<ClassroomAnnouncementEntity>,
    classroomStatus: SourceStatusEntity?,
    formatTime: (Long) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Google Classroom")
        TalomCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (courses.isEmpty())
                        "No Classroom data yet. Connect a Google account in Settings → Google Classroom, then Sync."
                    else
                        "Imported ${courses.size} courses, ${coursework.size} assignments, ${announcements.size} announcements.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Classroom sync: ${classroomStatus?.state ?: "NOT_CONFIGURED"}" +
                        (classroomStatus?.detail?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                coursework.take(10).forEach { a ->
                    Text(
                        a.title + (a.dueAtMillis?.let { " • due ${formatTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                announcements.take(5).forEach { an ->
                    Text(
                        "Announcement: ${an.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowCard(
    title: String,
    caption: String?,
    items: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        TalomCard {
            if (items.isEmpty()) {
                Text(
                    caption ?: "Nothing here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.title + (item.subject?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            item.details?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item.dueAtMillis?.let {
                                Text(
                                    text = "${formatTime(it)} · ${typeLabel(item.type)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun CollapsibleGroup(
    title: String,
    count: Int,
    items: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
) {
    if (count == 0) return
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "▾  $title" else "▸  $title",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        if (expanded) {
            TalomCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.take(20).forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.title + (item.subject?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            item.details?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item.dueAtMillis?.let {
                                Text(
                                    text = formatTime(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "CLASS_SCHEDULE" -> "Class"
    "ASSIGNMENT" -> "Assignment"
    "EXAM" -> "Exam"
    "DEADLINE" -> "Deadline"
    "ANNOUNCEMENT" -> "Announcement"
    "CANCELLATION" -> "Cancellation"
    "CLASS_TEST" -> "Class test"
    "PRESENTATION" -> "Presentation"
    "VIVA" -> "Viva"
    "INTERVIEW" -> "Interview"
    "PRACTICAL" -> "Practical"
    else -> type.lowercase().replaceFirstChar { it.uppercase() }
}
