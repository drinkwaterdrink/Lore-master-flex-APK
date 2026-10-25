package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CardConceptType
import com.example.model.CharacterCardV2
import com.example.model.ForgeBlueprint
import com.example.model.ForgeConfig
import com.example.model.ForgeGenerationTarget
import com.example.model.ForgeModule
import com.example.model.PremiseWizardState
import com.example.model.ProceduralDensity
import com.example.model.ProceduralPersistence
import com.example.model.ProceduralSystemType
import com.example.model.ProceduralWeightMode
import com.example.model.SpeculativeIntensity
import com.example.model.SpeculativeMode
import com.example.model.WorldDnaAnalysis
import com.example.ui.components.ArcaneSectionCard
import com.example.ui.components.EntityTypeBadge
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
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LoreMasterUiState
import com.example.viewmodel.LoreMasterViewModel

@Composable
fun ForgeWizardScreen(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    modifier: Modifier = Modifier
) {
    val project = uiState.activeProject ?: return
    val stageLabels = listOf(
        "1. Seed & DNA",
        "2. Blueprint",
        "3. Batch Forge",
        "4. Compile"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 4-Stage Visual Stepper Bar
        Surface(
            color = ObsidianElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                stageLabels.forEachIndexed { idx, label ->
                    val active = uiState.forgeStage == idx
                    val completed = uiState.forgeStage > idx
                    Surface(
                        color = when {
                            active -> IrisPrimary
                            completed -> EmeraldSuccess.copy(alpha = 0.2f)
                            else -> ObsidianGlass
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                active -> IrisLight
                                completed -> EmeraldSuccess.copy(alpha = 0.6f)
                                else -> ObsidianBorder
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setForgeStage(idx) }
                            .testTag("forge_stage_tab_$idx")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) TextPrimary else if (completed) EmeraldSuccess else TextSecondary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        when (uiState.forgeStage) {
            0 -> ForgeStageOneSeedAndDna(
                rawIdeaInput = uiState.rawIdeaInput,
                onUpdateRawIdea = viewModel::updateRawIdeaInput,
                onFleshOutIdea = { viewModel.fleshOutCurrentWorldIdea(uiState.rawIdeaInput) },
                wizard = project.wizardState,
                dna = project.dnaAnalysis,
                seedMarkdown = uiState.generatedSeedMarkdown,
                premiseHistory = uiState.premiseHistory,
                onUpdateWizard = viewModel::updatePremiseWizard,
                onSynthesizeSeed = viewModel::generateScenarioSeedFromWizard,
                onAnalyzeDna = viewModel::runWorldDnaAnalysis,
                onApplyRecommendations = viewModel::applyDnaRecommendations,
                onNextStage = { viewModel.setForgeStage(1) }
            )
            1 -> ForgeStageTwoConfigAndBlueprint(
                config = project.forgeConfig,
                blueprint = project.activeBlueprint,
                onUpdateConfig = viewModel::updateForgeConfig,
                onGenerateBlueprint = viewModel::generateForgeBlueprint,
                onProceedToBatchForge = { viewModel.setForgeStage(2) }
            )
            2 -> ForgeStageThreeBatchForge(
                blueprint = project.activeBlueprint,
                progressFraction = uiState.forgeProgressFraction,
                onForgeAllBatches = viewModel::runBatchForgeAll,
                onUpdateDraftContent = viewModel::updateBlueprintDraftContent,
                onUpdatePrimaryCard = viewModel::updateBlueprintPrimaryCard,
                onGenerateBlueprintFirst = viewModel::generateForgeBlueprint,
                onProceedToReview = { viewModel.setForgeStage(3) }
            )
            else -> ForgeStageFourReviewAndCompile(
                blueprint = project.activeBlueprint,
                autoGenerateCards = project.forgeConfig.autoGenerateCharacterCards,
                onCompileIntoUniverse = viewModel::compileForgedBlueprintIntoUniverse,
                onBackToBatchForge = { viewModel.setForgeStage(2) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForgeStageOneSeedAndDna(
    rawIdeaInput: String,
    onUpdateRawIdea: (String) -> Unit,
    onFleshOutIdea: () -> Unit,
    wizard: PremiseWizardState,
    dna: WorldDnaAnalysis?,
    seedMarkdown: String,
    premiseHistory: List<com.example.model.PremiseSnapshot>,
    onUpdateWizard: (PremiseWizardState) -> Unit,
    onSynthesizeSeed: () -> Unit,
    onAnalyzeDna: () -> Unit,
    onApplyRecommendations: () -> Unit,
    onNextStage: () -> Unit
) {
    // Top Quick AI Idea Flesher Card
    ArcaneSectionCard(
        title = "Idea Spark → AI World Flesher",
        subtitle = "Drop in a raw idea or tap an ideation spark—AI will flesh out all 8 Premise Pillars & World DNA",
        accentColor = LuminaCyan
    ) {
        val quickSparks = listOf(
            "Arcane Noir" to "A rain-slicked clockwork metropolis where memories are bottled as legal tender and private investigators hunt memory-forgers.",
            "Starfall Sanctum" to "An orbital monastic archive circling a dying white dwarf where rival houses duel with relic-code.",
            "Hollow Crown" to "A gothic plague-quarantined kingdom where immortal regents lease their royal bloodline to mercenary knights."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quickSparks.forEach { (label, sparkText) ->
                FilterChip(
                    selected = rawIdeaInput == sparkText,
                    onClick = { onUpdateRawIdea(sparkText) },
                    label = { Text("✨ $label") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LuminaCyan.copy(alpha = 0.22f),
                        selectedLabelColor = LuminaCyan
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = rawIdeaInput,
            onValueChange = onUpdateRawIdea,
            label = { Text("Your Raw World Idea or Plot Hook") },
            placeholder = {
                Text("e.g., A subterranean railway connecting last surviving sanctuary cities where conductors bargain with tunnel leviathans...")
            },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forge_raw_idea_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onFleshOutIdea,
            enabled = rawIdeaInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = LuminaCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flesh_out_idea_button")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ObsidianAbyss, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Flesh Out Idea into Full World Premise & DNA",
                color = ObsidianAbyss,
                fontWeight = FontWeight.Bold
            )
        }
    }

    ArcaneSectionCard(
        title = "Stage 1: Premise Pillars & Realism Gate",
        subtitle = "Customize every world pillar or switch between Detailed & Scenario mode",
        accentColor = IrisPrimary,
        trailingHeader = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (wizard.isDetailedMode) "Detailed" else "Scenario",
                    style = MaterialTheme.typography.labelSmall,
                    color = IrisLight
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = wizard.isDetailedMode,
                    onCheckedChange = { onUpdateWizard(wizard.copy(isDetailedMode = it)) }
                )
            }
        }
    ) {
        if (wizard.isDetailedMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = wizard.genre,
                    onValueChange = { onUpdateWizard(wizard.copy(genre = it)) },
                    label = { Text("Primary Genre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = wizard.subgenre,
                    onValueChange = { onUpdateWizard(wizard.copy(subgenre = it)) },
                    label = { Text("Subgenre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = wizard.era,
                    onValueChange = { onUpdateWizard(wizard.copy(era = it)) },
                    label = { Text("Era / Setting") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = wizard.vibes,
                    onValueChange = { onUpdateWizard(wizard.copy(vibes = it)) },
                    label = { Text("Atmosphere & Vibes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wizard.conflict,
                onValueChange = { onUpdateWizard(wizard.copy(conflict = it)) },
                label = { Text("Central Factional / Narrative Conflict") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wizard.strangeFactor,
                onValueChange = { onUpdateWizard(wizard.copy(strangeFactor = it)) },
                label = { Text("The Strange Factor / Unique Hook") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wizard.protagonist,
                onValueChange = { onUpdateWizard(wizard.copy(protagonist = it)) },
                label = { Text("Player / {{user}} Role") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wizard.hardRules,
                onValueChange = { onUpdateWizard(wizard.copy(hardRules = it)) },
                label = { Text("Hard Non-Negotiable World Rules") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = wizard.mustInclude,
                    onValueChange = { onUpdateWizard(wizard.copy(mustInclude = it)) },
                    label = { Text("Must Include") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = wizard.avoid,
                    onValueChange = { onUpdateWizard(wizard.copy(avoid = it)) },
                    label = { Text("Strictly Avoid") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            OutlinedTextField(
                value = wizard.freeformScenario,
                onValueChange = { onUpdateWizard(wizard.copy(freeformScenario = it)) },
                label = { Text("Freeform Scenario Premise") },
                placeholder = { Text("Describe your fictional universe, factions, characters, and core mystery...") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Speculative Realism Gate (FlowRow so chips never conflict with horizontal scroll)
        Text(
            text = "Speculative Realism Gate (${wizard.speculativeMode.label}):",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SpeculativeMode.entries.forEach { mode ->
                val isSelected = wizard.speculativeMode == mode
                Surface(
                    color = if (isSelected) IrisPrimary.copy(alpha = 0.25f) else ObsidianElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, if (isSelected) IrisLight else ObsidianBorder),
                    modifier = Modifier
                        .clickable { onUpdateWizard(wizard.copy(speculativeMode = mode)) }
                        .testTag("speculative_mode_${mode.name}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = IrisLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (wizard.speculativeMode == SpeculativeMode.SPECULATIVE) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Speculative Intensity (${wizard.speculativeIntensity.label}):",
                style = MaterialTheme.typography.labelMedium,
                color = LuminaCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SpeculativeIntensity.entries.forEach { intensity ->
                    val isSelected = wizard.speculativeIntensity == intensity
                    Surface(
                        color = if (isSelected) LuminaCyan.copy(alpha = 0.22f) else ObsidianElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, if (isSelected) LuminaCyan else ObsidianBorder),
                        modifier = Modifier
                            .clickable { onUpdateWizard(wizard.copy(speculativeIntensity = intensity)) }
                            .testTag("speculative_intensity_${intensity.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = LuminaCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = intensity.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) LuminaCyan else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSynthesizeSeed,
                colors = ButtonDefaults.buttonColors(containerColor = ArcaneViolet),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("synthesize_seed_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Synthesize Seed", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Scenario Seed")
            }
            Button(
                onClick = onAnalyzeDna,
                colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("extract_world_dna_button")
            ) {
                Icon(Icons.Default.Science, contentDescription = "Analyze DNA", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Extract World DNA")
            }
        }

        if (premiseHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Icon(Icons.Default.History, contentDescription = "History", tint = TextMuted, modifier = Modifier.size(16.dp))
                premiseHistory.forEach { snap ->
                    FilterChip(
                        selected = false,
                        onClick = { onUpdateWizard(snap.wizardState) },
                        label = { Text(snap.title) }
                    )
                }
            }
        }

        if (seedMarkdown.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ArcaneViolet.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = seedMarkdown,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = TextPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    // World DNA Analysis Card
    if (dna != null) {
        ArcaneSectionCard(
            title = "World DNA & Vibe Matrix",
            subtitle = dna.logline,
            accentColor = LuminaGold
        ) {
            val metrics = listOf(
                "Darkness" to dna.darkness,
                "Complexity" to dna.complexity,
                "Mystery" to dna.mystery,
                "Danger" to dna.danger,
                "Originality" to dna.originality,
                "Conflict Density" to dna.conflictDensity
            )
            metrics.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    pair.forEach { (name, score) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("$score%", style = MaterialTheme.typography.labelSmall, color = LuminaGold)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { score / 100f },
                                color = if (score >= 75) IrisPrimary else LuminaCyan,
                                trackColor = ObsidianElevated,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Extracted Core Entities (${dna.extractedEntities.size}):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            dna.extractedEntities.forEach { ent ->
                Surface(
                    color = ObsidianElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        EntityTypeBadge(type = ent.type)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${ent.name} (${ent.importance})",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = ent.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            if (dna.missingPillars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Detected World-Building Gaps:", style = MaterialTheme.typography.labelLarge, color = AmberWarning)
                dna.missingPillars.forEach { gap ->
                    Text("• $gap", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApplyRecommendations,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("apply_dna_recommendations_button")
                ) {
                    Text("Apply AI Recommendations & Continue →")
                }
                OutlinedButton(
                    onClick = onNextStage,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Skip to Stage 2")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForgeStageTwoConfigAndBlueprint(
    config: ForgeConfig,
    blueprint: ForgeBlueprint?,
    onUpdateConfig: (ForgeConfig) -> Unit,
    onGenerateBlueprint: () -> Unit,
    onProceedToBatchForge: () -> Unit
) {
    ArcaneSectionCard(
        title = "Stage 2: Target Mode, Concept Type & World Scale",
        subtitle = "Choose Lorebook, Character Card, or Card + Lorebook, pick your Roleplay Concept (Types A–E), and set 10–250 entries & NPC count",
        accentColor = LuminaCyan
    ) {
        // 1. Generation Target Mode
        Text(
            text = "What are we forging?",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ForgeGenerationTarget.entries.forEach { target ->
                val selected = config.generationTarget == target
                Surface(
                    color = if (selected) IrisPrimary.copy(alpha = 0.24f) else ObsidianElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, if (selected) IrisLight else ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdateConfig(config.copy(generationTarget = target))
                        }
                        .testTag("gen_target_${target.name}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Hub,
                            contentDescription = null,
                            tint = if (selected) IrisLight else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                text = target.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // 2. Roleplay & Card Concept Types (Types A–E) when generating a Character Card or Card + Lorebook
        if (config.generationTarget.includesCard) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Roleplay & Card Concept Type (${config.cardConceptType.shortCode}):",
                style = MaterialTheme.typography.labelLarge,
                color = LuminaCyan,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardConceptType.entries.forEach { concept ->
                    val selected = config.cardConceptType == concept
                    Surface(
                        color = if (selected) LuminaCyan.copy(alpha = 0.20f) else ObsidianElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, if (selected) LuminaCyan else ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateConfig(config.copy(cardConceptType = concept))
                            }
                            .testTag("card_concept_${concept.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (selected) LuminaCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = concept.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) LuminaCyan else TextPrimary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = concept.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Lorebook Entry Count (10–250 Entries, No Token Cap) & NPC Count in Lorebook
        if (config.generationTarget.includesLorebook) {
            Spacer(modifier = Modifier.height(14.dp))
            val safeEntryCount = config.targetEntryCount.coerceIn(10, 250)
            var entryInputText by remember(safeEntryCount) { mutableStateOf(safeEntryCount.toString()) }

            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lorebook Entry Count (10–250 Entries)",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "No Context Token Cap — entries trigger dynamically via keywords",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = entryInputText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                entryInputText = digits
                                digits.toIntOrNull()?.let { parsed ->
                                    val clamped = parsed.coerceIn(10, 250)
                                    onUpdateConfig(
                                        config.copy(
                                            targetEntryCount = clamped,
                                            lorebookNpcCount = config.lorebookNpcCount.coerceAtMost(clamped)
                                        )
                                    )
                                }
                            },
                            label = { Text("Entries") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .width(92.dp)
                                .testTag("lorebook_entry_count_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = safeEntryCount.toFloat(),
                        onValueChange = { v ->
                            val nextCount = v.toInt().coerceIn(10, 250)
                            entryInputText = nextCount.toString()
                            onUpdateConfig(
                                config.copy(
                                    targetEntryCount = nextCount,
                                    lorebookNpcCount = config.lorebookNpcCount.coerceAtMost(nextCount)
                                )
                            )
                        },
                        valueRange = 10f..250f,
                        colors = SliderDefaults.colors(thumbColor = IrisLight, activeTrackColor = IrisPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("entry_count_slider")
                    )

                    // Quick Steppers + Presets for 10..250 entries
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(-10 to "-10", -1 to "-1", 1 to "+1", 10 to "+10").forEach { (delta, label) ->
                            OutlinedButton(
                                onClick = {
                                    val next = (safeEntryCount + delta).coerceIn(10, 250)
                                    entryInputText = next.toString()
                                    onUpdateConfig(
                                        config.copy(
                                            targetEntryCount = next,
                                            lorebookNpcCount = config.lorebookNpcCount.coerceAtMost(next)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        listOf(10, 25, 50, 100, 150, 250).forEach { presetCount ->
                            FilterChip(
                                selected = safeEntryCount == presetCount,
                                onClick = {
                                    entryInputText = presetCount.toString()
                                    onUpdateConfig(
                                        config.copy(
                                            targetEntryCount = presetCount,
                                            lorebookNpcCount = config.lorebookNpcCount.coerceAtMost(presetCount)
                                        )
                                    )
                                },
                                label = { Text("$presetCount") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IrisPrimary.copy(alpha = 0.25f),
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Number of NPCs in the Lorebook
                    val maxNpcs = safeEntryCount.coerceAtMost(120)
                    val safeNpcCount = config.lorebookNpcCount.coerceIn(0, maxNpcs)
                    var npcInputText by remember(safeNpcCount) { mutableStateOf(safeNpcCount.toString()) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NPCs inside Lorebook ($safeNpcCount NPC entries)",
                                style = MaterialTheme.typography.labelLarge,
                                color = LuminaGold,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Generates individual NPC entries inside the Lorebook triggered by character names & roles",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = npcInputText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                npcInputText = digits
                                digits.toIntOrNull()?.let { parsed ->
                                    val clamped = parsed.coerceIn(0, maxNpcs)
                                    onUpdateConfig(config.copy(lorebookNpcCount = clamped))
                                }
                            },
                            label = { Text("NPCs") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .width(92.dp)
                                .testTag("lorebook_npc_count_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = safeNpcCount.toFloat(),
                        onValueChange = { v ->
                            val nextNpc = v.toInt().coerceIn(0, maxNpcs)
                            npcInputText = nextNpc.toString()
                            onUpdateConfig(config.copy(lorebookNpcCount = nextNpc))
                        },
                        valueRange = 0f..maxNpcs.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = LuminaGold, activeTrackColor = LuminaGold),
                        modifier = Modifier.fillMaxWidth().testTag("npc_count_slider")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(-5 to "-5", -1 to "-1", 1 to "+1", 5 to "+5").forEach { (delta, label) ->
                            OutlinedButton(
                                onClick = {
                                    val next = (safeNpcCount + delta).coerceIn(0, maxNpcs)
                                    npcInputText = next.toString()
                                    onUpdateConfig(config.copy(lorebookNpcCount = next))
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        listOf(0, 4, 8, 12, 20, 35, 50).filter { it <= maxNpcs }.forEach { presetNpc ->
                            FilterChip(
                                selected = safeNpcCount == presetNpc,
                                onClick = {
                                    npcInputText = presetNpc.toString()
                                    onUpdateConfig(config.copy(lorebookNpcCount = presetNpc))
                                },
                                label = { Text("$presetNpc NPCs") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuminaGold.copy(alpha = 0.22f),
                                    selectedLabelColor = LuminaGold
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 15 World-Building Modules
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "15 World-Building Modules (${config.enabledModules.size} active):",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "All 15",
                    style = MaterialTheme.typography.labelSmall,
                    color = IrisLight,
                    modifier = Modifier.clickable {
                        onUpdateConfig(config.copy(enabledModules = ForgeModule.entries.toSet()))
                    }
                )
                Text(
                    text = "Core 5",
                    style = MaterialTheme.typography.labelSmall,
                    color = LuminaCyan,
                    modifier = Modifier.clickable {
                        onUpdateConfig(
                            config.copy(
                                enabledModules = setOf(
                                    ForgeModule.FOUNDATION,
                                    ForgeModule.CHARACTERS,
                                    ForgeModule.LOCATIONS,
                                    ForgeModule.FACTIONS,
                                    ForgeModule.DYNAMICS
                                )
                            )
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ForgeModule.entries.forEach { mod ->
                val active = mod in config.enabledModules
                FilterChip(
                    selected = active,
                    onClick = {
                        val next = if (active && config.enabledModules.size > 1) {
                            config.enabledModules - mod
                        } else {
                            config.enabledModules + mod
                        }
                        onUpdateConfig(config.copy(enabledModules = next))
                    },
                    label = { Text(mod.label) },
                    leadingIcon = if (active) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = IrisLight,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IrisPrimary.copy(alpha = 0.25f),
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 8 Procedural Event Systems
        Text(
            text = "8 Procedural Event Systems (${config.enabledGenerators.size} active):",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProceduralSystemType.entries.forEach { proc ->
                val active = proc in config.enabledGenerators
                FilterChip(
                    selected = active,
                    onClick = {
                        val next = if (active) config.enabledGenerators - proc else config.enabledGenerators + proc
                        onUpdateConfig(config.copy(enabledGenerators = next))
                    },
                    label = { Text(proc.title) },
                    leadingIcon = if (active) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = LuminaCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LuminaCyan.copy(alpha = 0.22f),
                        selectedLabelColor = LuminaCyan
                    )
                )
            }
        }

        // Procedural System Tuning Controls (Randomness, Content Density, Persistence, Weight Distribution)
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = ObsidianElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Procedural Randomness Level:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("${config.randomnessLevel}%", style = MaterialTheme.typography.labelMedium, color = LuminaGold, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = config.randomnessLevel.toFloat(),
                    onValueChange = { v -> onUpdateConfig(config.copy(randomnessLevel = v.toInt())) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = LuminaGold, activeTrackColor = LuminaGold),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Procedural Content Density:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProceduralDensity.entries.forEach { density ->
                        FilterChip(
                            selected = config.contentDensity == density,
                            onClick = { onUpdateConfig(config.copy(contentDensity = density)) },
                            label = { Text(density.label) }
                        )
                    }
                }

                Text("Event Persistence Mode:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProceduralPersistence.entries.forEach { persistence ->
                        FilterChip(
                            selected = config.persistenceMode == persistence,
                            onClick = { onUpdateConfig(config.copy(persistenceMode = persistence)) },
                            label = { Text(persistence.label) }
                        )
                    }
                }

                Text("Outcome Weight Distribution:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProceduralWeightMode.entries.forEach { weightMode ->
                        FilterChip(
                            selected = config.weightDistribution == weightMode,
                            onClick = { onUpdateConfig(config.copy(weightDistribution = weightMode)) },
                            label = { Text(weightMode.label) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 9-Axis Tone Sliders
        Text(
            text = "9-Axis Tone Matrix:",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        config.toneAxes.entries.forEach { (axisName, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$axisName: $value%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.width(115.dp)
                )
                Slider(
                    value = value.toFloat(),
                    onValueChange = { v ->
                        val nextMap = config.toneAxes.toMutableMap()
                        nextMap[axisName] = v.toInt().coerceIn(0, 100)
                        onUpdateConfig(config.copy(toneAxes = nextMap))
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = LuminaCyan, activeTrackColor = LuminaCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tone_slider_${axisName.lowercase()}")
                )
            }
        }

        if (config.generationTarget.includesLorebook) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdateConfig(config.copy(autoGenerateCharacterCards = !config.autoGenerateCharacterCards)) }
            ) {
                Checkbox(
                    checked = config.autoGenerateCharacterCards,
                    onCheckedChange = { onUpdateConfig(config.copy(autoGenerateCharacterCards = it)) }
                )
                Text(
                    text = "Also generate standalone CHARX / CCV3 Character Cards for Lorebook NPCs marked as Card Candidates",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onGenerateBlueprint,
            colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("generate_blueprint_button")
        ) {
            Icon(Icons.Default.Hub, contentDescription = "Generate Blueprint")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when (config.generationTarget) {
                    ForgeGenerationTarget.LOREBOOK_ONLY ->
                        "Architect Lorebook Blueprint (${config.targetEntryCount} Entries, ${config.lorebookNpcCount} NPCs) →"
                    ForgeGenerationTarget.CHARACTER_CARD_ONLY ->
                        "Architect ${config.cardConceptType.shortCode} Character Card →"
                    ForgeGenerationTarget.CARD_WITH_LOREBOOK ->
                        "Architect ${config.cardConceptType.shortCode} Card + ${config.targetEntryCount}-Entry Lorebook →"
                }
            )
        }

        if (blueprint != null && (blueprint.plannedEntries.isNotEmpty() || blueprint.plannedPrimaryCard != null)) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onProceedToBatchForge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Proceed to Stage 3 with Existing Blueprint (${blueprint.plannedEntries.size} entries" +
                        (if (blueprint.plannedPrimaryCard != null) " + 1 Primary Card)" else ")")
                )
            }
        }
    }
}

@Composable
private fun ForgeStageThreeBatchForge(
    blueprint: ForgeBlueprint?,
    progressFraction: Float,
    onForgeAllBatches: () -> Unit,
    onUpdateDraftContent: (String, String) -> Unit,
    onUpdatePrimaryCard: (CharacterCardV2) -> Unit,
    onGenerateBlueprintFirst: () -> Unit,
    onProceedToReview: () -> Unit
) {
    if (blueprint == null || (blueprint.plannedEntries.isEmpty() && blueprint.plannedPrimaryCard == null)) {
        ArcaneSectionCard(
            title = "Stage 3: Co-Pilot Batch Forge",
            subtitle = "No active Blueprint yet — generate a structural Blueprint first",
            accentColor = AmberWarning
        ) {
            Button(
                onClick = onGenerateBlueprintFirst,
                colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Blueprint Now")
            }
        }
        return
    }

    val forgedCount = blueprint.plannedEntries.count { it.isForged || it.forgedContent.isNotBlank() }
    val readyToProceed = forgedCount > 0 || blueprint.plannedPrimaryCard != null

    // Primary Concept Card Editor when generating a Character Card or Card + Attached Lorebook
    blueprint.plannedPrimaryCard?.let { primaryCard ->
        ArcaneSectionCard(
            title = "Forged Primary Card: ${primaryCard.name} (${blueprint.cardConceptType.title})",
            subtitle = blueprint.cardConceptType.description,
            accentColor = LuminaCyan
        ) {
            OutlinedTextField(
                value = primaryCard.name,
                onValueChange = { onUpdatePrimaryCard(primaryCard.copy(name = it)) },
                label = { Text("Card / World / Engine Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = primaryCard.description,
                onValueChange = { onUpdatePrimaryCard(primaryCard.copy(description = it)) },
                label = { Text("Description & Bracketed World/Character Rules") },
                minLines = 3,
                maxLines = 7,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = primaryCard.scenario,
                onValueChange = { onUpdatePrimaryCard(primaryCard.copy(scenario = it)) },
                label = { Text("Scenario & Active State") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = primaryCard.firstMes,
                onValueChange = { onUpdatePrimaryCard(primaryCard.copy(firstMes = it)) },
                label = { Text("Opening Greeting (first_mes)") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    ArcaneSectionCard(
        title = if (blueprint.plannedEntries.isNotEmpty()) {
            "Stage 3: Co-Pilot Batch Forge ($forgedCount/${blueprint.plannedEntries.size} Lore Entries Forged)"
        } else {
            "Stage 3: Character Card Review & Refinement"
        },
        subtitle = blueprint.linkingStrategySummary,
        accentColor = ArcaneViolet
    ) {
        if (progressFraction > 0f && progressFraction < 1f) {
            LinearProgressIndicator(
                progress = { progressFraction },
                color = ArcaneViolet,
                trackColor = ObsidianElevated,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (blueprint.plannedEntries.isNotEmpty()) {
                Button(
                    onClick = onForgeAllBatches,
                    colors = ButtonDefaults.buttonColors(containerColor = ArcaneViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("forge_all_batches_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Forge All")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Forge All ${blueprint.plannedEntries.size} Entries")
                }
            }
            if (readyToProceed) {
                Button(
                    onClick = onProceedToReview,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("proceed_to_compile_button")
                ) {
                    Text("Stage 4 →")
                }
            }
        }

        if (blueprint.plannedEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            blueprint.plannedEntries.forEach { plan ->
                Surface(
                    color = ObsidianElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (plan.isForged) EmeraldSuccess.copy(alpha = 0.5f) else ObsidianBorder
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                EntityTypeBadge(type = plan.templateType)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = plan.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                            }
                            if (plan.isCharacterCardCandidate) {
                                Surface(
                                    color = LuminaCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "NPC Lore",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LuminaCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keys: ${plan.primaryKeys.joinToString(", ")} • Cross-Links: ${plan.shouldMentionEntities.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = IrisLight
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = plan.forgedContent.ifBlank { plan.briefDirective },
                            onValueChange = { onUpdateDraftContent(plan.id, it) },
                            label = { Text(if (plan.isForged) "Forged Bracketed Content (Editable)" else "Blueprint Directive (Click 'Forge All Entries')") },
                            minLines = 3,
                            maxLines = 6,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgeStageFourReviewAndCompile(
    blueprint: ForgeBlueprint?,
    autoGenerateCards: Boolean,
    onCompileIntoUniverse: () -> Unit,
    onBackToBatchForge: () -> Unit
) {
    if (blueprint == null) {
        ArcaneSectionCard(
            title = "Stage 4: Canon Review & Compile",
            subtitle = "No forged drafts available yet",
            accentColor = AmberWarning
        ) {
            OutlinedButton(onClick = onBackToBatchForge) { Text("Return to Stage 3") }
        }
        return
    }

    val primaryCardCount = if (blueprint.plannedPrimaryCard != null) 1 else 0
    val npcCardCount = if (autoGenerateCards && blueprint.generationTarget.includesLorebook) {
        blueprint.plannedEntries.count { it.isCharacterCardCandidate }
    } else 0
    val totalCardCount = primaryCardCount + npcCardCount

    ArcaneSectionCard(
        title = "Stage 4: Ready to Compile into Active Universe",
        subtitle = "Mode: ${blueprint.generationTarget.title} • Concept: ${blueprint.cardConceptType.title}",
        accentColor = EmeraldSuccess
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${blueprint.plannedEntries.size}", style = MaterialTheme.typography.headlineMedium, color = IrisLight)
                    Text("Lorebook V2/V3 Entries", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("$totalCardCount", style = MaterialTheme.typography.headlineMedium, color = LuminaCyan)
                    Text("CHARX / CCV3 Cards", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "NENS v3.0 naming verified • Keyword-triggered Lorebook (no token cap) • Bidirectional CHARX ↔ Lorebook links ready.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onCompileIntoUniverse,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("compile_blueprint_into_universe_button")
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = "Compile")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compile ${blueprint.plannedEntries.size} Lore Entries & $totalCardCount CHARX Cards into Studio")
        }
    }
}
