# LoreMaster Flex — Complete UI/UX Redesign & Architecture Plan

This redesign transforms **LoreMaster Flex** from a dense single-scroll prototype into a sleek, intuitive, production-grade mobile studio inspired by **Linear, Obsidian, and Raycast**. It eliminates visual clutter through strict progressive disclosure, introduces a dedicated **Worlds Home** with **Start from Blank** and **Start from an AI Spark** creation flows, separates list browsing from full-screen focused editing, and incorporates the full **Canon Expander & Gap Finder** workflow from the original specification.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX changes directly address your feedback and Phase 1 selections:

- **Confirmed Decision 1 — Linear × Obsidian Dark Glass Aesthetic & Custom Canvas Animations**: Replaces the heavy medieval serif fonts and boxed clutter with a pitch-black and deep slate-glass design system (`#06070B` void canvas, `#0F111A` elevated slate cards, `#171A26` interactive surfaces, 1dp specular hairline borders, and soft radial light blooms). Uses clean geometric typography (*Plus Jakarta Sans* + *JetBrains Mono*) and fluid spring animations.
- **Confirmed Decision 2 — Dedicated "Worlds Home" with Blank & AI Spark Creation**: Adds a clear **Worlds Home** launchpad where you can switch worlds or tap **+ New World** to choose between:
  1. **Start from an Idea Spark (AI Wizard)**: Type a rough idea or sentence; the AI fleshes out the premise, world rules, factions, locations, and character cards step-by-step, which you can then customize anywhere in the app.
  2. **Start Completely Blank**: Enter a title and begin with an empty, zero-clutter workspace ready for manual world-building.
  3. **Import JSON / PNG Character Card**: Create or populate a world directly from existing SillyTavern files.
- **Confirmed Decision 3 — Clutter-Free Master-Detail Navigation (List → Focused Full-Screen Editor)**: Never stacks the entry list and the entry editor on the same page. The **Codex** displays a clean, collapsible-group list of Lorebook Entries or Character Cards; tapping any item opens a **Dedicated Full-Screen Editor** organized into clean sub-tabs (*Writing & Keys*, *Trigger & STV2 Config*, and *Cross-Links*).
- **Confirmed Decision 4 — Compact Animated Orbital Architect Icon**: Replaces the bulky text FAB and duplicate header button with a single `46.dp` **Animated Orbital Core Icon** in the bottom-right corner—rendered with a custom multi-ring `Canvas` shader (rotating gradient halo + breathing starlight core) that opens the context-aware **Architect Co-Pilot Sheet** on tap.

---

## 1. Overview & Core Concept

- **What It Does**: A streamlined mobile creative studio for authoring, expanding, auditing, and exporting **SillyTavern V2 Lorebooks** and **Character Cards V2 (`chara_card_v2` / Lumiverse)**.
- **Target Audience / Persona**: World-builders and AI roleplay creators who want a fast, modern, distraction-free mobile workflow that scales effortlessly from a 1-sentence idea spark or a blank canvas up to a 100+ entry interconnected universe.
- **Key Value**: Combines the clarity of a modern note/knowledge app (clean lists, focused writing view, instant search) with deep SillyTavern V2 power tools (recursive trigger simulation, Canon Expander gap analysis, automatic entity cross-referencing, and PNG card export).

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Worlds Home & "New World" Creation Flow**:
   - Tap the top-left **Worlds** switcher (or launch into the Worlds Home view) to see your universe cards with entry counts, character portraits, and token stats.
   - Tap **+ New World** to open a clean creation sheet with two primary modes:
     - **Blank World**: Enter your World Name, optional Genre/Tone, and tap **Create Blank World** to open an empty Codex immediately.
     - **Flesh Out an Idea (AI Spark)**: Type any raw concept (e.g., *"A floating bazaar strapped to the back of a migrating sky-whale where merchants trade bottled dreams"*) → Tap **Flesh Out with AI** to launch directly into the **Forge Wizard**, where the AI expands your spark into a full premise, World DNA, and customizable blueprint.
2. **Clean Codex Directory → Focused Full-Screen Editor**:
   - **Codex List View**: Switch between **Lorebook Entries** and **Character Cards** with a sleek pill toggle. Search entries, filter by collapsible group headers (`foundation`, `characters`, `locations`, `factions`, `objects`, `dynamics`), or toggle Bulk Edit / Event Group Creator.
   - **Full-Screen Entry Editor (Opened on Tap)**:
     - **Tab A — Content & Keywords**: Clean title input, auto-detected Template Type pill with 1-tap *Apply Defaults*, Primary & Secondary keyword chip editors, distraction-free prose editor with live token badge, 1-tap *Bracket Template Inserter*, and an AI Action strip (*Enhance*, *Continue*, *Fix Keys*, *Promote to Character Card*).
     - **Tab B — Triggers & STV2 Config**: Injection Position (`0–4`), Order, Scan Depth, Probability slider, Sticky/Cooldown/Delay, Recursion toggles, Scene Event configurator, and Active Instruction magic phrases (`{{char}} will instantly...`).
     - **Tab C — Connections**: Live view of every other Lore Entry and Character Card that references or is referenced by this entry, with 1-tap keyword linking.
   - **Full-Screen Character Card V2 Editor (Opened on Tap)**:
     - **Tab A — Persona & Lore Sync**: Avatar header, Name, Role, Faction, Description, Personality, Lumiverse Traits (Archetype, Voice Cadence, Hidden Agenda), and 1-tap **Sync Card ↔ Lorebook Entry**.
     - **Tab B — Greetings & Dialogue**: Opening Scenario, First Message (`first_mes`), swipeable Alternate Greetings manager, and Example Dialogue (`mes_example`).
     - **Tab C — Prompts & Export**: System Prompt, Post-History Instructions, Creator Notes, Tags, and direct **Export Card JSON / PNG Portrait**.
3. **5-Stage Forge & Idea Flesher (Forge Tab)**:
   - A clean, guided wizard where each step occupies a focused card view with a bottom **Next Step / AI Action** bar:
     1. **Idea & Premise Studio**: Freeform idea box or structured Wizard fields + Speculative Realism Gate → *AI Flesh Out Premise* & *Analyze World DNA*.
     2. **World Config & Modules**: 15 toggleable modules, 8 Procedural Systems, 9 Tone Sliders, and Generation Scale.
     3. **Blueprint Architect**: Review, add, edit, or reorder planned Lorebook entries and Character Card candidates before prose generation.
     4. **Batch Forge**: Generate bracketed SillyTavern V2 entries and Character Cards with NENS v3.0 naming rules and inline editing.
     5. **Review & Commit**: Run Linter 2.0, auto-fix issues, and commit into your World's Codex.
4. **Expander — Canon Profile & Gap Finder (PRD §8)**:
   - Dedicated view that analyzes your existing entries to extract your world's **Canon Profile** (Core Premise, Tone Signals, Missing Pillars) and generates **Canon-Constrained Entry Suggestions** (with Grounding % and High/Medium/Low Impact badges). Select any suggestions and tap **Generate & Add Selected** to flesh out your world organically.
5. **Audit, Graph & Simulator**:
   - **Lore Web & Audit**: Interactive pan/zoom force-directed graph, Conflict & Loop Detector (Circular Recursion, Duplicates, Orphans, Contradictions), Token Budget meter, and 1-tap Optimizer.
   - **Trigger Simulator**: Test chat turns to watch Depth 0 direct matches and Depth 1–5 recursive cascades light up in real time.

### Visual Identity & Theme

- **Aesthetic Direction**: **Linear × Obsidian Studio** — ultra-clean dark minimalism with crisp hairline geometry, subtle glassmorphic depth, and zero visual noise.
- **Color Palette & Mood**:
  - **Canvas**: True Deep Void (`#06070B`) for effortless contrast.
  - **Elevated Surfaces**: Slate Glass (`#0E1118`), Interactive Card (`#151924`), Elevated Modal (`#1B2030`) with `1.dp` subtle borders (`#23293D`).
  - **Accents**: Electric Indigo (`#6366F1`) primary, Soft Violet (`#A855F7`) AI/Architect accent, Crisp Cyan (`#22D3EE`) Character Card & link accent, Emerald (`#10B981`) valid/constant state, Amber (`#F59E0B`) warnings.
- **Typography & Hierarchy**:
  - **Headers & UI**: *Plus Jakarta Sans* (Bold/SemiBold for titles with tight `-0.3.sp` letter spacing; Regular/Medium for clean body copy).
  - **Code, Keys & Brackets**: *JetBrains Mono* for bracketed lore attributes, token counters, and trigger chips.
- **Animated Orbital Architect Button**:
  - A compact `46.dp` circular floating orb in the bottom-right corner featuring a continuous smooth `infiniteTransition` Canvas animation (counter-rotating dual gradient rings + gently pulsing core star) that opens the Live Architect Co-Pilot sheet on tap.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Master-Detail Drill-Down Over Single-Page Stacked Forms**
  - *Chosen Approach*: Browsing happens in a clean, scannable list; editing happens in a dedicated full-screen overlay with 3 focused sub-tabs.
  - *Why*: Eliminates the overwhelming wall of text fields and sliders, making both browsing 100+ entries and writing a single entry feel calm, fast, and spacious.
- **Decision 2: First-Class "Blank World" + "Flesh Out an Idea Spark" Creation Modal**
  - *Chosen Approach*: Tapping **+ New World** always gives an explicit choice between starting completely empty or typing a rough concept for the AI to flesh out.
  - *Why*: Supports both manual authors who want total control from Entry #1 and brainstormers who want to type a two-line idea and let the wizard build the scaffolding.
- **Decision 3: Dedicated Canon Expander & Gap Finder Workflow (PRD §8)**
  - *Chosen Approach*: Include the full **Canon Profile Extraction + Canon-Constrained Suggestion Generator** alongside the Forge Wizard.
  - *Why*: Once a world exists (whether started from blank or forged), users need an easy way to ask the AI *"What's missing from my world?"* and generate 3–5 grounded new entries that fit their existing lore.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Navigation Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   TOP MINIMALIST HEADER & WORLD BAR                        │
│  [ ❖ Worlds Home / Active World Pill ]     [ Undo ] [ Redo ] [ Export/IO ] │
└───────────────────────────────────┬────────────────────────────────────────┘
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     5 CLEAN WORKSPACE TABS (BOTTOM BAR)                    │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐  │
│  │ 1. Codex   │ │ 2. Forge   │ │ 3. Expander│ │ 4. Graph & │ │ 5. Sim   │  │
│  │ (Entries & │ │ (Idea to   │ │ (Canon Gap │ │    Audit   │ │ (Trigger │  │
│  │  Cards)    │ │  World)    │ │  Finder)   │ │ (Analytics)│ │  Sandbox)│  │
│  └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └────┬─────┘  │
└────────┼──────────────┼──────────────┼──────────────┼─────────────┼────────┘
         │              │              │              │             │
         ▼              ▼              ▼              ▼             ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     FOCUSED FULL-SCREEN OVERLAYS (ON TAP)                  │
│  • Worlds Home & "+ New World" Sheet (Blank World vs. AI Idea Spark)       │
│  • Focused Lore Entry Editor (Tabs: Writing & Keys | STV2 Config | Links)  │
│  • Focused Character Card V2 Editor (Tabs: Persona | Greetings | Export)   │
│  • Compact Animated Orbital Architect Icon (46dp) -> Co-Pilot Drawer       │
└────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

- **Worlds Home & New World Flow**:
  - `createBlankUniverse(name, genre, description)` creates a project with `entries = emptyList()` and `characterCards = emptyList()` so the user starts with a truly blank slate.
  - `createUniverseFromSpark(name, rawIdea)` creates a new project, populates the Forge premise with `rawIdea`, runs the AI Idea Flesher to generate the expanded Premise & World DNA, and opens the Forge Wizard so the user can review and customize every detail.
- **Codex Master-Detail State**:
  - `isEditingEntryFullScreen: Boolean` and `isEditingCardFullScreen: Boolean` control whether the user is viewing the clean directory list or the focused full-screen editor, with `BackHandler` returning cleanly to the list.
- **Canon Expander Engine (PRD §8)**:
  - `CanonProfile` (`corePremise`, `toneSignals`, `genreSignals`, `missingPillars`) and `CanonSuggestion` (`id`, `title`, `type`, `suggestedKeys`, `justification`, `groundingScore`, `impactLevel`, `selected`) allow 1-tap AI analysis of existing lore and batch generation of selected gap-filling entries.
