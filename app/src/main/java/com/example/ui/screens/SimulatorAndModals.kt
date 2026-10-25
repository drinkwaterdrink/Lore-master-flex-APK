package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.ExportValidationReport
import com.example.model.AiProviderType
import com.example.model.ArchitectProposal
import com.example.model.ConnectionProfile
import com.example.ui.components.ArcaneSectionCard
import com.example.ui.components.EntityTypeBadge
import com.example.ui.components.TokenMeterChip
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IrisLight
import com.example.ui.theme.IrisPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminaCyan
import com.example.ui.theme.LuminaGold
import com.example.ui.theme.ObsidianAbyss
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LoreMasterUiState
import com.example.viewmodel.LoreMasterViewModel

/**
 * PRD §8: Existing Lorebook Canon Profile & Gap Expander Screen.
 * Extracts the world's Canon Profile (tone, naming style, formatting pattern, rules, factions,
 * structural gaps) and generates canon-constrained expansion entries with Grounding Score % badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanonExpanderScreen(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    modifier: Modifier = Modifier
) {
    val activeProject = uiState.activeProject ?: return
    val profile = uiState.canonProfile

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ArcaneSectionCard(
            title = "Canon Profile & Gap Expander",
            subtitle = "Analyze '${activeProject.name}' (${activeProject.lorebook.entries.size} entries) to detect world-building gaps and forge canon-grounded additions",
            accentColor = LuminaCyan
        ) {
            Text(
                text = "Unlike random generation, the Canon Expander inspects your existing entries' tone, naming conventions, rules, and formatting (`[ key(value); ]` & NENS v3.0) so new additions fit seamlessly without breaking continuity.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = viewModel::analyzeCanonProfile,
                colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyze_canon_profile_button")
            ) {
                Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (profile == null) "Extract Canon Profile & Find World Gaps"
                    else "Re-Scan Canon Profile & Refresh Suggestions"
                )
            }
        }

        if (profile != null) {
            // Extracted Canon DNA Fingerprints
            ArcaneSectionCard(
                title = "Extracted Canon Fingerprint",
                subtitle = "Detected voice, formatting conventions, and structural pillars",
                accentColor = ArcaneViolet
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanonFingerprintRow(label = "Tone Signature", value = profile.toneSummary)
                    CanonFingerprintRow(label = "Naming Style", value = profile.namingStyle)
                    CanonFingerprintRow(label = "Formatting Pattern", value = profile.formattingPattern)

                    if (profile.establishedRules.isNotEmpty()) {
                        Text("Established World Laws:", style = MaterialTheme.typography.labelMedium, color = LuminaGold)
                        profile.establishedRules.forEach { rule ->
                            Text("• $rule", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }

                    if (profile.detectedGaps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Detected Structural Gaps:", style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                        profile.detectedGaps.forEach { gap ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text("△ ", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                                Text(gap, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Canon-Constrained Suggestions
            val selectedCount = profile.suggestions.count { it.selected }
            ArcaneSectionCard(
                title = "Canon-Grounded Expansion Proposals (${profile.suggestions.size})",
                subtitle = "Select proposals to compile directly into your Lorebook V2 Codex",
                accentColor = EmeraldSuccess
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    profile.suggestions.forEach { sugg ->
                        Surface(
                            color = if (sugg.selected) IrisPrimary.copy(alpha = 0.1f) else ObsidianElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (sugg.selected) IrisPrimary.copy(alpha = 0.6f) else ObsidianBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleCanonSuggestion(sugg.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = sugg.selected,
                                        onCheckedChange = { viewModel.toggleCanonSuggestion(sugg.id) },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    EntityTypeBadge(type = sugg.category)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = sugg.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = EmeraldSuccess.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${sugg.groundingScore}% Grounded",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldSuccess,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = sugg.rationale,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    sugg.primaryKeys.forEach { k ->
                                        Surface(
                                            color = ObsidianCard,
                                            shape = RoundedCornerShape(5.dp),
                                            border = BorderStroke(1.dp, ObsidianBorder)
                                        ) {
                                            Text(
                                                text = "key: $k",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IrisLight,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    sugg.secondaryKeys.forEach { sk ->
                                        Surface(
                                            color = ObsidianCard,
                                            shape = RoundedCornerShape(5.dp),
                                            border = BorderStroke(1.dp, ObsidianBorder)
                                        ) {
                                            Text(
                                                text = "link: $sk",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LuminaCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (sugg.draftContent.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = ObsidianAbyss,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, ObsidianBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = sugg.draftContent,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = TextPrimary,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = viewModel::applySelectedCanonSuggestions,
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apply_canon_suggestions_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianAbyss)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add $selectedCount Selected Canon Entries to Codex",
                            color = ObsidianAbyss,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonFingerprintRow(label: String, value: String) {
    Surface(
        color = ObsidianElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelMedium,
                color = IrisLight,
                modifier = Modifier.width(125.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ActivationSimulatorScreen(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    modifier: Modifier = Modifier
) {
    val result = uiState.simulationResult

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ArcaneSectionCard(
            title = "SillyTavern V2 Recursive Trigger Simulator",
            subtitle = "Test how chat messages trigger Depth 0 and recursive Depth 1–5 Lorebook chains",
            accentColor = LuminaCyan
        ) {
            Text("Quick Sample Prompts:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "Sunken Ward & Contraband" to "Seraphina Vance steps into the Sunken Ward carrying a contraband chime as the Eclipse Bell tolls.",
                    "Uncork Memory Vial" to "Inside the Velvet Exchange, Caelum Rook threatens to uncork vial of stolen amber filament.",
                    "Inquisitor Sweep" to "A curfew siren wails outside the High Spire as an inquisitor patrol unleashes brass hounds."
                ).forEach { (label, prompt) ->
                    FilterChip(
                        selected = uiState.simulatorInput == prompt,
                        onClick = {
                            viewModel.updateSimulatorInput(prompt)
                            viewModel.runActivationSimulation()
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.simulatorInput,
                onValueChange = viewModel::updateSimulatorInput,
                label = { Text("Sample Roleplay Turn / Chat Message") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("simulator_chat_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Max Recursion Depth: ${uiState.simulatorMaxDepth}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.width(150.dp)
                )
                Slider(
                    value = uiState.simulatorMaxDepth.toFloat(),
                    onValueChange = { viewModel.updateSimulatorMaxDepth(it.toInt()) },
                    valueRange = 0f..5f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = LuminaCyan, activeTrackColor = LuminaCyan),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = viewModel::runActivationSimulation,
                colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("run_simulation_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Simulate")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulate Keyword & Recursive Activation")
            }
        }

        if (result != null) {
            val totalInjected = result.totalConstantTokens + result.totalTriggeredTokens
            ArcaneSectionCard(
                title = "Activation Cascade (${result.activatedSteps.size} Triggered + ${result.constantEntries.size} Constants)",
                subtitle = "Total Context Injected: $totalInjected / ${result.tokenBudget} tokens",
                accentColor = EmeraldSuccess,
                trailingHeader = {
                    TokenMeterChip(tokens = totalInjected, budget = result.tokenBudget)
                }
            ) {
                Surface(
                    color = ObsidianElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.simulatedRoleplayReply,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (result.constantEntries.isNotEmpty()) {
                    Text(
                        text = "Always-Active Constant Entries (${result.constantEntries.size} • ${result.totalConstantTokens}t):",
                        style = MaterialTheme.typography.labelLarge,
                        color = LuminaGold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    result.constantEntries.forEach { entry ->
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, LuminaGold.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                EntityTypeBadge(type = entry.classifyType())
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Pos ${entry.position} • ${entry.estimateTokens()}t",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LuminaGold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Keyword & Recursive Activation Chain (${result.activatedSteps.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    color = LuminaCyan
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (result.activatedSteps.isEmpty()) {
                    Text(
                        text = "No conditional entries matched this message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    result.activatedSteps.forEach { step ->
                        val depthColor = when (step.depthLevel) {
                            0 -> EmeraldSuccess
                            1 -> LuminaCyan
                            else -> ArcaneViolet
                        }
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, depthColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (step.depthLevel * 14).coerceAtMost(42).dp, bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = depthColor.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (step.depthLevel == 0) "DEPTH 0 (DIRECT)" else "DEPTH ${step.depthLevel} (RECURSIVE)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = depthColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        EntityTypeBadge(type = step.entry.classifyType())
                                    }
                                    Text(
                                        text = "+${step.entry.estimateTokens()}t (Σ ${step.cumulativeTokens}t)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = step.entry.comment,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Matched Key: \"${step.matchedKeyword}\" ← via ${step.triggeredBySource}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = depthColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveArchitectCoPilotDialog(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    onDismiss: () -> Unit
) {
    var inputPrompt by remember { mutableStateOf("") }
    val activeProject = uiState.activeProject

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(640.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IrisPrimary.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = IrisLight, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Live World Architect",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${activeProject?.name ?: "World"} • ${uiState.activeEntry?.comment?.take(24) ?: "Full Universe Context"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Architect Co-Pilot", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "Promote NPC to Character Card",
                        "Audit for plot holes & link universe",
                        "Forge a rival faction entry",
                        "Enrich selected entry & fix keys"
                    ).forEach { quickAction ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.sendArchitectPrompt(quickAction) },
                            label = { Text(quickAction, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    uiState.architectMessages.forEach { msg ->
                        Surface(
                            color = if (msg.isUser) IrisPrimary.copy(alpha = 0.16f) else ObsidianCard,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (msg.isUser) IrisPrimary.copy(alpha = 0.5f) else ObsidianBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (msg.isUser) "YOU" else "WORLD ARCHITECT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.isUser) IrisLight else LuminaCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )

                                if (msg.proposals.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    msg.proposals.forEach { proposal ->
                                        ArchitectProposalCard(
                                            proposal = proposal,
                                            onApply = { viewModel.applyArchitectProposal(msg.id, proposal) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = { Text("Ask Architect to edit, suggest, link, or forge...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("architect_prompt_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (inputPrompt.isNotBlank()) {
                                viewModel.sendArchitectPrompt(inputPrompt)
                                inputPrompt = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp).testTag("send_architect_prompt_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchitectProposalCard(
    proposal: ArchitectProposal,
    onApply: () -> Unit
) {
    Surface(
        color = ObsidianElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (proposal.isApplied) EmeraldSuccess else ObsidianBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = proposal.actionType.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = IrisLight
                )
                if (proposal.isApplied) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("APPLIED", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(proposal.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(proposal.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            if (proposal.afterPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = proposal.afterPreview,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianCanvas)
                        .padding(8.dp)
                )
            }

            if (!proposal.isApplied) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Change to World")
                }
            }
        }
    }
}

/**
 * Redesigned Worlds Home & New World Creation Studio.
 * Gives the user 3 clear, uncluttered modes:
 * 1. "+ New World" — Choose between:
 *    - Start Completely Blank (0 entries, 0 cards, custom name & genre)
 *    - Start from an Idea Spark (enter a raw idea -> AI fleshes out the 8 Premise Pillars, World DNA & Forge Blueprint)
 * 2. "Your Worlds" — Switch between saved worlds or import a file.
 * 3. "World Settings" — Edit the active world's Name, Genre, and Tone.
 */
@Composable
fun UniverseSwitcherDialog(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    onDismiss: () -> Unit
) {
    val active = uiState.activeProject
    var modalTab by remember { mutableIntStateOf(0) } // 0 = New World, 1 = Switch Worlds, 2 = World Settings
    var newWorldMode by remember { mutableIntStateOf(0) } // 0 = Blank World, 1 = AI Idea Spark

    // Blank World inputs
    var blankName by remember { mutableStateOf("") }
    var blankGenre by remember { mutableStateOf("") }
    var blankDesc by remember { mutableStateOf("") }

    // Idea Spark inputs
    var sparkWorldName by remember { mutableStateOf("") }
    var sparkIdeaText by remember { mutableStateOf("") }

    // Active World Settings inputs
    var editName by remember(active) { mutableStateOf(active?.name.orEmpty()) }
    var editGenre by remember(active) { mutableStateOf(active?.genre.orEmpty()) }
    var editTone by remember(active) { mutableStateOf(active?.tone.orEmpty()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(IrisPrimary.copy(alpha = 0.18f))
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = IrisLight, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Worlds Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Start a blank world, flesh out an idea with AI, or switch worlds",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Worlds Studio", tint = TextSecondary)
                    }
                }

                // Segmented Mode Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0 to "+ New World",
                        1 to "Your Worlds (${uiState.projects.size})",
                        2 to "World Settings"
                    ).forEach { (idx, label) ->
                        val selected = modalTab == idx
                        Surface(
                            color = if (selected) IrisPrimary.copy(alpha = 0.22f) else ObsidianElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selected) IrisPrimary else ObsidianBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { modalTab = idx }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp)
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

                when (modalTab) {
                    0 -> {
                        // + NEW WORLD TAB: Blank vs AI Idea Spark
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Card Option 0: Start Completely Blank
                            Surface(
                                color = if (newWorldMode == 0) IrisPrimary.copy(alpha = 0.14f) else ObsidianCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (newWorldMode == 0) IrisPrimary else ObsidianBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { newWorldMode = 0 }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NoteAdd,
                                            contentDescription = null,
                                            tint = if (newWorldMode == 0) IrisLight else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Blank World",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Start from a 100% clean slate (0 entries, 0 cards) and build at your own pace.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Card Option 1: Flesh Out an Idea Spark with AI
                            Surface(
                                color = if (newWorldMode == 1) LuminaCyan.copy(alpha = 0.14f) else ObsidianCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (newWorldMode == 1) LuminaCyan else ObsidianBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { newWorldMode = 1 }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (newWorldMode == 1) LuminaCyan else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "AI Idea Spark",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Type a raw concept and let the AI flesh out the premise, DNA & lore blueprint.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        if (newWorldMode == 0) {
                            // START COMPLETELY BLANK FORM
                            Surface(
                                color = ObsidianCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Text(
                                        text = "Create a Blank World",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    OutlinedTextField(
                                        value = blankName,
                                        onValueChange = { blankName = it },
                                        label = { Text("World Name") },
                                        placeholder = { Text("e.g., Aethelgard, Neon Kowloon, My Custom World") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("blank_world_name_input")
                                    )
                                    OutlinedTextField(
                                        value = blankGenre,
                                        onValueChange = { blankGenre = it },
                                        label = { Text("Genre / Setting (Optional)") },
                                        placeholder = { Text("e.g., Dark Fantasy, Cyberpunk Noir, Slice of Life") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = blankDesc,
                                        onValueChange = { blankDesc = it },
                                        label = { Text("Core Premise / Notes (Optional)") },
                                        placeholder = { Text("Optional summary or leave blank to fill in later...") },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.createBlankUniverse(
                                                name = blankName.ifBlank { "New Blank World" },
                                                genre = blankGenre.ifBlank { "Custom Universe" },
                                                description = blankDesc
                                            )
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("create_new_universe_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Blank World (Start Empty)")
                                    }
                                }
                            }
                        } else {
                            // START FROM AN IDEA SPARK (AI FLESHER)
                            Surface(
                                color = ObsidianCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Text(
                                        text = "Flesh Out a World from Your Idea",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Describe any rough concept, setting, or plot hook. The AI Wizard will expand it into structured World DNA, rules, factions, and an editable Lorebook blueprint.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    OutlinedTextField(
                                        value = sparkWorldName,
                                        onValueChange = { sparkWorldName = it },
                                        label = { Text("World Name (or leave blank for AI to name)") },
                                        placeholder = { Text("e.g., The Glass Archipelago") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = sparkIdeaText,
                                        onValueChange = { sparkIdeaText = it },
                                        label = { Text("Your World Idea / Concept Spark") },
                                        placeholder = {
                                            Text("e.g., A floating sky-rail empire where starlight is refined into fuel, and outlawed navigators can fold space using ancient songs...")
                                        },
                                        minLines = 3,
                                        maxLines = 6,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("idea_spark_input")
                                    )

                                    // Optional sample sparks for inspiration
                                    Text(
                                        text = "Need inspiration? Tap a sample spark:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        listOf(
                                            "Astral Train Heist" to "An armored trans-dimensional locomotive crossing a dead god's spine while rival syndicates hunt a passenger carrying a living star.",
                                            "Sunken Alchemy Venice" to "A bioluminescent lagoon city sinking into an abyssal trench where alchemists trade bottled emotions and debt-bound automata.",
                                            "Cyber-Occult Tokyo" to "Mega-corporations bind ancientkami inside quantum server shrines while street exorcists hack cursed neural implants."
                                        ).forEach { (title, spark) ->
                                            FilterChip(
                                                selected = sparkIdeaText == spark,
                                                onClick = {
                                                    if (sparkWorldName.isBlank()) sparkWorldName = title
                                                    sparkIdeaText = spark
                                                },
                                                label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.createUniverseFromIdeaSpark(
                                                worldName = sparkWorldName,
                                                rawIdea = sparkIdeaText.ifBlank {
                                                    "A mysterious realm where ancient relics and rival factions collide."
                                                }
                                            )
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LuminaCyan),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("create_idea_spark_universe_button")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ObsidianAbyss)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Create & Flesh Out World with AI",
                                            color = ObsidianAbyss,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // SWITCH / MANAGE EXISTING WORLDS
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.projects.forEach { proj ->
                                val isSelected = proj.id == uiState.activeProjectId
                                Surface(
                                    color = if (isSelected) IrisPrimary.copy(alpha = 0.16f) else ObsidianCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isSelected) IrisPrimary else ObsidianBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectProject(proj.id)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = proj.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = TextPrimary
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = EmeraldSuccess.copy(alpha = 0.18f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = EmeraldSuccess,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${proj.genre} • ${proj.lorebook.entries.size} Lore Entries • ${proj.characterCards.size} Character Cards",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted
                                            )
                                        }
                                        if (uiState.projects.size > 1) {
                                            IconButton(
                                                onClick = { viewModel.deleteProject(proj.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete world",
                                                    tint = RoseError,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // EDIT ACTIVE WORLD METADATA
                        Surface(
                            color = ObsidianCard,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "Edit Active World Metadata",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("World Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editGenre,
                                    onValueChange = { editGenre = it },
                                    label = { Text("Genre") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editTone,
                                    onValueChange = { editTone = it },
                                    label = { Text("Atmosphere & Tone") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        viewModel.renameActiveProject(editName, editGenre, editTone)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save World Metadata")
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
fun ImportExportModalDialog(
    validationReport: ExportValidationReport?,
    activeCardName: String?,
    onAutoFixForExport: () -> Unit,
    onExportLorebookJson: () -> Unit,
    onExportCharacterCardCharx: () -> Unit = {},
    onExportCharacterCardJson: () -> Unit,
    onExportCharacterCardPng: () -> Unit,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Lumiverse .CHARX & SillyTavern V2 Import / Export") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (validationReport != null) {
                    Surface(
                        color = ObsidianElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (validationReport.isValid) EmeraldSuccess else AmberWarning
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (validationReport.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (validationReport.isValid) EmeraldSuccess else AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (validationReport.isValid) "Export Ready (${validationReport.totalEntries} entries, ~${validationReport.estimatedTokens} tokens)"
                                    else "${validationReport.blockingErrors.size} Blocking Issues Found",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (validationReport.isValid) EmeraldSuccess else AmberWarning
                                )
                            }
                            validationReport.blockingErrors.take(3).forEach { err ->
                                Text("• $err", style = MaterialTheme.typography.bodySmall, color = RoseError)
                            }
                            validationReport.warnings.take(3).forEach { warn ->
                                Text("• $warn", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                            }
                            if (!validationReport.isValid || validationReport.warnings.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = onAutoFixForExport,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Auto-Fix All Export Issues")
                                }
                            }
                        }
                    }
                }

                if (activeCardName != null) {
                    Button(
                        onClick = onExportCharacterCardCharx,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        modifier = Modifier.fillMaxWidth().testTag("export_card_charx_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = ObsidianAbyss)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export '$activeCardName' Lumiverse Bundle (.charx)",
                            color = ObsidianAbyss,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onExportLorebookJson,
                    colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("export_lorebook_json_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Lorebook V2 (.json)")
                }

                if (activeCardName != null) {
                    Button(
                        onClick = onExportCharacterCardJson,
                        colors = ButtonDefaults.buttonColors(containerColor = LuminaCyan),
                        modifier = Modifier.fillMaxWidth().testTag("export_card_json_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = ObsidianCanvas)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export '$activeCardName' CCV3/V2 (.json)",
                            color = ObsidianCanvas,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onExportCharacterCardPng,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcaneViolet),
                        modifier = Modifier.fillMaxWidth().testTag("export_card_png_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export '$activeCardName' Portrait (.png + ccv3/chara)")
                    }
                }

                OutlinedButton(
                    onClick = onImportFile,
                    modifier = Modifier.fillMaxWidth().testTag("import_file_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import .charx, .png, .json, or RisuAI Polyglot")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

/**
 * Connection Profiles & BYOK Settings Dialog.
 * Supports Built-in Gemini, OpenRouter, NanoGPT, Google AI Studio, and Custom OpenAI-Compatible endpoints.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionProfilesModal(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    onDismiss: () -> Unit
) {
    val activeProfile = uiState.activeConnectionProfile
    var editingProfile by remember(activeProfile.id) { mutableStateOf(activeProfile) }
    var showApiKey by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Connection Profiles & BYOK",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Use Built-in Gemini or bring your own key for OpenRouter, NanoGPT, AI Studio & custom APIs",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Saved Profiles Selector
                Text("Saved Profiles (${uiState.connectionProfiles.size}):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.connectionProfiles.forEach { prof ->
                        val isSelected = prof.id == editingProfile.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                editingProfile = prof
                                viewModel.selectConnectionProfile(prof.id)
                            },
                            label = { Text(prof.name) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val newProf = ConnectionProfile(
                                name = "Custom Profile ${uiState.connectionProfiles.size + 1}",
                                providerType = AiProviderType.OPENROUTER,
                                baseUrl = AiProviderType.OPENROUTER.defaultBaseUrl,
                                modelId = AiProviderType.OPENROUTER.defaultModel
                            )
                            editingProfile = newProf
                            viewModel.saveConnectionProfile(newProf)
                        },
                        label = { Text("+ New Profile") }
                    )
                }

                // Provider Type Selector
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text("Provider Type:", style = MaterialTheme.typography.labelMedium, color = IrisLight)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AiProviderType.entries.forEach { pType ->
                                val selected = editingProfile.providerType == pType
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        editingProfile = editingProfile.copy(
                                            providerType = pType,
                                            baseUrl = pType.defaultBaseUrl,
                                            modelId = pType.defaultModel
                                        )
                                    },
                                    label = { Text(pType.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = editingProfile.name,
                            onValueChange = { editingProfile = editingProfile.copy(name = it) },
                            label = { Text("Profile Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (editingProfile.providerType != AiProviderType.BUILT_IN_GEMINI) {
                            OutlinedTextField(
                                value = editingProfile.baseUrl,
                                onValueChange = { editingProfile = editingProfile.copy(baseUrl = it) },
                                label = { Text("API Base URL") },
                                placeholder = { Text(editingProfile.providerType.defaultBaseUrl) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editingProfile.apiKey,
                                    onValueChange = { editingProfile = editingProfile.copy(apiKey = it) },
                                    label = { Text("${editingProfile.providerType.label} API Key (Stored Locally)") },
                                    placeholder = { Text("sk-or-v1-... or NanoGPT key") },
                                    singleLine = true,
                                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f).testTag("byok_api_key_input")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                    Text(if (showApiKey) "Hide" else "Show")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editingProfile.modelId,
                            onValueChange = { editingProfile = editingProfile.copy(modelId = it) },
                            label = { Text("Model ID") },
                            placeholder = { Text(editingProfile.providerType.defaultModel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("byok_model_id_input")
                        )

                        // Quick Model Presets for OpenRouter & NanoGPT
                        val modelPresets = when (editingProfile.providerType) {
                            AiProviderType.OPENROUTER -> listOf(
                                "deepseek/deepseek-r1",
                                "anthropic/claude-3.7-sonnet",
                                "google/gemini-2.5-pro-preview",
                                "qwen/qwq-32b"
                            )
                            AiProviderType.NANOGPT -> listOf(
                                "chatgpt-4o-latest",
                                "deepseek-r1",
                                "claude-3-7-sonnet-20250219",
                                "gemini-2.5-pro"
                            )
                            AiProviderType.GOOGLE_AI_STUDIO -> listOf(
                                "gemini-2.5-pro",
                                "gemini-2.5-flash"
                            )
                            else -> listOf("gemini-3.8-flash")
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            modelPresets.forEach { presetModel ->
                                FilterChip(
                                    selected = editingProfile.modelId == presetModel,
                                    onClick = { editingProfile = editingProfile.copy(modelId = presetModel) },
                                    label = { Text(presetModel, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Temperature: ${"%.2f".format(editingProfile.temperature)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.width(125.dp)
                            )
                            Slider(
                                value = editingProfile.temperature,
                                onValueChange = { editingProfile = editingProfile.copy(temperature = it) },
                                valueRange = 0.1f..1.5f,
                                colors = SliderDefaults.colors(thumbColor = LuminaCyan, activeTrackColor = LuminaCyan),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = editingProfile.includeReasoning,
                                onCheckedChange = { editingProfile = editingProfile.copy(includeReasoning = it) }
                            )
                            Text(
                                text = "Request & display live reasoning / <think> tokens during generation",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        if (!uiState.connectionTestStatus.isNullOrBlank()) {
                            Surface(
                                color = ObsidianElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.connectionTestStatus,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = TextPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { viewModel.testConnectionProfile(editingProfile) },
                                modifier = Modifier.weight(1f).testTag("test_connection_profile_button")
                            ) {
                                Text("Test Connection")
                            }
                            Button(
                                onClick = {
                                    viewModel.saveConnectionProfile(editingProfile)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                modifier = Modifier.weight(1f).testTag("save_connection_profile_button")
                            ) {
                                Text("Save & Activate", color = ObsidianAbyss, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

