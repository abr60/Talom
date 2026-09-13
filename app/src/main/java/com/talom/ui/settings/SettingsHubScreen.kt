package com.talom.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talom.core.ai.AiMode
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.ui.components.AppHeader
import com.talom.ui.components.NavRow
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SettingsGroup
import com.talom.ui.components.ToggleRow

@Composable
fun SettingsHubScreen(
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    isUnlocked: Boolean,
    onUnlockToggle: (Boolean) -> Unit,
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
    onNavigateUpdate: () -> Unit,
    onNavigateAbout: () -> Unit,
) {
    val ctx = LocalContext.current
    var unlocked by remember { mutableStateOf(isUnlocked) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }

    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(
            modifier = Modifier.clickable {
                val now = System.currentTimeMillis()
                tapCount = if (now - lastTapMs > 700) 1 else tapCount + 1
                lastTapMs = now
                if (tapCount >= 4) {
                    val next = !unlocked
                    unlocked = next
                    onUnlockToggle(next)
                    Toast.makeText(
                        ctx,
                        if (next) "Advanced theme engine unlocked!" else "Advanced theme engine hidden",
                        Toast.LENGTH_SHORT,
                    ).show()
                    tapCount = 0
                    lastTapMs = 0
                }
            },
        ) {
            AppHeader(secondary = "v${com.talom.BuildConfig.VERSION_NAME}")
        }
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Appearance")
            SettingsGroup {
                ToggleRow(
                    label = "Follow system",
                    caption = if (followSystemTheme) "Uses your system theme" else "Uses the opposite of your system theme",
                    checked = followSystemTheme,
                    onCheckedChange = onFollowSystemThemeChange,
                )
            }
        }
        if (unlocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Advanced")
                SettingsGroup {
                    ToggleRow(
                        label = "Dynamic colors (Material You)",
                        caption = "Derives accents from your wallpaper",
                        checked = dynamicColorsEnabled,
                        onCheckedChange = onDynamicColorsChange,
                    )
                }
            }
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
                NavRow(
                    label = "App Updates",
                    caption = "v${com.talom.BuildConfig.VERSION_NAME} • Check for updates",
                    onClick = onNavigateUpdate,
                    showDivider = true,
                )
                NavRow(
                    label = "About",
                    caption = "Our story, motto & version",
                    onClick = onNavigateAbout,
                )
            }
        }
    }
}
