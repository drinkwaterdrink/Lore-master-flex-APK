package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CharacterCardV2
import com.example.model.CharxExpressionAsset
import com.example.model.LumiverseCustomAction
import com.example.model.UniverseProject
import com.example.ui.components.ArcaneSectionCard
import com.example.ui.components.KeywordTagEditor
import com.example.ui.components.TokenMeterChip
import com.example.ui.theme.ArcaneViolet
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
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CharacterCardStudioSection(
    project: UniverseProject,
    activeCard: CharacterCardV2?,
    isEditingFullScreen: Boolean = false,
    onSelectCard: (String) -> Unit,
    onOpenCardFullScreen: (String) -> Unit = onSelectCard,
    onCloseCardFullScreen: () -> Unit = {},
    onAddCard: () -> Unit,
    onUpdateCard: (CharacterCardV2) -> Unit,
    onDeleteCard: (String) -> Unit,
    onSyncWithLorebook: (CharacterCardV2) -> Unit,
    onJumpToLoreEntry: (Int) -> Unit
) {
    val cards = project.characterCards

    if (isEditingFullScreen && activeCard != null) {
        BackHandler { onCloseCardFullScreen() }
        FullScreenCharacterCardEditor(
            card = activeCard,
            onBack = onCloseCardFullScreen,
            onUpdateCard = onUpdateCard,
            onDeleteCard = {
                onDeleteCard(activeCard.id)
                onCloseCardFullScreen()
            },
            onSyncWithLorebook = { onSyncWithLorebook(activeCard) },
            onJumpToLoreEntry = onJumpToLoreEntry
        )
        return
    }

    // CLEAN DIRECTORY VIEW FOR CHARX & CCV3 CHARACTER CARDS
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lumiverse .CHARX & CCV3 Cards (${cards.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Tap any persona card to edit CCV3 fields, expressions, and Lumiverse modules",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Button(
                onClick = onAddCard,
                colors = ButtonDefaults.buttonColors(containerColor = LuminaCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_character_card_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Character Card", tint = ObsidianAbyss, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Card", color = ObsidianAbyss, fontWeight = FontWeight.Bold)
            }
        }

        if (cards.isEmpty()) {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = LuminaCyan,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No CHARX or Character Cards Yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create a Lumiverse .charx / CCV3 card from scratch, import an existing .charx / PNG / JSON card, or promote any Character entry from your Lorebook.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddCard,
                        colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First CHARX Card")
                    }
                }
            }
        } else {
            cards.forEach { card ->
                val initials = card.name.split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .take(2)
                    .joinToString("")
                    .ifBlank { "CX" }

                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCardFullScreen(card.id) }
                        .testTag("character_card_item_${card.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(LuminaCyan, IrisPrimary))
                                )
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = card.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = ArcaneViolet.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Text(
                                        text = "CHARX v${card.specVersion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IrisLight,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (card.linkedLoreEntryUid != null) EmeraldSuccess.copy(alpha = 0.14f) else LuminaGold.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Text(
                                        text = if (card.linkedLoreEntryUid != null) "Lore #${card.linkedLoreEntryUid}" else "Unlinked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (card.linkedLoreEntryUid != null) EmeraldSuccess else LuminaGold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${card.roleTitle.ifBlank { "Persona" }} • ${card.expressions.size} expr • ${card.lumiverseModules.customActions.size} actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TokenMeterChip(tokens = card.estimateTokens())
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Edit Character Card",
                            tint = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullScreenCharacterCardEditor(
    card: CharacterCardV2,
    onBack: () -> Unit,
    onUpdateCard: (CharacterCardV2) -> Unit,
    onDeleteCard: () -> Unit,
    onSyncWithLorebook: () -> Unit,
    onJumpToLoreEntry: (Int) -> Unit
) {
    // 0 = Persona & Sync, 1 = Greetings, 2 = Prompts & Tags, 3 = CHARX & Lumiverse
    var editorSubTab by remember { mutableIntStateOf(0) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Focused Editor Bar
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
                        modifier = Modifier.size(36.dp).testTag("back_to_cards_list_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Character Cards",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.name.ifBlank { "Untitled Persona" },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "CCV3 / CHARX • ${card.roleTitle.ifBlank { "Persona" }} • ${card.expressions.size} Expressions",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuminaCyan
                        )
                    }
                    TokenMeterChip(tokens = card.estimateTokens())
                    IconButton(onClick = onDeleteCard, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Card", tint = RoseError, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4 Sub-Tabs including CHARX & Lumiverse
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        0 to "Persona & Sync",
                        1 to "Greetings (${card.alternateGreetings.size + 1})",
                        2 to "Prompts & Tags",
                        3 to "CHARX & Lumiverse"
                    ).forEach { (idx, label) ->
                        val selected = editorSubTab == idx
                        Surface(
                            color = if (selected) IrisPrimary.copy(alpha = 0.22f) else ObsidianElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) IrisPrimary else ObsidianBorder),
                            modifier = Modifier
                                .clickable { editorSubTab = idx }
                                .testTag("card_subtab_$idx")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
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

        when (editorSubTab) {
            0 -> {
                ArcaneSectionCard(
                    title = "Identity, Nickname & Lorebook Sync",
                    subtitle = "Bidirectional link between this CCV3 / CHARX Card and your Lorebook",
                    accentColor = LuminaCyan
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = card.name,
                            onValueChange = { onUpdateCard(card.copy(name = it)) },
                            label = { Text("Character Name ({{char}})") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("card_name_input")
                        )
                        OutlinedTextField(
                            value = card.nickname,
                            onValueChange = { onUpdateCard(card.copy(nickname = it)) },
                            label = { Text("CCV3 Nickname") },
                            placeholder = { Text("Optional alias") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = card.roleTitle,
                            onValueChange = { onUpdateCard(card.copy(roleTitle = it)) },
                            label = { Text("Role / Epithet") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = card.factionAffiliation,
                            onValueChange = { onUpdateCard(card.copy(factionAffiliation = it)) },
                            label = { Text("Faction Affiliation") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onSyncWithLorebook,
                            colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("sync_card_to_lorebook_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync with Lorebook", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (card.linkedLoreEntryUid != null) "Sync to Lore Entry #${card.linkedLoreEntryUid}"
                                else "Create & Bind Lore Entry"
                            )
                        }
                        if (card.linkedLoreEntryUid != null) {
                            OutlinedButton(
                                onClick = { onJumpToLoreEntry(card.linkedLoreEntryUid) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = "Jump to Lore Entry", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Entry #${card.linkedLoreEntryUid}")
                            }
                        }
                    }
                }

                ArcaneSectionCard(
                    title = "Persona, Voice & Scenario",
                    subtitle = "Core character description, personality matrix, secret agenda, and scene context",
                    accentColor = IrisPrimary
                ) {
                    OutlinedTextField(
                        value = card.description,
                        onValueChange = { onUpdateCard(card.copy(description = it)) },
                        label = { Text("Description & Appearance (description)") },
                        minLines = 4,
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IrisPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianElevated,
                            unfocusedContainerColor = ObsidianElevated
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = card.personality,
                        onValueChange = { onUpdateCard(card.copy(personality = it)) },
                        label = { Text("Personality Matrix (personality)") },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IrisPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianElevated,
                            unfocusedContainerColor = ObsidianElevated
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = card.voiceCadence,
                            onValueChange = { onUpdateCard(card.copy(voiceCadence = it)) },
                            label = { Text("Voice Cadence") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = card.hiddenAgenda,
                            onValueChange = { onUpdateCard(card.copy(hiddenAgenda = it)) },
                            label = { Text("Secret / Hidden Agenda") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = card.scenario,
                        onValueChange = { onUpdateCard(card.copy(scenario = it)) },
                        label = { Text("Roleplay Scenario Context (scenario)") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            1 -> {
                ArcaneSectionCard(
                    title = "First Message, Alternate & Group Greetings",
                    subtitle = "Opening roleplay scene + swipeable alternate & CCV3 group-only greetings",
                    accentColor = LuminaGold
                ) {
                    OutlinedTextField(
                        value = card.firstMes,
                        onValueChange = { onUpdateCard(card.copy(firstMes = it)) },
                        label = { Text("Primary Opening Message (first_mes)") },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth().testTag("card_first_mes_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    var newGreetingText by remember { mutableStateOf("") }
                    Text(
                        text = "Alternate Greetings (${card.alternateGreetings.size}):",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    card.alternateGreetings.forEachIndexed { idx, greeting ->
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Alternate Greeting #${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LuminaGold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = greeting,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = card.alternateGreetings.filterIndexed { i, _ -> i != idx }
                                        onUpdateCard(card.copy(alternateGreetings = updated))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete greeting", tint = RoseError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newGreetingText,
                            onValueChange = { newGreetingText = it },
                            placeholder = { Text("Add a new alternate greeting scenario...") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newGreetingText.isNotBlank()) {
                                    onUpdateCard(card.copy(alternateGreetings = card.alternateGreetings + newGreetingText.trim()))
                                    newGreetingText = ""
                                }
                            },
                            enabled = newGreetingText.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                }
            }

            2 -> {
                ArcaneSectionCard(
                    title = "Example Dialogue, Prompts & Export Options",
                    subtitle = "mes_example (<START>), system_prompt, post_history_instructions & character_book",
                    accentColor = ArcaneViolet
                ) {
                    OutlinedTextField(
                        value = card.mesExample,
                        onValueChange = { onUpdateCard(card.copy(mesExample = it)) },
                        label = { Text("Example Dialogue (mes_example)") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = card.systemPrompt,
                        onValueChange = { onUpdateCard(card.copy(systemPrompt = it)) },
                        label = { Text("Character System Prompt Override (system_prompt)") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = card.postHistoryInstructions,
                        onValueChange = { onUpdateCard(card.copy(postHistoryInstructions = it)) },
                        label = { Text("Post-History / Jailbreak Instructions") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KeywordTagEditor(
                        label = "Character Card Tags",
                        keys = card.tags,
                        onKeysChanged = { onUpdateCard(card.copy(tags = it)) },
                        warnBroadKeys = false,
                        placeholder = "Add tag..."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = card.embedLorebookOnExport,
                            onCheckedChange = { onUpdateCard(card.copy(embedLorebookOnExport = it)) }
                        )
                        Text(
                            text = "Embed active World Lorebook inside .charx Archive ('lorebooks/worldbook.json' + 'character_book'), PNG & JSON exports",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            3 -> {
                // TAB 3: LUMIVERSE .CHARX BUNDLE, EXPRESSIONS & MODULES
                ArcaneSectionCard(
                    title = "CHARX Expression Assets & Emotion Mapping",
                    subtitle = "Bundled inside the .charx archive at 'assets/emotion/images/<emotion>.png' and CCV3 assets[]",
                    accentColor = LuminaCyan
                ) {
                    var newEmotion by remember { mutableStateOf("") }
                    var newTrigger by remember { mutableStateOf("") }

                    card.expressions.forEachIndexed { idx, expr ->
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = null,
                                    tint = LuminaCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${expr.emotion.uppercase()} → Trigger: \"${expr.triggerKeyword}\"",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Archive Path: ${expr.assetPath}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = TextMuted
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val remaining = card.expressions.filterIndexed { i, _ -> i != idx }
                                        onUpdateCard(card.copy(expressions = remaining))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove expression", tint = RoseError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newEmotion,
                            onValueChange = { newEmotion = it },
                            label = { Text("Emotion Name") },
                            placeholder = { Text("e.g., defiant") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newTrigger,
                            onValueChange = { newTrigger = it },
                            label = { Text("Trigger Keyword") },
                            placeholder = { Text("e.g., draws blade") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val cleanEmo = newEmotion.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
                            if (cleanEmo.isNotBlank()) {
                                val added = CharxExpressionAsset(
                                    emotion = cleanEmo,
                                    triggerKeyword = newTrigger.trim().ifBlank { cleanEmo },
                                    assetPath = "assets/emotion/images/$cleanEmo.png"
                                )
                                onUpdateCard(card.copy(expressions = card.expressions + added))
                                newEmotion = ""
                                newTrigger = ""
                            }
                        },
                        enabled = newEmotion.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = LuminaCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianAbyss, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add CHARX Expression Sprite Mapping", color = ObsidianAbyss, fontWeight = FontWeight.Bold)
                    }
                }

                ArcaneSectionCard(
                    title = "Lumiverse Modules (lumiverse_modules.json)",
                    subtitle = "Custom actions, HUD overlay themes, state variables & system extensions bundled in .charx",
                    accentColor = ArcaneViolet
                ) {
                    val modules = card.lumiverseModules

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Lumiverse HUD Overlay", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Text("Displays state tracker variables & custom action buttons in Lumiverse", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Switch(
                            checked = modules.enableHudOverlay,
                            onCheckedChange = {
                                onUpdateCard(card.copy(lumiverseModules = modules.copy(enableHudOverlay = it)))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Lumiverse HUD Theme Style:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("obsidian_arcane", "crimson_synod", "cyber_neon", "parchment_codex").forEach { styleId ->
                            FilterChip(
                                selected = modules.hudThemeStyle == styleId,
                                onClick = {
                                    onUpdateCard(card.copy(lumiverseModules = modules.copy(hudThemeStyle = styleId)))
                                },
                                label = { Text(styleId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    KeywordTagEditor(
                        label = "Lumiverse State Tracker Variables",
                        keys = modules.stateTrackerVariables,
                        onKeysChanged = {
                            onUpdateCard(card.copy(lumiverseModules = modules.copy(stateTrackerVariables = it)))
                        },
                        warnBroadKeys = false,
                        placeholder = "e.g., Affection (0..100), Suspicion..."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Lumiverse Custom Actions (${modules.customActions.size}):",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    modules.customActions.forEach { action ->
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = IrisLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${action.label} (${action.triggerMacro})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = action.promptInjection,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val nextActions = modules.customActions.filterNot { it.id == action.id }
                                        onUpdateCard(card.copy(lumiverseModules = modules.copy(customActions = nextActions)))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete custom action", tint = RoseError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    var actLabel by remember { mutableStateOf("") }
                    var actMacro by remember { mutableStateOf("") }
                    var actPrompt by remember { mutableStateOf("") }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = actLabel,
                            onValueChange = { actLabel = it },
                            label = { Text("Action Label") },
                            placeholder = { Text("Inspect Relic") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = actMacro,
                            onValueChange = { actMacro = it },
                            label = { Text("Trigger Macro") },
                            placeholder = { Text("/inspect") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = actPrompt,
                        onValueChange = { actPrompt = it },
                        label = { Text("System Prompt Injection on Action") },
                        placeholder = { Text("[LUMIVERSE ACTION: {{user}} activates the relic; {{char}} reacts immediately.]") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (actLabel.isNotBlank() && actPrompt.isNotBlank()) {
                                val added = LumiverseCustomAction(
                                    label = actLabel.trim(),
                                    triggerMacro = actMacro.trim().ifBlank { "/${actLabel.trim().lowercase().replace(" ", "_")}" },
                                    promptInjection = actPrompt.trim()
                                )
                                onUpdateCard(
                                    card.copy(
                                        lumiverseModules = modules.copy(
                                            customActions = modules.customActions + added
                                        )
                                    )
                                )
                                actLabel = ""
                                actMacro = ""
                                actPrompt = ""
                            }
                        },
                        enabled = actLabel.isNotBlank() && actPrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Lumiverse Custom Action")
                    }
                }
            }
        }
    }
}
