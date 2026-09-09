package com.talom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.talom.core.ai.AiMode
import com.talom.core.ai.ConnectionStatus
import com.talom.ui.components.ActionRow
import com.talom.ui.components.ModelPickerField
import com.talom.ui.components.SegmentedControl
import com.talom.ui.components.SettingsGroup
import com.talom.ui.components.SettingsSubpageScaffold
import com.talom.ui.components.StatusPanel
import com.talom.ui.components.StatusPanelState
import com.talom.ui.components.ToggleRow

@Composable
fun AiSettingsScreen(
    aiMode: AiMode,
    onAiModeChange: (AiMode) -> Unit,
    aiKey: String,
    onAiKeyChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    ollamaEndpoint: String,
    onOllamaEndpointChange: (String) -> Unit,
    openAiEndpoint: String,
    onOpenAiEndpointChange: (String) -> Unit,
    useOpenAiCompatible: Boolean,
    onUseOpenAiCompatibleChange: (Boolean) -> Unit,
    cloudConsent: Boolean,
    onCloudConsentChange: (Boolean) -> Unit,
    localConsent: Boolean,
    onLocalConsentChange: (Boolean) -> Unit,
    aiSettingsState: String,
    localTestState: String,
    availableModels: List<String>,
    modelsLoading: Boolean,
    modelsError: String?,
    hasSavedKey: Boolean,
    availableLocalModels: List<String>,
    localModelsLoading: Boolean,
    localModelsError: String?,
    messageWindowDays: Int,
    onMessageWindowChange: (Int) -> Unit,
    onSaveAi: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshModels: () -> Unit,
    connectionStatus: ConnectionStatus?,
    modelRecommendationInProgress: Boolean,
    onTestOllama: () -> Unit,
    onBack: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
    SettingsSubpageScaffold(title = "AI processing", onBack = onBack) {
        SettingsGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                SegmentedControl(
                    options = listOf("Off", "Cloud", "Local"),
                    selectedIndex = when (aiMode) {
                        AiMode.DISABLED -> 0; AiMode.CLOUD -> 1; AiMode.LOCAL -> 2
                    },
                    onSelect = {
                        onAiModeChange(when (it) { 1 -> AiMode.CLOUD; 2 -> AiMode.LOCAL; else -> AiMode.DISABLED })
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            when (aiMode) {
                AiMode.CLOUD -> CloudSection(
                    aiKey = aiKey,
                    onAiKeyChange = onAiKeyChange,
                    aiModel = aiModel,
                    onAiModelChange = onAiModelChange,
                    openAiEndpoint = openAiEndpoint,
                    onOpenAiEndpointChange = onOpenAiEndpointChange,
                    useOpenAiCompatible = useOpenAiCompatible,
                    onUseOpenAiCompatibleChange = onUseOpenAiCompatibleChange,
                    cloudConsent = cloudConsent,
                    onCloudConsentChange = onCloudConsentChange,
                    availableModels = availableModels,
                    modelsLoading = modelsLoading,
                    modelsError = modelsError,
                    hasSavedKey = hasSavedKey,
                    fieldColors = fieldColors,
                    onTestConnection = onTestConnection,
                    onRefreshModels = onRefreshModels,
                    connectionStatus = connectionStatus,
                    modelRecommendationInProgress = modelRecommendationInProgress,
                )
                AiMode.LOCAL -> {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        OutlinedTextField(value = ollamaEndpoint, onValueChange = onOllamaEndpointChange, label = { Text("Ollama endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                        AiModelDropdown(value = aiModel, onValueChange = onAiModelChange, models = availableLocalModels, loading = localModelsLoading, error = localModelsError, label = "Local model", fieldColors = fieldColors, emptyHint = "Enter endpoint to list models.")
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
                    ToggleRow(label = "Allow local network", caption = "Message text goes over local network to Ollama.", checked = localConsent, onCheckedChange = onLocalConsentChange, showDivider = true)
                    ActionRow(label = "Test Ollama connection", caption = localTestState.takeIf { it.isNotBlank() }, actionLabel = "Test", onAction = onTestOllama, showDivider = true)
                }
                AiMode.DISABLED -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text("AI processing is off. No message text is sent anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
                }
            }
            ActionRow(label = "Save AI settings", caption = aiSettingsState, actionLabel = "Save", onAction = onSaveAi)
        }
        // Message window — rolling N days
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.talom.ui.components.SectionHeader("Message window")
            com.talom.ui.components.SettingsGroup {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    androidx.compose.material3.Text("Only messages from the last N days are sent to AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    com.talom.ui.components.SegmentedControl(
                        options = listOf("7 days", "14 days", "30 days", "90 days"),
                        selectedIndex = when (messageWindowDays) { 7 -> 0; 14 -> 1; 90 -> 3; else -> 2 },
                        onSelect = { onMessageWindowChange(when (it) { 0 -> 7; 1 -> 14; 3 -> 90; else -> 30 }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudSection(
    aiKey: String,
    onAiKeyChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    openAiEndpoint: String,
    onOpenAiEndpointChange: (String) -> Unit,
    useOpenAiCompatible: Boolean,
    onUseOpenAiCompatibleChange: (Boolean) -> Unit,
    cloudConsent: Boolean,
    onCloudConsentChange: (Boolean) -> Unit,
    availableModels: List<String>,
    modelsLoading: Boolean,
    modelsError: String?,
    hasSavedKey: Boolean,
    fieldColors: TextFieldColors,
    onTestConnection: () -> Unit,
    onRefreshModels: () -> Unit,
    connectionStatus: ConnectionStatus?,
    modelRecommendationInProgress: Boolean,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        ToggleRow(
            label = "Use OpenAI-compatible API",
            caption = if (useOpenAiCompatible) {
                "Any service speaking the OpenAI Chat Completions format: OpenRouter, Groq, Mistral, Together, vLLM, llama.cpp, LM Studio."
            } else {
                "Off: use Google Gemini. On: use any OpenAI-compatible provider (you'll need an endpoint URL + key)."
            },
            checked = useOpenAiCompatible,
            onCheckedChange = onUseOpenAiCompatibleChange,
            showDivider = false,
        )
        Spacer(Modifier.height(8.dp))
        if (useOpenAiCompatible) {
            OutlinedTextField(
                value = openAiEndpoint,
                onValueChange = onOpenAiEndpointChange,
                label = { Text("Endpoint URL") },
                placeholder = { Text("https://openrouter.ai/api/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiKey,
                onValueChange = onAiKeyChange,
                label = { Text("API key") },
                placeholder = if (hasSavedKey && aiKey.isEmpty()) { { Text("••••") } } else null,
                supportingText = if (hasSavedKey) {
                    { Text(if (aiKey.isEmpty()) "Key saved. Type to replace." else "Typing will replace the saved key.") }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(8.dp))
            ModelPickerField(
                value = aiModel,
                onValueChange = onAiModelChange,
                models = availableModels,
                loading = modelsLoading,
                error = modelsError,
                emptyHint = "Enter endpoint and key, then tap Refresh models.",
            )
        } else {
            OutlinedTextField(
                value = aiKey, onValueChange = onAiKeyChange,
                label = { Text("Gemini API key") },
                placeholder = if (hasSavedKey && aiKey.isEmpty()) { { Text("••••") } } else null,
                supportingText = if (hasSavedKey) { { Text(if (aiKey.isEmpty()) "Key saved. Type to replace." else "Typing will replace the saved key.") } } else null,
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            AiModelDropdown(
                value = aiModel,
                onValueChange = onAiModelChange,
                models = availableModels,
                loading = modelsLoading,
                error = modelsError,
                label = "Gemini model",
                fieldColors = fieldColors,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
    ToggleRow(
        label = "Allow cloud processing",
        caption = buildString {
            append("Message text will leave this phone.")
            if (useOpenAiCompatible && openAiEndpoint.isNotBlank()) {
                append(" Sent to: ")
                append(openAiEndpoint)
            } else if (!useOpenAiCompatible) {
                append(" Sent to Google Gemini.")
            }
        },
        checked = cloudConsent,
        onCheckedChange = onCloudConsentChange,
        showDivider = true,
    )
    if (useOpenAiCompatible) {
        ActionRow(
            label = "Test connection",
            caption = "Saves no settings. Checks endpoint + key.",
            actionLabel = "Test",
            onAction = onTestConnection,
            showDivider = true,
        )
        ActionRow(
            label = "Refresh models",
            caption = "Re-fetches the model list from the endpoint.",
            actionLabel = "Refresh",
            onAction = onRefreshModels,
            showDivider = true,
        )
        if (modelRecommendationInProgress || connectionStatus != null) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                val state = when {
                    modelRecommendationInProgress -> StatusPanelState.InProgress("Finding best model…")
                    connectionStatus is ConnectionStatus.Ok -> StatusPanelState.Ok(
                        headline = "Connected.",
                        secondary = buildString {
                            connectionStatus.modelCount?.let { append("$it models available. ") }
                            append("Using: ${connectionStatus.selectedModel ?: "—"}")
                        },
                    )
                    connectionStatus is ConnectionStatus.Failed -> StatusPanelState.Failed(connectionStatus.error)
                    else -> StatusPanelState.Idle
                }
                StatusPanel(state = state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiModelDropdown(
    value: String, onValueChange: (String) -> Unit, models: List<String>, loading: Boolean, error: String?, label: String, fieldColors: TextFieldColors, emptyHint: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(expanded = expanded && models.isNotEmpty(), onExpandedChange = { if (models.isNotEmpty()) expanded = it }) {
            OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && models.isNotEmpty()) },
                modifier = Modifier.menuAnchor(type = androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), colors = fieldColors)
            ExposedDropdownMenu(expanded = expanded && models.isNotEmpty(), onDismissRequest = { expanded = false }) {
                models.forEach { model ->
                    val selected = model == value
                    DropdownMenuItem(text = { Text(model, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { onValueChange(model); expanded = false }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
        val caption = when { loading -> "Loading models…"; error != null -> error; models.isNotEmpty() -> "${models.size} models available."; else -> emptyHint ?: "Enter a key above to list its models." }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
