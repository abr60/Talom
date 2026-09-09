package com.talom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talom.core.ai.AiMode
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.ui.components.AppHeader
import com.talom.ui.components.NavRow
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SegmentedControl
import com.talom.ui.components.SettingsGroup
import com.talom.ui.components.SettingsRow
import com.talom.ui.theme.TalomThemeMode

@Composable
fun SettingsHubScreen(
    themeMode: TalomThemeMode,
    onThemeChange: (TalomThemeMode) -> Unit,
    aiMode: AiMode,
    providerId: String? = null,
    whitelist: List<WhatsAppWhitelist>,
    classroomAccount: String?,
    showNotificationPrompt: Boolean,
    onEnableNotifications: () -> Unit,
    onNavigateCustomize: () -> Unit,
    onNavigateAi: () -> Unit,
    onNavigateWhatsApp: () -> Unit,
    onNavigateClassroom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        AppHeader(secondary = "v${com.talom.BuildConfig.VERSION_NAME}")
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Appearance")
            SegmentedControl(
                options = listOf("System", "Light", "Dark"),
                selectedIndex = when (themeMode) {
                    TalomThemeMode.SYSTEM -> 0
                    TalomThemeMode.LIGHT -> 1
                    else -> 2
                },
                onSelect = {
                    onThemeChange(
                        when (it) {
                            1 -> TalomThemeMode.LIGHT
                            2 -> TalomThemeMode.DARK
                            else -> TalomThemeMode.SYSTEM
                        },
                    )
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Customize")
            SettingsGroup {
                NavRow(
                    label = "Customize",
                    caption = "Font and identity",
                    onClick = onNavigateCustomize,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("AI processing")
            SettingsGroup {
                NavRow(label = "AI processing", caption = StatusText.hubAiCaption(aiMode, providerId), onClick = onNavigateAi)
            }
        }
        if (showNotificationPrompt) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Notifications")
                SettingsGroup {
                    NavRow(label = "Talom notifications", caption = "Quiet completion alerts for pulls.", onClick = onEnableNotifications)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("WhatsApp")
            SettingsGroup {
                NavRow(label = "WhatsApp", caption = "${whitelist.size} conversations whitelisted", onClick = onNavigateWhatsApp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Google Classroom")
            SettingsGroup {
                NavRow(label = "Classroom", caption = classroomAccount ?: "Not connected — tap to connect", onClick = onNavigateClassroom)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("About")
            SettingsGroup {
                SettingsRow(label = "Version", value = com.talom.BuildConfig.VERSION_NAME, showDivider = true)
                SettingsRow(label = "Privacy", value = "Local-first. No raw text stored.")
            }
        }
    }
}
