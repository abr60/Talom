package com.talom.ui.academic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
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
import com.talom.data.source.PullLogEntity
import com.talom.data.source.SourceStatusEntity
import com.talom.ui.components.AppHeader
import com.talom.ui.components.PullStatusBanner
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.StatBar
import com.talom.ui.components.TalomCard
import java.time.Instant
import java.time.ZoneId

private val ASSESSMENT_TYPES = setOf(
    "EXAM", "CLASS_TEST", "VIVA", "PRESENTATION", "PRACTICAL", "INTERVIEW",
)

@Composable
fun AcademicScreen(
    academicItems: List<AcademicItemEntity>,
    courses: List<ClassroomCourseEntity>,
    coursework: List<ClassroomCourseworkEntity>,
    announcements: List<ClassroomAnnouncementEntity>,
    classroomStatus: SourceStatusEntity?,
    formatTime: (Long) -> String,
    onMarkDone: (String) -> Unit = {},
    latestPullLog: PullLogEntity? = null,
) {
    val now = remember { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(now, zone) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
    val todayStart = remember(today, zone) {
        today.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val todayEnd = remember(today, zone) {
        today.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
    }
    val weekEndMs = remember(today, zone) {
        today.plusDays(7).atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
    }

    val pending = remember(academicItems) { academicItems.filter { !it.done } }
    val submitted = remember(academicItems) {
        academicItems.filter { it.done }.sortedByDescending { it.submittedAtMillis ?: 0L }
    }
    val pendingAssignments = remember(pending) {
        pending.filter { it.type == "ASSIGNMENT" }
            .sortedWith(compareBy({ it.dueAtMillis ?: Long.MAX_VALUE }, { it.receivedAtMillis ?: it.storedAtMillis }))
    }
    val assessments = remember(pending) {
        pending.filter { it.type in ASSESSMENT_TYPES }
            .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE }
    }
    val deadlines = remember(pending) {
        pending.filter { it.type == "DEADLINE" }
            .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE }
    }
    val announcementItems = remember(pending) {
        pending.filter { it.type == "ANNOUNCEMENT" }
            .sortedByDescending { it.receivedAtMillis ?: it.storedAtMillis }
    }
    // Today: any class scheduled for calendar today (even if the hour already passed).
    val todayClasses = remember(pending, todayStart, todayEnd) {
        pending.filter {
            it.type == "CLASS_SCHEDULE" &&
                it.dueAtMillis != null &&
                it.dueAtMillis in todayStart..todayEnd
        }.sortedBy { it.dueAtMillis }
    }
    // Upcoming: tomorrow through +7 days (fixes "tomorrow not shown").
    val upcomingClasses = remember(pending, todayEnd, weekEndMs) {
        pending.filter {
            it.type == "CLASS_SCHEDULE" &&
                it.dueAtMillis != null &&
                it.dueAtMillis > todayEnd &&
                it.dueAtMillis <= weekEndMs
        }.sortedBy { it.dueAtMillis }
    }
    val scheduleCancellations = remember(pending, todayStart, weekEndMs, today, zone) {
        pending.filter { item ->
            item.type == "CANCELLATION" && (
                (item.dueAtMillis != null && item.dueAtMillis in todayStart..weekEndMs) ||
                    (item.dueAtMillis == null &&
                        Instant.ofEpochMilli(item.receivedAtMillis ?: item.storedAtMillis)
                            .atZone(zone).toLocalDate() >= today.minusDays(1))
                )
        }
    }
    val upcomingClassCount = todayClasses.size + upcomingClasses.size

    val overviewStats = remember(pendingAssignments, assessments, upcomingClassCount) {
        listOf(
            pendingAssignments.size.toString() to "Assignments",
            assessments.size.toString() to "Assessments",
            upcomingClassCount.toString() to "Classes (7d)",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        AppHeader(secondary = "${academicItems.size} items")
        Text(
            text = "Academic",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        PullStatusBanner(latestPullLog)

        StatBar(header = "Overview", stats = overviewStats)

        GoogleClassroomCard(
            courses = courses,
            coursework = coursework,
            announcements = announcements,
            classroomStatus = classroomStatus,
            formatTime = formatTime,
        )

        ClassScheduleSection(
            todayClasses = todayClasses,
            upcomingClasses = upcomingClasses,
            cancellations = scheduleCancellations,
            formatTime = formatTime,
        )

        AssignmentsSection(
            items = pendingAssignments,
            formatTime = formatTime,
            onMarkDone = onMarkDone,
        )

        ExamsSection(
            items = assessments,
            formatTime = formatTime,
        )

        SimpleItemsSection(
            title = "Deadlines",
            items = deadlines,
            formatTime = formatTime,
            emptyCaption = null,
        )

        SimpleItemsSection(
            title = "Announcements",
            items = announcementItems,
            formatTime = formatTime,
            emptyCaption = null,
        )

        SubmittedSection(
            items = submitted,
            formatTime = formatTime,
        )
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
private fun ClassScheduleSection(
    todayClasses: List<AcademicItemEntity>,
    upcomingClasses: List<AcademicItemEntity>,
    cancellations: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Class schedule")
        TalomCard {
            if (todayClasses.isEmpty() && upcomingClasses.isEmpty() && cancellations.isEmpty()) {
                Text(
                    "No class today — enjoy your day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScheduleDayBlock(
                        label = "Today",
                        classes = todayClasses,
                        emptyFallback = "No class today — enjoy your day",
                        formatTime = formatTime,
                    )
                    if (upcomingClasses.isNotEmpty()) {
                        ScheduleDayBlock(
                            label = "Upcoming",
                            classes = upcomingClasses,
                            emptyFallback = null,
                            formatTime = formatTime,
                        )
                    }
                    cancellations.forEach { item ->
                        Text(
                            text = "Cancelled: ${item.title}" + (item.subject?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        item.details?.let {
                            Text(
                                text = it,
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

@Composable
private fun ScheduleDayBlock(
    label: String,
    classes: List<AcademicItemEntity>,
    emptyFallback: String?,
    formatTime: (Long) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (classes.isEmpty()) {
            if (emptyFallback != null) {
                Text(
                    emptyFallback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            classes.groupBy { courseKey(it) }.forEach { (course, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = course,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    items.forEach { item ->
                        AcademicItemBlock(
                            item = item,
                            formatTime = formatTime,
                            showReceived = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleItemsSection(
    title: String,
    items: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
    emptyCaption: String?,
) {
    if (items.isEmpty() && emptyCaption == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        TalomCard {
            if (items.isEmpty()) {
                Text(
                    emptyCaption ?: "Nothing here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { item ->
                        AcademicItemBlock(
                            item = item,
                            formatTime = formatTime,
                            showReceived = true,
                            typeLabel = typeLabel(item.type),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentsSection(
    items: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
    onMarkDone: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Assignments")
        TalomCard {
            if (items.isEmpty()) {
                Text(
                    "No pending assignments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { item ->
                        var visible by remember(item.stableId) { mutableStateOf(true) }
                        AnimatedVisibility(
                            visible = visible,
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = {
                                        visible = false
                                        onMarkDone(item.stableId)
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    AcademicItemBlock(
                                        item = item,
                                        formatTime = formatTime,
                                        showReceived = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamsSection(
    items: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Exams & assessments")
        TalomCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    AcademicItemBlock(
                        item = item,
                        formatTime = formatTime,
                        showReceived = true,
                        typeLabel = typeLabel(item.type),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmittedSection(
    items: List<AcademicItemEntity>,
    formatTime: (Long) -> String,
) {
    if (items.isEmpty()) return
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
                text = if (expanded) "▾  Submitted" else "▸  Submitted",
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
                    text = items.size.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        if (expanded) {
            TalomCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { item ->
                        AcademicItemBlock(
                            item = item,
                            formatTime = formatTime,
                            showReceived = true,
                            subtitleOverride = item.submittedAtMillis?.let { "Submitted ${formatTime(it)}" },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicItemBlock(
    item: AcademicItemEntity,
    formatTime: (Long) -> String,
    showReceived: Boolean,
    typeLabel: String? = null,
    subtitleOverride: String? = null,
) {
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
        val meta = subtitleOverride ?: buildString {
            typeLabel?.let {
                append(it)
                append(" · ")
            }
            item.dueAtMillis?.let {
                append(formatTime(it))
                if (showReceived) append(" · ")
            }
            if (showReceived) {
                val received = item.receivedAtMillis ?: item.storedAtMillis
                append("received ${formatTime(received)}")
            }
        }
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Prefer "CODE > Title" when subject looks like a code; otherwise subject or title. */
private fun courseKey(item: AcademicItemEntity): String {
    val subject = item.subject?.trim().orEmpty()
    return when {
        subject.isBlank() -> item.title
        subject.contains('>') -> subject
        else -> "$subject > ${item.title}"
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
