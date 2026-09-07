package com.talom.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.talom.data.whatsapp.RelationCategory
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.source.whatsapp.WhatsAppConversation
import com.talom.ui.components.SectionHeader
import com.talom.ui.components.SettingsGroup
import com.talom.ui.components.SettingsSubpageScaffold
import com.talom.ui.components.SpinningSyncIcon
import com.talom.ui.components.TalomCard

@Composable
fun ManageConversationsScreen(
    whitelist: List<WhatsAppWhitelist>,
    directory: List<WhatsAppConversation>,
    directoryQuery: String,
    onDirectoryQueryChange: (String) -> Unit,
    directoryState: String,
    directoryLoading: Boolean,
    onLoadDirectory: () -> Unit,
    onUpdateCategory: (WhatsAppWhitelist, RelationCategory) -> Unit,
    onRemove: (WhatsAppWhitelist) -> Unit,
    onWhitelistWithCategory: (WhatsAppConversation, RelationCategory) -> Unit,
    onBack: () -> Unit,
) {
    var categoryFilter by remember { mutableStateOf<RelationCategory?>(null) }
    var addExpanded by remember { mutableStateOf(false) }
    SettingsSubpageScaffold(title = "Manage conversations", onBack = onBack) {
        // Filter chips: 4 categories, no "All" — tap again to clear.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RelationCategory.entries.forEach { cat ->
                val selected = categoryFilter == cat
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        categoryFilter = if (selected) null else cat
                    },
                ) {
                    Text(
                        text = cat.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Whitelisted rows (JID hidden per design — only the name + category + trash are shown)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Whitelisted")
            val filtered = if (categoryFilter == null) whitelist
            else whitelist.filter { it.category == categoryFilter }
            if (whitelist.isEmpty()) {
                TalomCard {
                    Text(
                        "No conversations yet. Add one from your directory.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (filtered.isEmpty()) {
                TalomCard {
                    Text(
                        "No conversations in this category.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SettingsGroup {
                    filtered.forEachIndexed { idx, item ->
                        ManageRow(
                            item = item,
                            showDivider = idx < filtered.lastIndex,
                            onUpdateCategory = { onUpdateCategory(item, it) },
                            onRemove = { onRemove(item) },
                        )
                    }
                }
            }
        }

        // Add section: Load button + collapsed directory listing.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Add contacts & groups")
            SettingsGroup {
                Column {
                    // Load row: always-visible, with spinning icon while loading.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !directoryLoading) { onLoadDirectory() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpinningSyncIcon(
                            isSpinning = directoryLoading,
                            contentDescription = "Load WhatsApp contacts and groups",
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = if (directoryLoading) "Loading…"
                                    else if (directoryState.isNotBlank()) directoryState
                                    else "Load contacts & groups",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    // Collapsible directory row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addExpanded = !addExpanded }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Pick from directory",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (addExpanded) "▴" else "▾",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (addExpanded) {
                        // Bounded container so the search field stays pinned at the
                        // top of the section and the results fill the remaining space.
                        // heightIn(min) keeps the section visible even when the activity
                        // shrinks (keyboard open); heightIn(max) prevents it from
                        // dominating the screen.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp, max = 620.dp)
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = directoryQuery,
                                onValueChange = onDirectoryQueryChange,
                                label = { Text("Search to add") },
                                placeholder = { Text("Type a name or JID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (directoryQuery.isBlank()) {
                                Text(
                                    "Type a name or JID to find a contact or group.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val shown = directory.filter { conv ->
                                    !whitelist.any { it.jid == conv.jid } &&
                                        (conv.label.contains(directoryQuery, ignoreCase = true) ||
                                            conv.jid.contains(directoryQuery, ignoreCase = true))
                                }.take(20)
                                if (shown.isEmpty()) {
                                    Text(
                                        if (directory.isEmpty()) "Tap Load contacts & groups to fetch from WhatsApp."
                                        else "No matches.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    // Results fill remaining height with their own scroll —
                                    // the search field above stays put and the activity
                                    // scroll is not affected.
                                    Column(
                                        modifier = Modifier
                                            .weight(1f, fill = true)
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        shown.forEach { conv ->
                                            AddRow(
                                                conv = conv,
                                                onAdd = { cat -> onWhitelistWithCategory(conv, cat) },
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
    }
}

@Composable
private fun ManageRow(
    item: WhatsAppWhitelist,
    showDivider: Boolean,
    onUpdateCategory: (RelationCategory) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label ?: item.jid,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(6.dp))
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { menuOpen = true },
                ) {
                    Text(
                        text = item.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Column {
                            RelationCategory.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        menuOpen = false
                                        onUpdateCategory(cat)
                                    },
                                    colors = MenuItemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(2.dp))
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Remove ${item.label ?: item.jid}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onRemove)
                    .padding(6.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun AddRow(
    conv: WhatsAppConversation,
    onAdd: (RelationCategory) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conv.label + if (conv.isGroup) " (group)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = conv.jid,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clickable { menuOpen = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "▾",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(4.dp),
                ) {
                    Column {
                        RelationCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    menuOpen = false
                                    onAdd(cat)
                                },
                                colors = MenuItemColors(
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
