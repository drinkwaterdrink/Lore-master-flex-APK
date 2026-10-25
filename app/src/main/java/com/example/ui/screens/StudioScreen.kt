package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.sp
import com.example.model.DreamWeaverBook
import com.example.model.SelectiveLogicType
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CrossRefMention
import com.example.model.EntryTemplateType
import com.example.model.EventGroupPreset
import com.example.model.EventOutcomeItem
import com.example.model.STEntry
import com.example.ui.components.ArcaneSectionCard
import com.example.ui.components.EntityTypeBadge
import com.example.ui.components.KeywordTagEditor
import com.example.ui.components.TokenMeterChip
import com.example.ui.components.accentColor
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.ColorDynamicTrigger
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IrisLight
import com.example.ui.theme.IrisPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminaCyan
import com.example.ui.theme.LuminaGold
import com.example.ui.theme.ObsidianAbyss
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LoreMasterUiState
import com.example.viewmodel.LoreMasterViewModel
import com.example.viewmodel.MainWorkspaceTab
import com.example.viewmodel.StudioSubMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    modifier: Modifier = Modifier
) {
    val project = uiState.activeProject ?: return
    val entries = project.lorebook.entries
    var showAddTemplateMenu by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showEventGroupDialog by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Focused Full-Screen Entry Editor when an entry is opened
        val activeEntry = uiState.activeEntry
        if (uiState.studioSubMode == StudioSubMode.LOREBOOK_ENTRIES &&
            uiState.isEditingEntryFullScreen &&
            activeEntry != null
        ) {
            BackHandler { viewModel.closeEntryFullScreenEditor() }
            FullScreenEntryDetailEditor(
                entry = activeEntry,
                pendingEnhancedEntry = uiState.pendingEnhancedEntry,
                crossRefMentions = uiState.crossRefMentions.filter {
                    it.sourceUidOrId == activeEntry.uid.toString() || it.targetUidOrId == activeEntry.uid.toString()
                },
                onBack = viewModel::closeEntryFullScreenEditor,
                onUpdateEntry = viewModel::updateEntry,
                onDuplicate = { viewModel.duplicateEntry(activeEntry.uid) },
                onDelete = {
                    viewModel.deleteEntry(activeEntry.uid)
                    viewModel.closeEntryFullScreenEditor()
                },
                onPromoteToCard = { viewModel.promoteEntryToCharacterCard(activeEntry) },
                onAiEnhance = viewModel::runAiEntryEnhancement,
                onAcceptPending = viewModel::acceptPendingEntryEnhancement,
                onRejectPending = viewModel::rejectPendingEntryEnhancement,
                onApplyCrossRef = viewModel::applySingleCrossRef,
                onSplitNpc = { viewModel.splitNpcEntry(activeEntry.uid) }
            )
            return@Column
        }

        // Focused Full-Screen Character Card Editor when a card is opened
        if (uiState.studioSubMode == StudioSubMode.CHARACTER_CARDS &&
            uiState.isEditingCardFullScreen &&
            uiState.activeCard != null
        ) {
            CharacterCardStudioSection(
                project = project,
                activeCard = uiState.activeCard,
                isEditingFullScreen = true,
                onSelectCard = viewModel::selectCharacterCard,
                onOpenCardFullScreen = viewModel::openCardFullScreenEditor,
                onCloseCardFullScreen = viewModel::closeCardFullScreenEditor,
                onAddCard = viewModel::addNewCharacterCard,
                onUpdateCard = viewModel::updateCharacterCard,
                onDeleteCard = viewModel::deleteCharacterCard,
                onSyncWithLorebook = viewModel::syncCharacterCardWithLorebook,
                onJumpToLoreEntry = { uid ->
                    viewModel.setStudioSubMode(StudioSubMode.LOREBOOK_ENTRIES)
                    viewModel.openEntryFullScreenEditor(uid)
                }
            )
            return@Column
        }

        // 1. Clean Linear-Style Segmented Switcher: Lorebook V2 vs Character Cards V2
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioSubMode.entries.forEach { mode ->
                    val selected = uiState.studioSubMode == mode
                    val badgeCount = if (mode == StudioSubMode.LOREBOOK_ENTRIES) entries.size else project.characterCards.size
                    Surface(
                        color = if (selected) IrisPrimary.copy(alpha = 0.22f) else ObsidianCard,
                        shape = RoundedCornerShape(9.dp),
                        border = if (selected) BorderStroke(1.dp, IrisPrimary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setStudioSubMode(mode) }
                            .testTag("studio_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "${mode.label} ($badgeCount)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (uiState.studioSubMode == StudioSubMode.CHARACTER_CARDS) {
            CharacterCardStudioSection(
                project = project,
                activeCard = uiState.activeCard,
                isEditingFullScreen = false,
                onSelectCard = viewModel::selectCharacterCard,
                onOpenCardFullScreen = viewModel::openCardFullScreenEditor,
                onCloseCardFullScreen = viewModel::closeCardFullScreenEditor,
                onAddCard = viewModel::addNewCharacterCard,
                onUpdateCard = viewModel::updateCharacterCard,
                onDeleteCard = viewModel::deleteCharacterCard,
                onSyncWithLorebook = viewModel::syncCharacterCardWithLorebook,
                onJumpToLoreEntry = { uid ->
                    viewModel.setStudioSubMode(StudioSubMode.LOREBOOK_ENTRIES)
                    viewModel.openEntryFullScreenEditor(uid)
                }
            )
            return@Column
        }

        // --- CLEAN LOREBOOK V2 CODEX DIRECTORY ---
        val groups = remember(entries) {
            listOf("ALL") + entries.map { it.group.ifBlank { "ungrouped" } }.distinct()
        }
        val filteredEntries = remember(entries, uiState.searchQuery, uiState.selectedGroupFilter, uiState.selectedDreamWeaverBook) {
            entries.filter { entry ->
                val matchesSearch = uiState.searchQuery.isBlank() ||
                    entry.comment.contains(uiState.searchQuery, ignoreCase = true) ||
                    entry.key.any { it.contains(uiState.searchQuery, ignoreCase = true) } ||
                    entry.content.contains(uiState.searchQuery, ignoreCase = true)
                val matchesGroup = uiState.selectedGroupFilter == "ALL" ||
                    entry.group.ifBlank { "ungrouped" }.equals(uiState.selectedGroupFilter, ignoreCase = true)
                val matchesBook = uiState.selectedDreamWeaverBook == DreamWeaverBook.ALL ||
                    entry.dreamWeaverBook() == uiState.selectedDreamWeaverBook ||
                    entry.isSectionDivider()
                matchesSearch && matchesGroup && matchesBook
            }
        }

        // Top Search & Action Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search entries, keys, or lore...", style = MaterialTheme.typography.bodySmall, color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search entries", tint = TextMuted, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IrisPrimary,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard
                ),
                modifier = Modifier.weight(1f).testTag("entry_search_input")
            )

            // Add Entry Dropdown
            Box {
                Button(
                    onClick = { showAddTemplateMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(52.dp).testTag("add_entry_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Entry")
                }
                DropdownMenu(
                    expanded = showAddTemplateMenu,
                    onDismissRequest = { showAddTemplateMenu = false }
                ) {
                    EntryTemplateType.entries.forEach { tpl ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EntityTypeBadge(type = tpl)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tpl.label)
                                }
                            },
                            onClick = {
                                showAddTemplateMenu = false
                                viewModel.addNewEntry(tpl)
                            }
                        )
                    }
                }
            }
        }

        // 3-Book "Dream Weaver" Architecture Strip (Rules Book • Atlas Book • Cast Book)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            DreamWeaverBook.entries.forEach { book ->
                val selected = uiState.selectedDreamWeaverBook == book
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setDreamWeaverBook(book) },
                    label = { Text(book.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LuminaCyan.copy(alpha = 0.22f),
                        selectedLabelColor = LuminaCyan
                    )
                )
            }
            FilterChip(
                selected = false,
                onClick = { viewModel.insertDreamWeaverDividers() },
                label = { Text("+ 3-Book Dividers", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Default.FolderSpecial, contentDescription = "Insert Folder Dividers", tint = IrisLight, modifier = Modifier.size(15.dp))
                }
            )
        }

        // Compact Filter & Tools Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            TokenMeterChip(
                tokens = entries.sumOf { it.estimateTokens() },
                budget = project.lorebook.tokenBudget
            )
            groups.forEach { group ->
                val selected = uiState.selectedGroupFilter.equals(group, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setGroupFilter(group) },
                    label = { Text(group.uppercase(), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IrisPrimary.copy(alpha = 0.22f),
                        selectedLabelColor = IrisLight
                    )
                )
            }
            FilterChip(
                selected = false,
                onClick = { showEventGroupDialog = true },
                label = { Text("Event Group", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Default.Casino, contentDescription = "Event Group Creator", tint = LuminaGold, modifier = Modifier.size(15.dp))
                },
                modifier = Modifier.testTag("open_event_group_creator_button")
            )
            FilterChip(
                selected = false,
                onClick = { showFindReplaceDialog = true },
                label = { Text("Find & Replace", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Default.FindReplace, contentDescription = "Find and Replace", tint = LuminaCyan, modifier = Modifier.size(15.dp))
                }
            )
            FilterChip(
                selected = isMultiSelectMode,
                onClick = {
                    isMultiSelectMode = !isMultiSelectMode
                    if (!isMultiSelectMode) viewModel.clearMultiSelection()
                },
                label = {
                    Text(
                        if (isMultiSelectMode) "Done (${uiState.multiSelectedEntryUids.size})" else "Bulk",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isMultiSelectMode) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Bulk Select Mode",
                        modifier = Modifier.size(15.dp)
                    )
                }
            )
        }

        // Bulk Edit Bar (only visible when Bulk mode is toggled)
        AnimatedVisibility(visible = isMultiSelectMode) {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, IrisPrimary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(6.dp)
                ) {
                    TextButton(onClick = { viewModel.selectAllFilteredEntries(filteredEntries.map { it.uid }) }) {
                        Text("Select All (${filteredEntries.size})")
                    }
                    TextButton(onClick = { viewModel.clearMultiSelection() }) {
                        Text("Clear")
                    }
                    TextButton(onClick = { viewModel.bulkApplyTemplate(EntryTemplateType.CHARACTER) }) {
                        Text("Set CHAR Preset")
                    }
                    TextButton(onClick = { viewModel.bulkApplyTemplate(EntryTemplateType.WORLD_RULE) }) {
                        Text("Set RULE Preset")
                    }
                    TextButton(
                        onClick = { viewModel.bulkDeleteSelectedEntries() },
                        colors = ButtonDefaults.textButtonColors(contentColor = RoseError)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Bulk Delete", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete (${uiState.multiSelectedEntryUids.size})")
                    }
                }
            }
        }

        // Empty State when World is Blank
        if (entries.isEmpty()) {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAdd,
                        contentDescription = null,
                        tint = IrisLight,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "'${project.name}' is a Blank Canvas",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start adding custom Lorebook V2 entries manually, or jump to the AI Forge tab to type a raw concept and let the AI flesh out your world's premise, rules, factions, and cast.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.addNewEntry(EntryTemplateType.WORLD_RULE) },
                            colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add First Entry")
                        }
                        OutlinedButton(
                            onClick = { viewModel.setMainTab(MainWorkspaceTab.FORGE) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LuminaCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LuminaCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Forge Idea", color = LuminaCyan)
                        }
                    }
                }
            }
        } else {
            // Clean Directory List of Codex Entries (tap to open full-screen editor)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                filteredEntries.forEach { entry ->
                    if (entry.isSectionDivider()) {
                        Surface(
                            color = IrisPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, IrisPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = IrisLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.comment,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IrisLight,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(0 tokens • Folder Divider)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                        return@forEach
                    }

                    val type = entry.classifyType()
                    val isChecked = entry.uid in uiState.multiSelectedEntryUids
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isChecked) IrisPrimary else ObsidianBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isMultiSelectMode) {
                                    viewModel.toggleMultiSelectEntry(entry.uid)
                                } else {
                                    viewModel.openEntryFullScreenEditor(entry.uid)
                                }
                            }
                            .testTag("entry_card_${entry.uid}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (isMultiSelectMode) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.toggleMultiSelectEntry(entry.uid) },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            EntityTypeBadge(type = type)
                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.comment.ifBlank { "Untitled Entry #${entry.uid}" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (entry.disable) TextMuted else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (entry.constant) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = LuminaGold.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "CONST",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LuminaGold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (entry.key.isNotEmpty()) {
                                        "Keys: ${entry.key.take(4).joinToString(", ")}"
                                    } else {
                                        "No trigger keywords"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            // LoreBible-style 1-tap inline ON / OFF context toggle
                            Surface(
                                color = if (!entry.disable) EmeraldSuccess.copy(alpha = 0.14f) else ObsidianElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (!entry.disable) EmeraldSuccess.copy(alpha = 0.45f) else ObsidianBorder
                                ),
                                modifier = Modifier
                                    .clickable { viewModel.toggleEntryEnabled(entry.uid) }
                                    .testTag("toggle_entry_enabled_${entry.uid}")
                            ) {
                                Text(
                                    text = if (!entry.disable) "ON" else "OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!entry.disable) EmeraldSuccess else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${entry.estimateTokens()}t",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Pos ${entry.position}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Entry Editor",
                                tint = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFindReplaceDialog) {
        FindAndReplaceModal(
            onDismiss = { showFindReplaceDialog = false },
            onApply = { find, rep, ignoreCase ->
                viewModel.bulkFindAndReplace(find, rep, ignoreCase)
                showFindReplaceDialog = false
            }
        )
    }

    if (showEventGroupDialog) {
        EventGroupCreatorModal(
            onDismiss = { showEventGroupDialog = false },
            onCreateGroup = { groupName, triggerKey, sticky, outcomes ->
                viewModel.createWeightedEventGroup(groupName, triggerKey, sticky, outcomes)
                showEventGroupDialog = false
            }
        )
    }
}

/**
 * Dedicated Full-Screen Entry Editor with 3 clean sub-tabs:
 * - Tab 0: Writing & Keys (Title, Primary Trigger Keys, Content Editor, AI Enhance/Continue/Fix Keys, Bracket Templates)
 * - Tab 1: Triggers & STV2 Config (Secondary Keys, Selective Logic, Position 0–4, Order, Depth, Sticky, Probability, Flags)
 * - Tab 2: Cross-Links (Incoming & Outgoing entity mentions across the universe with 1-tap linking)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullScreenEntryDetailEditor(
    entry: STEntry,
    pendingEnhancedEntry: STEntry?,
    crossRefMentions: List<CrossRefMention>,
    onBack: () -> Unit,
    onUpdateEntry: (STEntry) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPromoteToCard: () -> Unit,
    onAiEnhance: (String) -> Unit,
    onAcceptPending: () -> Unit,
    onRejectPending: () -> Unit,
    onApplyCrossRef: (CrossRefMention) -> Unit,
    onSplitNpc: () -> Unit = {}
) {
    val detectedType = entry.classifyType()
    var editorSubTab by remember { mutableIntStateOf(0) } // 0 = Writing & Keys, 1 = Triggers & STV2, 2 = Cross-Links
    var showTemplateInsertMenu by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Focused Navigation & Action Header
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp).testTag("back_to_codex_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Codex",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    EntityTypeBadge(type = detectedType)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.comment.ifBlank { "Entry #${entry.uid}" },
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "#${entry.uid} • ${detectedType.label} • ~${entry.estimateTokens()} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onPromoteToCard, modifier = Modifier.size(34.dp).testTag("promote_to_card_button")) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Promote to Character Card", tint = LuminaCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Entry", tint = TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Entry", tint = RoseError, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3 Clean Sub-Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0 to "Writing & Keys",
                        1 to "Triggers & STV2",
                        2 to "Cross-Links (${crossRefMentions.size})"
                    ).forEach { (idx, label) ->
                        val selected = editorSubTab == idx
                        Surface(
                            color = if (selected) IrisPrimary.copy(alpha = 0.22f) else ObsidianElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) IrisPrimary else ObsidianBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editorSubTab = idx }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // AI Enhancement Diff Preview Banner
        if (pendingEnhancedEntry != null && pendingEnhancedEntry.uid == entry.uid) {
            ArcaneSectionCard(
                title = "AI Proposed Entry Enhancement",
                subtitle = "Review the enriched bracketed lore before committing",
                accentColor = LuminaGold
            ) {
                Text(
                    text = pendingEnhancedEntry.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = EmeraldSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianElevated)
                        .padding(10.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAcceptPending,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        modifier = Modifier.weight(1f).testTag("accept_ai_enhancement_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = ObsidianAbyss)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Accept Enhancement", color = ObsidianAbyss, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRejectPending,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Reject")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Discard")
                    }
                }
            }
        }

        when (editorSubTab) {
            0 -> {
                // TAB 0: WRITING & PRIMARY TRIGGER KEYS
                ArcaneSectionCard(
                    title = "Entry Identity & Trigger Keywords",
                    subtitle = "Display title and primary words that activate this entry in chat",
                    accentColor = detectedType.accentColor()
                ) {
                    OutlinedTextField(
                        value = entry.comment,
                        onValueChange = { onUpdateEntry(entry.copy(comment = it)) },
                        label = { Text("Entry Comment / Display Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = detectedType.accentColor(),
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianElevated,
                            unfocusedContainerColor = ObsidianElevated
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("entry_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    KeywordTagEditor(
                        label = "Primary Activation Keys",
                        keys = entry.key,
                        onKeysChanged = { onUpdateEntry(entry.copy(key = it)) }
                    )
                }

                ArcaneSectionCard(
                    title = "Lore Content & AI Co-Writer",
                    subtitle = "~${entry.estimateTokens()} tokens • ${entry.content.length} chars",
                    accentColor = IrisPrimary
                ) {
                    // AI Content Toolbar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Button(
                            onClick = { onAiEnhance("EXPAND") },
                            colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("ai_expand_entry_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "AI Enhance", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Enhance")
                        }
                        OutlinedButton(
                            onClick = { onAiEnhance("CONTINUE") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("AI Continue")
                        }
                        OutlinedButton(
                            onClick = { onAiEnhance("FIX_KEYS") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("AI Fix Keys")
                        }
                        Box {
                            OutlinedButton(
                                onClick = { showTemplateInsertMenu = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Insert Template")
                            }
                            DropdownMenu(
                                expanded = showTemplateInsertMenu,
                                onDismissRequest = { showTemplateInsertMenu = false }
                            ) {
                                EntryTemplateType.entries.forEach { tpl ->
                                    DropdownMenuItem(
                                        text = { Text("${tpl.label} Template") },
                                        onClick = {
                                            showTemplateInsertMenu = false
                                            val combined = if (entry.content.isBlank()) tpl.bracketTemplate else "${entry.content}\n${tpl.bracketTemplate}"
                                            onUpdateEntry(entry.copy(content = combined))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = entry.content,
                        onValueChange = { onUpdateEntry(entry.copy(content = it)) },
                        label = {
                            Text("Lorebook Content (Bracketed `[ key(val); ]`, NENS v3.0, or Prose)")
                        },
                        minLines = 8,
                        maxLines = 16,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IrisPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianElevated,
                            unfocusedContainerColor = ObsidianElevated
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("entry_content_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Lumiverse & SillyTavern Quick Macro & Delimiter Toolbar:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = ObsidianGlass,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, LuminaCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                val current = entry.content.trimStart()
                                val updated = if (current.startsWith("----")) current else "----\n$current"
                                onUpdateEntry(entry.copy(content = updated))
                            }
                        ) {
                            Text(
                                text = "---- Delimiter",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuminaCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        if (detectedType == EntryTemplateType.CHARACTER) {
                            Surface(
                                color = IrisPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, IrisPrimary),
                                modifier = Modifier.clickable {
                                    onSplitNpc()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = IrisLight, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "3-Part NPC Split",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IrisLight
                                    )
                                }
                            }
                        }

                        listOf(
                            "{{@var}}" to "{{@state_var}}",
                            "{{if @var}}" to "\n{{if @var >= 1}}\nSensory cue when condition met\n{{/if}}",
                            "{{pick::A::B}}" to "{{pick::Branch A::Branch B::Branch C}}",
                            "{{roll::1d20}}" to "{{roll::1d20}}",
                            "<wi-exclude>" to "<wi-exclude>classified</wi-exclude>",
                            "{{char}} will instantly " to "{{char}} will instantly react by "
                        ).forEach { (label, snippet) ->
                            Surface(
                                color = ObsidianGlass,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.clickable {
                                    val updatedContent = if (entry.content.isBlank()) snippet else "${entry.content.trimEnd()} $snippet"
                                    onUpdateEntry(entry.copy(content = updatedContent))
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: TRIGGERS & SILLYTAVERN V2 / LUMIVERSE PARAMETERS
                ArcaneSectionCard(
                    title = "Secondary Keys & Selective Logic Gates",
                    subtitle = "Filter triggers defensively using SillyTavern & Lumiverse logic gates",
                    accentColor = ArcaneViolet
                ) {
                    KeywordTagEditor(
                        label = "Secondary / Cross-Reference Keys",
                        keys = entry.keysecondary,
                        onKeysChanged = { onUpdateEntry(entry.copy(keysecondary = it)) },
                        warnBroadKeys = false,
                        placeholder = "Add secondary cross-ref key..."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = entry.selective,
                                onCheckedChange = { onUpdateEntry(entry.copy(selective = it)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (entry.selective) "Selective Gate Active" else "Selective Gate Off (Triggers on Primary Alone)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.selective) TextPrimary else TextSecondary
                            )
                        }
                    }

                    if (entry.selective) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Logic Gate Type:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SelectiveLogicType.entries.forEach { gate ->
                                val selected = entry.selectiveLogic == gate.code
                                FilterChip(
                                    selected = selected,
                                    onClick = { onUpdateEntry(entry.copy(selectiveLogic = gate.code)) },
                                    label = { Text(gate.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ArcaneViolet.copy(alpha = 0.25f),
                                        selectedLabelColor = IrisLight
                                    )
                                )
                            }
                        }
                        val currentGate = SelectiveLogicType.fromCode(entry.selectiveLogic)
                        Text(
                            text = currentGate.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                ArcaneSectionCard(
                    title = "Injection Position & Priority Order",
                    subtitle = "Position ${entry.position} • Order ${entry.order} • Sticky ${entry.sticky} • Prob ${entry.probability}%",
                    accentColor = LuminaCyan
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Injection Position:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        TextButton(onClick = { onUpdateEntry(entry.applyTemplateDefaults(detectedType)) }) {
                            Text("Reset to ${detectedType.badgeCode} Defaults")
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EntryTemplateType.fromPositionLabels().forEach { (posInt, label) ->
                            val selected = entry.position == posInt
                            Surface(
                                color = if (selected) IrisPrimary.copy(alpha = 0.22f) else ObsidianElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selected) IrisPrimary else ObsidianBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateEntry(entry.copy(position = posInt)) }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = entry.order.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onUpdateEntry(entry.copy(order = it.coerceIn(0, 999))) } },
                            label = { Text("Priority Order (0-999)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = entry.depth.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onUpdateEntry(entry.copy(depth = it.coerceIn(0, 100))) } },
                            label = { Text("Scan Depth") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = entry.group,
                            onValueChange = { onUpdateEntry(entry.copy(group = it)) },
                            label = { Text("Inclusion Group") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (entry.group.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prioritize Inclusion (Group Override)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Mutually exclusive state machine: only the highest-priority active entry in '${entry.group}' injects.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = entry.groupOverride,
                                onCheckedChange = { onUpdateEntry(entry.copy(groupOverride = it)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = entry.sticky.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onUpdateEntry(entry.copy(sticky = it.coerceAtLeast(0))) } },
                            label = { Text("Sticky Turns") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = entry.cooldown.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onUpdateEntry(entry.copy(cooldown = it.coerceAtLeast(0))) } },
                            label = { Text("Cooldown") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = entry.delay.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onUpdateEntry(entry.copy(delay = it.coerceAtLeast(0))) } },
                            label = { Text("Delay (Turns)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Trigger Probability: ${entry.probability}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Slider(
                        value = entry.probability.toFloat(),
                        onValueChange = { onUpdateEntry(entry.copy(probability = it.toInt())) },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(thumbColor = IrisPrimary, activeTrackColor = IrisPrimary)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = entry.constant,
                            onClick = { onUpdateEntry(entry.copy(constant = !entry.constant)) },
                            label = { Text("Constant (Always On)") }
                        )
                        FilterChip(
                            selected = entry.excludeGreeting,
                            onClick = { onUpdateEntry(entry.copy(excludeGreeting = !entry.excludeGreeting)) },
                            label = { Text("Exclude Greeting Turn") }
                        )
                        FilterChip(
                            selected = entry.preventRecursion,
                            onClick = { onUpdateEntry(entry.copy(preventRecursion = !entry.preventRecursion)) },
                            label = { Text("Prevent Recursion") }
                        )
                        FilterChip(
                            selected = entry.excludeRecursion,
                            onClick = { onUpdateEntry(entry.copy(excludeRecursion = !entry.excludeRecursion)) },
                            label = { Text("Exclude Recursion") }
                        )
                        FilterChip(
                            selected = entry.delayUntilRecursion,
                            onClick = { onUpdateEntry(entry.copy(delayUntilRecursion = !entry.delayUntilRecursion)) },
                            label = { Text("Delay Until Recursion") }
                        )
                        FilterChip(
                            selected = entry.caseSensitive,
                            onClick = { onUpdateEntry(entry.copy(caseSensitive = !entry.caseSensitive)) },
                            label = { Text("Case Sensitive") }
                        )
                        FilterChip(
                            selected = entry.vectorized,
                            onClick = { onUpdateEntry(entry.copy(vectorized = !entry.vectorized)) },
                            label = { Text("Vectorized") }
                        )
                        FilterChip(
                            selected = entry.ignoreBudget,
                            onClick = { onUpdateEntry(entry.copy(ignoreBudget = !entry.ignoreBudget)) },
                            label = { Text("Ignore Budget") }
                        )
                        FilterChip(
                            selected = entry.disable,
                            onClick = { onUpdateEntry(entry.copy(disable = !entry.disable)) },
                            label = { Text("Disabled") }
                        )
                    }
                }
            }

            2 -> {
                // TAB 2: CROSS-LINKS & ENTITY MENTIONS FOR THIS ENTRY
                ArcaneSectionCard(
                    title = "Detected Entity Cross-References (${crossRefMentions.size})",
                    subtitle = "Entities mentioned by or referencing '${entry.comment}'",
                    accentColor = EmeraldSuccess
                ) {
                    if (crossRefMentions.isEmpty()) {
                        Text(
                            text = "No cross-references detected yet. Mention other characters, factions, or locations inside this entry's prose to automatically surface cross-links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            crossRefMentions.forEach { mention ->
                                Surface(
                                    color = ObsidianElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            tint = if (mention.isKeywordAlreadyLinked) EmeraldSuccess else LuminaCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${mention.sourceTitle} → ${mention.targetTitle}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Matched phrase: \"${mention.matchedPhrase}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted
                                            )
                                        }
                                        if (!mention.isKeywordAlreadyLinked) {
                                            Button(
                                                onClick = { onApplyCrossRef(mention) },
                                                colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    if (mention.targetType == "UNCREATED_ENTITY") "Create Stub" else "Bind Key",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Linked ✓",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldSuccess
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
private fun FindAndReplaceModal(
    onDismiss: () -> Unit,
    onApply: (String, String, Boolean) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Bulk Find & Replace Across Lorebook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Find phrase or entity name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                    Text("Case-insensitive matching", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(findText, replaceText, ignoreCase) },
                enabled = findText.isNotEmpty()
            ) {
                Text("Replace All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EventGroupCreatorModal(
    onDismiss: () -> Unit,
    onCreateGroup: (String, String, Int, List<EventOutcomeItem>) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(EventGroupPreset.COMBAT_ROLL) }
    var groupName by remember { mutableStateOf(selectedPreset.groupName) }
    var triggerKey by remember { mutableStateOf(selectedPreset.triggerKey) }
    var stickyDuration by remember { mutableIntStateOf(selectedPreset.stickyDuration) }
    var outcomes by remember { mutableStateOf(selectedPreset.defaultOutcomes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Active Lorebook Event Group Creator") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Creates weighted random outcome entries sharing a trigger keyword so SillyTavern rolls one dynamic outcome.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    EventGroupPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                groupName = preset.groupName
                                triggerKey = preset.triggerKey
                                stickyDuration = preset.stickyDuration
                                outcomes = preset.defaultOutcomes
                            },
                            label = { Text(preset.title) }
                        )
                    }
                }
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Inclusion Group ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = triggerKey,
                    onValueChange = { triggerKey = it },
                    label = { Text("Shared Trigger Keyword") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Outcomes (${outcomes.size})", style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = {
                            val eq = (100 / maxOf(1, outcomes.size))
                            outcomes = outcomes.map { it.copy(weight = eq) }
                        }
                    ) {
                        Text("Equalize Weights")
                    }
                }
                outcomes.forEachIndexed { idx, item ->
                    Surface(
                        color = ObsidianElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.label} (${item.weight}%)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LuminaGold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Slider(
                                value = item.weight.toFloat(),
                                onValueChange = { w ->
                                    outcomes = outcomes.mapIndexed { i, o ->
                                        if (i == idx) o.copy(weight = w.toInt()) else o
                                    }
                                },
                                valueRange = 5f..100f
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreateGroup(groupName, triggerKey, stickyDuration, outcomes) },
                modifier = Modifier.testTag("confirm_create_event_group_button")
            ) {
                Text("Compile ${outcomes.size} Weighted Entries")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
