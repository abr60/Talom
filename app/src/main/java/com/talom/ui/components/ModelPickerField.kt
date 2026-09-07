package com.talom.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Free-text model input with a dropdown of discovered models. Used by the
 * OpenAI-compatible provider so the user can either pick from the provider's
 * model list or type any model name (including ones the /models endpoint didn't
 * return, e.g. older releases or self-hosted).
 */
@Composable
fun ModelPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    models: List<String>,
    loading: Boolean,
    error: String?,
    emptyHint: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = fieldColors,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Pick from list",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(enabled = models.isNotEmpty()) { expanded = true }
                            .padding(8.dp),
                    )
                },
            )
            DropdownMenu(
                expanded = expanded && models.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                models.take(60).forEach { model ->
                    val selected = model == value
                    DropdownMenuItem(
                        text = {
                            Text(
                                model,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onValueChange(model)
                            expanded = false
                        },
                    )
                }
                if (models.size > 60) {
                    Text(
                        "${models.size - 60} more — type to search",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
        val caption = when {
            loading -> "Loading models…"
            error != null -> error
            models.isNotEmpty() -> "${models.size} models available"
            value.isNotBlank() -> "Custom model — endpoint may not support /models"
            else -> emptyHint
        }
        if (caption.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
