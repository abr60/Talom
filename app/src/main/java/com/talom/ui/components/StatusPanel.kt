package com.talom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talom.core.ai.CloudErrorMapper

/**
 * Small monochrome status panel. Renders one of:
 * - Idle: nothing
 * - Ok: ✓ headline + optional secondary line (e.g. "Using: model-x")
 * - Failed: ✗ headline + suggestion line + optional technical detail
 * - In-progress: ◐ headline (e.g. "Testing..." / "Finding best model...")
 */
@Composable
fun StatusPanel(
    state: StatusPanelState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is StatusPanelState.Idle -> Unit
        is StatusPanelState.Ok -> {
            PanelRow(
                symbol = "✓",
                headline = state.headline,
                secondary = state.secondary,
                emphasis = false,
                modifier = modifier,
            )
        }
        is StatusPanelState.Failed -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusSymbol("✗")
                    Text(
                        text = state.error.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!state.error.suggestion.isNullOrBlank()) {
                    Text(
                        text = state.error.suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is StatusPanelState.InProgress -> {
            PanelRow(
                symbol = "◐",
                headline = state.headline,
                secondary = null,
                emphasis = false,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PanelRow(
    symbol: String,
    headline: String,
    secondary: String?,
    emphasis: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusSymbol(symbol)
        Column {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusSymbol(symbol: String) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

sealed interface StatusPanelState {
    data object Idle : StatusPanelState
    data class Ok(
        val headline: String,
        val secondary: String? = null,
    ) : StatusPanelState
    data class Failed(
        val error: CloudErrorMapper.CloudError,
    ) : StatusPanelState
    data class InProgress(
        val headline: String,
    ) : StatusPanelState
}
