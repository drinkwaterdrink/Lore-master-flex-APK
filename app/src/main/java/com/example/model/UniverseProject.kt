package com.example.model

import java.util.UUID

data class UniverseProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Crimson Bastion",
    val description: String = "",
    val genre: String = "Arcane Clockwork Noir",
    val tone: String = "Atmospheric, Conspiratorial, Morally Grey",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lorebook: STLorebookRoot = STLorebookRoot(name = name, description = description),
    val characterCards: List<CharacterCardV2> = emptyList(),
    val wizardState: PremiseWizardState = PremiseWizardState(),
    val forgeConfig: ForgeConfig = ForgeConfig(),
    val dnaAnalysis: WorldDnaAnalysis? = null,
    val activeBlueprint: ForgeBlueprint? = null
)

data class EventOutcomeItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val content: String,
    val weight: Int
)

enum class EventGroupPreset(
    val title: String,
    val groupName: String,
    val triggerKey: String,
    val stickyDuration: Int,
    val defaultOutcomes: List<EventOutcomeItem>
) {
    COMBAT_ROLL(
        title = "Combat & Threat Roll",
        groupName = "event_combat_roll",
        triggerKey = "draw weapon",
        stickyDuration = 5,
        defaultOutcomes = listOf(
            EventOutcomeItem(label = "De-escalation (30%)", content = "{{char}} will instantly notice {{user}}'s stance and raise a hand to signal a tense parley before blades clash.", weight = 30),
            EventOutcomeItem(label = "Tactical Standoff (40%)", content = "[EVENT] will instantly lock the scene in a high-stakes standoff; bystanders scatter and cover becomes critical.", weight = 40),
            EventOutcomeItem(label = "Ambush Complication (20%)", content = "[EVENT] will instantly introduce a third-party patrol or environmental hazard interrupting the confrontation.", weight = 20),
            EventOutcomeItem(label = "Lethal Escalation (10%)", content = "{{char}} will instantly strike first with ruthless precision, exploiting the nearest terrain advantage.", weight = 10)
        )
    ),
    NPC_REACTION(
        title = "NPC Disposition Roll",
        groupName = "event_npc_reaction",
        triggerKey = "ask around",
        stickyDuration = 4,
        defaultOutcomes = listOf(
            EventOutcomeItem(label = "Forthcoming Informant (30%)", content = "{{char}} will instantly lower their voice and share a verifiable rumor in exchange for a minor favor.", weight = 30),
            EventOutcomeItem(label = "Guarded & Transactional (40%)", content = "{{char}} will instantly offer half-truths while testing {{user}}'s faction loyalties.", weight = 40),
            EventOutcomeItem(label = "Suspicious Evasion (20%)", content = "{{char}} will instantly deflect the inquiry and subtly signal an associate nearby.", weight = 20),
            EventOutcomeItem(label = "Hostile Misdirection (10%)", content = "{{char}} will instantly feed {{user}} a planted trap lead designed to draw them into danger.", weight = 10)
        )
    ),
    WEATHER_SHIFT(
        title = "Atmospheric Weather Shift",
        groupName = "event_weather_shift",
        triggerKey = "step outside",
        stickyDuration = 6,
        defaultOutcomes = listOf(
            EventOutcomeItem(label = "Amber Lantern Haze (30%)", content = "[EVENT] will instantly drape the district in a dry amber mist that carries distant clock-tower chimes.", weight = 30),
            EventOutcomeItem(label = "Cold Basalt Downpour (35%)", content = "[EVENT] will instantly unleash a driving rain that drowns out footsteps and slicks the cobblestones.", weight = 35),
            EventOutcomeItem(label = "Aether-Static Storm (20%)", content = "[EVENT] will instantly charge the air with violet static; brass instruments hum and hair stands on end.", weight = 20),
            EventOutcomeItem(label = "Suffocating Ash Fog (15%)", content = "[EVENT] will instantly roll a dense, metallic fog across the street, reducing visibility to ten paces.", weight = 15)
        )
    ),
    RANDOM_ENCOUNTER(
        title = "Street & Corridor Encounter",
        groupName = "event_street_encounter",
        triggerKey = "travel",
        stickyDuration = 6,
        defaultOutcomes = listOf(
            EventOutcomeItem(label = "Quiet Passage (35%)", content = "[EVENT] will instantly grant an uninterrupted transit marked only by ambient world details.", weight = 35),
            EventOutcomeItem(label = "Contraband Peddler (25%)", content = "[EVENT] will instantly introduce a nervous courier offering a sealed cipher or black-market relic.", weight = 25),
            EventOutcomeItem(label = "Checkpoint Inspection (25%)", content = "[EVENT] will instantly block the route with an inquisitorial patrol demanding transit seals.", weight = 25),
            EventOutcomeItem(label = "Arcane Anomaly (15%)", content = "[EVENT] will instantly warp local time or gravity for a brief, disorienting moment.", weight = 15)
        )
    )
}
