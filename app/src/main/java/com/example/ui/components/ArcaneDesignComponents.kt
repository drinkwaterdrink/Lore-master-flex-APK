package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiReasoningTelemetry
import com.example.model.EntryTemplateType
import com.example.model.LoreKeywordValidator
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.ColorCharacter
import com.example.ui.theme.ColorConcept
import com.example.ui.theme.ColorDialogue
import com.example.ui.theme.ColorDynamicTrigger
import com.example.ui.theme.ColorFaction
import com.example.ui.theme.ColorItem
import com.example.ui.theme.ColorLocation
import com.example.ui.theme.ColorSceneEvent
import com.example.ui.theme.ColorWorldRule
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
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

fun EntryTemplateType.accentColor(): Color = when (this) {
    EntryTemplateType.WORLD_RULE -> ColorWorldRule
    EntryTemplateType.CHARACTER -> ColorCharacter
    EntryTemplateType.LOCATION -> ColorLocation
    EntryTemplateType.FACTION -> ColorFaction
    EntryTemplateType.ITEM -> ColorItem
    EntryTemplateType.SCENE_EVENT -> ColorSceneEvent
    EntryTemplateType.DIALOGUE_PATTERN -> ColorDialogue
    EntryTemplateType.DYNAMIC_TRIGGER -> ColorDynamicTrigger
    EntryTemplateType.CONCEPT -> ColorConcept
}

@Composable
fun EntityTypeBadge(
    type: EntryTemplateType,
    modifier: Modifier = Modifier
) {
    val accent = type.accentColor()
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
        modifier = modifier
    ) {
        Text(
            text = type.badgeCode,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TokenMeterChip(
    tokens: Int,
    budget: Int? = null,
    modifier: Modifier = Modifier
) {
    val ratio = if (budget != null && budget > 0) tokens.toFloat() / budget.toFloat() else 0f
    val accent = when {
        budget == null -> LuminaCyan
        ratio > 0.85f -> RoseError
        ratio > 0.6f -> AmberWarning
        else -> EmeraldSuccess
    }
    Surface(
        color = ObsidianElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (budget != null) "$tokens/$budget tkn" else "${tokens}t",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ArcaneSectionCard(
    title: String,
    subtitle: String? = null,
    accentColor: Color = IrisPrimary,
    initiallyExpanded: Boolean = true,
    collapsible: Boolean = false,
    trailingHeader: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                        )
                    )
                    .then(
                        if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                if (trailingHeader != null) {
                    trailingHeader()
                }
                if (collapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse section" else "Expand section",
                        tint = TextSecondary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordTagEditor(
    label: String,
    keys: List<String>,
    onKeysChanged: (List<String>) -> Unit,
    warnBroadKeys: Boolean = true,
    placeholder: String = "Add trigger key (comma or Enter)...",
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val broadDetected = if (warnBroadKeys) LoreKeywordValidator.findBroadKeywords(keys).toSet() else emptySet()

    fun commitInput() {
        val tokens = inputText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
            onKeysChanged((keys + tokens).distinct())
            inputText = ""
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label (${keys.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            if (broadDetected.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Broad key warning",
                        tint = AmberWarning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Broad: ${broadDetected.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (keys.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                keys.forEach { key ->
                    val isBroad = key.trim().lowercase() in LoreKeywordValidator.BROAD_KEYS || key.trim().length <= 2
                    val chipColor = if (isBroad && warnBroadKeys) AmberWarning else IrisLight
                    Surface(
                        color = chipColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, chipColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onKeysChanged(keys - key) }
                                .padding(start = 9.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isBroad && warnBroadKeys) AmberWarning else TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove keyword $key",
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { value ->
                    if (value.endsWith(",")) {
                        inputText = value.dropLast(1)
                        commitInput()
                    } else {
                        inputText = value
                    }
                },
                placeholder = {
                    Text(placeholder, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitInput() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IrisPrimary,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianElevated,
                    unfocusedContainerColor = ObsidianElevated
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = IrisPrimary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(48.dp)
                    .clickable { commitInput() }
                    .testTag("add_keyword_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add keyword",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Ultra-thin single-row Top Workspace Bar (~38.dp height) with:
 * - World switcher pill (Name + compact entry/card counts)
 * - Quick "+ New" world button
 * - Connection Profiles / BYOK button (OpenRouter, NanoGPT, AI Studio, Built-in Gemini)
 * - AI LIVE / SYNTH toggle
 * - Undo, Redo & Import/Export buttons
 * - Expandable Live Reasoning & Cancel Panel whenever AI is generating
 */
@Composable
fun TopWorkspaceHeader(
    universeName: String,
    universeGenre: String,
    entryCount: Int,
    cardCount: Int,
    canonIssueCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    useMockAiMode: Boolean,
    activeProfileShortLabel: String = "Gemini",
    isBusy: Boolean,
    busyLabel: String,
    reasoningTelemetry: AiReasoningTelemetry = AiReasoningTelemetry(),
    onOpenUniverseModal: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleMockMode: () -> Unit,
    onOpenConnectionProfiles: () -> Unit = {},
    onCancelGeneration: () -> Unit = {},
    onOpenExportImportModal: () -> Unit,
    onOpenArchitectSheet: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Ultra-thin single-row header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Active World Pill (single line)
                Surface(
                    color = ObsidianElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenUniverseModal() }
                        .testTag("universe_selector_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Worlds Home",
                            tint = IrisLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = universeName,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${entryCount}E•${cardCount}C",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextMuted,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Switch World",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Quick "+ New" World Pill
                Surface(
                    color = IrisPrimary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, IrisPrimary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clickable { onOpenUniverseModal() }
                        .testTag("quick_new_world_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New World",
                            tint = IrisLight,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Connection Profiles / BYOK Pill (OpenRouter, NanoGPT, AI Studio, Built-in)
                Surface(
                    color = ArcaneViolet.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, ArcaneViolet.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clickable { onOpenConnectionProfiles() }
                        .testTag("connection_profiles_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "AI Connection Profiles",
                            tint = IrisLight,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = activeProfileShortLabel.take(9),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = IrisLight,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // AI Engine Mode Pill (Live AI vs Instant Synth)
                Surface(
                    color = if (useMockAiMode) LuminaGold.copy(alpha = 0.14f) else LuminaCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(
                        1.dp,
                        if (useMockAiMode) LuminaGold.copy(alpha = 0.4f) else LuminaCyan.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .clickable { onToggleMockMode() }
                        .testTag("toggle_ai_mode_button")
                ) {
                    Text(
                        text = if (useMockAiMode) "SYNTH" else "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (useMockAiMode) LuminaGold else LuminaCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                // Compact Undo / Redo / Export
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(28.dp).testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) TextSecondary else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(28.dp).testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) TextSecondary else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onOpenExportImportModal,
                    modifier = Modifier.size(28.dp).testTag("export_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Import and Export CHARX, Lorebook or Cards",
                        tint = LuminaCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Live AI Reasoning & Cancel Drawer (shown whenever AI is generating)
            if (isBusy || reasoningTelemetry.isGenerating) {
                LiveAiReasoningBanner(
                    busyLabel = busyLabel,
                    telemetry = reasoningTelemetry,
                    onCancel = onCancelGeneration
                )
            }
        }
    }
}

/**
 * Interactive Live Reasoning & Cancel bar displayed whenever any AI task is running.
 * Shows elapsed seconds, live reasoning steps, streaming `<think>` / token output, and a Cancel button.
 */
@Composable
fun LiveAiReasoningBanner(
    busyLabel: String,
    telemetry: AiReasoningTelemetry,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReasoningDetails by remember { mutableStateOf(true) }
    var elapsedSeconds by remember(telemetry.startedAtEpochMs) { mutableLongStateOf(0L) }

    LaunchedEffect(telemetry.startedAtEpochMs, telemetry.isGenerating) {
        val start = if (telemetry.startedAtEpochMs > 0L) telemetry.startedAtEpochMs else System.currentTimeMillis()
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
            delay(500L)
        }
    }

    Surface(
        color = ObsidianAbyss,
        border = BorderStroke(1.dp, IrisPrimary.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                color = LuminaCyan,
                trackColor = ObsidianElevated,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "AI Reasoning Active",
                    tint = LuminaCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = telemetry.taskTitle.ifBlank { busyLabel.ifBlank { "AI Generating..." } },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${elapsedSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = LuminaGold
                        )
                    }
                    Text(
                        text = "${telemetry.activeModelBadge} • ${telemetry.steps.lastOrNull() ?: "Streaming reasoning..."}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Toggle Reasoning Stream View
                Surface(
                    color = IrisPrimary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, IrisPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clickable { showReasoningDetails = !showReasoningDetails }
                        .testTag("toggle_reasoning_view_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showReasoningDetails) "Hide Reasoning" else "View Reasoning",
                            style = MaterialTheme.typography.labelSmall,
                            color = IrisLight
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Explicit Cancel Generation Button (replaces hard timeout!)
                Surface(
                    color = RoseError.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, RoseError.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { onCancel() }
                        .testTag("cancel_ai_generation_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Cancel AI Generation",
                            tint = RoseError,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoseError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showReasoningDetails) {
                Surface(
                    color = ObsidianElevated.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .heightIn(max = 150.dp)
                ) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(telemetry.reasoningText.length, telemetry.outputPreview.length, telemetry.steps.size) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        telemetry.steps.forEach { step ->
                            Text(
                                text = "▸ $step",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = LuminaCyan
                            )
                        }
                        if (telemetry.reasoningText.isNotBlank()) {
                            Text(
                                text = "💭 Reasoning:\n${telemetry.reasoningText.takeLast(700)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = IrisLight
                            )
                        }
                        if (telemetry.outputPreview.isNotBlank()) {
                            Text(
                                text = "⚡ Live Stream (${telemetry.chunksReceived} chunks):\n${telemetry.outputPreview.takeLast(600)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact 46.dp floating animated Orbital Core button for the Live World Architect Co-Pilot.
 */
@Composable
fun AnimatedArchitectOrbButton(
    canonIssueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "architect_orb_transition")
    val outerOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_orbit"
    )
    val innerOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_orbit"
    )
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(50.dp)
            .semantics { contentDescription = "Open Live World Architect Co-Pilot" }
            .testTag("floating_architect_fab")
    ) {
        Surface(
            color = ObsidianAbyss,
            shape = CircleShape,
            border = BorderStroke(
                1.dp,
                Brush.sweepGradient(
                    listOf(
                        IrisPrimary,
                        LuminaCyan,
                        ArcaneViolet,
                        IrisPrimary
                    )
                )
            ),
            modifier = Modifier
                .size(46.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = IrisPrimary,
                    spotColor = LuminaCyan
                )
                .clip(CircleShape)
                .clickable { onClick() }
                .testTag("open_architect_copilot_button")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IrisPrimary.copy(alpha = 0.36f * corePulse.coerceAtMost(1f)),
                            ArcaneViolet.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR * 0.95f
                    ),
                    radius = maxR * 0.95f,
                    center = center
                )

                rotate(degrees = outerOrbitAngle, pivot = center) {
                    val ringR = maxR * 0.68f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                LuminaCyan.copy(alpha = 0.85f),
                                IrisLight.copy(alpha = 0.9f),
                                Color.Transparent
                            )
                        ),
                        startAngle = 15f,
                        sweepAngle = 230f,
                        useCenter = false,
                        topLeft = Offset(center.x - ringR, center.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val satAngleRad = Math.toRadians(245.0)
                    val satOffset = Offset(
                        x = center.x + (ringR * cos(satAngleRad)).toFloat(),
                        y = center.y + (ringR * sin(satAngleRad)).toFloat()
                    )
                    drawCircle(
                        color = LuminaCyan,
                        radius = 2.2.dp.toPx(),
                        center = satOffset
                    )
                }

                rotate(degrees = innerOrbitAngle, pivot = center) {
                    val innerR = maxR * 0.44f
                    drawArc(
                        color = ArcaneViolet.copy(alpha = 0.8f),
                        startAngle = 120f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - innerR, center.y - innerR),
                        size = Size(innerR * 2f, innerR * 2f),
                        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                val starRadius = (maxR * 0.28f) * corePulse
                val innerWaist = starRadius * 0.28f
                val starPath = Path().apply {
                    moveTo(center.x, center.y - starRadius)
                    quadraticTo(center.x + innerWaist, center.y - innerWaist, center.x + starRadius, center.y)
                    quadraticTo(center.x + innerWaist, center.y + innerWaist, center.x, center.y + starRadius)
                    quadraticTo(center.x - innerWaist, center.y + innerWaist, center.x - starRadius, center.y)
                    quadraticTo(center.x - innerWaist, center.y - innerWaist, center.x, center.y - starRadius)
                    close()
                }
                drawPath(
                    path = starPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, IrisLight, LuminaCyan)
                    )
                )
            }
        }

        if (canonIssueCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 1.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(AmberWarning)
            ) {
                Text(
                    text = canonIssueCount.coerceAtMost(9).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ObsidianAbyss,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
