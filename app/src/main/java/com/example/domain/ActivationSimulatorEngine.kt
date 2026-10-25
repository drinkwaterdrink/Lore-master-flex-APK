package com.example.domain

import com.example.model.ActivatedEntryStep
import com.example.model.STEntry
import com.example.model.SimulationResult
import com.example.model.UniverseProject

object ActivationSimulatorEngine {

    /**
     * Simulates SillyTavern V2 & Lumiverse keyword scanning, selective logic gates,
     * group override state machines, and recursive activation up to [maxRecursionDepth].
     */
    fun simulateActivation(
        project: UniverseProject,
        sampleInput: String,
        maxRecursionDepth: Int = 3,
        isGreetingTurn: Boolean = false
    ): SimulationResult {
        val entries = project.lorebook.entries.filter { !it.disable && !it.isSectionDivider() }
        val constants = entries.filter { it.constant }.sortedWith(compareBy({ it.position }, { -it.order }))
        val conditionalPool = entries.filter { !it.constant }

        val activatedUids = constants.map { it.uid }.toMutableSet()
        val steps = mutableListOf<ActivatedEntryStep>()
        var runningTriggeredTokens = 0

        // Depth 0: Direct scan against sampleInput
        val depthZeroTriggered = mutableListOf<STEntry>()
        for (entry in conditionalPool) {
            if (isGreetingTurn && entry.excludeGreeting) continue
            if (entry.delayUntilRecursion) continue // Only triggers on recursive passes

            val match = findMatchingKeyword(entry, sampleInput, project.lorebook.settings.matchWholeWords)
            if (match != null) {
                activatedUids.add(entry.uid)
                runningTriggeredTokens += entry.estimateTokens()
                steps.add(
                    ActivatedEntryStep(
                        entry = entry,
                        depthLevel = 0,
                        matchedKeyword = match,
                        triggeredBySource = "Chat Input (Direct Match)",
                        cumulativeTokens = runningTriggeredTokens
                    )
                )
                depthZeroTriggered.add(entry)
            }
        }

        // Depth 1..maxRecursionDepth: Recursive scan if enabled
        if (project.lorebook.recursiveScanning && maxRecursionDepth > 0) {
            var currentWave = depthZeroTriggered.filter { !it.preventRecursion }
            for (depth in 1..maxRecursionDepth) {
                if (currentWave.isEmpty()) break
                val nextWave = mutableListOf<STEntry>()
                for (parent in currentWave) {
                    for (candidate in conditionalPool) {
                        if (candidate.uid in activatedUids || candidate.excludeRecursion) continue
                        if (isGreetingTurn && candidate.excludeGreeting) continue

                        val match = findMatchingKeyword(
                            candidate,
                            parent.content,
                            project.lorebook.settings.matchWholeWords
                        )
                        if (match != null) {
                            activatedUids.add(candidate.uid)
                            runningTriggeredTokens += candidate.estimateTokens()
                            steps.add(
                                ActivatedEntryStep(
                                    entry = candidate,
                                    depthLevel = depth,
                                    matchedKeyword = match,
                                    triggeredBySource = parent.comment.ifBlank { "Entry #${parent.uid}" },
                                    cumulativeTokens = runningTriggeredTokens
                                )
                            )
                            if (!candidate.preventRecursion) {
                                nextWave.add(candidate)
                            }
                        }
                    }
                }
                currentWave = nextWave
            }
        }

        // Lumiverse / ST Inclusion Group Filtering:
        // When multiple entries in the same group trigger, if any has groupOverride == true,
        // only the highest-order entry in that group is retained!
        val resolvedSteps = mutableListOf<ActivatedEntryStep>()
        val groupedCandidates = steps.groupBy { it.entry.group.trim().lowercase() }
        for ((groupKey, groupSteps) in groupedCandidates) {
            if (groupKey.isEmpty() || groupSteps.size == 1) {
                resolvedSteps.addAll(groupSteps)
            } else {
                val hasOverride = groupSteps.any { it.entry.groupOverride }
                if (hasOverride) {
                    // Highest order wins!
                    val winner = groupSteps.maxByOrNull { it.entry.order }
                    if (winner != null) resolvedSteps.add(winner)
                } else {
                    resolvedSteps.addAll(groupSteps)
                }
            }
        }

        // Sort by injection priority: Position ASC, then Order DESC
        val finalTriggeredSteps = resolvedSteps.sortedWith(compareBy({ it.entry.position }, { -it.entry.order }))
        val constantTokens = constants.sumOf { it.estimateTokens() }
        val activeLeadCard = project.characterCards.firstOrNull()
        val previewReply = buildSimulatedRoleplayPreview(project, activeLeadCard?.name ?: "Narrator", finalTriggeredSteps, constants)

        return SimulationResult(
            sampleInput = sampleInput,
            constantEntries = constants,
            activatedSteps = finalTriggeredSteps,
            totalConstantTokens = constantTokens,
            totalTriggeredTokens = finalTriggeredSteps.sumOf { it.entry.estimateTokens() },
            tokenBudget = project.lorebook.tokenBudget,
            simulatedRoleplayReply = previewReply
        )
    }

    private fun findMatchingKeyword(entry: STEntry, text: String, defaultMatchWholeWords: Boolean): String? {
        if (text.isBlank() || entry.key.isEmpty()) return null
        val matchWhole = entry.matchWholeWords ?: defaultMatchWholeWords
        val primaryMatch = entry.key.firstOrNull { key ->
            matchesKey(text, key, entry.caseSensitive, matchWhole)
        } ?: return null

        if (entry.selective && entry.keysecondary.isNotEmpty()) {
            val secondaryMatches = entry.keysecondary.map { sec ->
                matchesKey(text, sec, entry.caseSensitive, matchWhole)
            }
            val passesSelective = when (entry.selectiveLogic) {
                0 -> secondaryMatches.all { it } // AND ALL: All secondary keys must match
                1 -> secondaryMatches.none { it } // NOT ANY (NOT NONE): None of the secondary keys may match
                2 -> secondaryMatches.any { it } // AND ANY (OR ANY): At least one secondary key matches
                3 -> !secondaryMatches.all { it } // NOT ALL: Not all secondary keys match together
                else -> secondaryMatches.any { it }
            }
            if (!passesSelective) return null
        }
        return primaryMatch
    }

    private fun matchesKey(text: String, key: String, caseSensitive: Boolean, wholeWord: Boolean): Boolean {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return false
        return if (wholeWord) {
            val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex("""\b${Regex.escape(cleanKey)}\b""", opts).containsMatchIn(text)
        } else {
            text.contains(cleanKey, ignoreCase = !caseSensitive)
        }
    }

    private fun buildSimulatedRoleplayPreview(
        project: UniverseProject,
        personaName: String,
        triggered: List<ActivatedEntryStep>,
        constants: List<STEntry>
    ): String {
        if (triggered.isEmpty() && constants.isEmpty()) {
            return "No Lorebook entries were triggered by this message. Try mentioning keywords like ${project.lorebook.entries.flatMap { it.key }.take(4).joinToString(", ") { "\"$it\"" }}."
        }
        val activeTitles = (triggered.map { it.entry.comment } + constants.map { it.comment }).take(4)
        val dynamicDirective = triggered.firstOrNull { it.entry.content.contains("will instantly") }?.entry?.content
        return buildString {
            appendLine("[Context Injected into $personaName: ${activeTitles.joinToString(" • ")}]")
            if (dynamicDirective != null) {
                appendLine("⚡ Active Directive Fired: $dynamicDirective")
            }
            append("*Drawing directly upon the active world state of ${project.name}, $personaName responds with full canonical awareness of ")
            append(triggered.joinToString(", ") { "'${it.matchedKeyword}'" }.ifBlank { "the foundational world laws" })
            append(".*")
        }
    }
}
