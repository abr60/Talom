package com.talom.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.talom.data.source.SourceStatusEntity
import com.talom.ui.components.*

@Composable
fun ClassroomSettingsScreen(
    classroomAccount: String?,
    classroomStatus: SourceStatusEntity?,
    classroomSyncing: Boolean,
    onConnectClassroom: () -> Unit,
    onSyncClassroom: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsSubpageScaffold(title = "Classroom", onBack = onBack) {
        SettingsGroup {
            ActionRow(
                label = "Google account",
                caption = classroomAccount ?: "Not connected.",
                actionLabel = if (classroomAccount == null) "Connect" else "Change",
                onAction = onConnectClassroom,
                showDivider = true,
            )
            ActionRow(
                label = "Classroom sync",
                caption = classroomStatus?.let { "${it.state}${it.detail?.let { d -> " — $d" } ?: ""}" } ?: "NOT_CONFIGURED — connect an account first.",
                actionLabel = if (classroomSyncing) "..." else "Sync",
                onAction = onSyncClassroom,
                actionEnabled = classroomAccount != null && !classroomSyncing,
            )
        }
    }
}
