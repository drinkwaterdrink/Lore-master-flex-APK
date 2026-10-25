package com.example.model

import java.util.UUID

/**
 * CCV3 (`chara_card_v3`) Asset Descriptor used inside `.charx` archives (`card.json` -> `data.assets`).
 */
data class CharxAssetDescriptor(
    val type: String = "icon", // "icon", "emotion", "background", "user_icon", "alt_avatar"
    val uri: String = "embeded://assets/icon/images/main.png",
    val name: String = "main",
    val ext: String = "png"
)

/**
 * Expression Sprite & Mapping bundled inside `.charx` (`assets/emotion/images/<emotion>.png`)
 * and mapped in `lumiverse_modules.json`.
 */
data class CharxExpressionAsset(
    val id: String = UUID.randomUUID().toString(),
    val emotion: String,
    val triggerCue: String = "",
    val triggerKeyword: String = triggerCue.ifBlank { emotion },
    val assetPath: String = "assets/emotion/images/${emotion.lowercase().replace(" ", "_")}.png",
    val accentHex: String = "#38BDF8"
)

/**
 * Custom Quick Action defined inside `lumiverse_modules.json` for Lumiverse UI buttons & macros.
 */
data class LumiverseCustomAction(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val macroCommand: String = "",
    val triggerMacro: String = macroCommand.ifBlank { "/${label.lowercase().replace(" ", "_")}" },
    val promptInjection: String = macroCommand,
    val description: String = ""
)

/**
 * Dedicated Lumiverse Module Configuration (`lumiverse_modules.json`) bundled inside `.charx` archives.
 */
data class LumiverseModulesConfig(
    val moduleName: String = "Lumiverse Persona Module",
    val version: String = "1.2.0",
    val uiThemeAccent: String = "#06B6D4",
    val hudThemeStyle: String = "obsidian_arcane",
    val immersiveHudEnabled: Boolean = true,
    val enableHudOverlay: Boolean = immersiveHudEnabled,
    val expressionPackEnabled: Boolean = true,
    val autoSyncWorldbook: Boolean = true,
    val stateTrackerFields: List<String> = listOf("Trust", "Suspicion", "Faction Standing", "Location"),
    val stateTrackerVariables: List<String> = stateTrackerFields,
    val customActions: List<LumiverseCustomAction> = listOf(
        LumiverseCustomAction(
            label = "Probe Hidden Agenda",
            macroCommand = "*{{user}} watches {{char}}'s micro-expressions closely, pressing them on their true allegiance.*",
            triggerMacro = "/probe_agenda",
            promptInjection = "*{{user}} watches {{char}}'s micro-expressions closely, pressing them on their true allegiance.*",
            description = "Triggers secret/agenda lore keys and shifts disposition"
        ),
        LumiverseCustomAction(
            label = "Inspect Surroundings",
            macroCommand = "*{{user}} scans the immediate area around {{char}} for faction markings, hazards, or exits.*",
            triggerMacro = "/inspect_scene",
            promptInjection = "*{{user}} scans the immediate area around {{char}} for faction markings, hazards, or exits.*",
            description = "Activates location and atmospheric scene triggers"
        )
    ),
    val systemExtensionScript: String = "[Lumiverse HUD & Expression Sync: Select the matching emotion sprite from available expressions each turn and honor active worldbook rules.]"
)

/**
 * Full Character Card V2/V3 (`chara_card_v2` & `chara_card_v3`) + `.charx` Archive + Lumiverse Extensions model.
 * Bidirectionally links with SillyTavern V2 Lorebook entries.
 */
data class CharacterCardV2(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Unnamed Persona",
    val nickname: String = "",
    val roleTitle: String = "Key Figure",
    val factionAffiliation: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val groupOnlyGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "LoreMaster Flex",
    val characterVersion: String = "3.0",
    val specVersion: String = "3.0", // "3.0" (CCV3 / CHARX) or "2.0" (CCV2)
    val avatarColorHex: String = "#06B6D4",
    val linkedLoreEntryUid: Int? = null,
    val embedLorebookOnExport: Boolean = true,
    // Lumiverse extended persona traits (Archetype, Voice Cadence, Hidden Agenda)
    val archetype: String = "",
    val voiceCadence: String = "",
    val hiddenAgenda: String = "",
    // CHARX / CCV3 Assets, Expressions & Lumiverse Modules (`lumiverse_modules.json`)
    val expressions: List<CharxExpressionAsset> = defaultExpressions(),
    val assets: List<CharxAssetDescriptor> = defaultAssetDescriptors(defaultExpressions()),
    val lumiverseModules: LumiverseModulesConfig = LumiverseModulesConfig(),
    val creationDate: Long = System.currentTimeMillis() / 1000L,
    val modificationDate: Long = System.currentTimeMillis() / 1000L
) {
    fun estimateTokens(): Int {
        val totalChars = description.length + personality.length + scenario.length +
            firstMes.length + mesExample.length + systemPrompt.length +
            postHistoryInstructions.length + lumiverseModules.systemExtensionScript.length
        return maxOf(1, (totalChars + 3) / 4)
    }

    /**
     * Converts this Character Card into a condensed, high-signal SillyTavern V2 Lorebook Entry
     * using the bracketed data format so other characters and world events can reference this character.
     */
    fun toLoreEntry(uid: Int, existingEntry: STEntry? = null): STEntry {
        val nameTokens = name.split(" ").map { it.trim() }.filter { it.length >= 3 }
        val primaryKeys = (listOf(name) + listOfNotNull(nickname.takeIf { it.isNotBlank() }) + nameTokens).distinct()
        val secondaryKeys = listOfNotNull(
            factionAffiliation.takeIf { it.isNotBlank() },
            roleTitle.takeIf { it.isNotBlank() }
        )
        val condensedContent = buildString {
            append("[ ")
            append("name($name); ")
            if (nickname.isNotBlank()) append("alias($nickname); ")
            if (roleTitle.isNotBlank()) append("role($roleTitle); ")
            if (factionAffiliation.isNotBlank()) append("faction($factionAffiliation); ")
            if (archetype.isNotBlank()) append("archetype($archetype); ")
            if (personality.isNotBlank()) append("personality(${personality.replace("\n", " ").take(220)}); ")
            if (description.isNotBlank()) append("background(${description.replace("\n", " ").take(280)}); ")
            if (voiceCadence.isNotBlank()) append("speech_style($voiceCadence); ")
            if (hiddenAgenda.isNotBlank()) append("secret($hiddenAgenda); ")
            append("]")
        }
        return (existingEntry ?: STEntry(uid = uid)).copy(
            uid = uid,
            comment = "$name — $roleTitle".trimEnd(' ', '—'),
            key = if (existingEntry != null && existingEntry.key.isNotEmpty()) existingEntry.key else primaryKeys,
            keysecondary = if (existingEntry != null && existingEntry.keysecondary.isNotEmpty()) existingEntry.keysecondary else secondaryKeys,
            content = condensedContent,
            group = "characters",
            position = 1,
            order = 200,
            preventRecursion = true,
            constant = false
        )
    }

    companion object {
        fun defaultExpressions(): List<CharxExpressionAsset> = listOf(
            CharxExpressionAsset(emotion = "neutral", triggerCue = "Default composed demeanor", accentHex = "#38BDF8"),
            CharxExpressionAsset(emotion = "smirk", triggerCue = "Gaining leverage, teasing, or knowing a secret", accentHex = "#A855F7"),
            CharxExpressionAsset(emotion = "joy", triggerCue = "Genuine warmth, triumph, or shared camaraderie", accentHex = "#10B981"),
            CharxExpressionAsset(emotion = "anger", triggerCue = "Betrayal, crossed boundaries, or combat threat", accentHex = "#F43F5E"),
            CharxExpressionAsset(emotion = "sorrow", triggerCue = "Recalling lost memories or heavy sacrifice", accentHex = "#6366F1")
        )

        fun defaultAssetDescriptors(expressions: List<CharxExpressionAsset>): List<CharxAssetDescriptor> {
            val base = mutableListOf(
                CharxAssetDescriptor(
                    type = "icon",
                    uri = "embeded://assets/icon/images/main.png",
                    name = "main",
                    ext = "png"
                ),
                CharxAssetDescriptor(
                    type = "background",
                    uri = "embeded://assets/background/images/sanctum.png",
                    name = "main",
                    ext = "png"
                )
            )
            expressions.forEach { expr ->
                val clean = expr.emotion.lowercase().replace(" ", "_")
                base.add(
                    CharxAssetDescriptor(
                        type = "emotion",
                        uri = "embeded://assets/emotion/images/$clean.png",
                        name = clean,
                        ext = "png"
                    )
                )
            }
            return base
        }

        /**
         * Promotes a CHARACTER Lorebook entry into a full CHARX / Character Card V3 + Lumiverse Persona.
         */
        fun fromLoreEntry(entry: STEntry, universeName: String, universeGenre: String): CharacterCardV2 {
            val cleanName = entry.comment
                .substringBefore("—")
                .substringBefore("-")
                .substringBefore("(")
                .trim()
                .ifBlank { entry.key.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "New Character" }

            fun extractBracketField(field: String): String? {
                val regex = Regex("""$field\(([^)]+)\)""", RegexOption.IGNORE_CASE)
                return regex.find(entry.content)?.groupValues?.getOrNull(1)?.trim()
            }

            val role = extractBracketField("role") ?: "Notable Figure of $universeName"
            val pers = extractBracketField("personality") ?: "Complex, observant, and shaped by the tensions of $universeName."
            val bg = extractBracketField("background") ?: entry.content
            val speech = extractBracketField("speech_style") ?: "Measured, evocative, and grounded in local idiom."
            val secret = extractBracketField("secret") ?: "Harbors unspoken loyalties tied to the central conflict."
            val faction = extractBracketField("faction") ?: entry.keysecondary.firstOrNull().orEmpty()
            val alias = extractBracketField("alias") ?: cleanName.split(" ").firstOrNull().orEmpty()

            return CharacterCardV2(
                name = cleanName,
                nickname = alias,
                roleTitle = role,
                factionAffiliation = faction,
                description = "{{char}} ($cleanName) is a $role within $universeName.\n\nLore & Background:\n$bg",
                personality = pers,
                scenario = "{{user}} encounters {{char}} amidst the unfolding tensions of $universeName ($universeGenre).",
                firstMes = "*The ambient tension of $universeName hangs heavy in the air as $cleanName turns toward you, gaze sharp and appraising.*\n\n\"You're either remarkably bold or remarkably lost to walk this far unannounced,\" *$cleanName says, voice carrying a distinct $speech cadence.* \"Speak quickly—who sent you?\"",
                mesExample = "<START>\n{{user}}: \"Tell me what really happens behind closed doors here.\"\n{{char}}: *$cleanName pauses, fingers tracing the edge of an old emblem before answering in a low, deliberate tone.*\n\"Truth is a currency with a steep exchange rate in $universeName. If you want answers, show me what you're willing to wager first.\"",
                creatorNotes = "CHARX / CCV3 Bundle synced from Lorebook Entry UID #${entry.uid}. Linked keys: ${entry.key.joinToString(", ")}.",
                systemPrompt = "Write {{char}}'s responses staying strictly true to the canon rules of $universeName. Maintain {{char}}'s speech style ($speech) and hidden agenda ($secret).",
                postHistoryInstructions = "[Maintain atmospheric sensory details, update Lumiverse expression cues, and honor active Lorebook triggers.]",
                alternateGreetings = listOf(
                    "*Rain streaks the leaded glass as $cleanName studies a spread of dispatches on the table, barely glancing up as the door clicks shut behind you.*\n\n\"Close the latch. If the hounds caught your trail, we have less than five minutes.\""
                ),
                groupOnlyGreetings = listOf(
                    "*$cleanName steps into the war room, addressing the gathered party with a quiet, commanding nod.*\n\n\"Now that everyone is assembled, let's lay our cards on the table.\""
                ),
                tags = (listOf("charx", "lumiverse", universeGenre.lowercase(), "character", role.lowercase()) + entry.key).distinct().take(8),
                linkedLoreEntryUid = entry.uid,
                archetype = role,
                voiceCadence = speech,
                hiddenAgenda = secret,
                lumiverseModules = LumiverseModulesConfig(
                    moduleName = "$cleanName — Lumiverse Module",
                    systemExtensionScript = "[Lumiverse Persona Extension for $cleanName: Enforce speech cadence ($speech) and secret agenda ($secret). Match emotion sprites dynamically.]"
                )
            )
        }
    }
}
