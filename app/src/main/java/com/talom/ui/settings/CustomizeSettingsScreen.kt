package com.talom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talom.core.TalomPreferences
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SettingsGroup
import com.talom.ui.components.ToggleRow
import com.talom.ui.components.SettingsSubpageScaffold

@Composable
fun CustomizeSettingsScreen(
    fontPreference: String,
    onFontPreferenceChange: (String) -> Unit,
    userIdentity: String,
    onUserIdentityChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsSubpageScaffold(title = "Customize", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Font")
            SettingsGroup {
                ToggleRow(
                    label = "Follow System",
                    caption = if (fontPreference == TalomPreferences.FONT_SYSTEM_DEFAULT)
                        "Uses the system font" else "Uses the app's enforced typography",
                    checked = fontPreference == TalomPreferences.FONT_SYSTEM_DEFAULT,
                    onCheckedChange = {
                        onFontPreferenceChange(
                            if (it) TalomPreferences.FONT_SYSTEM_DEFAULT
                            else TalomPreferences.FONT_APP_DEFAULT,
                        )
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Identity")
            SettingsGroup {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        "User identity",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Used for export path templates (optional).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = userIdentity,
                        onValueChange = onUserIdentityChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true,
                        placeholder = { Text("e.g. abr") },
                    )
                }
            }
        }
    }
}
