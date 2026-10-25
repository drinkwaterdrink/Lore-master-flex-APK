package com.example.model

import kotlin.math.max

enum class EntryTemplateType(
    val label: String,
    val badgeCode: String,
    val defaultPosition: Int,
    val defaultOrder: Int,
    val defaultConstant: Boolean,
    val defaultPreventRecursion: Boolean,
    val defaultSticky: Int,
    val defaultGroup: String,
    val bracketTemplate: String,
    val description: String
) {
    WORLD_RULE(
        label = "World Rule",
        badgeCode = "RULE",
        defaultPosition = 4,
        defaultOrder = 290,
        defaultConstant = true,
        defaultPreventRecursion = true,
        defaultSticky = 0,
        defaultGroup = "foundation",
        bracketTemplate = "RULE: [Core Law / Metaphysical Constraint]. [Mechanism: how it operates in-world]. Violation WILL [immediate narrative consequence].",
        description = "System-level laws, magic limits, and immutable axioms injected at Author Note depth."
    ),
    CHARACTER(
        label = "Character",
        badgeCode = "CHAR",
        defaultPosition = 1,
        defaultOrder = 200,
        defaultConstant = false,
        defaultPreventRecursion = true,
        defaultSticky = 0,
        defaultGroup = "characters",
        bracketTemplate = "[ role(); personality(); background(); relationships(); age(); skills(); speech_style(); secret(); ]",
        description = "NPCs, personas, and key figures injected directly after character definitions."
    ),
    LOCATION(
        label = "Location",
        badgeCode = "LOC",
        defaultPosition = 0,
        defaultOrder = 110,
        defaultConstant = false,
        defaultPreventRecursion = false,
        defaultSticky = 0,
        defaultGroup = "locations",
        bracketTemplate = "[ owner(); atmosphere(); architecture_features(); history(); inhabitants(); hazards(); connected_to(); ]",
        description = "Cities, sanctuaries, districts, and landmarks that can recursively trigger local factions/NPCs."
    ),
    FACTION(
        label = "Faction",
        badgeCode = "FACT",
        defaultPosition = 1,
        defaultOrder = 130,
        defaultConstant = false,
        defaultPreventRecursion = true,
        defaultSticky = 0,
        defaultGroup = "factions",
        bracketTemplate = "[ purpose(); methods(); leadership(); territory(); reputation(); allies(); rivals(); signature_gear(); ]",
        description = "Orders, syndicates, houses, and institutions shaping political tension."
    ),
    ITEM(
        label = "Artifact / Item",
        badgeCode = "ITEM",
        defaultPosition = 0,
        defaultOrder = 105,
        defaultConstant = false,
        defaultPreventRecursion = true,
        defaultSticky = 0,
        defaultGroup = "objects",
        bracketTemplate = "[ type(); material(); powers_effect(); cost_limitation(); origin(); current_holder(); ]",
        description = "Relics, weapons, substances, and technology with concrete rules and costs."
    ),
    SCENE_EVENT(
        label = "Scene Event",
        badgeCode = "EVENT",
        defaultPosition = 3,
        defaultOrder = 100,
        defaultConstant = false,
        defaultPreventRecursion = true,
        defaultSticky = 8,
        defaultGroup = "dynamics",
        bracketTemplate = "[ event_type(); severity(); sensory_cues(); immediate_effect(); resolution_condition(); ]",
        description = "Sticky atmospheric, combat, or environmental states that persist across turns."
    ),
    DIALOGUE_PATTERN(
        label = "Dialogue & Lexicon",
        badgeCode = "DIAL",
        defaultPosition = 2,
        defaultOrder = 100,
        defaultConstant = false,
        defaultPreventRecursion = true,
        defaultSticky = 0,
        defaultGroup = "lexicon",
        bracketTemplate = "[ speaker_or_culture(); cadence(); idioms(); forbidden_phrases(); sample_lines(); ]",
        description = "Cultural idioms, cant, and voice patterns injected before example dialogue."
    ),
    DYNAMIC_TRIGGER(
        label = "Dynamic Trigger",
        badgeCode = "TRIG",
        defaultPosition = 3,
        defaultOrder = 100,
        defaultConstant = false,
        defaultPreventRecursion = true,
        defaultSticky = 5,
        defaultGroup = "dynamics",
        bracketTemplate = "{{char}} will instantly react to [condition] by [concrete behavioral response and consequence].",
        description = "Active instruction hooks using immediate behavioral directives."
    ),
    CONCEPT(
        label = "Concept & Lore",
        badgeCode = "LORE",
        defaultPosition = 0,
        defaultOrder = 100,
        defaultConstant = false,
        defaultPreventRecursion = false,
        defaultSticky = 0,
        defaultGroup = "cosmology",
        bracketTemplate = "[ category(); core_principle(); cultural_impact(); common_misconception(); secret_truth(); ]",
        description = "History, cosmology, biology, customs, and overarching world concepts."
    );

    companion object {
        fun fromPositionLabels(): Map<Int, String> = mapOf(
            0 to "0 • General Context (Before Char Defs)",
            1 to "1 • Characters (After Char Defs)",
            2 to "2 • History & Lexicon (Before Examples)",
            3 to "3 • Scene & Action Hooks (After Examples)",
            4 to "4 • Author Note / Strong System Depth"
        )
    }
}

enum class SelectiveLogicType(
    val code: Int,
    val label: String,
    val description: String
) {
    AND_ALL(0, "AND ALL", "Primary key + ALL secondary keys must match"),
    NOT_ANY(1, "NOT ANY (NOT NONE)", "Primary key matches, and NONE of secondary keys may appear"),
    AND_ANY(2, "AND ANY (OR ANY)", "Primary key + AT LEAST ONE secondary key must match"),
    NOT_ALL(3, "NOT ALL", "Primary key matches, as long as NOT ALL secondary keys appear together");

    companion object {
        fun fromCode(code: Int): SelectiveLogicType = entries.find { it.code == code } ?: AND_ALL
    }
}

enum class DreamWeaverBook(val label: String, val badge: String, val dividerTag: String, val description: String) {
    ALL("All Entries", "ALL", "", "View all entries in the Lorebook"),
    RULES("Rules Book", "RULES", "=== RULES ===", "Constant re-anchors, magic laws, narrator behavior & state trackers"),
    ATLAS("Atlas / Deep Lore", "ATLAS", "=== ATLAS ===", "Locations, nested topography, factions, artifacts & history"),
    CAST("NPC Cast Book", "CAST", "=== CAST ===", "3-Part Character splits: Identity, Presence, and Deep Secrets"),
    DYNAMICS("Dynamic Hooks", "DYN", "=== DYNAMICS ===", "Scene events, weather, combat phases, and active behavioral triggers");
}

data class STCharacterFilter(
    val isExclude: Boolean = false,
    val names: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

data class STEntry(
    val uid: Int,
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val content: String = "",
    val comment: String = "",
    val probability: Int = 100,
    val useProbability: Boolean = true,
    val depth: Int = 4,
    val group: String = "",
    val groupWeight: Int = 100,
    val groupOverride: Boolean = false, // Mutually exclusive state machine: only highest-order active entry in group injects
    val displayIndex: Int = 0,
    val disable: Boolean = false,
    val excludeRecursion: Boolean = false,
    val preventRecursion: Boolean = false,
    val delayUntilRecursion: Boolean = false, // Only activates during recursive scanning passes, never direct chat input
    val delay: Int = 0, // ST: message count delay; Lumiverse: sustained theme filter turns
    val sticky: Int = 0, // Turn persistence (3-5 for scenes, 8-12 for combat)
    val cooldown: Int = 0,
    val selective: Boolean = false,
    val selectiveLogic: Int = 0, // 0 = AND_ALL, 1 = NOT_ANY, 2 = AND_ANY, 3 = NOT_ALL
    val scanDepth: Int? = null,
    val matchWholeWords: Boolean? = null,
    val caseSensitive: Boolean = false,
    val constant: Boolean = false,
    val position: Int = 0, // 0=Before Char, 1=After Char, 2=Before Examples, 3=After Examples, 4=At Depth / Author Note
    val order: Int = 100, // Priority ordering (higher wins in budget & group overrides)
    val addMemo: Boolean = true,
    val triggers: List<String> = emptyList(),
    val characterFilter: STCharacterFilter = STCharacterFilter(),
    val outletName: String = "",
    val vectorized: Boolean = false,
    val ignoreBudget: Boolean = false,
    val useGroupScoring: Boolean = false,
    val excludeGreeting: Boolean = false, // Lumiverse / ST: Prevents heavy entries from bloating turn 1 greeting
    val role: String = "", // "system", "user", "assistant" for depth injection
    // Extended metadata for Scene Event & UI classification
    val sceneEventType: String = "",
    val sceneSeverity: String = "",
    val activeInstructionEnabled: Boolean = false
) {
    fun estimateTokens(): Int {
        if (isSectionDivider()) return 0
        return max(1, (content.length + 3) / 4)
    }

    /**
     * Lumiverse 0-token folder divider check (e.g., "=== RULES ===", "=== ATLAS ===", "=== CAST ===").
     */
    fun isSectionDivider(): Boolean {
        val trimmed = comment.trim()
        return trimmed.startsWith("===") && trimmed.endsWith("===")
    }

    /**
     * Validates whether content starts with the Lumiverse / ST four-dash token boundary delimiter.
     */
    fun hasFourDashDelimiter(): Boolean {
        val firstLine = content.trimStart().lineSequence().firstOrNull().orEmpty()
        return firstLine.startsWith("----")
    }

    /**
     * Categorizes this entry into the 3-Book "Dream Weaver" architecture.
     */
    fun dreamWeaverBook(): DreamWeaverBook {
        val lowerComment = comment.lowercase()
        val lowerContent = content.lowercase()
        val lowerGroup = group.lowercase()

        if (lowerComment.contains("=== rules ===") || constant && position == 4 || lowerGroup == "foundation" || lowerGroup == "rules" || lowerComment.startsWith("rule:")) {
            return DreamWeaverBook.RULES
        }
        if (lowerComment.contains("=== cast ===") || lowerGroup == "characters" || lowerGroup == "cast" || lowerGroup.contains("npc") ||
            lowerContent.contains("part 1: identity") || lowerContent.contains("part 2: presence") || lowerContent.contains("part 3: secrets") ||
            classifyType() == EntryTemplateType.CHARACTER
        ) {
            return DreamWeaverBook.CAST
        }
        if (lowerComment.contains("=== dynamics ===") || sticky >= 4 || activeInstructionEnabled || classifyType() == EntryTemplateType.SCENE_EVENT || classifyType() == EntryTemplateType.DYNAMIC_TRIGGER) {
            return DreamWeaverBook.DYNAMICS
        }
        return DreamWeaverBook.ATLAS
    }

    fun classifyType(): EntryTemplateType {
        val lowerTitle = comment.lowercase()
        val lowerContent = content.lowercase()
        val lowerGroup = group.lowercase()

        if (constant && position == 4 || lowerContent.startsWith("rule:") || lowerGroup == "foundation" || lowerGroup == "law") {
            return EntryTemplateType.WORLD_RULE
        }
        if (activeInstructionEnabled || lowerContent.contains("will instantly") || lowerTitle.contains("trigger")) {
            return EntryTemplateType.DYNAMIC_TRIGGER
        }
        if (sceneEventType.isNotEmpty() || sticky >= 6 && position == 3 || lowerGroup == "events" || lowerTitle.contains("event:")) {
            return EntryTemplateType.SCENE_EVENT
        }
        if (lowerContent.contains("role(") || lowerContent.contains("personality(") || lowerGroup == "characters" || lowerGroup == "cast" || position == 1 && !lowerGroup.contains("faction")) {
            return EntryTemplateType.CHARACTER
        }
        if (lowerContent.contains("leadership(") || lowerContent.contains("territory(") || lowerGroup == "factions" || lowerTitle.contains("synod") || lowerTitle.contains("guild") || lowerTitle.contains("order") || lowerTitle.contains("house")) {
            return EntryTemplateType.FACTION
        }
        if (lowerContent.contains("atmosphere(") || lowerContent.contains("inhabitants(") || lowerGroup == "locations" || lowerTitle.contains("city") || lowerTitle.contains("district") || lowerTitle.contains("sanctum") || lowerTitle.contains("spire")) {
            return EntryTemplateType.LOCATION
        }
        if (lowerContent.contains("material(") || lowerContent.contains("powers_effect(") || lowerGroup == "objects" || lowerGroup == "items") {
            return EntryTemplateType.ITEM
        }
        if (position == 2 || lowerGroup == "lexicon" || lowerContent.contains("idioms(") || lowerContent.contains("cadence(")) {
            return EntryTemplateType.DIALOGUE_PATTERN
        }
        return EntryTemplateType.CONCEPT
    }

    fun applyTemplateDefaults(type: EntryTemplateType): STEntry = copy(
        position = type.defaultPosition,
        order = type.defaultOrder,
        constant = type.defaultConstant,
        preventRecursion = type.defaultPreventRecursion,
        sticky = type.defaultSticky,
        group = if (group.isBlank()) type.defaultGroup else group
    )
}

data class STSettings(
    val scanDepth: Int = 100,
    val tokenBudget: Int = 3000,
    val recursiveScanning: Boolean = true,
    val caseInsensitiveKeys: Boolean = true,
    val matchWholeWords: Boolean = true,
    val useRomanNumerals: Boolean = false
)

data class STLorebookRoot(
    val name: String = "Untitled Lorebook",
    val description: String = "",
    val scanDepth: Int = 100,
    val tokenBudget: Int = 3000,
    val recursiveScanning: Boolean = true,
    val entries: List<STEntry> = emptyList(),
    val settings: STSettings = STSettings()
)

object LoreKeywordValidator {
    val BROAD_KEYS = setOf(
        "the", "a", "an", "he", "she", "they", "it", "man", "woman", "person",
        "city", "town", "world", "magic", "sword", "weapon", "house", "room",
        "day", "night", "time", "dark", "light", "good", "bad", "war", "king",
        "queen", "lord", "lady", "god", "place", "thing", "item", "secret",
        "talk", "speak", "look", "walk", "run", "fight", "attack", "blood"
    )

    fun findBroadKeywords(keys: List<String>): List<String> {
        return keys.filter { k ->
            val trimmed = k.trim().lowercase()
            trimmed.length <= 2 || trimmed in BROAD_KEYS
        }
    }
}
