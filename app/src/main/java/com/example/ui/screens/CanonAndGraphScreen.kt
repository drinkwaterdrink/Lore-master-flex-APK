package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CanonIssue
import com.example.model.CrossRefMention
import com.example.model.EntryTemplateType
import com.example.model.IssueSeverity
import com.example.model.UniverseProject
import com.example.ui.components.ArcaneSectionCard
import com.example.ui.components.EntityTypeBadge
import com.example.ui.components.accentColor
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IrisLight
import com.example.ui.theme.IrisPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminaCyan
import com.example.ui.theme.LuminaGold
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LoreMasterUiState
import com.example.viewmodel.LoreMasterViewModel
import com.example.viewmodel.MainWorkspaceTab
import com.example.viewmodel.StudioSubMode
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class GraphNodeData(
    val id: String,
    val label: String,
    val subtitle: String,
    val color: Color,
    val isCard: Boolean,
    val entryUid: Int? = null,
    val cardId: String? = null,
    val normalizedX: Float,
    val normalizedY: Float,
    val degree: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanonAndGraphScreen(
    uiState: LoreMasterUiState,
    viewModel: LoreMasterViewModel,
    modifier: Modifier = Modifier
) {
    val project = uiState.activeProject ?: return
    val mentions = uiState.crossRefMentions
    val issues = uiState.canonIssues
    val unlinkedMentions = mentions.filter { !it.isKeywordAlreadyLinked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Overview KPI Strip
        val entries = project.lorebook.entries
        val totalTokens = entries.sumOf { it.estimateTokens() }
        val constantTokens = entries.filter { it.constant }.sumOf { it.estimateTokens() }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatSummaryPill(
                label = "Cross-Links",
                value = "${mentions.size}",
                sub = "${unlinkedMentions.size} unlinked",
                color = IrisLight,
                modifier = Modifier.weight(1f)
            )
            StatSummaryPill(
                label = "Canon Health",
                value = if (issues.isEmpty()) "100%" else "${issues.size} Issues",
                sub = if (issues.isEmpty()) "All Clean" else "${issues.count { it.severity == IssueSeverity.CRITICAL }} Critical",
                color = if (issues.isEmpty()) EmeraldSuccess else AmberWarning,
                modifier = Modifier.weight(1f)
            )
            StatSummaryPill(
                label = "Token Budget",
                value = "$totalTokens",
                sub = "Const: $constantTokens / ${project.lorebook.tokenBudget}",
                color = LuminaCyan,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. Interactive Force-Directed Lore & Character Graph
        InteractiveLoreGraphSection(
            project = project,
            mentions = mentions,
            onInspectEntry = { uid ->
                viewModel.selectEntry(uid)
                viewModel.setStudioSubMode(StudioSubMode.LOREBOOK_ENTRIES)
                viewModel.setMainTab(MainWorkspaceTab.STUDIO)
            },
            onInspectCard = { cardId ->
                viewModel.selectCharacterCard(cardId)
                viewModel.setStudioSubMode(StudioSubMode.CHARACTER_CARDS)
                viewModel.setMainTab(MainWorkspaceTab.STUDIO)
            }
        )

        // 3. Auto-Detected Entity Cross-Reference Engine
        ArcaneSectionCard(
            title = "AI Cross-Reference & Entity Linker (${mentions.size})",
            subtitle = "Scans Lorebook entries & Character Cards for entity mentions and uncreated stubs",
            accentColor = IrisPrimary,
            trailingHeader = {
                if (unlinkedMentions.isNotEmpty()) {
                    Button(
                        onClick = viewModel::autoLinkAllCrossReferences,
                        colors = ButtonDefaults.buttonColors(containerColor = IrisPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("auto_link_all_button")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Auto-Link All", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link All (${unlinkedMentions.size})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        ) {
            if (mentions.isEmpty()) {
                Text(
                    text = "No cross-entity mentions detected yet. Add more entries or run the Forge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                mentions.take(14).forEach { mention ->
                    val isStub = mention.targetType == "UNCREATED_ENTITY"
                    Surface(
                        color = ObsidianElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isStub -> LuminaGold.copy(alpha = 0.55f)
                                mention.isKeywordAlreadyLinked -> EmeraldSuccess.copy(alpha = 0.35f)
                                else -> IrisPrimary.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${mention.sourceTitle} → ${mention.targetTitle}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Matched '${mention.matchedPhrase}': \"${mention.contextSnippet}\"",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (mention.isKeywordAlreadyLinked && !isStub) {
                                Surface(
                                    color = EmeraldSuccess.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "LINKED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.applySingleCrossRef(mention) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isStub) LuminaGold else IrisLight)
                                ) {
                                    Icon(
                                        imageVector = if (isStub) Icons.Default.AddCircleOutline else Icons.Default.Link,
                                        contentDescription = "Apply Cross-Ref",
                                        tint = if (isStub) LuminaGold else IrisLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isStub) "Spawn Stub" else "Link Key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isStub) LuminaGold else IrisLight
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Real-Time Canon Auditor & Lorebook Linter 2.0
        ArcaneSectionCard(
            title = "Real-Time Canon Auditor & Linter 2.0 (${issues.size})",
            subtitle = "Detects plot contradictions, Card ↔ Lore desync, recursion loops & broad keys",
            accentColor = if (issues.isEmpty()) EmeraldSuccess else AmberWarning,
            trailingHeader = {
                if (issues.isNotEmpty()) {
                    Button(
                        onClick = viewModel::repairAllCriticalAndMajorIssues,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("repair_all_canon_issues_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Repair All", tint = ObsidianCanvas, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Fix All", style = MaterialTheme.typography.labelSmall, color = ObsidianCanvas, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            if (issues.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldSuccess.copy(alpha = 0.14f))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Clean Canon", tint = EmeraldSuccess)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Zero canon contradictions, recursion loops, or structural lint warnings found!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldSuccess
                    )
                }
            } else {
                issues.forEach { issue ->
                    CanonIssueItemCard(
                        issue = issue,
                        onRepair = { viewModel.repairSingleCanonIssue(issue) }
                    )
                }
            }
        }

        // 5. Token Budget & Category Breakdown
        ArcaneSectionCard(
            title = "Token Budget & Category Distribution",
            subtitle = "Constant tokens vs conditional triggers across SillyTavern positions 0–4",
            accentColor = LuminaCyan,
            collapsible = true,
            initiallyExpanded = true
        ) {
            val budget = maxOf(1, project.lorebook.tokenBudget)
            val ratio = (constantTokens.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
            Text(
                text = "Constant Context Load: $constantTokens / $budget tokens (${(ratio * 100).toInt()}%) • Total Universe Tokens: $totalTokens",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ratio },
                color = if (ratio > 0.65f) AmberWarning else EmeraldSuccess,
                trackColor = ObsidianElevated,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EntryTemplateType.entries.forEach { type ->
                    val count = entries.count { it.classifyType() == type }
                    if (count > 0) {
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, type.accentColor().copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                EntityTypeBadge(type = type)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("$count", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveLoreGraphSection(
    project: UniverseProject,
    mentions: List<CrossRefMention>,
    onInspectEntry: (Int) -> Unit,
    onInspectCard: (String) -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedNode by remember { mutableStateOf<GraphNodeData?>(null) }

    val nodesAndEdges = remember(project, mentions) {
        val entries = project.lorebook.entries
        val cards = project.characterCards
        val nodeList = mutableListOf<GraphNodeData>()
        val totalEntries = maxOf(1, entries.size)

        entries.forEachIndexed { idx, entry ->
            val angle = (2.0 * Math.PI * idx) / totalEntries
            val type = entry.classifyType()
            val radius = if (entry.constant) 0.22f else 0.38f
            val nx = 0.5f + (cos(angle) * radius).toFloat()
            val ny = 0.5f + (sin(angle) * radius).toFloat()
            val deg = mentions.count {
                it.sourceUidOrId == entry.uid.toString() || it.targetUidOrId == entry.uid.toString()
            }
            nodeList.add(
                GraphNodeData(
                    id = "E_${entry.uid}",
                    label = entry.comment.substringBefore("—").removePrefix("RULE:").removePrefix("EVENT:").removePrefix("TRIGGER:").trim().take(16),
                    subtitle = "${type.label} #${entry.uid} (${entry.estimateTokens()}t)",
                    color = type.accentColor(),
                    isCard = false,
                    entryUid = entry.uid,
                    normalizedX = nx,
                    normalizedY = ny,
                    degree = deg
                )
            )
        }

        cards.forEachIndexed { idx, card ->
            val angle = (2.0 * Math.PI * idx) / maxOf(1, cards.size) + 0.4
            val nx = 0.5f + (cos(angle) * 0.14f).toFloat()
            val ny = 0.5f + (sin(angle) * 0.14f).toFloat()
            nodeList.add(
                GraphNodeData(
                    id = "C_${card.id}",
                    label = "★ ${card.name.take(14)}",
                    subtitle = "Character Card V2 • ${card.roleTitle}",
                    color = LuminaCyan,
                    isCard = true,
                    cardId = card.id,
                    normalizedX = nx,
                    normalizedY = ny,
                    degree = 3
                )
            )
        }

        val edges = mutableListOf<Pair<GraphNodeData, GraphNodeData>>()
        mentions.forEach { m ->
            val src = nodeList.find { it.entryUid?.toString() == m.sourceUidOrId || it.cardId == m.sourceUidOrId }
            val dst = nodeList.find { it.entryUid?.toString() == m.targetUidOrId || it.cardId == m.targetUidOrId }
            if (src != null && dst != null && src != dst) {
                edges.add(src to dst)
            }
        }
        cards.forEach { card ->
            if (card.linkedLoreEntryUid != null) {
                val src = nodeList.find { it.cardId == card.id }
                val dst = nodeList.find { it.entryUid == card.linkedLoreEntryUid }
                if (src != null && dst != null) edges.add(src to dst)
            }
        }
        nodeList to edges
    }

    val (nodes, edges) = nodesAndEdges

    ArcaneSectionCard(
        title = "Interactive Universe Entity Graph",
        subtitle = "Drag to pan • Tap any node to inspect links or jump to editor",
        accentColor = ArcaneViolet,
        trailingHeader = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(2.6f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.6f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        zoom = 1f
                        panOffset = Offset.Zero
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset graph view", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCanvas)
                .pointerInput(nodes, zoom, panOffset) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val center = Offset(w / 2f, h / 2f)
                        val hit = nodes.minByOrNull { node ->
                            val rawPos = Offset(node.normalizedX * w, node.normalizedY * h)
                            val screenPos = center + (rawPos - center) * zoom + panOffset
                            hypot(tapOffset.x - screenPos.x, tapOffset.y - screenPos.y)
                        }
                        if (hit != null) {
                            val rawPos = Offset(hit.normalizedX * w, hit.normalizedY * h)
                            val screenPos = center + (rawPos - center) * zoom + panOffset
                            if (hypot(tapOffset.x - screenPos.x, tapOffset.y - screenPos.y) <= 46f) {
                                selectedNode = hit
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset += dragAmount
                    }
                }
                .testTag("interactive_lore_graph_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)

                fun toScreen(node: GraphNodeData): Offset {
                    val raw = Offset(node.normalizedX * w, node.normalizedY * h)
                    return center + (raw - center) * zoom + panOffset
                }

                // Draw edges
                edges.forEach { (a, b) ->
                    val p1 = toScreen(a)
                    val p2 = toScreen(b)
                    val isHighlighted = selectedNode?.id == a.id || selectedNode?.id == b.id
                    drawLine(
                        color = if (isHighlighted) LuminaGold.copy(alpha = 0.85f) else IrisPrimary.copy(alpha = 0.32f),
                        start = p1,
                        end = p2,
                        strokeWidth = if (isHighlighted) 4f else 2f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw nodes
                nodes.forEach { node ->
                    val pos = toScreen(node)
                    val isSelected = selectedNode?.id == node.id
                    val radius = (if (node.isCard) 18f else 13f + (node.degree * 1.5f).coerceAtMost(8f)) * zoom.coerceIn(0.8f, 1.4f)

                    if (isSelected) {
                        drawCircle(
                            color = LuminaGold.copy(alpha = 0.3f),
                            radius = radius + 12f,
                            center = pos
                        )
                    }
                    drawCircle(
                        color = node.color.copy(alpha = 0.28f),
                        radius = radius + 5f,
                        center = pos
                    )
                    drawCircle(
                        color = node.color,
                        radius = radius,
                        center = pos
                    )
                }
            }
        }

        val inspected = selectedNode ?: nodes.firstOrNull()
        if (inspected != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, inspected.color.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(inspected.color)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(inspected.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(inspected.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    OutlinedButton(
                        onClick = {
                            if (inspected.isCard && inspected.cardId != null) {
                                onInspectCard(inspected.cardId)
                            } else if (inspected.entryUid != null) {
                                onInspectEntry(inspected.entryUid)
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Edit in Studio")
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonIssueItemCard(
    issue: CanonIssue,
    onRepair: () -> Unit
) {
    val sevColor = when (issue.severity) {
        IssueSeverity.CRITICAL -> RoseError
        IssueSeverity.MAJOR -> AmberWarning
        IssueSeverity.MINOR -> LuminaCyan
    }
    Surface(
        color = ObsidianElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, sevColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = sevColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${issue.severity.label.uppercase()} • ${issue.category.code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sevColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = issue.targetTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onRepair,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, sevColor)
                ) {
                    Text("Auto-Fix", style = MaterialTheme.typography.labelSmall, color = sevColor)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(issue.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
                text = "Fix: ${issue.proposedFixSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldSuccess,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun StatSummaryPill(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
