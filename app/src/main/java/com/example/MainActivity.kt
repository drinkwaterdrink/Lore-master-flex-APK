package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.data.LorebookAndCardCodec
import com.example.ui.components.AnimatedArchitectOrbButton
import com.example.ui.components.TopWorkspaceHeader
import com.example.ui.screens.ActivationSimulatorScreen
import com.example.ui.screens.CanonAndGraphScreen
import com.example.ui.screens.CanonExpanderScreen
import com.example.ui.screens.ConnectionProfilesModal
import com.example.ui.screens.ForgeWizardScreen
import com.example.ui.screens.ImportExportModalDialog
import com.example.ui.screens.LiveArchitectCoPilotDialog
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.UniverseSwitcherDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IrisLight
import com.example.ui.theme.IrisPrimary
import com.example.ui.theme.LoreMasterTheme
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.LoreMasterViewModel
import com.example.viewmodel.MainWorkspaceTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            LoreMasterTheme {
                Surface(
                    color = ObsidianCanvas,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LoreMasterFlexApp()
                }
            }
        }
    }
}

private fun MainWorkspaceTab.icon(): ImageVector = when (this) {
    MainWorkspaceTab.STUDIO -> Icons.Default.EditNote
    MainWorkspaceTab.FORGE -> Icons.Default.Build
    MainWorkspaceTab.EXPANDER -> Icons.Default.Explore
    MainWorkspaceTab.CANON_GRAPH -> Icons.Default.Hub
    MainWorkspaceTab.SIMULATOR -> Icons.Default.Terminal
}

@Composable
fun LoreMasterFlexApp(
    viewModel: LoreMasterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activeProject = uiState.activeProject

    var showUniverseDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    // BackHandler for secondary tabs so pressing Back returns to STUDIO (Codex)
    BackHandler(enabled = uiState.activeTab != MainWorkspaceTab.STUDIO) {
        viewModel.setMainTab(MainWorkspaceTab.STUDIO)
    }

    // SAF Document Creation & Import Launchers
    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    val createCharxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.importFromBytes(bytes, uri.lastPathSegment ?: "import.charx")
                }
            }
        }
    }

    val activeProfile = uiState.activeConnectionProfile
    val activeProfileShortLabel = when {
        uiState.useMockAiMode -> "MOCK"
        activeProfile != null -> activeProfile.providerType.shortTag
        else -> "GEMINI"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isExpandedScreen = maxWidth >= 720.dp

        Scaffold(
            containerColor = ObsidianCanvas,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopWorkspaceHeader(
                    universeName = activeProject?.name ?: "Loading World...",
                    universeGenre = activeProject?.genre ?: "World Studio",
                    entryCount = activeProject?.lorebook?.entries?.size ?: 0,
                    cardCount = activeProject?.characterCards?.size ?: 0,
                    canonIssueCount = uiState.canonIssues.size,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    useMockAiMode = uiState.useMockAiMode,
                    activeProfileShortLabel = activeProfileShortLabel,
                    isBusy = uiState.isBusy,
                    busyLabel = uiState.busyStepLabel,
                    reasoningTelemetry = uiState.reasoningTelemetry,
                    onOpenUniverseModal = { showUniverseDialog = true },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onToggleMockMode = viewModel::toggleMockAiMode,
                    onOpenConnectionProfiles = { viewModel.setConnectionDialogOpen(true) },
                    onOpenExportImportModal = { showImportExportDialog = true },
                    onOpenArchitectSheet = { viewModel.setArchitectOpen(true) },
                    onCancelGeneration = viewModel::cancelActiveGeneration
                )
            },
            floatingActionButton = {
                AnimatedArchitectOrbButton(
                    canonIssueCount = uiState.canonIssues.size,
                    onClick = { viewModel.setArchitectOpen(true) }
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        MainWorkspaceTab.entries.forEach { tab ->
                            val selected = uiState.activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.setMainTab(tab) },
                                icon = {
                                    if (tab == MainWorkspaceTab.CANON_GRAPH && uiState.canonIssues.isNotEmpty()) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = AmberWarning) {
                                                    Text("${uiState.canonIssues.size}")
                                                }
                                            }
                                        ) {
                                            Icon(tab.icon(), contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(tab.icon(), contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = IrisLight,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = IrisPrimary.copy(alpha = 0.22f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        MainWorkspaceTab.entries.forEach { tab ->
                            val selected = uiState.activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.setMainTab(tab) },
                                icon = { Icon(tab.icon(), contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Status / Error Toast Banner
                    AnimatedVisibility(
                        visible = uiState.statusBannerMessage != null || uiState.errorBannerMessage != null
                    ) {
                        val isError = uiState.errorBannerMessage != null
                        val bannerColor = if (isError) RoseError else EmeraldSuccess
                        val bannerText = uiState.errorBannerMessage ?: uiState.statusBannerMessage.orEmpty()
                        Surface(
                            color = ObsidianElevated,
                            border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .clickable { viewModel.dismissBanners() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = bannerText,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (uiState.activeTab) {
                            MainWorkspaceTab.STUDIO -> StudioScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            MainWorkspaceTab.FORGE -> ForgeWizardScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            MainWorkspaceTab.EXPANDER -> CanonExpanderScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            MainWorkspaceTab.CANON_GRAPH -> CanonAndGraphScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            MainWorkspaceTab.SIMULATOR -> ActivationSimulatorScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isArchitectOpen) {
        LiveArchitectCoPilotDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.setArchitectOpen(false) }
        )
    }

    if (uiState.isConnectionDialogOpen) {
        ConnectionProfilesModal(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.setConnectionDialogOpen(false) }
        )
    }

    if (showUniverseDialog || uiState.showWorldsHome) {
        UniverseSwitcherDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showUniverseDialog = false
                viewModel.setWorldsHomeOpen(false)
            }
        )
    }

    if (showImportExportDialog && activeProject != null) {
        val activeCard = uiState.activeCard
        ImportExportModalDialog(
            validationReport = viewModel.getExportValidationReport(),
            activeCardName = activeCard?.name,
            onAutoFixForExport = viewModel::autoFixForExport,
            onExportLorebookJson = {
                val jsonStr = LorebookAndCardCodec.exportLorebookV2Json(activeProject.lorebook)
                pendingExportBytes = jsonStr.toByteArray(Charsets.UTF_8)
                val safeFileName = activeProject.name.replace(" ", "_").lowercase() + "_lorebook_v2.json"
                createJsonLauncher.launch(safeFileName)
            },
            onExportCharacterCardCharx = {
                if (activeCard != null) {
                    pendingExportBytes = LorebookAndCardCodec.exportCharxArchiveBytes(activeCard, activeProject.lorebook)
                    val safeName = activeCard.name.replace(" ", "_").lowercase() + ".charx"
                    createCharxLauncher.launch(safeName)
                }
            },
            onExportCharacterCardJson = {
                if (activeCard != null) {
                    val jsonStr = LorebookAndCardCodec.exportCharacterCardV2Json(activeCard, activeProject.lorebook)
                    pendingExportBytes = jsonStr.toByteArray(Charsets.UTF_8)
                    val safeName = activeCard.name.replace(" ", "_").lowercase() + "_chara_v3.json"
                    createJsonLauncher.launch(safeName)
                }
            },
            onExportCharacterCardPng = {
                if (activeCard != null) {
                    pendingExportBytes = LorebookAndCardCodec.exportCharacterCardPngBytes(activeCard, activeProject.lorebook)
                    val safeName = activeCard.name.replace(" ", "_").lowercase() + "_card.png"
                    createPngLauncher.launch(safeName)
                }
            },
            onImportFile = {
                showImportExportDialog = false
                openDocumentLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showImportExportDialog = false }
        )
    }
}
