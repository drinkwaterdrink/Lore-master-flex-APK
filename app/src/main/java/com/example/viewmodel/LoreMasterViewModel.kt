package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiReasoningTelemetry
import com.example.ai.GeminiLoreService
import com.example.data.ExportValidationReport
import com.example.data.LorebookAndCardCodec
import com.example.data.UniverseDatabase
import com.example.data.UniverseRepository
import com.example.domain.ActivationSimulatorEngine
import com.example.domain.CrossRefAndAuditEngine
import com.example.model.AiProviderType
import com.example.model.ArchitectActionType
import com.example.model.ArchitectMessage
import com.example.model.ArchitectProposal
import com.example.model.BlueprintEntryPlan
import com.example.model.CanonIssue
import com.example.model.CanonProfile
import com.example.model.CharacterCardV2
import com.example.model.ConnectionProfile
import com.example.model.CrossRefMention
import com.example.model.EntryTemplateType
import com.example.model.EventOutcomeItem
import com.example.model.ForgeBlueprint
import com.example.model.ForgeConfig
import com.example.model.DreamWeaverBook
import com.example.model.IssueCategory
import com.example.model.IssueSeverity
import com.example.model.PremiseSnapshot
import com.example.model.PremiseWizardState
import com.example.model.STEntry
import com.example.model.STLorebookRoot
import com.example.model.SimulationResult
import com.example.model.UniverseProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class MainWorkspaceTab(val label: String) {
    STUDIO("Codex"),
    FORGE("AI Forge"),
    EXPANDER("Expander"),
    CANON_GRAPH("Graph & Audit"),
    SIMULATOR("Simulator")
}

enum class StudioSubMode(val label: String) {
    LOREBOOK_ENTRIES("Lorebook V2"),
    CHARACTER_CARDS("CHARX & Cards V3")
}

data class LoreMasterUiState(
    val projects: List<UniverseProject> = emptyList(),
    val activeProjectId: String = "universe-vaelis-synod",
    val activeTab: MainWorkspaceTab = MainWorkspaceTab.STUDIO,
    val studioSubMode: StudioSubMode = StudioSubMode.LOREBOOK_ENTRIES,
    val selectedEntryUid: Int? = null,
    val selectedCardId: String? = null,
    // Master-Detail Full-Screen Editor Navigation & Worlds Home Launchpad
    val showWorldsHome: Boolean = false,
    val isEditingEntryFullScreen: Boolean = false,
    val isEditingCardFullScreen: Boolean = false,
    val multiSelectedEntryUids: Set<Int> = emptySet(),
    val searchQuery: String = "",
    val selectedGroupFilter: String = "ALL",
    val selectedDreamWeaverBook: DreamWeaverBook = DreamWeaverBook.ALL,
    // AI / Mock Mode, Connection Profiles & Live Reasoning Telemetry
    val useMockAiMode: Boolean = false,
    val connectionProfiles: List<ConnectionProfile> = emptyList(),
    val activeProfileId: String = "builtin-gemini-default",
    val isConnectionDialogOpen: Boolean = false,
    val connectionTestStatus: String? = null,
    val reasoningTelemetry: AiReasoningTelemetry = AiReasoningTelemetry(),
    val isBusy: Boolean = false,
    val busyStepLabel: String = "",
    val statusBannerMessage: String? = null,
    val errorBannerMessage: String? = null,
    // Entry AI Enhancement Diff Preview
    val pendingEnhancedEntry: STEntry? = null,
    // 4-Stage Hybrid Forge State (0 = Seed & DNA, 1 = Blueprint, 2 = Batch Forge, 3 = Audit & Compile)
    val forgeStage: Int = 0,
    val rawIdeaInput: String = "",
    val premiseHistory: List<PremiseSnapshot> = emptyList(),
    val generatedSeedMarkdown: String = "",
    val forgeProgressFraction: Float = 0f,
    // PRD §8 Existing Lorebook Expander State
    val canonProfile: CanonProfile? = null,
    // Cross-Ref & Canon Auditor computed state
    val crossRefMentions: List<CrossRefMention> = emptyList(),
    val canonIssues: List<CanonIssue> = emptyList(),
    // Activation Simulator state
    val simulatorInput: String = "Seraphina Vance steps into the Sunken Ward carrying a contraband chime as the Eclipse Bell tolls.",
    val simulatorMaxDepth: Int = 3,
    val simulationResult: SimulationResult? = null,
    // Live Architect Co-Pilot Sheet
    val isArchitectOpen: Boolean = false,
    val architectMessages: List<ArchitectMessage> = listOf(
        ArchitectMessage(
            isUser = false,
            text = "I am your Live World Architect. I have full context of your active Lorebook V2 entries, Lumiverse .charx / CCV3 cards, and cross-references. Ask me to flesh out ideas, audit canon, promote characters, or tune triggers."
        )
    ),
    // Undo / Redo availability
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val activeProject: UniverseProject?
        get() = projects.find { it.id == activeProjectId } ?: projects.firstOrNull()

    val activeEntry: STEntry?
        get() = activeProject?.lorebook?.entries?.find { it.uid == selectedEntryUid }
            ?: activeProject?.lorebook?.entries?.firstOrNull()

    val activeCard: CharacterCardV2?
        get() = activeProject?.characterCards?.find { it.id == selectedCardId }
            ?: activeProject?.characterCards?.firstOrNull()

    val activeConnectionProfile: ConnectionProfile
        get() = connectionProfiles.find { it.id == activeProfileId }
            ?: connectionProfiles.firstOrNull()
            ?: ConnectionProfile()
}

class LoreMasterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UniverseRepository(
        UniverseDatabase.getInstance(application).universeDao()
    )
    private val prefs = application.getSharedPreferences("loremaster_profiles_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(LoreMasterUiState())
    val uiState: StateFlow<LoreMasterUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<UniverseProject>()
    private val redoStack = ArrayDeque<UniverseProject>()

    private var activeAiJob: Job? = null
    private var debouncedSaveJob: Job? = null
    private var debouncedAuditJob: Job? = null
    @Volatile
    private var lastLocalMutationEpochMs: Long = 0L

    init {
        loadSavedConnectionProfiles()

        // Forward live reasoning telemetry from GeminiLoreService to UI state
        viewModelScope.launch {
            GeminiLoreService.reasoningTelemetry.collect { telemetry ->
                _uiState.update { it.copy(reasoningTelemetry = telemetry) }
            }
        }

        viewModelScope.launch {
            repository.ensureSeededIfEmpty()
            repository.allProjectsFlow.collect { loadedProjects ->
                if (loadedProjects.isNotEmpty()) {
                    val elapsedSinceLocalEdit = System.currentTimeMillis() - lastLocalMutationEpochMs
                    _uiState.update { current ->
                        // If the user is actively typing or dragging a slider (< 600ms ago) and projects are already loaded,
                        // preserve the in-memory activeProject so asynchronous SQLite emissions never scramble characters!
                        val inMemoryActive = current.projects.find { it.id == current.activeProjectId }
                        val mergedProjects = if (elapsedSinceLocalEdit < 600L && inMemoryActive != null) {
                            loadedProjects.map { dbProj ->
                                if (dbProj.id == inMemoryActive.id) inMemoryActive else dbProj
                            }
                        } else {
                            loadedProjects
                        }
                        val active = mergedProjects.find { it.id == current.activeProjectId } ?: mergedProjects.first()
                        val selEntryUid = current.selectedEntryUid?.takeIf { uid -> active.lorebook.entries.any { it.uid == uid } }
                            ?: active.lorebook.entries.firstOrNull()?.uid
                        val selCardId = current.selectedCardId?.takeIf { cid -> active.characterCards.any { it.id == cid } }
                            ?: active.characterCards.firstOrNull()?.id
                        val mentions = if (current.crossRefMentions.isEmpty()) {
                            CrossRefAndAuditEngine.scanCrossReferences(active)
                        } else current.crossRefMentions
                        val issues = if (current.canonIssues.isEmpty()) {
                            CrossRefAndAuditEngine.runCanonAudit(active)
                        } else current.canonIssues
                        val simResult = current.simulationResult ?: ActivationSimulatorEngine.simulateActivation(
                            active,
                            current.simulatorInput,
                            current.simulatorMaxDepth
                        )
                        current.copy(
                            projects = mergedProjects,
                            activeProjectId = active.id,
                            selectedEntryUid = selEntryUid,
                            selectedCardId = selCardId,
                            crossRefMentions = mentions,
                            canonIssues = issues,
                            simulationResult = simResult,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty()
                        )
                    }
                }
            }
        }
    }

    /**
     * Synchronously updates `_uiState` on the main thread for zero-latency typing, slider dragging,
     * and chip selection, then persists to Room in the background with debouncing.
     */
    private fun commitProjectMutation(
        mutated: UniverseProject,
        toastMessage: String? = null,
        recordUndo: Boolean = true,
        immediatePersist: Boolean = false
    ) {
        lastLocalMutationEpochMs = System.currentTimeMillis()
        val currentActive = _uiState.value.activeProject
        if (recordUndo && currentActive != null) {
            if (undoStack.size >= 50) undoStack.removeFirst()
            undoStack.addLast(currentActive)
            redoStack.clear()
        }

        // 1. Synchronous in-memory state update so UI never drops keystrokes or slider/chip selections
        _uiState.update { current ->
            val exists = current.projects.any { it.id == mutated.id }
            val updatedProjects = if (exists) {
                current.projects.map { if (it.id == mutated.id) mutated else it }
            } else {
                listOf(mutated) + current.projects
            }
            current.copy(
                projects = updatedProjects,
                statusBannerMessage = toastMessage ?: current.statusBannerMessage,
                errorBannerMessage = if (toastMessage != null) null else current.errorBannerMessage,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }

        // 2. Debounced background Room write
        debouncedSaveJob?.cancel()
        debouncedSaveJob = viewModelScope.launch {
            if (!immediatePersist) {
                delay(180L)
            }
            repository.saveProject(mutated)
        }

        // 3. Debounced Cross-Ref & Canon Audit refresh
        debouncedAuditJob?.cancel()
        debouncedAuditJob = viewModelScope.launch {
            delay(320L)
            val mentions = CrossRefAndAuditEngine.scanCrossReferences(mutated)
            val issues = CrossRefAndAuditEngine.runCanonAudit(mutated)
            val sim = ActivationSimulatorEngine.simulateActivation(
                mutated,
                _uiState.value.simulatorInput,
                _uiState.value.simulatorMaxDepth
            )
            _uiState.update {
                it.copy(
                    crossRefMentions = mentions,
                    canonIssues = issues,
                    simulationResult = sim
                )
            }
        }
    }

    /**
     * Cancels any active AI generation immediately when the user clicks the Cancel button.
     */
    fun cancelActiveGeneration() {
        activeAiJob?.cancel()
        activeAiJob = null
        GeminiLoreService.endTelemetry()
        _uiState.update {
            it.copy(
                isBusy = false,
                busyStepLabel = "",
                statusBannerMessage = "AI generation cancelled by user."
            )
        }
    }

    // --- Connection Profiles (BYOK for OpenRouter, NanoGPT, Google AI Studio, Custom OpenAI) ---
    private fun defaultConnectionProfiles(): List<ConnectionProfile> = listOf(
        ConnectionProfile(
            id = "builtin-gemini-default",
            name = "Built-in Gemini 2.5 Flash",
            providerType = AiProviderType.BUILT_IN_GEMINI,
            baseUrl = "",
            apiKey = "",
            modelId = "gemini-3.8-flash",
            temperature = 0.75f,
            maxTokens = 4096,
            includeReasoning = true
        ),
        ConnectionProfile(
            id = "openrouter-preset",
            name = "OpenRouter (Claude / DeepSeek R1)",
            providerType = AiProviderType.OPENROUTER,
            baseUrl = AiProviderType.OPENROUTER.defaultBaseUrl,
            apiKey = "",
            modelId = "deepseek/deepseek-r1",
            temperature = 0.7f,
            maxTokens = 16384,
            includeReasoning = true
        ),
        ConnectionProfile(
            id = "nanogpt-preset",
            name = "NanoGPT (BYOK All-Models)",
            providerType = AiProviderType.NANOGPT,
            baseUrl = AiProviderType.NANOGPT.defaultBaseUrl,
            apiKey = "",
            modelId = "deepseek-r1",
            temperature = 0.75f,
            maxTokens = 16384,
            includeReasoning = true
        ),
        ConnectionProfile(
            id = "aistudio-preset",
            name = "Google AI Studio (Direct Key)",
            providerType = AiProviderType.GOOGLE_AI_STUDIO,
            baseUrl = AiProviderType.GOOGLE_AI_STUDIO.defaultBaseUrl,
            apiKey = "",
            modelId = "gemini-2.5-pro",
            temperature = 0.7f,
            maxTokens = 8192,
            includeReasoning = true
        )
    )

    private fun loadSavedConnectionProfiles() {
        val rawJson = prefs.getString("profiles_json", null)
        val savedActiveId = prefs.getString("active_profile_id", "builtin-gemini-default") ?: "builtin-gemini-default"
        val loaded = mutableListOf<ConnectionProfile>()
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pType = runCatching {
                        AiProviderType.valueOf(o.optString("providerType", AiProviderType.BUILT_IN_GEMINI.name))
                    }.getOrDefault(AiProviderType.BUILT_IN_GEMINI)
                    loaded.add(
                        ConnectionProfile(
                            id = o.optString("id", "profile-$i"),
                            name = o.optString("name", pType.label),
                            providerType = pType,
                            baseUrl = o.optString("baseUrl", pType.defaultBaseUrl),
                            apiKey = o.optString("apiKey", ""),
                            modelId = o.optString("modelId", pType.defaultModel),
                            temperature = o.optDouble("temperature", 0.75).toFloat(),
                            maxTokens = o.optInt("maxTokens", 4096),
                            includeReasoning = o.optBoolean("includeReasoning", true)
                        )
                    )
                }
            }
        }
        val finalProfiles = if (loaded.isEmpty()) defaultConnectionProfiles() else loaded
        val validActiveId = finalProfiles.find { it.id == savedActiveId }?.id ?: finalProfiles.first().id
        val activeProf = finalProfiles.find { it.id == validActiveId } ?: finalProfiles.first()
        GeminiLoreService.activeProfile = activeProf
        _uiState.update {
            it.copy(
                connectionProfiles = finalProfiles,
                activeProfileId = validActiveId
            )
        }
    }

    private fun persistConnectionProfiles(profiles: List<ConnectionProfile>, activeId: String) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("providerType", p.providerType.name)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", p.apiKey)
                    put("modelId", p.modelId)
                    put("temperature", p.temperature.toDouble())
                    put("maxTokens", p.maxTokens)
                    put("includeReasoning", p.includeReasoning)
                }
            )
        }
        prefs.edit()
            .putString("profiles_json", arr.toString())
            .putString("active_profile_id", activeId)
            .apply()
    }

    fun setConnectionDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isConnectionDialogOpen = open, connectionTestStatus = null) }
    }

    fun selectConnectionProfile(profileId: String) {
        val profiles = _uiState.value.connectionProfiles
        val chosen = profiles.find { it.id == profileId } ?: return
        GeminiLoreService.activeProfile = chosen
        persistConnectionProfiles(profiles, chosen.id)
        _uiState.update {
            it.copy(
                activeProfileId = chosen.id,
                useMockAiMode = false,
                statusBannerMessage = "Switched AI Profile to '${chosen.name}' (${chosen.modelId.ifBlank { chosen.providerType.defaultModel }})"
            )
        }
    }

    fun saveConnectionProfile(profile: ConnectionProfile) {
        val current = _uiState.value.connectionProfiles.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            current[idx] = profile
        } else {
            current.add(profile)
        }
        GeminiLoreService.activeProfile = profile
        persistConnectionProfiles(current, profile.id)
        _uiState.update {
            it.copy(
                connectionProfiles = current,
                activeProfileId = profile.id,
                useMockAiMode = false,
                statusBannerMessage = "Saved & activated profile '${profile.name}'"
            )
        }
    }

    fun deleteConnectionProfile(profileId: String) {
        if (profileId == "builtin-gemini-default") {
            _uiState.update { it.copy(errorBannerMessage = "Cannot delete the default Built-in Gemini profile.") }
            return
        }
        val remaining = _uiState.value.connectionProfiles.filterNot { it.id == profileId }
            .ifEmpty { defaultConnectionProfiles() }
        val nextActive = remaining.first()
        GeminiLoreService.activeProfile = nextActive
        persistConnectionProfiles(remaining, nextActive.id)
        _uiState.update {
            it.copy(
                connectionProfiles = remaining,
                activeProfileId = nextActive.id,
                statusBannerMessage = "Deleted connection profile"
            )
        }
    }

    fun testConnectionProfile(profile: ConnectionProfile) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    busyStepLabel = "Testing ${profile.name}...",
                    connectionTestStatus = "Testing connection to ${profile.providerType.label}..."
                )
            }
            val res = GeminiLoreService.testConnectionProfile(profile)
            res.fold(
                onSuccess = { msg ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            connectionTestStatus = "✅ $msg"
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            connectionTestStatus = "❌ ${err.localizedMessage ?: "Connection failed"}"
                        )
                    }
                }
            )
        }
    }

    fun undo() {
        val current = _uiState.value.activeProject ?: return
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        commitProjectMutation(previous, "Undid last change", recordUndo = false, immediatePersist = true)
    }

    fun redo() {
        val current = _uiState.value.activeProject ?: return
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        commitProjectMutation(next, "Redid change", recordUndo = false, immediatePersist = true)
    }

    fun dismissBanners() {
        _uiState.update { it.copy(statusBannerMessage = null, errorBannerMessage = null) }
    }

    fun setMainTab(tab: MainWorkspaceTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                isEditingEntryFullScreen = false,
                isEditingCardFullScreen = false
            )
        }
    }

    fun setWorldsHomeOpen(open: Boolean) {
        _uiState.update { it.copy(showWorldsHome = open) }
    }

    fun openEntryFullScreenEditor(uid: Int) {
        _uiState.update {
            it.copy(
                selectedEntryUid = uid,
                isEditingEntryFullScreen = true,
                isEditingCardFullScreen = false,
                pendingEnhancedEntry = null
            )
        }
    }

    fun closeEntryFullScreenEditor() {
        _uiState.update { it.copy(isEditingEntryFullScreen = false) }
    }

    fun openCardFullScreenEditor(cardId: String) {
        _uiState.update {
            it.copy(
                selectedCardId = cardId,
                isEditingCardFullScreen = true,
                isEditingEntryFullScreen = false
            )
        }
    }

    fun closeCardFullScreenEditor() {
        _uiState.update { it.copy(isEditingCardFullScreen = false) }
    }

    fun setStudioSubMode(mode: StudioSubMode) {
        _uiState.update {
            it.copy(
                studioSubMode = mode,
                isEditingEntryFullScreen = false,
                isEditingCardFullScreen = false
            )
        }
    }

    fun toggleMockAiMode() {
        _uiState.update {
            val next = !it.useMockAiMode
            it.copy(
                useMockAiMode = next,
                statusBannerMessage = if (next) "Switched to Instant Offline Synthesis Mode" else "Switched to Live AI Mode (${it.activeConnectionProfile.name})"
            )
        }
    }

    fun setArchitectOpen(open: Boolean) {
        _uiState.update { it.copy(isArchitectOpen = open) }
    }

    // --- Universe Project Management ---
    fun selectProject(projectId: String) {
        undoStack.clear()
        redoStack.clear()
        val target = _uiState.value.projects.find { it.id == projectId } ?: return
        val mentions = CrossRefAndAuditEngine.scanCrossReferences(target)
        val issues = CrossRefAndAuditEngine.runCanonAudit(target)
        val sim = ActivationSimulatorEngine.simulateActivation(target, _uiState.value.simulatorInput, _uiState.value.simulatorMaxDepth)
        _uiState.update {
            it.copy(
                activeProjectId = projectId,
                showWorldsHome = false,
                isEditingEntryFullScreen = false,
                isEditingCardFullScreen = false,
                selectedEntryUid = target.lorebook.entries.firstOrNull()?.uid,
                selectedCardId = target.characterCards.firstOrNull()?.id,
                multiSelectedEntryUids = emptySet(),
                canonProfile = null,
                crossRefMentions = mentions,
                canonIssues = issues,
                simulationResult = sim,
                canUndo = false,
                canRedo = false,
                statusBannerMessage = "Switched to '${target.name}'"
            )
        }
    }

    /**
     * Creates a 100% Blank World with zero pre-populated entries or cards so the user
     * can build from a clean slate.
     */
    fun createBlankUniverse(
        name: String,
        genre: String = "Custom Universe",
        description: String = ""
    ) {
        val cleanName = name.trim().ifBlank { "Untitled World" }
        val cleanGenre = genre.trim().ifBlank { "Custom Universe" }
        val cleanDesc = description.trim().ifBlank { "Blank world ready for custom Lorebook entries and CHARX Character Cards." }

        val blankProject = UniverseProject(
            name = cleanName,
            description = cleanDesc,
            genre = cleanGenre,
            tone = "Custom Atmosphere",
            lorebook = STLorebookRoot(
                name = cleanName,
                description = cleanDesc,
                entries = emptyList()
            ),
            characterCards = emptyList(),
            wizardState = PremiseWizardState(
                genre = cleanGenre,
                subgenre = "",
                era = "",
                vibes = "",
                conflict = cleanDesc,
                strangeFactor = "",
                protagonist = "{{user}}",
                hardRules = ""
            )
        )
        commitProjectMutation(blankProject, "Created blank world '$cleanName'", recordUndo = false, immediatePersist = true)
        _uiState.update {
            it.copy(
                activeProjectId = blankProject.id,
                showWorldsHome = false,
                isEditingEntryFullScreen = false,
                isEditingCardFullScreen = false,
                selectedEntryUid = null,
                selectedCardId = null,
                canonProfile = null,
                activeTab = MainWorkspaceTab.STUDIO
            )
        }
    }

    /**
     * Creates a new world from a raw user idea spark, automatically fleshes out the
     * structured Premise Wizard & World DNA via AI, and opens the AI Forge tab.
     */
    fun createUniverseFromIdeaSpark(
        worldName: String,
        rawIdea: String
    ) {
        val cleanIdea = rawIdea.trim()
        if (cleanIdea.isBlank()) {
            createBlankUniverse(worldName)
            return
        }
        val cleanName = worldName.trim().ifBlank {
            cleanIdea.split(" ").take(3).joinToString(" ").replaceFirstChar { it.uppercase() }.ifBlank { "New Spark World" }
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    busyStepLabel = "AI Fleshing Out '$cleanName' from your idea...",
                    rawIdeaInput = cleanIdea
                )
            }
            val wizardRes = GeminiLoreService.fleshOutIdeaSpark(cleanName, cleanIdea, _uiState.value.useMockAiMode)
            val fleshedWizard = wizardRes.getOrElse { err ->
                if (err is CancellationException) return@launch
                PremiseWizardState(genre = "Speculative Worldbuilding", conflict = cleanIdea)
            }
            val dnaRes = GeminiLoreService.analyzePremiseDna(fleshedWizard, _uiState.value.useMockAiMode)
            val dna = dnaRes.getOrNull()
            val seedRes = GeminiLoreService.generatePremiseSeed(fleshedWizard, _uiState.value.useMockAiMode)
            val seedMd = seedRes.getOrDefault("")

            val initialRule = STEntry(
                uid = 101,
                comment = "RULE: ${fleshedWizard.hardRules.take(42).ifBlank { "Core Law of $cleanName" }}",
                key = listOf(cleanName.lowercase(), "world law", "core axiom"),
                content = "RULE: ${fleshedWizard.hardRules.ifBlank { cleanIdea }}. [Setting: ${fleshedWizard.era} — ${fleshedWizard.vibes}].",
                constant = true,
                position = 4,
                order = 290,
                preventRecursion = true,
                group = "foundation"
            )

            val newProj = UniverseProject(
                name = cleanName,
                description = fleshedWizard.conflict,
                genre = fleshedWizard.genre,
                tone = fleshedWizard.vibes,
                lorebook = STLorebookRoot(
                    name = cleanName,
                    description = fleshedWizard.conflict,
                    entries = listOf(initialRule)
                ),
                wizardState = fleshedWizard,
                dnaAnalysis = dna
            )
            commitProjectMutation(
                newProj,
                "Fleshed out '$cleanName' — review your AI World DNA & Blueprint!",
                recordUndo = false,
                immediatePersist = true
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    activeProjectId = newProj.id,
                    showWorldsHome = false,
                    isEditingEntryFullScreen = false,
                    isEditingCardFullScreen = false,
                    selectedEntryUid = 101,
                    generatedSeedMarkdown = seedMd,
                    activeTab = MainWorkspaceTab.FORGE,
                    forgeStage = 0
                )
            }
        }
    }

    /**
     * Fleshes out a raw idea string inside the currently active world's Forge tab.
     */
    fun fleshOutCurrentWorldIdea(rawIdea: String) {
        val active = _uiState.value.activeProject ?: return
        if (rawIdea.isBlank()) return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    rawIdeaInput = rawIdea,
                    busyStepLabel = "AI Fleshing Out Idea into World Premise & DNA..."
                )
            }
            val fleshedWizard = GeminiLoreService.fleshOutIdeaSpark(
                active.name,
                rawIdea,
                _uiState.value.useMockAiMode
            ).getOrElse { err ->
                if (err is CancellationException) return@launch
                active.wizardState.copy(conflict = rawIdea)
            }

            val dna = GeminiLoreService.analyzePremiseDna(fleshedWizard, _uiState.value.useMockAiMode).getOrNull()
            val seedMd = GeminiLoreService.generatePremiseSeed(fleshedWizard, _uiState.value.useMockAiMode).getOrDefault("")

            commitProjectMutation(
                active.copy(
                    genre = fleshedWizard.genre,
                    tone = fleshedWizard.vibes,
                    description = fleshedWizard.conflict,
                    wizardState = fleshedWizard,
                    dnaAnalysis = dna
                ),
                "AI fleshed out your idea into 8 Premise Pillars & World DNA!",
                immediatePersist = true
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    generatedSeedMarkdown = seedMd
                )
            }
        }
    }

    fun updateRawIdeaInput(text: String) {
        _uiState.update { it.copy(rawIdeaInput = text) }
    }

    fun createNewUniverse(customName: String? = null, customGenre: String? = null) {
        if (!customName.isNullOrBlank()) {
            createBlankUniverse(customName, customGenre ?: "Custom Universe")
            return
        }
        createBlankUniverse("New Blank World", customGenre ?: "Custom Universe")
    }

    fun renameActiveProject(newName: String, newGenre: String, newTone: String) {
        val active = _uiState.value.activeProject ?: return
        if (newName.isBlank()) return
        commitProjectMutation(
            active.copy(
                name = newName.trim(),
                genre = newGenre.trim().ifBlank { active.genre },
                tone = newTone.trim().ifBlank { active.tone }
            ),
            "Updated Universe Metadata",
            immediatePersist = true
        )
    }

    fun deleteProject(projectId: String) {
        if (_uiState.value.projects.size <= 1) {
            _uiState.update { it.copy(errorBannerMessage = "Cannot delete the last remaining universe.") }
            return
        }
        viewModelScope.launch {
            repository.deleteProject(projectId)
            _uiState.update { it.copy(statusBannerMessage = "Deleted universe project") }
        }
    }

    // --- Lorebook Entry CRUD, Inline Toggle, Bulk Edit & AI Enhancer ---
    fun selectEntry(uid: Int) {
        _uiState.update { it.copy(selectedEntryUid = uid, pendingEnhancedEntry = null) }
    }

    fun toggleEntryEnabled(uid: Int) {
        val active = _uiState.value.activeProject ?: return
        val updatedEntries = active.lorebook.entries.map { entry ->
            if (entry.uid == uid) entry.copy(disable = !entry.disable) else entry
        }
        val toggled = updatedEntries.find { it.uid == uid }
        val statusMsg = if (toggled?.disable == true) {
            "Disabled '${toggled.comment}' in context budget"
        } else {
            "Enabled '${toggled?.comment.orEmpty()}' in context budget"
        }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = updatedEntries)),
            toastMessage = statusMsg,
            immediatePersist = true
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setGroupFilter(group: String) {
        _uiState.update { it.copy(selectedGroupFilter = group) }
    }

    fun toggleMultiSelectEntry(uid: Int) {
        _uiState.update { state ->
            val next = state.multiSelectedEntryUids.toMutableSet()
            if (!next.add(uid)) next.remove(uid)
            state.copy(multiSelectedEntryUids = next)
        }
    }

    fun selectAllFilteredEntries(uids: List<Int>) {
        _uiState.update { it.copy(multiSelectedEntryUids = uids.toSet()) }
    }

    fun clearMultiSelection() {
        _uiState.update { it.copy(multiSelectedEntryUids = emptySet()) }
    }

    fun setDreamWeaverBook(book: DreamWeaverBook) {
        _uiState.update { it.copy(selectedDreamWeaverBook = book) }
    }

    fun insertDreamWeaverDividers() {
        val active = _uiState.value.activeProject ?: return
        val current = active.lorebook.entries
        val hasRules = current.any { it.comment.contains("=== RULES ===", ignoreCase = true) }
        val hasAtlas = current.any { it.comment.contains("=== ATLAS ===", ignoreCase = true) }
        val hasCast = current.any { it.comment.contains("=== CAST ===", ignoreCase = true) }
        val hasDynamics = current.any { it.comment.contains("=== DYNAMICS ===", ignoreCase = true) }

        var maxUid = (current.maxOfOrNull { it.uid } ?: 100)
        val dividersToAdd = mutableListOf<STEntry>()

        if (!hasRules) {
            dividersToAdd.add(
                STEntry(
                    uid = ++maxUid,
                    comment = "=== RULES ===",
                    content = "",
                    disable = true,
                    constant = false,
                    position = 4,
                    order = 999,
                    group = "rules"
                )
            )
        }
        if (!hasAtlas) {
            dividersToAdd.add(
                STEntry(
                    uid = ++maxUid,
                    comment = "=== ATLAS ===",
                    content = "",
                    disable = true,
                    constant = false,
                    position = 0,
                    order = 999,
                    group = "atlas"
                )
            )
        }
        if (!hasCast) {
            dividersToAdd.add(
                STEntry(
                    uid = ++maxUid,
                    comment = "=== CAST ===",
                    content = "",
                    disable = true,
                    constant = false,
                    position = 1,
                    order = 999,
                    group = "cast"
                )
            )
        }
        if (!hasDynamics) {
            dividersToAdd.add(
                STEntry(
                    uid = ++maxUid,
                    comment = "=== DYNAMICS ===",
                    content = "",
                    disable = true,
                    constant = false,
                    position = 3,
                    order = 999,
                    group = "dynamics"
                )
            )
        }

        if (dividersToAdd.isNotEmpty()) {
            val updated = current + dividersToAdd
            commitProjectMutation(
                active.copy(lorebook = active.lorebook.copy(entries = updated)),
                "Inserted Lumiverse 3-Book Dream Weaver section dividers!",
                immediatePersist = true
            )
        }
    }

    fun splitNpcEntry(entryUid: Int) {
        val active = _uiState.value.activeProject ?: return
        val syntheticIssue = CanonIssue(
            severity = IssueSeverity.MAJOR,
            category = IssueCategory.OVERSIZED_NPC,
            targetTitle = "Split NPC",
            entryUid = entryUid,
            description = "Manual 3-Part Split requested",
            proposedFixSummary = "Split into Identity, Presence & Secrets",
            autoFixAction = "SPLIT_NPC_3_PART"
        )
        val repaired = CrossRefAndAuditEngine.repairCanonIssue(active, syntheticIssue)
        commitProjectMutation(repaired, "Split character entry into 3-Part Architecture (Identity, Presence, Secrets)!", immediatePersist = true)
    }

    fun addNewEntry(templateType: EntryTemplateType = EntryTemplateType.CONCEPT) {
        val active = _uiState.value.activeProject ?: return
        val nextUid = (active.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1
        val safeContent = if (templateType.bracketTemplate.startsWith("----")) {
            templateType.bracketTemplate
        } else {
            "----\n${templateType.bracketTemplate}"
        }
        val newEntry = STEntry(
            uid = nextUid,
            comment = "New ${templateType.label} #$nextUid",
            key = listOf("keyword_$nextUid"),
            content = safeContent,
            displayIndex = active.lorebook.entries.size
        ).applyTemplateDefaults(templateType)

        val updatedEntries = active.lorebook.entries + newEntry
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = updatedEntries)),
            "Added '${newEntry.comment}'",
            immediatePersist = true
        )
        _uiState.update {
            it.copy(
                selectedEntryUid = nextUid,
                isEditingEntryFullScreen = true
            )
        }
    }

    fun updateEntry(updated: STEntry) {
        val active = _uiState.value.activeProject ?: return
        val newList = active.lorebook.entries.map { if (it.uid == updated.uid) updated else it }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = newList)),
            recordUndo = false
        )
    }

    fun duplicateEntry(uid: Int) {
        val active = _uiState.value.activeProject ?: return
        val source = active.lorebook.entries.find { it.uid == uid } ?: return
        val nextUid = (active.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1
        val copy = source.copy(
            uid = nextUid,
            comment = "${source.comment} (Copy)",
            displayIndex = active.lorebook.entries.size
        )
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = active.lorebook.entries + copy)),
            "Duplicated entry as #$nextUid",
            immediatePersist = true
        )
        _uiState.update { it.copy(selectedEntryUid = nextUid) }
    }

    fun deleteEntry(uid: Int) {
        val active = _uiState.value.activeProject ?: return
        val remaining = active.lorebook.entries.filterNot { it.uid == uid }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = remaining)),
            "Deleted Entry #$uid",
            immediatePersist = true
        )
        _uiState.update {
            it.copy(
                selectedEntryUid = remaining.firstOrNull()?.uid,
                multiSelectedEntryUids = it.multiSelectedEntryUids - uid
            )
        }
    }

    fun bulkApplyTemplate(templateType: EntryTemplateType) {
        val active = _uiState.value.activeProject ?: return
        val selected = _uiState.value.multiSelectedEntryUids
        if (selected.isEmpty()) return
        val updated = active.lorebook.entries.map { entry ->
            if (entry.uid in selected) entry.applyTemplateDefaults(templateType) else entry
        }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = updated)),
            "Applied ${templateType.label} preset to ${selected.size} entries",
            immediatePersist = true
        )
    }

    fun bulkFindAndReplace(findText: String, replaceText: String, ignoreCase: Boolean) {
        if (findText.isEmpty()) return
        val active = _uiState.value.activeProject ?: return
        val selected = _uiState.value.multiSelectedEntryUids
        val targetSet = if (selected.isNotEmpty()) selected else active.lorebook.entries.map { it.uid }.toSet()
        var count = 0
        val updated = active.lorebook.entries.map { entry ->
            if (entry.uid in targetSet && entry.content.contains(findText, ignoreCase = ignoreCase)) {
                count++
                entry.copy(content = entry.content.replace(findText, replaceText, ignoreCase = ignoreCase))
            } else entry
        }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = updated)),
            "Replaced matches across $count entries",
            immediatePersist = true
        )
    }

    fun bulkDeleteSelectedEntries() {
        val active = _uiState.value.activeProject ?: return
        val selected = _uiState.value.multiSelectedEntryUids
        if (selected.isEmpty()) return
        val remaining = active.lorebook.entries.filterNot { it.uid in selected }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = remaining)),
            "Deleted ${selected.size} selected entries",
            immediatePersist = true
        )
        _uiState.update {
            it.copy(
                multiSelectedEntryUids = emptySet(),
                selectedEntryUid = remaining.firstOrNull()?.uid
            )
        }
    }

    fun createWeightedEventGroup(
        groupName: String,
        triggerKeyword: String,
        stickyDuration: Int,
        outcomes: List<EventOutcomeItem>
    ) {
        val active = _uiState.value.activeProject ?: return
        var nextUid = (active.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1
        val created = outcomes.mapIndexed { idx, outcome ->
            val uid = nextUid++
            STEntry(
                uid = uid,
                comment = "[$groupName] ${outcome.label}",
                key = listOf(triggerKeyword.trim().ifBlank { groupName }),
                content = outcome.content,
                group = groupName.trim().ifBlank { "event_group" },
                groupWeight = outcome.weight.coerceIn(1, 100),
                probability = outcome.weight.coerceIn(1, 100),
                useProbability = true,
                useGroupScoring = true,
                position = 3,
                order = 100 + idx,
                sticky = stickyDuration,
                preventRecursion = true,
                displayIndex = active.lorebook.entries.size + idx
            )
        }
        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = active.lorebook.entries + created)),
            "Created Event Group '$groupName' (${created.size} weighted outcomes)",
            immediatePersist = true
        )
    }

    fun runAiEntryEnhancement(mode: String) {
        val active = _uiState.value.activeProject ?: return
        val entry = _uiState.value.activeEntry ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyStepLabel = "AI Refining '${entry.comment}'...") }
            val result = GeminiLoreService.enhanceEntryContent(active, entry, mode, _uiState.value.useMockAiMode)
            result.fold(
                onSuccess = { enhanced ->
                    if (mode == "FIX_KEYS") {
                        updateEntry(enhanced)
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                statusBannerMessage = "Updated trigger keywords for '${entry.comment}'"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                pendingEnhancedEntry = enhanced,
                                statusBannerMessage = "AI Enhancement ready — review and Accept below"
                            )
                        }
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorBannerMessage = err.localizedMessage ?: "Enhancement failed."
                        )
                    }
                }
            )
        }
    }

    fun acceptPendingEntryEnhancement() {
        val pending = _uiState.value.pendingEnhancedEntry ?: return
        updateEntry(pending)
        _uiState.update {
            it.copy(
                pendingEnhancedEntry = null,
                statusBannerMessage = "Applied AI enhancement to '${pending.comment}'"
            )
        }
    }

    fun rejectPendingEntryEnhancement() {
        _uiState.update { it.copy(pendingEnhancedEntry = null) }
    }

    // --- Character Card V2 / CCV3 / CHARX CRUD & Lorebook Bidirectional Sync ---
    fun selectCharacterCard(cardId: String) {
        _uiState.update { it.copy(selectedCardId = cardId) }
    }

    fun addNewCharacterCard() {
        val active = _uiState.value.activeProject ?: return
        val newCard = CharacterCardV2(
            name = "New Persona ${active.characterCards.size + 1}",
            roleTitle = "Notable Figure of ${active.name}",
            factionAffiliation = active.lorebook.entries.firstOrNull { it.classifyType() == EntryTemplateType.FACTION }?.comment.orEmpty(),
            description = "{{char}} is a key figure within ${active.name} (${active.genre}).",
            personality = "Observant, strong-willed, and deeply entangled in the central tensions of ${active.name}.",
            scenario = "{{user}} crosses paths with {{char}} inside ${active.name}.",
            firstMes = "*The atmosphere of ${active.name} settles around you as {{char}} regards you with a measured gaze.*\n\n\"You chose an interesting hour to arrive. Tell me what brings you here.\"",
            mesExample = "<START>\n{{user}}: \"Who do you really answer to?\"\n{{char}}: \"In ${active.name}, everyone answers to the ledger eventually.\"",
            tags = listOf(active.genre.lowercase(), "lumiverse", "charx")
        )
        val updatedCards = active.characterCards + newCard
        commitProjectMutation(
            active.copy(characterCards = updatedCards),
            "Created CHARX / CCV3 Character Card '${newCard.name}'",
            immediatePersist = true
        )
        _uiState.update {
            it.copy(
                studioSubMode = StudioSubMode.CHARACTER_CARDS,
                selectedCardId = newCard.id,
                isEditingCardFullScreen = true
            )
        }
    }

    fun updateCharacterCard(updated: CharacterCardV2) {
        val active = _uiState.value.activeProject ?: return
        val stamped = updated.copy(modificationDate = System.currentTimeMillis() / 1000L)
        val newCards = active.characterCards.map { if (it.id == stamped.id) stamped else it }
        commitProjectMutation(active.copy(characterCards = newCards), recordUndo = false)
    }

    fun deleteCharacterCard(cardId: String) {
        val active = _uiState.value.activeProject ?: return
        val remaining = active.characterCards.filterNot { it.id == cardId }
        commitProjectMutation(
            active.copy(characterCards = remaining),
            "Deleted Character Card",
            immediatePersist = true
        )
        _uiState.update { it.copy(selectedCardId = remaining.firstOrNull()?.id) }
    }

    fun promoteEntryToCharacterCard(entry: STEntry) {
        val active = _uiState.value.activeProject ?: return
        val existingCard = active.characterCards.find { it.linkedLoreEntryUid == entry.uid }
        if (existingCard != null) {
            _uiState.update {
                it.copy(
                    studioSubMode = StudioSubMode.CHARACTER_CARDS,
                    selectedCardId = existingCard.id,
                    statusBannerMessage = "Opened linked Character Card '${existingCard.name}'"
                )
            }
            return
        }
        val promoted = CharacterCardV2.fromLoreEntry(entry, active.name, active.genre)
        commitProjectMutation(
            active.copy(characterCards = active.characterCards + promoted),
            "Promoted '${entry.comment}' to CHARX / CCV3 Character Card!",
            immediatePersist = true
        )
        _uiState.update {
            it.copy(
                studioSubMode = StudioSubMode.CHARACTER_CARDS,
                selectedCardId = promoted.id
            )
        }
    }

    fun syncCharacterCardWithLorebook(card: CharacterCardV2) {
        val active = _uiState.value.activeProject ?: return
        val entries = active.lorebook.entries.toMutableList()
        val existingIdx = entries.indexOfFirst { it.uid == card.linkedLoreEntryUid || it.comment.startsWith(card.name, ignoreCase = true) }

        val syncedUid: Int
        if (existingIdx >= 0) {
            val existing = entries[existingIdx]
            syncedUid = existing.uid
            entries[existingIdx] = card.toLoreEntry(syncedUid, existing)
        } else {
            syncedUid = (entries.maxOfOrNull { it.uid } ?: 100) + 1
            entries.add(card.toLoreEntry(syncedUid).copy(displayIndex = entries.size))
        }

        val updatedCards = active.characterCards.map {
            if (it.id == card.id) card.copy(linkedLoreEntryUid = syncedUid) else it
        }
        commitProjectMutation(
            active.copy(
                lorebook = active.lorebook.copy(entries = entries),
                characterCards = updatedCards
            ),
            "Synchronized '${card.name}' ↔ Lorebook Entry #$syncedUid",
            immediatePersist = true
        )
    }

    // --- 4-Stage Hybrid Agentic Forge Pipeline ---
    fun setForgeStage(stage: Int) {
        _uiState.update { it.copy(forgeStage = stage.coerceIn(0, 3)) }
    }

    fun updatePremiseWizard(updated: PremiseWizardState) {
        val active = _uiState.value.activeProject ?: return
        commitProjectMutation(active.copy(wizardState = updated), recordUndo = false)
    }

    fun updateForgeConfig(updated: ForgeConfig) {
        val active = _uiState.value.activeProject ?: return
        commitProjectMutation(active.copy(forgeConfig = updated), recordUndo = false)
    }

    fun generateScenarioSeedFromWizard() {
        val active = _uiState.value.activeProject ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyStepLabel = "Synthesizing Scenario Seed...") }
            val res = GeminiLoreService.generatePremiseSeed(active.wizardState, _uiState.value.useMockAiMode)
            res.fold(
                onSuccess = { seedMd ->
                    val snapshot = PremiseSnapshot(
                        title = "${active.wizardState.genre} (${active.wizardState.era})",
                        premiseText = seedMd,
                        wizardState = active.wizardState
                    )
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            generatedSeedMarkdown = seedMd,
                            premiseHistory = (listOf(snapshot) + it.premiseHistory).take(15),
                            statusBannerMessage = "Scenario Seed synthesized!"
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorBannerMessage = err.localizedMessage ?: "Failed to generate scenario seed."
                        )
                    }
                }
            )
        }
    }

    fun runWorldDnaAnalysis() {
        val active = _uiState.value.activeProject ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyStepLabel = "Extracting World DNA & Pillars...") }
            val res = GeminiLoreService.analyzePremiseDna(active.wizardState, _uiState.value.useMockAiMode)
            res.fold(
                onSuccess = { dna ->
                    commitProjectMutation(
                        active.copy(dnaAnalysis = dna),
                        "Extracted World DNA (${dna.extractedEntities.size} core entities, ${dna.narrativeHooks.size} hooks)",
                        immediatePersist = true
                    )
                    _uiState.update { it.copy(isBusy = false) }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorBannerMessage = err.localizedMessage ?: "World DNA extraction failed."
                        )
                    }
                }
            )
        }
    }

    fun applyDnaRecommendations() {
        val active = _uiState.value.activeProject ?: return
        val dna = active.dnaAnalysis ?: return
        var cfg = active.forgeConfig
        dna.recommendations.filter { it.selected }.forEach { rec ->
            if (rec.moduleToEnable != null) {
                cfg = cfg.copy(enabledModules = cfg.enabledModules + rec.moduleToEnable)
            }
            if (rec.sliderKey != null && rec.sliderTargetValue != null) {
                val axes = cfg.toneAxes.toMutableMap()
                axes[rec.sliderKey] = rec.sliderTargetValue
                cfg = cfg.copy(toneAxes = axes)
            }
        }
        commitProjectMutation(active.copy(forgeConfig = cfg), "Applied AI DNA Recommendations to Forge Config", immediatePersist = true)
        _uiState.update { it.copy(forgeStage = 1) }
    }

    fun generateForgeBlueprint() {
        val active = _uiState.value.activeProject ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val cfg = active.forgeConfig
            val stepDesc = when (cfg.generationTarget) {
                com.example.model.ForgeGenerationTarget.LOREBOOK_ONLY ->
                    "Architecting Lorebook Blueprint (${cfg.targetEntryCount} entries, ${cfg.lorebookNpcCount} NPCs)..."
                com.example.model.ForgeGenerationTarget.CHARACTER_CARD_ONLY ->
                    "Architecting ${cfg.cardConceptType.label} CHARX Card..."
                com.example.model.ForgeGenerationTarget.CARD_WITH_LOREBOOK ->
                    "Architecting ${cfg.cardConceptType.label} Card + ${cfg.targetEntryCount}-Entry Lorebook (${cfg.lorebookNpcCount} NPCs)..."
            }
            _uiState.update { it.copy(isBusy = true, busyStepLabel = stepDesc) }
            val res = GeminiLoreService.generateBlueprint(
                active,
                active.wizardState,
                cfg,
                _uiState.value.useMockAiMode
            )
            res.fold(
                onSuccess = { bp ->
                    val toast = when (bp.generationTarget) {
                        com.example.model.ForgeGenerationTarget.LOREBOOK_ONLY ->
                            "Generated Lorebook Blueprint (${bp.plannedEntries.size} entries, ${cfg.lorebookNpcCount} NPCs)"
                        com.example.model.ForgeGenerationTarget.CHARACTER_CARD_ONLY ->
                            "Generated ${bp.cardConceptType.label} CHARX Card Blueprint"
                        com.example.model.ForgeGenerationTarget.CARD_WITH_LOREBOOK ->
                            "Generated ${bp.cardConceptType.label} Card + ${bp.plannedEntries.size} Lorebook Entries"
                    }
                    commitProjectMutation(
                        active.copy(activeBlueprint = bp),
                        toast,
                        immediatePersist = true
                    )
                    _uiState.update { it.copy(isBusy = false, forgeStage = 2) }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorBannerMessage = err.localizedMessage ?: "Blueprint generation failed."
                        )
                    }
                }
            )
        }
    }

    fun runBatchForgeAll() {
        val active = _uiState.value.activeProject ?: return
        val bp = active.activeBlueprint ?: return
        val plans = bp.plannedEntries
        if (plans.isEmpty()) {
            _uiState.update { it.copy(forgeProgressFraction = 1f, forgeStage = 3) }
            return
        }

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    forgeProgressFraction = 0.05f,
                    busyStepLabel = "Forging Batch 1 (NENS v3.0 & Bracketed Lore)..."
                )
            }
            val chunkSize = if (plans.size > 30) 8 else 4
            val chunks = plans.chunked(chunkSize)
            val forgedAll = mutableListOf<BlueprintEntryPlan>()
            for ((index, chunk) in chunks.withIndex()) {
                _uiState.update {
                    it.copy(
                        forgeProgressFraction = (index.toFloat() / chunks.size.toFloat()).coerceIn(0.1f, 0.95f),
                        busyStepLabel = "Forging Batch ${index + 1}/${chunks.size}: ${chunk.first().title}..."
                    )
                }
                val batchResult = GeminiLoreService.forgeBlueprintBatch(
                    active,
                    chunk,
                    _uiState.value.useMockAiMode
                )
                val items = batchResult.getOrElse { err ->
                    if (err is CancellationException) return@launch
                    chunk
                }
                forgedAll.addAll(items)
            }

            val updatedBp = bp.copy(plannedEntries = forgedAll)
            commitProjectMutation(
                active.copy(activeBlueprint = updatedBp),
                "Forged all ${forgedAll.size} entries! Ready for Canon Audit & Compile.",
                immediatePersist = true
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    forgeProgressFraction = 1f,
                    forgeStage = 3
                )
            }
        }
    }

    fun updateBlueprintDraftContent(planId: String, newContent: String) {
        val active = _uiState.value.activeProject ?: return
        val bp = active.activeBlueprint ?: return
        val updatedPlans = bp.plannedEntries.map {
            if (it.id == planId) it.copy(forgedContent = newContent, isForged = true) else it
        }
        commitProjectMutation(active.copy(activeBlueprint = bp.copy(plannedEntries = updatedPlans)), recordUndo = false)
    }

    fun updateBlueprintPrimaryCard(updatedCard: CharacterCardV2) {
        val active = _uiState.value.activeProject ?: return
        val bp = active.activeBlueprint ?: return
        commitProjectMutation(
            active.copy(activeBlueprint = bp.copy(plannedPrimaryCard = updatedCard)),
            recordUndo = false
        )
    }

    fun compileForgedBlueprintIntoUniverse() {
        val active = _uiState.value.activeProject ?: return
        val bp = active.activeBlueprint ?: return
        var nextUid = (active.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1

        val newEntries = mutableListOf<STEntry>()
        val newCards = mutableListOf<CharacterCardV2>()

        bp.plannedPrimaryCard?.let { primaryCard ->
            newCards.add(
                primaryCard.copy(
                    embedLorebookOnExport = bp.generationTarget.includesLorebook
                )
            )
        }

        bp.plannedEntries.forEachIndexed { idx, plan ->
            val uid = nextUid++
            val rawContent = plan.forgedContent.ifBlank {
                "Name: ${plan.title}\nDetails: ${plan.briefDirective}\nConnected: ${plan.shouldMentionEntities.joinToString(", ")}."
            }
            val content = if (rawContent.trimStart().startsWith("----")) rawContent else "----\n$rawContent"
            val baseEntry = STEntry(
                uid = uid,
                comment = plan.title,
                key = plan.primaryKeys.ifEmpty { listOf(plan.title.substringBefore("—").trim()) },
                keysecondary = (plan.secondaryKeys + plan.shouldMentionEntities).distinct(),
                content = content,
                group = plan.module.id,
                displayIndex = active.lorebook.entries.size + idx
            ).applyTemplateDefaults(plan.templateType)

            // Apply Lumiverse token hygiene & presence dynamics
            val entry = when (plan.templateType) {
                EntryTemplateType.CHARACTER -> baseEntry.copy(sticky = 3, preventRecursion = true)
                EntryTemplateType.LOCATION -> baseEntry.copy(excludeGreeting = true)
                EntryTemplateType.WORLD_RULE -> baseEntry.copy(order = 290, constant = true, preventRecursion = true)
                else -> baseEntry
            }

            newEntries.add(entry)
            if (plan.isCharacterCardCandidate && active.forgeConfig.autoGenerateCharacterCards) {
                newCards.add(CharacterCardV2.fromLoreEntry(entry, active.name, active.genre))
            }
        }

        val mergedProject = active.copy(
            lorebook = active.lorebook.copy(entries = active.lorebook.entries + newEntries),
            characterCards = active.characterCards + newCards
        )
        commitProjectMutation(
            mergedProject,
            "Compiled ${newEntries.size} Lore Entries and ${newCards.size} CHARX Cards into '${active.name}'!",
            immediatePersist = true
        )
        _uiState.update {
            it.copy(
                activeTab = MainWorkspaceTab.STUDIO,
                studioSubMode = if (newEntries.isEmpty() && newCards.isNotEmpty()) {
                    StudioSubMode.CHARACTER_CARDS
                } else {
                    it.studioSubMode
                },
                selectedEntryUid = newEntries.firstOrNull()?.uid ?: it.selectedEntryUid,
                selectedCardId = newCards.firstOrNull()?.id ?: it.selectedCardId
            )
        }
    }

    // --- Cross-Ref & Real-Time Canon Auditor Actions ---
    fun applySingleCrossRef(mention: CrossRefMention) {
        val active = _uiState.value.activeProject ?: return
        val updated = CrossRefAndAuditEngine.applyCrossRefMention(active, mention)
        val actionLabel = if (mention.targetType == "UNCREATED_ENTITY") {
            "Spawned new Lorebook Stub Entry for '${mention.matchedPhrase}'"
        } else {
            "Linked '${mention.matchedPhrase}' between '${mention.sourceTitle}' and '${mention.targetTitle}'"
        }
        commitProjectMutation(updated, actionLabel, immediatePersist = true)
    }

    fun autoLinkAllCrossReferences() {
        var current = _uiState.value.activeProject ?: return
        val unlinked = _uiState.value.crossRefMentions.filter { !it.isKeywordAlreadyLinked && it.targetType != "UNCREATED_ENTITY" }
        unlinked.forEach { mention ->
            current = CrossRefAndAuditEngine.applyCrossRefMention(current, mention)
        }
        commitProjectMutation(current, "Auto-linked ${unlinked.size} cross-references across entries & cards!", immediatePersist = true)
    }

    fun repairSingleCanonIssue(issue: CanonIssue) {
        val active = _uiState.value.activeProject ?: return
        val repaired = CrossRefAndAuditEngine.repairCanonIssue(active, issue)
        commitProjectMutation(repaired, "Repaired: ${issue.targetTitle}", immediatePersist = true)
    }

    fun repairAllCriticalAndMajorIssues() {
        var current = _uiState.value.activeProject ?: return
        val issues = _uiState.value.canonIssues.filter { it.severity == IssueSeverity.CRITICAL || it.severity == IssueSeverity.MAJOR }
        issues.forEach { issue ->
            current = CrossRefAndAuditEngine.repairCanonIssue(current, issue)
        }
        commitProjectMutation(current, "Auto-repaired ${issues.size} Canon & Linter issues!", immediatePersist = true)
    }

    // --- Activation & Roleplay Simulator ---
    fun updateSimulatorInput(input: String) {
        _uiState.update { it.copy(simulatorInput = input) }
    }

    fun updateSimulatorMaxDepth(depth: Int) {
        _uiState.update { it.copy(simulatorMaxDepth = depth.coerceIn(0, 5)) }
        runActivationSimulation()
    }

    fun runActivationSimulation() {
        val active = _uiState.value.activeProject ?: return
        val result = ActivationSimulatorEngine.simulateActivation(
            active,
            _uiState.value.simulatorInput,
            _uiState.value.simulatorMaxDepth
        )
        _uiState.update { it.copy(simulationResult = result) }
    }

    // --- Live Architect Co-Pilot ---
    fun sendArchitectPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val active = _uiState.value.activeProject ?: return
        val userMsg = ArchitectMessage(isUser = true, text = prompt.trim())
        _uiState.update {
            it.copy(
                architectMessages = it.architectMessages + userMsg,
                isBusy = true,
                busyStepLabel = "Architect analyzing universe graph..."
            )
        }

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val res = GeminiLoreService.askArchitectCoPilot(
                project = active,
                activeEntry = _uiState.value.activeEntry,
                activeCard = _uiState.value.activeCard,
                userPrompt = prompt,
                useMockMode = _uiState.value.useMockAiMode
            )
            res.fold(
                onSuccess = { replyMsg ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            architectMessages = it.architectMessages + replyMsg
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    val fallback = GeminiLoreService.askArchitectCoPilot(
                        project = active,
                        activeEntry = _uiState.value.activeEntry,
                        activeCard = _uiState.value.activeCard,
                        userPrompt = prompt,
                        useMockMode = true
                    ).getOrNull()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            architectMessages = if (fallback != null) it.architectMessages + fallback else it.architectMessages,
                            errorBannerMessage = err.localizedMessage
                        )
                    }
                }
            )
        }
    }

    fun applyArchitectProposal(messageId: String, proposal: ArchitectProposal) {
        val active = _uiState.value.activeProject ?: return
        when (proposal.actionType) {
            ArchitectActionType.UPDATE_ENTRY -> {
                proposal.proposedEntry?.let { updateEntry(it) }
            }
            ArchitectActionType.CREATE_ENTRY -> {
                proposal.proposedEntry?.let { newEntry ->
                    commitProjectMutation(
                        active.copy(lorebook = active.lorebook.copy(entries = active.lorebook.entries + newEntry)),
                        "Architect created entry '${newEntry.comment}'",
                        immediatePersist = true
                    )
                }
            }
            ArchitectActionType.CREATE_CHARACTER_CARD, ArchitectActionType.UPDATE_CHARACTER_CARD -> {
                proposal.proposedCard?.let { card ->
                    val exists = active.characterCards.any { it.id == card.id }
                    val updatedCards = if (exists) {
                        active.characterCards.map { if (it.id == card.id) card else it }
                    } else {
                        active.characterCards + card
                    }
                    commitProjectMutation(
                        active.copy(characterCards = updatedCards),
                        "Architect added CHARX Card '${card.name}'",
                        immediatePersist = true
                    )
                }
            }
            ArchitectActionType.AUTO_LINK_UNIVERSE -> {
                autoLinkAllCrossReferences()
                repairAllCriticalAndMajorIssues()
            }
            ArchitectActionType.REPAIR_CANON_ISSUES -> {
                repairAllCriticalAndMajorIssues()
            }
            ArchitectActionType.TUNE_FORGE_CONFIG -> {
                applyDnaRecommendations()
            }
        }

        _uiState.update { state ->
            val updatedMsgs = state.architectMessages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(
                        proposals = msg.proposals.map { p ->
                            if (p.id == proposal.id) p.copy(isApplied = true) else p
                        }
                    )
                } else msg
            }
            state.copy(
                architectMessages = updatedMsgs,
                statusBannerMessage = "Applied Architect Proposal: ${proposal.title}"
            )
        }
    }

    // --- Import / Export Helpers (Supports .charx ZIP, Polyglot JPEG/PNG+ZIP, PNG ccv3/chara, and JSON) ---
    fun getExportValidationReport(): ExportValidationReport? {
        val active = _uiState.value.activeProject ?: return null
        return LorebookAndCardCodec.validateForExport(active.lorebook)
    }

    fun autoFixForExport() {
        val active = _uiState.value.activeProject ?: return
        val repairedBook = LorebookAndCardCodec.repairForExport(active.lorebook)
        commitProjectMutation(active.copy(lorebook = repairedBook), "Auto-repaired Lorebook for SillyTavern V2 & Lumiverse export", immediatePersist = true)
    }

    fun importFromBytes(bytes: ByteArray, fileName: String) {
        val active = _uiState.value.activeProject ?: return
        runCatching {
            val (importedLorebook, importedCard) = LorebookAndCardCodec.importUniversalBytes(bytes, fileName)
            var mutated = active
            if (importedCard != null) {
                mutated = mutated.copy(characterCards = mutated.characterCards + importedCard)
            }
            if (importedLorebook != null && importedLorebook.entries.isNotEmpty()) {
                val baseUid = (mutated.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1
                val remapped = importedLorebook.entries.mapIndexed { idx, entry ->
                    entry.copy(uid = baseUid + idx, displayIndex = mutated.lorebook.entries.size + idx)
                }
                mutated = mutated.copy(
                    lorebook = mutated.lorebook.copy(entries = mutated.lorebook.entries + remapped)
                )
            }
            val cardMsg = if (importedCard != null) {
                "CHARX/CCV3 Card '${importedCard.name}' (${importedCard.expressions.size} expressions, ${importedCard.lumiverseModules.customActions.size} Lumiverse actions)"
            } else "0 cards"
            commitProjectMutation(
                mutated,
                "Imported ${importedLorebook?.entries?.size ?: 0} entries & $cardMsg",
                immediatePersist = true
            )
            _uiState.update { it.copy(showWorldsHome = false) }
        }.onFailure { err ->
            _uiState.update {
                it.copy(errorBannerMessage = "Import failed: ${err.localizedMessage}")
            }
        }
    }

    // --- PRD §8: Existing Lorebook Canon Profile & Gap Expander ---
    fun analyzeCanonProfile() {
        val active = _uiState.value.activeProject ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    busyStepLabel = "Extracting Canon Profile & Grounded Gap Suggestions..."
                )
            }
            val res = GeminiLoreService.extractCanonProfileAndSuggestions(active, _uiState.value.useMockAiMode)
            res.fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            canonProfile = profile,
                            statusBannerMessage = "Extracted Canon Profile (${profile.suggestions.size} canon-grounded suggestions)"
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorBannerMessage = err.localizedMessage ?: "Canon extraction failed."
                        )
                    }
                }
            )
        }
    }

    fun toggleCanonSuggestion(suggestionId: String) {
        val currentProfile = _uiState.value.canonProfile ?: return
        val updated = currentProfile.suggestions.map {
            if (it.id == suggestionId) it.copy(selected = !it.selected) else it
        }
        _uiState.update { it.copy(canonProfile = currentProfile.copy(suggestions = updated)) }
    }

    fun applySelectedCanonSuggestions() {
        val active = _uiState.value.activeProject ?: return
        val profile = _uiState.value.canonProfile ?: return
        val selected = profile.suggestions.filter { it.selected }
        if (selected.isEmpty()) return

        var nextUid = (active.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1
        val createdEntries = selected.mapIndexed { idx, sugg ->
            val uid = nextUid++
            STEntry(
                uid = uid,
                comment = sugg.title,
                key = sugg.primaryKeys.ifEmpty { listOf(sugg.title.lowercase()) },
                keysecondary = sugg.secondaryKeys,
                content = sugg.draftContent,
                displayIndex = active.lorebook.entries.size + idx
            ).applyTemplateDefaults(sugg.category)
        }

        commitProjectMutation(
            active.copy(lorebook = active.lorebook.copy(entries = active.lorebook.entries + createdEntries)),
            "Expanded '${active.name}' with ${createdEntries.size} canon-grounded entries!",
            immediatePersist = true
        )
    }
}
