package com.talom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talom.BuildConfig
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SettingsSubpageScaffold

@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "About", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "TALOM",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Your academic companion. Every insight accounted for.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Talom v${BuildConfig.VERSION_NAME} \u2022 build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.7f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("WHAT IS TALOM")
                Text(
                    "Talom is a local-first academic assistant for students who juggle WhatsApp groups, " +
                        "Google Classroom, and a lot of messages. It reads your whitelisted conversations " +
                        "and your Classroom courses on-device, then extracts structured \u201cacademic items\u201d " +
                        "and \u201cconversation insights\u201d through configurable AI \u2014 so deadlines, assignments, " +
                        "and the important bits never get lost in the scroll.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("FEATURES")
                Bullet("WhatsApp + Classroom, unified \u2014 whitelisting, daily auto-pulls at your chosen time (default 22:30), and read-only Classroom sync.")
                Bullet("Configurable AI \u2014 Gemini in the cloud, OpenAI-compatible endpoints (OpenRouter, Groq, etc.), or a local Ollama server. You choose.")
                Bullet("Keys in the Keystore \u2014 API keys are encrypted with Android Keystore AES/GCM; nothing sensitive leaves the device.")
                Bullet("Local-first, no telemetry \u2014 messages are chunked, sent only to your chosen provider for extraction, and insights are stored locally in Room.")
                Bullet(" Quiet notifications for completed pulls and a calm Academic/Personal tab layout.")
                Bullet("Featherweight despite big dependencies \u2014 shrunk and compressed for fast installs, and now Android 7+.")
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("PRINCIPLES")
                Bullet("Local-first. Your chats stay yours; no analytics, no tracking, no raw-text storage.")
                Bullet("Your keys, your provider. Swap Gemini \u2194 Ollama \u2194 OpenRouter anytime without reimporting.")
                Bullet("Whitelist, not dragnet. Only the conversations you pick are ever read.")
                Bullet("Calm over clever. No ads, no paywalls, no cloud account required.")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Psst \u2014 try tapping the TALOM title 4 times", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "An advanced theme engine is hidden behind a consecutive 4-tap on the TALOM header in Settings. Once unlocked, Material You dynamic colors (derived from your wallpaper) light up the whole app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Made with \u2665 for students who keep their studies in order.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.6f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("\u2022 $text", style = MaterialTheme.typography.bodyMedium)
}
