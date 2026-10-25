package com.example.model

import java.util.UUID

/**
 * Multi-Provider AI & BYOK Connection Profile Models (PRD §4.1, §14, §15).
 */
enum class AiProviderType(
    val id: String,
    val label: String,
    val shortTag: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val defaultModels: List<String>,
    val docsUrl: String
) {
    BUILT_IN_GEMINI(
        id = "builtin_gemini",
        label = "Built-in Gemini (Firebase AI)",
        shortTag = "GEMINI",
        defaultBaseUrl = "firebase://ai-logic",
        defaultModel = "gemini-3.8-flash",
        defaultModels = listOf(
            "gemini-3.8-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash"
        ),
        docsUrl = "https://firebase.google.com/docs/ai-logic"
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter (BYOK)",
        shortTag = "OPENROUTER",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "deepseek/deepseek-r1",
        defaultModels = listOf(
            "deepseek/deepseek-r1",
            "deepseek/deepseek-chat-v3-0324",
            "anthropic/claude-3.7-sonnet:thinking",
            "anthropic/claude-3.7-sonnet",
            "anthropic/claude-3.5-sonnet",
            "google/gemini-2.5-pro-preview",
            "openai/gpt-4o",
            "x-ai/grok-3-beta",
            "qwen/qwq-32b"
        ),
        docsUrl = "https://openrouter.ai/keys"
    ),
    NANOGPT(
        id = "nanogpt",
        label = "NanoGPT (BYOK)",
        shortTag = "NANOGPT",
        defaultBaseUrl = "https://nano-gpt.com/api/v1",
        defaultModel = "deepseek-r1",
        defaultModels = listOf(
            "deepseek-r1",
            "deepseek-v3.2",
            "deepseek-chat",
            "glm-4.7",
            "kimi-k2.5",
            "claude-3-7-sonnet-thinking",
            "claude-3-7-sonnet",
            "gemini-2.5-pro"
        ),
        docsUrl = "https://nano-gpt.com/api"
    ),
    GOOGLE_AI_STUDIO(
        id = "google_ai_studio",
        label = "Google AI Studio (BYOK)",
        shortTag = "AI STUDIO",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.5-pro",
        defaultModels = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.0-flash-thinking-exp",
            "gemini-2.0-flash"
        ),
        docsUrl = "https://aistudio.google.com/apikey"
    ),
    CUSTOM_OPENAI(
        id = "custom_openai",
        label = "Custom OpenAI-Compatible (BYOK)",
        shortTag = "CUSTOM API",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        defaultModels = listOf(
            "gpt-4o",
            "o3-mini",
            "deepseek-reasoner"
        ),
        docsUrl = "https://platform.openai.com/api-keys"
    )
}

data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default Built-in Gemini",
    val providerType: AiProviderType = AiProviderType.BUILT_IN_GEMINI,
    val apiKey: String = "",
    val baseUrl: String = providerType.defaultBaseUrl,
    val modelId: String = providerType.defaultModel,
    val temperature: Float = 0.78f,
    val maxTokens: Int = 4096,
    val includeReasoning: Boolean = true,
    val customModels: List<String> = emptyList()
) {
    val provider: AiProviderType get() = providerType
    val maxOutputTokens: Int get() = maxTokens

    fun availableModels(): List<String> = (providerType.defaultModels + customModels + listOf(modelId))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    fun maskedKeyPreview(): String {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return if (providerType == AiProviderType.BUILT_IN_GEMINI) "Managed by App" else "No Key Set"
        if (trimmed.length <= 8) return "••••••••"
        return "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
    }
}

enum class SpeculativeMode(val label: String, val subtitle: String) {
    AUTO("Auto-Detect", "Inferred from genre and premise cues"),
    STRICT_REALISM("Strict Realism", "Zero supernatural, magic, or sci-fi elements"),
    SPECULATIVE("Speculative Allowed", "Enables magic, sci-fi, or surreal mechanics")
}

enum class SpeculativeIntensity(val label: String, val subtitle: String) {
    MINIMAL("Minimal", "Low-magic / one impossible anomaly"),
    MODERATE("Moderate", "Grounded fantasy / hard sci-fi rules"),
    FULL("Full Speculative", "Deep cosmology, metaphysics & systems")
}

data class PremiseWizardState(
    val isDetailedMode: Boolean = true,
    val freeformScenario: String = "",
    val genre: String = "Arcane Clockwork Noir",
    val subgenre: String = "Political Espionage & Forbidden Metaphysics",
    val era: String = "Late Industrial Gilded Eclipse",
    val vibes: String = "Rain-slick basalt spires, brass automatons, whispered heresy, velvet conspiracy",
    val conflict: String = "The Obsidian Synod monopolizes soul-bound chronometers while underground Fracturists sabotage the Great Pendulum.",
    val strangeFactor: String = "Time in the lower wards flows at half-speed whenever the Eclipse Bell tolls.",
    val protagonist: String = "An unlicenced Horologist carrying a contraband chime that resonates with erased memories.",
    val mustInclude: String = "Factional intrigue, sensory street-level atmosphere, morally grey inquisitors, distinct speech idioms",
    val avoid: String = "Generic medieval tropes, chosen-one prophecies, clichés, AI-default names",
    val hardRules: String = "Chrono-resonance always exacts a physical memory toll; no resurrection magic exists.",
    val tokenCap: Int = 3500,
    val speculativeMode: SpeculativeMode = SpeculativeMode.SPECULATIVE,
    val speculativeIntensity: SpeculativeIntensity = SpeculativeIntensity.MODERATE
) {
    fun buildEffectivePremise(): String {
        if (!isDetailedMode && freeformScenario.isNotBlank()) return freeformScenario
        return buildString {
            if (freeformScenario.isNotBlank()) {
                appendLine("RAW IDEA SPARK:\n$freeformScenario\n")
            }
            if (genre.isNotBlank()) appendLine("GENRE: $genre ($subgenre) | ERA: $era")
            if (vibes.isNotBlank()) appendLine("ATMOSPHERE & VIBES: $vibes")
            if (conflict.isNotBlank()) appendLine("CENTRAL CONFLICT: $conflict")
            if (strangeFactor.isNotBlank()) appendLine("STRANGE FACTOR / HOOK: $strangeFactor")
            if (protagonist.isNotBlank()) appendLine("PROTAGONIST ROLE: $protagonist")
            if (mustInclude.isNotBlank()) appendLine("MUST INCLUDE: $mustInclude")
            if (avoid.isNotBlank()) appendLine("AVOID: $avoid")
            if (hardRules.isNotBlank()) appendLine("HARD WORLD RULES: $hardRules")
            appendLine("SPECULATIVE GATE: ${speculativeMode.label} (${speculativeIntensity.label})")
        }.trim()
    }
}

data class PremiseSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val premiseText: String,
    val wizardState: PremiseWizardState
)

data class WorldDnaEntity(
    val name: String,
    val type: EntryTemplateType,
    val importance: String, // "CORE", "MAJOR", "SUPPORTING"
    val summary: String
)

data class ConfigRecommendation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val rationale: String,
    val impact: String, // "HIGH", "MEDIUM", "LOW"
    val moduleToEnable: ForgeModule? = null,
    val sliderKey: String? = null,
    val sliderTargetValue: Int? = null,
    val selected: Boolean = true
)

data class WorldDnaAnalysis(
    val logline: String = "",
    val settingSummary: String = "",
    val coreConflict: String = "",
    val themes: List<String> = emptyList(),
    val stakes: String = "",
    val darkness: Int = 72,
    val complexity: Int = 84,
    val danger: Int = 68,
    val hope: Int = 38,
    val mystery: Int = 88,
    val clarity: Int = 90,
    val originality: Int = 86,
    val conflictDensity: Int = 82,
    val mysteryLoad: Int = 78,
    val castPressure: Int = 64,
    val complexityRisk: Int = 42,
    val extractedEntities: List<WorldDnaEntity> = emptyList(),
    val narrativeHooks: List<String> = emptyList(),
    val missingPillars: List<String> = emptyList(),
    val recommendations: List<ConfigRecommendation> = emptyList()
)

/**
 * PRD §8: Expander Panel — Canon Extraction & Canon-Constrained Gap Suggestions
 */
data class CanonSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: EntryTemplateType,
    val rationale: String,
    val impact: String = "HIGH",
    val groundingScore: Int = 94,
    val primaryKeys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val draftContent: String = "",
    val selected: Boolean = true
)

data class CanonProfile(
    val toneSummary: String = "",
    val namingStyle: String = "",
    val formattingPattern: String = "",
    val establishedRules: List<String> = emptyList(),
    val factions: List<String> = emptyList(),
    val detectedGaps: List<String> = emptyList(),
    val suggestions: List<CanonSuggestion> = emptyList()
)

enum class ForgeModule(val id: String, val label: String, val defaultType: EntryTemplateType) {
    FOUNDATION("foundation", "Foundation & Rules", EntryTemplateType.WORLD_RULE),
    CHARACTERS("characters", "Characters & Cast", EntryTemplateType.CHARACTER),
    LOCATIONS("locations", "Locations & Landmarks", EntryTemplateType.LOCATION),
    FACTIONS("factions", "Factions & Orders", EntryTemplateType.FACTION),
    HISTORY("history", "History & Eras", EntryTemplateType.CONCEPT),
    OBJECTS("objects", "Artifacts & Relics", EntryTemplateType.ITEM),
    SYSTEMS("systems", "Magic & Tech Systems", EntryTemplateType.WORLD_RULE),
    HAZARDS("hazards", "Hazards & Threats", EntryTemplateType.SCENE_EVENT),
    SECRETS("secrets", "Secrets & Conspiracies", EntryTemplateType.CONCEPT),
    DYNAMICS("dynamics", "Dynamic Triggers", EntryTemplateType.DYNAMIC_TRIGGER),
    LEXICON("lexicon", "Lexicon & Speech", EntryTemplateType.DIALOGUE_PATTERN),
    COSMOLOGY("cosmology", "Cosmology & Myth", EntryTemplateType.CONCEPT),
    SOCIOLOGY("sociology", "Culture & Daily Life", EntryTemplateType.CONCEPT),
    BIOLOGY("biology", "Flora, Fauna & Kin", EntryTemplateType.CONCEPT),
    LAW("law", "Taboos & Decrees", EntryTemplateType.WORLD_RULE)
}

enum class ProceduralSystemType(
    val id: String,
    val label: String,
    val description: String,
    val defaultPosition: Int,
    val defaultProbability: Int,
    val defaultSticky: Int,
    val defaultCooldown: Int
) {
    RANDOM_ENCOUNTERS("encounters", "Random Encounters", "Combat, discovery & street ambushes", 3, 75, 6, 4),
    WEATHER("weather", "Dynamic Weather", "Atmospheric shifts affecting visibility & mood", 0, 70, 4, 10),
    LOOT_TREASURE("loot", "Loot & Curiosities", "Variable relics, documents & contraband", 0, 55, 0, 0),
    AMBIENT_NPCS("ambient_npcs", "Ambient NPCs", "Passersby, informants & patrol guards", 1, 65, 6, 4),
    COMPLICATIONS("complications", "Scene Complications", "Sudden plot twists & mechanical failures", 3, 45, 5, 6),
    RUMORS("rumors", "Tavern & Street Rumors", "Whispered gossip & encrypted dispatches", 0, 60, 0, 3),
    MOOD_RELATIONSHIPS("mood", "NPC Mood Shifts", "Dynamic trust, suspicion & rivalry cues", 1, 65, 4, 2),
    QUEST_HOOKS("quests", "Quest & Contract Hooks", "Urgent bounties, mysteries & favors", 3, 50, 6, 5);

    val title: String get() = label
}

enum class ProceduralDensity(val label: String) {
    SPARSE("Sparse"),
    STANDARD("Standard"),
    ABUNDANT("Abundant")
}

enum class ProceduralPersistence(val label: String) {
    EPHEMERAL("Ephemeral"),
    STANDARD("Standard"),
    LASTING("Lasting")
}

enum class ProceduralWeightMode(val label: String) {
    FLAT("Flat"),
    REALISTIC("Realistic"),
    CURATED("Curated")
}

/**
 * Defines what primary artifact(s) the AI Forge Pipeline will generate.
 */
enum class ForgeGenerationTarget(
    val label: String,
    val shortBadge: String,
    val subtitle: String
) {
    LOREBOOK_ONLY(
        label = "Lorebook Only",
        shortBadge = "LOREBOOK",
        subtitle = "Generates a standalone keyword-triggered Lorebook (10–250 entries, with NPCs stored as Lorebook entries)."
    ),
    CHARACTER_CARD_ONLY(
        label = "Character Card Only",
        shortBadge = "CHARX CARD",
        subtitle = "Generates a standalone Lumiverse CHARX / CCV3 Character Card using your selected Roleplay Concept Type (A–E)."
    ),
    CARD_WITH_LOREBOOK(
        label = "Character Card + Attached Lorebook",
        shortBadge = "CARD + LOREBOOK",
        subtitle = "Generates a primary CHARX / CCV3 Card (Type A–E) with a full keyword-triggered Lorebook (10–250 entries & NPCs) attached."
    );

    val title: String get() = label
    val includesLorebook: Boolean get() = this != CHARACTER_CARD_ONLY
    val includesCard: Boolean get() = this != LOREBOOK_ONLY
}

/**
 * Roleplay & Card Concept Types (Types A–E).
 */
enum class CardConceptType(
    val typeCode: String,
    val label: String,
    val description: String,
    val defaultRoleLabel: String
) {
    TYPE_A_SINGLE(
        typeCode = "Type A",
        label = "Single Character (Type A)",
        description = "Focuses on one specific individual interacting with the user's persona.",
        defaultRoleLabel = "Primary Protagonist / Foil"
    ),
    TYPE_B_ENSEMBLE(
        typeCode = "Type B",
        label = "Multi-Character / Ensemble (Type B)",
        description = "Manages two or more characters inside a single card or group chat dynamic.",
        defaultRoleLabel = "Ensemble Cast & Faction Dynamic"
    ),
    TYPE_C_SCENARIO_WORLD(
        typeCode = "Type C",
        label = "Scenario / World (Type C)",
        description = "Focuses heavily on an overarching event, environment, or setting rather than just a single person.",
        defaultRoleLabel = "Living World & Scenario Director"
    ),
    TYPE_D_PERSONA_OPERATOR(
        typeCode = "Type D",
        label = "Persona / Operator (Type D)",
        description = "A card built to act as an assistant, narrator, or system operator rather than a standard roleplay partner.",
        defaultRoleLabel = "System Operator & Narrative Arbiter"
    ),
    TYPE_E_UNIVERSE_SIMULATOR(
        typeCode = "Type E",
        label = "Universe / Simulator (Type E)",
        description = "Acts as a text-adventure engine, sorting hat, or open-ended sandbox simulation.",
        defaultRoleLabel = "Open-Ended Sandbox & Simulation Engine"
    );

    val title: String get() = label
    val shortCode: String get() = typeCode
}

enum class GenerationSizePreset(
    val label: String,
    val targetEntries: Int,
    val targetCards: Int,
    val targetTokens: Int
) {
    MINIMAL("Compact Lorebook (15 Entries)", 15, 1, 2000),
    BALANCED("Standard Lorebook (30 Entries)", 30, 1, 3500),
    DEEP("Deep Lorebook (75 Entries)", 75, 1, 6000),
    MASSIVE("Grand Archive (150 Entries)", 150, 1, 12000)
}

data class ForgeConfig(
    val generationTarget: ForgeGenerationTarget = ForgeGenerationTarget.CARD_WITH_LOREBOOK,
    val cardConceptType: CardConceptType = CardConceptType.TYPE_C_SCENARIO_WORLD,
    val targetEntryCount: Int = 25, // 10..250 entries (keyword-triggered, no artificial token cap)
    val lorebookNpcCount: Int = 6, // Number of NPCs generated as entries inside the lorebook
    val sizePreset: GenerationSizePreset = GenerationSizePreset.BALANCED,
    val maxTokenCap: Int = 0, // Legacy compatibility; no token cap enforced
    val enabledModules: Set<ForgeModule> = setOf(
        ForgeModule.FOUNDATION,
        ForgeModule.CHARACTERS,
        ForgeModule.LOCATIONS,
        ForgeModule.FACTIONS,
        ForgeModule.OBJECTS,
        ForgeModule.SYSTEMS,
        ForgeModule.SECRETS,
        ForgeModule.DYNAMICS,
        ForgeModule.LEXICON
    ),
    val enabledProcedural: Set<ProceduralSystemType> = setOf(
        ProceduralSystemType.RANDOM_ENCOUNTERS,
        ProceduralSystemType.WEATHER,
        ProceduralSystemType.RUMORS
    ),
    val enabledGenerators: Set<ProceduralSystemType> = enabledProcedural,
    val toneAxes: Map<String, Int> = mapOf(
        "Darkness" to 70,
        "Complexity" to 80,
        "Humor" to 25,
        "Violence" to 55,
        "Romance" to 40,
        "Mystery" to 85,
        "Hope" to 45,
        "Realism" to 65,
        "Politics" to 80
    ),
    val castSize: Int = 6,
    val relationshipDensity: String = "High",
    val secretDensity: String = "High",
    val keyStrategy: String = "Smart Cross-Linked",
    val keyStrictness: String = "Balanced",
    val temperature: Float = 0.78f,
    val autoGenerateCharacterCards: Boolean = true,
    val enforceNensNaming: Boolean = true,
    // Procedural generator parameters (PRD §13.2)
    val randomnessLevel: Int = 55, // 0..100
    val contentDensity: ProceduralDensity = ProceduralDensity.STANDARD,
    val persistenceMode: ProceduralPersistence = ProceduralPersistence.STANDARD,
    val weightDistribution: ProceduralWeightMode = ProceduralWeightMode.CURATED
)

data class BlueprintEntryPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val module: ForgeModule,
    val templateType: EntryTemplateType,
    val primaryKeys: List<String>,
    val secondaryKeys: List<String>,
    val shouldMentionEntities: List<String>,
    val briefDirective: String,
    val isCharacterCardCandidate: Boolean = false,
    val forgedContent: String = "",
    val isForged: Boolean = false
)

data class ForgeBlueprint(
    val universeTitle: String = "",
    val linkingStrategySummary: String = "",
    val plannedEntries: List<BlueprintEntryPlan> = emptyList(),
    val plannedPrimaryCard: CharacterCardV2? = null,
    val generationTarget: ForgeGenerationTarget = ForgeGenerationTarget.CARD_WITH_LOREBOOK,
    val cardConceptType: CardConceptType = CardConceptType.TYPE_C_SCENARIO_WORLD,
    val batchSize: Int = 5
)

data class CrossRefMention(
    val id: String = UUID.randomUUID().toString(),
    val sourceTitle: String,
    val sourceType: String, // "LORE_ENTRY" or "CHARACTER_CARD"
    val sourceUidOrId: String,
    val targetTitle: String,
    val targetType: String, // "LORE_ENTRY", "CHARACTER_CARD", or "UNCREATED_ENTITY"
    val targetUidOrId: String,
    val matchedPhrase: String,
    val contextSnippet: String,
    val isKeywordAlreadyLinked: Boolean
)

enum class IssueSeverity(val label: String) {
    CRITICAL("Critical"),
    MAJOR("Major"),
    MINOR("Minor")
}

enum class IssueCategory(val code: String, val label: String) {
    CONTRADICTION("CANON_CONFLICT", "Narrative Contradiction"),
    CARD_LORE_MISMATCH("CARD_DESYNC", "Character Card ↔ Lore Desync"),
    CIRCULAR_RECURSION("RECURSION_LOOP", "Circular Recursion Loop"),
    ORPHAN_ENTRY("ORPHAN", "Unreachable Orphan Entry"),
    DUPLICATE_ENTRY("DUPLICATE", "Duplicate / Overlapping Keys"),
    BROAD_KEY("BROAD_KEY", "Overly Broad Trigger Key"),
    NO_KEYWORDS("NO_KEYS", "Missing Activation Keywords"),
    MISSING_BRACKET("FORMAT_BRACKET", "Unstructured / Missing Brackets"),
    TRUNCATED("TRUNCATED", "Truncated or Dangling Prose"),
    TOO_SHORT("SPARSE_LORE", "Under-Developed Content"),
    BAD_POSITION("BAD_POSITION", "Suboptimal Injection Position"),
    BUDGET_OVERFLOW("BUDGET_RISK", "High Constant Token Drain"),
    TOKEN_HYGIENE("TOKEN_HYGIENE", "Token Boundary / Delimiter Missing"),
    OVERSIZED_NPC("OVERSIZED_NPC", "Oversized NPC (Needs 3-Part Split)"),
    GREETING_OVERFLOW("GREETING_OVERFLOW", "Unsuppressed Greeting Turn Lore")
}

data class CanonIssue(
    val id: String = UUID.randomUUID().toString(),
    val severity: IssueSeverity,
    val category: IssueCategory,
    val targetTitle: String,
    val entryUid: Int? = null,
    val cardId: String? = null,
    val description: String,
    val proposedFixSummary: String,
    val autoFixAction: String
)

enum class ArchitectActionType(val label: String) {
    UPDATE_ENTRY("Update Lore Entry"),
    CREATE_ENTRY("Create New Lore Entry"),
    CREATE_CHARACTER_CARD("Promote / Create Character Card"),
    UPDATE_CHARACTER_CARD("Refine Character Card"),
    AUTO_LINK_UNIVERSE("Cross-Link Universe Keywords"),
    REPAIR_CANON_ISSUES("Repair Canon & Linter Issues"),
    TUNE_FORGE_CONFIG("Optimize Forge Configuration")
}

data class ArchitectProposal(
    val id: String = UUID.randomUUID().toString(),
    val actionType: ArchitectActionType,
    val title: String,
    val summary: String,
    val beforePreview: String = "",
    val afterPreview: String = "",
    val targetEntryUid: Int? = null,
    val targetCardId: String? = null,
    val proposedEntry: STEntry? = null,
    val proposedCard: CharacterCardV2? = null,
    val isApplied: Boolean = false
)

data class ArchitectMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val proposals: List<ArchitectProposal> = emptyList()
)

data class ActivatedEntryStep(
    val entry: STEntry,
    val depthLevel: Int,
    val matchedKeyword: String,
    val triggeredBySource: String,
    val cumulativeTokens: Int
)

data class SimulationResult(
    val sampleInput: String,
    val constantEntries: List<STEntry>,
    val activatedSteps: List<ActivatedEntryStep>,
    val totalConstantTokens: Int,
    val totalTriggeredTokens: Int,
    val tokenBudget: Int,
    val simulatedRoleplayReply: String = ""
)
