package com.example.domain

import com.example.model.CanonIssue
import com.example.model.CharacterCardV2
import com.example.model.CrossRefMention
import com.example.model.EntryTemplateType
import com.example.model.IssueCategory
import com.example.model.IssueSeverity
import com.example.model.LoreKeywordValidator
import com.example.model.STEntry
import com.example.model.UniverseProject

object CrossRefAndAuditEngine {

    /**
     * Scans every Lorebook Entry and Character Card in the Universe to detect entity cross-references,
     * unlinked keyword opportunities, and uncreated proper-noun entities.
     */
    fun scanCrossReferences(project: UniverseProject): List<CrossRefMention> {
        val mentions = mutableListOf<CrossRefMention>()
        val entries = project.lorebook.entries
        val cards = project.characterCards

        // Build canonical target index from entries and character cards
        data class TargetNode(
            val idOrUid: String,
            val title: String,
            val type: String,
            val searchTerms: List<String>
        )

        val targets = mutableListOf<TargetNode>()
        entries.forEach { entry ->
            val cleanTitle = entry.comment
                .substringBefore("—")
                .substringBefore(":")
                .replace("RULE:", "")
                .replace("EVENT:", "")
                .replace("TRIGGER:", "")
                .trim()
            val terms = (listOf(cleanTitle) + entry.key)
                .map { it.trim() }
                .filter { it.length >= 4 && it.lowercase() !in LoreKeywordValidator.BROAD_KEYS }
                .distinctBy { it.lowercase() }
            if (terms.isNotEmpty()) {
                targets.add(TargetNode(entry.uid.toString(), entry.comment, "LORE_ENTRY", terms))
            }
        }

        cards.forEach { card ->
            val terms = listOf(card.name, card.factionAffiliation)
                .map { it.trim() }
                .filter { it.length >= 4 }
                .distinctBy { it.lowercase() }
            if (terms.isNotEmpty()) {
                targets.add(TargetNode(card.id, "${card.name} (Card)", "CHARACTER_CARD", terms))
            }
        }

        // 1. Scan Lorebook Entries -> Targets
        entries.forEach { sourceEntry ->
            val text = sourceEntry.content
            val existingKeysLower = (sourceEntry.key + sourceEntry.keysecondary).map { it.lowercase() }.toSet()

            targets.forEach { target ->
                if (target.type == "LORE_ENTRY" && target.idOrUid == sourceEntry.uid.toString()) return@forEach
                if (target.type == "CHARACTER_CARD" && cards.find { it.id == target.idOrUid }?.linkedLoreEntryUid == sourceEntry.uid) return@forEach

                val matchedTerm = target.searchTerms.firstOrNull { term ->
                    containsWholePhrase(text, term)
                }
                if (matchedTerm != null) {
                    val isLinked = matchedTerm.lowercase() in existingKeysLower ||
                        target.searchTerms.any { it.lowercase() in existingKeysLower }
                    mentions.add(
                        CrossRefMention(
                            sourceTitle = sourceEntry.comment.ifBlank { "Entry #${sourceEntry.uid}" },
                            sourceType = "LORE_ENTRY",
                            sourceUidOrId = sourceEntry.uid.toString(),
                            targetTitle = target.title,
                            targetType = target.type,
                            targetUidOrId = target.idOrUid,
                            matchedPhrase = matchedTerm,
                            contextSnippet = extractSnippet(text, matchedTerm),
                            isKeywordAlreadyLinked = isLinked
                        )
                    )
                }
            }
        }

        // 2. Scan Character Cards -> Lorebook Entries
        cards.forEach { card ->
            val combinedCardText = listOf(
                card.description,
                card.personality,
                card.scenario,
                card.firstMes,
                card.mesExample
            ).joinToString("\n")

            entries.forEach { targetEntry ->
                if (card.linkedLoreEntryUid == targetEntry.uid) return@forEach
                val searchTerms = targetEntry.key.filter { it.length >= 4 }
                val matchedTerm = searchTerms.firstOrNull { term ->
                    containsWholePhrase(combinedCardText, term)
                }
                if (matchedTerm != null) {
                    val isLinked = card.tags.any { it.equals(matchedTerm, ignoreCase = true) } ||
                        targetEntry.keysecondary.any { it.equals(card.name, ignoreCase = true) }
                    mentions.add(
                        CrossRefMention(
                            sourceTitle = "${card.name} (Character Card)",
                            sourceType = "CHARACTER_CARD",
                            sourceUidOrId = card.id,
                            targetTitle = targetEntry.comment,
                            targetType = "LORE_ENTRY",
                            targetUidOrId = targetEntry.uid.toString(),
                            matchedPhrase = matchedTerm,
                            contextSnippet = extractSnippet(combinedCardText, matchedTerm),
                            isKeywordAlreadyLinked = isLinked
                        )
                    )
                }
            }
        }

        // 3. Detect Named Entities in brackets (e.g., "House Kaelen", "Arch-Horologist Malakor", "Whispering Foundry")
        // that do not yet have their own dedicated Lorebook Entry!
        val allKnownTermsLower = entries.flatMap { it.key + listOf(it.comment) }.map { it.lowercase() }
        val properNounRegex = Regex("""\b(?:The\s+)?([A-Z][a-z]{3,}\s+[A-Z][a-z]{3,})\b""")
        val ignoredPhrases = setOf("Great Pendulum", "High Spire", "Sunken Ward", "Obsidian Synod", "Half Time", "Eclipse Bell", "Chrono Tithe")

        entries.forEach { entry ->
            properNounRegex.findAll(entry.content).forEach { match ->
                val candidate = match.groupValues[1].trim()
                if (candidate !in ignoredPhrases &&
                    allKnownTermsLower.none { it.contains(candidate.lowercase()) } &&
                    mentions.none { it.matchedPhrase.equals(candidate, ignoreCase = true) }
                ) {
                    mentions.add(
                        CrossRefMention(
                            sourceTitle = entry.comment,
                            sourceType = "LORE_ENTRY",
                            sourceUidOrId = entry.uid.toString(),
                            targetTitle = "$candidate (Uncreated Lore Stub)",
                            targetType = "UNCREATED_ENTITY",
                            targetUidOrId = candidate,
                            matchedPhrase = candidate,
                            contextSnippet = extractSnippet(entry.content, candidate),
                            isKeywordAlreadyLinked = false
                        )
                    )
                }
            }
        }

        return mentions.sortedBy { it.isKeywordAlreadyLinked }
    }

    /**
     * Links a single CrossRefMention by adding the matched phrase to secondary keys or tags,
     * or spawning a new Lorebook stub entry if the target is an UNCREATED_ENTITY.
     */
    fun applyCrossRefMention(project: UniverseProject, mention: CrossRefMention): UniverseProject {
        val entries = project.lorebook.entries.toMutableList()
        val cards = project.characterCards.toMutableList()

        if (mention.targetType == "UNCREATED_ENTITY") {
            val nextUid = (entries.maxOfOrNull { it.uid } ?: 100) + 1
            val phrase = mention.matchedPhrase
            val newStub = STEntry(
                uid = nextUid,
                comment = phrase,
                key = listOf(phrase, phrase.substringAfterLast(" ")).distinct(),
                keysecondary = listOf(mention.sourceTitle.substringBefore("—").trim()),
                content = "[ entity($phrase); origin(Referenced in ${mention.sourceTitle}); significance(Key figure, faction, or landmark within ${project.name}); details(${mention.contextSnippet}); ]",
                group = "concepts",
                position = 0,
                order = 115,
                preventRecursion = true,
                displayIndex = entries.size
            )
            entries.add(newStub)
            return project.copy(lorebook = project.lorebook.copy(entries = entries))
        }

        if (mention.sourceType == "LORE_ENTRY") {
            val uid = mention.sourceUidOrId.toIntOrNull()
            val idx = entries.indexOfFirst { it.uid == uid }
            if (idx >= 0) {
                val e = entries[idx]
                val updatedSecondary = (e.keysecondary + mention.matchedPhrase).distinct()
                entries[idx] = e.copy(keysecondary = updatedSecondary)
            }
        } else if (mention.sourceType == "CHARACTER_CARD") {
            val cIdx = cards.indexOfFirst { it.id == mention.sourceUidOrId }
            val targetUid = mention.targetUidOrId.toIntOrNull()
            val eIdx = entries.indexOfFirst { it.uid == targetUid }
            if (cIdx >= 0) {
                val c = cards[cIdx]
                cards[cIdx] = c.copy(tags = (c.tags + mention.matchedPhrase.lowercase()).distinct())
                if (eIdx >= 0) {
                    val e = entries[eIdx]
                    entries[eIdx] = e.copy(keysecondary = (e.keysecondary + c.name).distinct())
                }
            }
        }
        return project.copy(
            lorebook = project.lorebook.copy(entries = entries),
            characterCards = cards
        )
    }

    /**
     * Runs the Real-Time Canon Auditor (Lorebook Linter 2.0 + Cross-Card Consistency + Recursion Loop Detection).
     */
    fun runCanonAudit(project: UniverseProject): List<CanonIssue> {
        val issues = mutableListOf<CanonIssue>()
        val entries = project.lorebook.entries
        val cards = project.characterCards

        // 1. Check Token Budget Overflow
        val constantTokens = entries.filter { it.constant && !it.disable }.sumOf { it.estimateTokens() }
        if (constantTokens > project.lorebook.tokenBudget * 0.65) {
            issues.add(
                CanonIssue(
                    severity = IssueSeverity.MAJOR,
                    category = IssueCategory.BUDGET_OVERFLOW,
                    targetTitle = "Lorebook Token Budget",
                    description = "Constant entries consume $constantTokens tokens (${(constantTokens * 100) / maxOf(1, project.lorebook.tokenBudget)}% of ${project.lorebook.tokenBudget} budget), leaving limited room for dynamic triggers.",
                    proposedFixSummary = "Increase Lorebook Token Budget to ${maxOf(3500, constantTokens * 2)} or convert non-essential constants to keyword-triggered entries.",
                    autoFixAction = "INCREASE_BUDGET"
                )
            )
        }

        // 2. Per-entry Heuristic & Structural Checks
        entries.forEach { entry ->
            val title = entry.comment.ifBlank { "Entry #${entry.uid}" }
            val detectedType = entry.classifyType()

            if (entry.content.isBlank()) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.CRITICAL,
                        category = IssueCategory.TOO_SHORT,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Entry content is completely empty and will inject zero context.",
                        proposedFixSummary = "Synthesize structured bracketed lore for '$title'.",
                        autoFixAction = "EXPAND_CONTENT"
                    )
                )
            } else if (entry.content.length < 110) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.TOO_SHORT,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Entry has sparse content (${entry.content.length} chars, ~${entry.estimateTokens()} tokens).",
                        proposedFixSummary = "Enrich '$title' with sensory hooks, faction ties, and bracketed attributes.",
                        autoFixAction = "EXPAND_CONTENT"
                    )
                )
            }

            if (!entry.constant && entry.key.isEmpty()) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.CRITICAL,
                        category = IssueCategory.NO_KEYWORDS,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Non-constant entry has no primary activation keywords and can never trigger.",
                        proposedFixSummary = "Extract high-signal activation keys from '$title' and its content.",
                        autoFixAction = "FIX_KEYWORDS"
                    )
                )
            }

            val broadKeys = LoreKeywordValidator.findBroadKeywords(entry.key)
            if (broadKeys.isNotEmpty()) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MAJOR,
                        category = IssueCategory.BROAD_KEY,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Uses overly generic trigger keys (${broadKeys.joinToString(", ")}) that cause false-positive context pollution.",
                        proposedFixSummary = "Remove generic keys and replace with specific entity/domain keys.",
                        autoFixAction = "REMOVE_BROAD_KEYS"
                    )
                )
            }

            val trimmed = entry.content.trim()
            if (trimmed.isNotEmpty() && (trimmed.endsWith(",") || trimmed.endsWith("and") || trimmed.endsWith("the") || (trimmed.contains("[") && !trimmed.contains("]")))) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.CRITICAL,
                        category = IssueCategory.TRUNCATED,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Entry prose appears truncated or is missing a closing bracket ']'.",
                        proposedFixSummary = "Complete dangling sentence and close bracketed block.",
                        autoFixAction = "REPAIR_TRUNCATION"
                    )
                )
            }

            if (entry.isSectionDivider()) {
                return@forEach
            }

            if (!entry.hasFourDashDelimiter() && entry.content.isNotBlank()) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.TOKEN_HYGIENE,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Missing '----' delimiter on line 1. In Lumiverse & SillyTavern, leading '----' creates a hard token boundary to prevent prompt bleeding.",
                        proposedFixSummary = "Prepend '----' delimiter boundary at the top of '$title'.",
                        autoFixAction = "INSERT_FOUR_DASH_DELIMITER"
                    )
                )
            }

            if (detectedType == EntryTemplateType.CHARACTER && entry.estimateTokens() > 320) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MAJOR,
                        category = IssueCategory.OVERSIZED_NPC,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Oversized NPC entry (~${entry.estimateTokens()} tokens). Single bloated NPC entries waste context tokens on passing mentions and hallucinate physical presence.",
                        proposedFixSummary = "Split into 3-Part Character architecture (Identity ~50t, Presence ~200t with sticky 3, Secrets).",
                        autoFixAction = "SPLIT_NPC_3_PART"
                    )
                )
            }

            if (entry.estimateTokens() > 220 && !entry.excludeGreeting && !entry.constant) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.GREETING_OVERFLOW,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "Heavy entry (~${entry.estimateTokens()} tokens) without 'excludeGreeting' can bloat context on the initial greeting turn.",
                        proposedFixSummary = "Enable excludeGreeting = true to keep the opening greeting turn lean.",
                        autoFixAction = "ENABLE_EXCLUDE_GREETING"
                    )
                )
            }

            if (detectedType == EntryTemplateType.WORLD_RULE && entry.position != 4) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.BAD_POSITION,
                        targetTitle = title,
                        entryUid = entry.uid,
                        description = "World Rule entry is set to Position ${entry.position} instead of Position 4 (Author Note depth).",
                        proposedFixSummary = "Move '$title' to Position 4, Order 290, Constant = true.",
                        autoFixAction = "FIX_POSITION"
                    )
                )
            }
        }

        // 3. Circular Recursion Detection (Entry A triggers Entry B AND Entry B triggers Entry A while neither prevents recursion)
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                if (!a.preventRecursion && !b.preventRecursion && !b.excludeRecursion && !a.excludeRecursion) {
                    val aMentionsB = b.key.any { k -> k.length >= 4 && containsWholePhrase(a.content, k) }
                    val bMentionsA = a.key.any { k -> k.length >= 4 && containsWholePhrase(b.content, k) }
                    if (aMentionsB && bMentionsA) {
                        issues.add(
                            CanonIssue(
                                severity = IssueSeverity.MAJOR,
                                category = IssueCategory.CIRCULAR_RECURSION,
                                targetTitle = "${a.comment} ↔ ${b.comment}",
                                entryUid = b.uid,
                                description = "Mutual recursion loop: '${a.comment}' and '${b.comment}' trigger each other's keywords with preventRecursion disabled.",
                                proposedFixSummary = "Enable preventRecursion = true on '${b.comment}' to break the cycle.",
                                autoFixAction = "BREAK_RECURSION_LOOP"
                            )
                        )
                    }
                }
            }
        }

        // 4. Character Card <-> Lorebook Desync & Canon Contradiction Audit
        cards.forEach { card ->
            val linkedEntry = entries.find { it.uid == card.linkedLoreEntryUid }
                ?: entries.find { it.comment.contains(card.name, ignoreCase = true) }

            if (linkedEntry == null) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MAJOR,
                        category = IssueCategory.CARD_LORE_MISMATCH,
                        targetTitle = "${card.name} (Character Card)",
                        cardId = card.id,
                        description = "Character Card '${card.name}' has no corresponding Lorebook Entry, so other characters won't recognize them in shared scenes.",
                        proposedFixSummary = "Generate and link a synchronized CHARACTER Lorebook Entry for '${card.name}'.",
                        autoFixAction = "SYNC_CARD_TO_LORE"
                    )
                )
            } else if (card.linkedLoreEntryUid != linkedEntry.uid) {
                issues.add(
                    CanonIssue(
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.CARD_LORE_MISMATCH,
                        targetTitle = "${card.name} ↔ ${linkedEntry.comment}",
                        cardId = card.id,
                        entryUid = linkedEntry.uid,
                        description = "Character Card '${card.name}' matches Lorebook Entry #${linkedEntry.uid} by name, but their bidirectional link ID is not bound.",
                        proposedFixSummary = "Bind Character Card '${card.name}' to Lorebook Entry #${linkedEntry.uid}.",
                        autoFixAction = "SYNC_CARD_TO_LORE"
                    )
                )
            }
        }

        // 5. Semantic Rule Contradiction Check (e.g., Resurrection / Forbidden Terms against World Rules)
        val worldRules = entries.filter { it.classifyType() == EntryTemplateType.WORLD_RULE }
        val forbidsResurrection = worldRules.any { it.content.contains("no resurrection", ignoreCase = true) } ||
            project.wizardState.hardRules.contains("no resurrection", ignoreCase = true)

        if (forbidsResurrection) {
            entries.filter { it.classifyType() != EntryTemplateType.WORLD_RULE }.forEach { entry ->
                if (entry.content.contains("resurrected", ignoreCase = true) || entry.content.contains("brought back from the dead", ignoreCase = true)) {
                    issues.add(
                        CanonIssue(
                            severity = IssueSeverity.CRITICAL,
                            category = IssueCategory.CONTRADICTION,
                            targetTitle = entry.comment,
                            entryUid = entry.uid,
                            description = "Contradicts World Rule ('No resurrection magic exists'): entry mentions resurrection.",
                            proposedFixSummary = "Reframe as memory-echo preservation or near-death chronal stasis to respect canon law.",
                            autoFixAction = "RESOLVE_CONTRADICTION"
                        )
                    )
                }
            }
        }

        return issues.sortedBy { it.severity.ordinal }
    }

    /**
     * Applies a one-tap repair for any CanonIssue.
     */
    fun repairCanonIssue(project: UniverseProject, issue: CanonIssue): UniverseProject {
        val entries = project.lorebook.entries.toMutableList()
        val cards = project.characterCards.toMutableList()

        when (issue.autoFixAction) {
            "INCREASE_BUDGET" -> {
                val constantTokens = entries.filter { it.constant && !it.disable }.sumOf { it.estimateTokens() }
                val newBudget = maxOf(3500, constantTokens * 2)
                return project.copy(
                    lorebook = project.lorebook.copy(
                        tokenBudget = newBudget,
                        settings = project.lorebook.settings.copy(tokenBudget = newBudget)
                    )
                )
            }
            "EXPAND_CONTENT" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    val type = e.classifyType()
                    val expanded = if (e.content.isBlank()) {
                        "[ title(${e.comment}); category(${type.label}); atmosphere(Grounded in ${project.name}'s ${project.tone} tone); significance(Interconnected pillar of ${project.genre}); hooks(Triggers cross-referenced tension when ${e.key.firstOrNull() ?: e.comment} enters the scene); ]"
                    } else {
                        "${e.content.trim().removeSuffix("]")} ; narrative_hook(Directly impacts ${project.name}'s central conflict and cross-links with active factions); ]"
                    }
                    entries[idx] = e.copy(content = expanded)
                }
            }
            "FIX_KEYWORDS" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    val extracted = e.comment
                        .split("—", "-", ":", "(", ")")
                        .map { it.trim() }
                        .filter { it.length >= 3 && it.lowercase() !in LoreKeywordValidator.BROAD_KEYS }
                        .ifEmpty { listOf("lore_${e.uid}") }
                    entries[idx] = e.copy(key = extracted.distinct())
                }
            }
            "REMOVE_BROAD_KEYS" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    val filtered = e.key.filter { it.trim().lowercase() !in LoreKeywordValidator.BROAD_KEYS && it.trim().length > 2 }
                        .ifEmpty { listOf(e.comment.substringBefore("—").trim().ifBlank { "entry_${e.uid}" }) }
                    entries[idx] = e.copy(key = filtered)
                }
            }
            "REPAIR_TRUNCATION" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    var fixed = e.content.trim().trimEnd(',', ';')
                    if (fixed.endsWith("and") || fixed.endsWith("the")) {
                        fixed = "$fixed canonical forces of ${project.name}."
                    }
                    if (fixed.contains("[") && !fixed.endsWith("]")) {
                        fixed = "$fixed; ]"
                    }
                    entries[idx] = e.copy(content = fixed)
                }
            }
            "FIX_POSITION" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    entries[idx] = e.applyTemplateDefaults(e.classifyType())
                }
            }
            "BREAK_RECURSION_LOOP" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    entries[idx] = entries[idx].copy(preventRecursion = true)
                }
            }
            "SYNC_CARD_TO_LORE" -> {
                val cIdx = cards.indexOfFirst { it.id == issue.cardId }
                if (cIdx >= 0) {
                    val card = cards[cIdx]
                    val existingIdx = entries.indexOfFirst { it.uid == card.linkedLoreEntryUid || it.comment.contains(card.name, ignoreCase = true) }
                    if (existingIdx >= 0) {
                        val existing = entries[existingIdx]
                        entries[existingIdx] = card.toLoreEntry(existing.uid, existing)
                        cards[cIdx] = card.copy(linkedLoreEntryUid = existing.uid)
                    } else {
                        val newUid = (entries.maxOfOrNull { it.uid } ?: 100) + 1
                        val newEntry = card.toLoreEntry(newUid).copy(displayIndex = entries.size)
                        entries.add(newEntry)
                        cards[cIdx] = card.copy(linkedLoreEntryUid = newUid)
                    }
                }
            }
            "RESOLVE_CONTRADICTION" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    val patched = e.content
                        .replace(Regex("resurrected", RegexOption.IGNORE_CASE), "preserved as a chronal memory-echo")
                        .replace(Regex("brought back from the dead", RegexOption.IGNORE_CASE), "sustained in half-time stasis")
                    entries[idx] = e.copy(content = patched)
                }
            }
            "INSERT_FOUR_DASH_DELIMITER" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val e = entries[idx]
                    val fixed = if (e.content.trimStart().startsWith("----")) e.content else "----\n${e.content.trimStart()}"
                    entries[idx] = e.copy(content = fixed)
                }
            }
            "ENABLE_EXCLUDE_GREETING" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    entries[idx] = entries[idx].copy(excludeGreeting = true)
                }
            }
            "SPLIT_NPC_3_PART" -> {
                val idx = entries.indexOfFirst { it.uid == issue.entryUid }
                if (idx >= 0) {
                    val original = entries[idx]
                    val npcName = original.comment
                        .substringBefore("—")
                        .substringBefore(":")
                        .trim()
                        .ifBlank { "NPC" }

                    // Part 1: Identity stub (~50 tokens)
                    val part1 = original.copy(
                        comment = "$npcName — Part 1: Identity",
                        key = listOf(npcName),
                        keysecondary = emptyList(),
                        content = "----\nName: $npcName\nType: Character Identity Stub\nOverview: Canonical persona in ${project.name}. Recognized by name across scenes without forcing physical room presence.",
                        position = 1,
                        order = 35,
                        sticky = 0,
                        preventRecursion = true
                    )
                    entries[idx] = part1

                    var maxUid = entries.maxOfOrNull { it.uid } ?: 100

                    // Part 2: Presence (~200 tokens) with sticky: 3 on action keys
                    val part2 = STEntry(
                        uid = ++maxUid,
                        comment = "$npcName — Part 2: Presence & Mannerisms",
                        key = listOf("$npcName says", "$npcName looks", "$npcName walks", "$npcName enters", "$npcName stands"),
                        keysecondary = emptyList(),
                        content = "----\nName: $npcName (Active Physical Presence)\nDefinition: Sensory cues, voice cadence, and demeanor when physically in the room.\nSensory Anchors: Distinct posture, signature vocal habits, and responsive expressions.\nAtmosphere: Active scene participant.",
                        position = 1,
                        order = 75,
                        sticky = 3,
                        preventRecursion = true,
                        group = "cast_presence",
                        displayIndex = entries.size
                    )
                    entries.add(part2)

                    // Part 3: Secrets / Deep Lore with selective logic
                    val part3 = STEntry(
                        uid = ++maxUid,
                        comment = "$npcName — Part 3: Secrets & Hidden Lore",
                        key = listOf(npcName),
                        keysecondary = listOf("secret", "truth", "past", "betrayal", "hidden", "lore"),
                        selective = true,
                        selectiveLogic = 2, // AND ANY
                        content = "----\nName: $npcName (Hidden Agendas & Lore)\nDefinition: Concealed motives, vulnerabilities, and classified backstory revelations.\nActivation Rule: Only surfaces when $npcName is actively interrogated or paired with conspiratorial topics.",
                        position = 1,
                        order = 85,
                        sticky = 0,
                        preventRecursion = true,
                        group = "cast_secrets",
                        displayIndex = entries.size
                    )
                    entries.add(part3)
                }
            }
        }

        return project.copy(
            lorebook = project.lorebook.copy(entries = entries),
            characterCards = cards
        )
    }

    private fun containsWholePhrase(text: String, phrase: String): Boolean {
        if (phrase.isBlank() || text.isBlank()) return false
        val escaped = Regex.escape(phrase.trim())
        return Regex("""\b$escaped\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun extractSnippet(text: String, term: String): String {
        val idx = text.indexOf(term, ignoreCase = true)
        if (idx < 0) return text.take(100)
        val start = maxOf(0, idx - 36)
        val end = minOf(text.length, idx + term.length + 54)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix${text.substring(start, end).replace("\n", " ").trim()}$suffix"
    }
}
