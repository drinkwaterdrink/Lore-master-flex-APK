# Loremaster Flex — Product Requirements Document

> **Version:** Comprehensive Reconstruction Spec v1.0
> **Purpose:** Enable full recreation of the Loremaster Flex application from prompts alone.

---

## 1. Product Overview

### 1.1 What It Is
Loremaster Flex is a **production-grade SillyTavern Lorebook generator and editor**. It creates, manages, optimizes, and exports lorebooks (structured world-building data files) compatible with SillyTavern v1.12.6+ in strict V2 JSON format. The app uses AI to generate rich, interconnected lorebook entries from user-provided creative premises.

### 1.2 Core Value Proposition
- **AI-powered lorebook generation** from natural language premises via a multi-stage pipeline
- **Full manual editing** of every lorebook field with SillyTavern-compatible defaults
- **Analytics, optimization, and quality validation** of lorebook content
- **Multi-provider AI support** (Google AI Studio, OpenRouter, NanoGPT)
- **Activation simulation** to test how entries trigger in SillyTavern
- **Export** to SillyTavern-compatible JSON

### 1.3 Target Users
AI roleplay enthusiasts using SillyTavern who want to create rich, well-structured lorebooks without manually authoring hundreds of entries.

---

## 2. Technology Stack

### 2.1 Frontend
- **Framework:** React 19 with TypeScript
- **Build Tool:** Vite 6
- **State Management:** Zustand 5 with `persist` middleware (localStorage)
- **Icons:** lucide-react
- **Toasts/Notifications:** Sonner
- **Virtualization:** react-window + react-virtualized-auto-sizer (for large entry lists)
- **File Export:** JSZip + file-saver
- **Styling:** Vanilla CSS utility classes (dark theme, glassmorphism aesthetic)

### 2.2 AI Service Layer
- **Google AI Studio:** Direct SDK via `@google/genai`
- **OpenRouter:** OpenAI-compatible REST API (`https://openrouter.ai/api/v1`)
- **NanoGPT:** OpenAI-compatible REST API (`https://nano-gpt.com/api/v1`)

### 2.3 Deployment
- Runs locally via `npm run dev` (Vite dev server)
- API key stored in `.env.local` as `GEMINI_API_KEY` (for Google provider)
- All data persisted in browser localStorage under key `lorebook-forge-storage`

---

## 3. Application Layout & Navigation

### 3.1 Overall Layout
- **Full-screen dark UI** (`bg-black text-white`) with no page scrolling — everything is contained
- **Left:** Collapsible Project Sidebar (slide-out drawer on mobile, always visible on desktop)
- **Center:** Main content area determined by active tab
- **Top:** Glass-effect header bar with project name, tab switcher, undo/redo, export, and settings buttons
- **Floating:** Oracle Chat widget (bottom-right corner, expandable)

### 3.2 Main Tabs (5 tabs)
Displayed as a pill-shaped segmented control in the header:

| Tab ID | Label | Icon | Component |
|--------|-------|------|-----------|
| `editor` | Editor | Edit3 | EntryList (sidebar) + EntryEditor (main) |
| `analytics` | Analytics | BarChart3 | AnalyticsDashboard |
| `expander` | Expander | Sparkles | AnalyzerPanel |
| `generator` | Forge | Hammer | GeneratorPanel |
| `simulator` | Sim | Terminal | SimulatorPanel |

### 3.3 Mobile Responsiveness
- Tab bar moves to a horizontal strip below the header on mobile
- Entry list becomes a slide-out overlay on mobile
- Project sidebar becomes a slide-out overlay on mobile
- All components are responsive with hidden labels on small screens

---

## 4. Data Model (Complete Type Definitions)

### 4.1 Core Types

#### `ApiProvider`
```
'google' | 'openrouter' | 'nanogpt'
```

#### `ApiKey`
```
{ id: string, name: string, key: string }
```

#### `ApiSettings`
```
{
  provider: ApiProvider,
  apiKey: string,
  model: string,
  customModels?: Record<ApiProvider, string[]>,
  savedKeys?: Record<ApiProvider, ApiKey[]>
}
```

#### `Project`
```
{
  id: string,
  name: string,
  description: string,
  genre: string,
  tone: string,
  createdAt: number,
  updatedAt: number,
  lorebook: STLorebookRoot,
  generationConfig?: any
}
```

#### `STLorebookRoot` (V2 Lorebook Format)
```
{
  name: string,
  description: string,
  scanDepth: number (default: 100),
  tokenBudget: number (default: 3000),
  recursiveScanning: boolean (default: true),
  extensions: Record<string, any>,
  entries: STEntry[],
  settings: STSettings
}
```

#### `STSettings`
```
{
  scanDepth?: number,
  tokenBudget?: number,
  recursiveScanning?: boolean,
  caseInsensitiveKeys?: boolean,
  matchWholeWords?: boolean,
  useRomanNumerals?: boolean,
  extensions?: Record<string, any>
}
```

### 4.2 Entry Type (`STEntry`) — The Core Data Unit
Every lorebook entry has these fields:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `uid` | number | auto | Unique identifier |
| `key` | string[] | [] | Primary activation keywords |
| `keysecondary` | string[] | [] | Secondary keywords (for selective logic) |
| `content` | string | "" | The actual lorebook content injected into context |
| `comment` | string | "" | Display name/title (not injected) |
| `probability` | number | 100 | Activation chance (0-100) |
| `useProbability` | boolean | true | Whether probability is used |
| `depth` | number | 4 | How many messages back to scan |
| `group` | string | "" | Group name for organizing entries |
| `groupWeight` | number | 100 | Weight for random selection within group |
| `displayIndex` | number | 0 | Sort order in UI |
| `disable` | boolean | false | Whether entry is disabled |
| `excludeRecursion` | boolean | false | Exclude from recursive scanning |
| `preventRecursion` | boolean | false | Don't trigger other entries |
| `delay` | number | 0 | Messages before activation |
| `sticky` | number | 0 | Messages to stay active after trigger |
| `cooldown` | number | 0 | Messages to wait before re-triggering |
| `selective` | boolean | false | Use AND/OR logic with secondary keys |
| `selectiveLogic` | number | 0 | 0=AND, 1=OR for secondary keys |
| `scanDepth` | number/null | null | Per-entry override of scan depth |
| `matchWholeWords` | boolean/null | null | Per-entry word matching |
| `caseSensitive` | boolean | false | Case-sensitive keyword matching |
| `constant` | boolean | false | Always active regardless of keywords |
| `position` | number | 0 | Injection position (0-4, see below) |
| `order` | number | 100 | Priority within position |
| `addMemo` | boolean | true | Add to memory |
| `triggers` | string[] | [] | Additional triggers |
| `characterFilter` | object | {isExclude:false, names:[], tags:[]} | Character-specific filtering |
| `outletName` | string | "" | Outlet hook name |
| `vectorized` | boolean | false | Use vector matching |
| `ignoreBudget` | boolean | false | Ignore token budget |
| `useGroupScoring` | boolean | false | Use keyword specificity scoring |

#### Position Values
| Value | Label | Use Case |
|-------|-------|----------|
| 0 | General Context | Locations, items, concepts |
| 1 | Characters | Character entries (after char defs) |
| 2 | Items / History | Before example messages |
| 3 | Before/After Actions | Scene events, dynamic triggers |
| 4 | Author Note (Strong) | World rules, system-level instructions |

### 4.3 Entry Templates (9 types)
Each template has specific default configurations:

| Template | Position | Order | Constant | PreventRecursion | Sticky |
|----------|----------|-------|----------|------------------|--------|
| WORLD_RULE | 4 | 290 | true | true | 0 |
| CHARACTER | 1 | 200 | false | true | 0 |
| LOCATION | 0 | 110 | false | false | 0 |
| ITEM | 0 | 105 | false | true | 0 |
| FACTION | 1 | 130 | false | true | 0 |
| SCENE_EVENT | 3 | 100 | false | true | 8 |
| DIALOGUE_PATTERN | 2 | 100 | false | true | 0 |
| DYNAMIC_TRIGGER | 3 | 100 | false | true | 5 |
| CONCEPT | 0 | 100 | false | false | 0 |

---

## 5. Project Management (ProjectSidebar)

### 5.1 Project CRUD
- **Create Project:** Button generates a random project name (from adjective+noun combos like "Crimson Bastion") with a random genre. Creates a new `Project` with empty lorebook.
- **Select Project:** Click to switch active project
- **Rename Project:** Inline editing via click on name → input field (also available in header)
- **Delete Project:** Trash icon with immediate deletion (no confirmation dialog currently)

### 5.2 Import
- **File input** accepts `.json` files
- Parses SillyTavern V2 lorebook JSON format
- Handles both `{ entries: {...} }` (object-keyed) and `{ entries: [...] }` (array) formats
- Auto-converts object-keyed entries to array format
- Runs `ensureUniqueUids()` to fix duplicate UIDs
- Creates a new project from the imported data
- Auto-detects and sets project name from lorebook name field

### 5.3 Export
- **Export Preview Dialog** shows before download:
  - Runs `validateForExport()` checking for:
    - Entries with no keywords and not constant
    - Entries with empty content
    - Duplicate UIDs
    - Entries with overly broad keywords
  - Shows blocking errors (red) and warnings (orange)
  - **Auto-Fix All** button to repair common issues
  - Shows stats when valid: entry count, constant count, estimated tokens
  - Downloads as `{projectName}.json` in V2 lorebook format
- Export normalizes entries: ensures all required fields exist, assigns sequential displayIndex values
- Uses `file-saver` library for download

---

## 6. Entry Editor (Editor Tab)

### 6.1 Entry List (Left Panel)
- Searchable list of all entries in active project
- **Search bar** filters entries by comment (title) text
- Entries grouped by `group` field with collapsible group headers
- Shows entry count per group
- Each entry shows: comment (title), first few keywords as pills, disabled state
- **Multi-select mode:**
  - Click checkbox to select, Shift+Click for range selection
  - "Select All" / "Clear Selection" buttons
  - Selected count indicator
- **Bulk Edit Toolbar** appears when entries are selected (see §6.3)
- **Add Entry** button creates new entry with defaults
- **Event Group Creator** button opens the event group creation modal
- Color-coded type indicator per entry

### 6.2 Entry Editor (Right Panel)
Full editing form for a single entry with these sections:

#### Header
- **Comment/Title** field (text input)
- **Auto-detect type** badge showing detected entry type (CHARACTER, LOCATION, etc.)
- **Apply Type** button to apply template defaults for detected type
- **Convert to Single Event** button
- **Delete Entry** button
- **Duplicate Entry** button

#### Keywords Section
- **Primary Keys:** Tag-style input where each keyword is a removable chip. Comma-separated input, Enter to add.
- **Secondary Keys:** Same tag-style input
- **Keyword Validation:** Real-time warnings for broad/generic keywords (from BROAD_KEYS list)
- **AI Fix Keys** button: Calls `generateBetterKeywords()` to get AI-suggested improvements
- **Selective toggle** with AND/OR logic selector

#### Content Section
- **Large textarea** for entry content
- **Token count** display (estimated via `text.length / 4` formula)
- **AI Enhance** button: Calls `expandEntryContent()` to AI-expand sparse content
  - Shows expansion preview with Accept/Reject buttons
- **AI Continue** button: Calls `continueContent()` to add more content to existing text
- **Template Insert** dropdown with pre-built content templates:
  - Character Template: `[ role(); personality(); background(); relationships(); age(); skills(); ]`
  - Location Template: `[ owner(); atmosphere(); features(); history(); inhabitants(); ]`
  - Faction Template: `[ purpose(); methods(); leadership(); territory(); reputation(); ]`
  - Item Template: `[ type(); material(); powers(); limitation(); origin(); ]`
  - World Rule Template: `RULE: [STATEMENT]. [Mechanism]. Violation WILL [consequence].`

#### Configuration Section (Collapsible)
All STEntry fields exposed as form controls:
- **Position:** Dropdown (0-4)
- **Order:** Number input (0-999)
- **Depth:** Number input
- **Probability / Use Probability:** Number + toggle
- **Constant:** Toggle
- **Disable:** Toggle
- **Sticky:** Number input
- **Cooldown:** Number input
- **Delay:** Number input
- **Group:** Text input
- **Group Weight:** Number input
- **Prevent Recursion / Exclude Recursion:** Toggles
- **Match Whole Words / Case Sensitive:** Toggles
- **Vectorized / Ignore Budget / Add Memo:** Toggles
- **Scan Depth override:** Number input (null = use lorebook default)

#### Scene Event Controls (Conditional)
Shown when entry type is SCENE_EVENT:
- **Enable Scene Event** toggle
- **Event Type** selector: combat, weather, injury, emotional, environmental, social
- **Severity** selector: brief, standard, extended, persistent
- Auto-configures sticky, position, and preventRecursion based on selections

#### Active Instruction Controls (Conditional)
For DYNAMIC_TRIGGER entries:
- **Enable Active Instruction** toggle
- **Magic Phrase Insertion** buttons:
  - `{{char}} will instantly...` (character action)
  - `{{user}} will instantly...` (user-directed action)
  - `[EVENT] will instantly...` (environmental event)
- Auto-sets position=4, sticky=4, preventRecursion=true

### 6.3 Bulk Edit Toolbar
Appears when multiple entries are selected:

- **Bulk Edit Properties:** Opens dialog to set shared values across all selected:
  - Position, Order, Group, Depth, Probability
  - Constant, Disable, Prevent Recursion toggles
  - Apply preset template configs
- **Find & Replace:** Text find/replace across content of selected entries
  - Case sensitive option
  - Preview of matches before applying
- **Bulk Delete:** Delete all selected entries
- **Apply Preset Config:** Apply entry template defaults (CHARACTER, LOCATION, etc.) to all selected
- **Select All / Clear Selection**

---

## 7. Analytics Dashboard (Analytics Tab)

### 7.1 Overview Statistics
Grid of stat cards showing:
- **Total Entries** count
- **Total Tokens** (estimated)
- **Average Tokens/Entry**
- **Constant Entries** count
- **Groups** count
- **Characters / Locations / Factions / Items** counts (auto-classified)

### 7.2 Entry Distribution
- **By Category:** Visual breakdown showing counts per detected type (CHARACTER, LOCATION, FACTION, ITEM, CONCEPT, WORLD_RULE, DYNAMIC_TRIGGER, SCENE_EVENT) with color-coded icons
- **By Group:** Breakdown by group field
- **By Position:** Distribution across positions 0-4

### 7.3 Keyword Analysis
- **Most Connected Entries:** Centrality scores showing which entries are referenced most
- **Broad Keyword Warnings:** Identifies entries using overly generic keywords with "Fix" buttons
- **Keyword Casing Inconsistencies:** Detects mixed-case variants of the same keyword
- **Empty Content Warnings:** Entries with no content
- **Missing Keyword Warnings:** Entries with no keywords and not constant
- **Bad Ordering Warnings:** Entries with incorrect order values for their type

### 7.4 Conflict Dashboard (Sub-component)
Detects and displays:
- **Circular References:** Entry A references Entry B AND B references A (recursion loops)
  - Auto-fix button sets `preventRecursion: true` on one entry
- **Duplicate Entries:** Exact duplicates, keyword conflicts, similar content
  - Shows shared keywords and similarity percentage
- **Orphan/Unreachable Entries:** Entries with no keywords, not constant, and not referenced
- Health status indicator (green checkmark when clean)

### 7.5 Budget Dashboard (Sub-component)
- **Token Budget** display with editable value
- **Constant Tokens** (always consumed) vs **Conditional Tokens** (consumed on activation)
- **Budget utilization** progress bar
- **Largest/Smallest entry** identification
- **Entries Over Budget** list with token counts
- Per-entry token breakdown with color coding (green/yellow/red)

### 7.6 Content Expander (within Analytics)
- **Smart Expand All** button: AI-expands all entries under a configurable token threshold
- Select entries for expansion via checkboxes
- Batch expansion with progress indicator
- Individual entry expansion

### 7.7 Optimizer (within Analytics)
- **Scan** button runs `optimizeLorebook()` which:
  - Classifies entry types (character, location, faction, item, rule, trigger, concept)
  - Builds reference graph (who mentions whom)
  - Identifies hub entries
  - Checks and fixes: missing fields, bad positions, bad ordering, broad keywords, missing preventRecursion, duplicate entries
- **Issue list** with selectable issues and severity (error/warning/info)
- **Apply Selected** to auto-fix chosen issues
- **Apply All** to fix everything

---

## 8. Expander Panel (Expander Tab)

### 8.1 Canon Extraction
- **Analyze Canon** button calls `extractCanonProfile()`:
  - AI reads all existing entries and extracts:
    - Core premise summary
    - Tone signals (dark, hopeful, mysterious, etc.)
    - Genre signals
    - Missing pillars (what types of entries are lacking)
  - Displays the canon profile with pillar analysis

### 8.2 AI Suggestion Generation
- **Generate Suggestions** button calls `generateCanonConstrainedSuggestions()`:
  - AI analyzes gaps in existing lorebook
  - Generates suggested new entries with:
    - Title, type, suggested keywords
    - Justification for why this entry is needed
    - Grounding score (how well it fits existing canon)
    - Narrative impact assessment
    - Gap type identification
    - Recommended STEntry config overrides
  - Each suggestion has an impact badge (High/Medium/Low)

### 8.3 Apply Suggestions
- Checkboxes to select which suggestions to apply
- **Apply Selected** button:
  - Calls `generateBatchContent()` to AI-generate full content for each selected suggestion
  - Compiles into STEntry format with proper configs
  - Adds to active project's lorebook

---

## 9. Forge Panel (Generator Tab) — The 6-Stage Pipeline

The Forge is a **multi-step wizard** that generates an entire lorebook from a creative premise. It has 6 sequential stages with a visual stepper.

### Stage Labels
1. **Premise Studio** — Write or generate the creative premise
2. **Config** — Configure generation parameters
3. **Blueprint** — AI generates the structural plan
4. **Forge** — AI generates actual entry content in batches
5. **Review** — Validate, lint, and fix generated content
6. **Finish** — Compile and add to project

### 9.1 Stage 1: Premise Studio

#### Wizard Modes
Two modes toggled by a switch:

**Scenario Mode (Free-form):**
- Single large textarea for writing a creative premise directly
- No structured fields

**Detailed Mode (Structured Wizard):**
Fields:
| Field | Type | Description |
|-------|------|-------------|
| Genre | Dropdown | Primary genre selection |
| Subgenre | Text | Secondary genre detail |
| Era | Text | Time period/setting |
| Vibes | Text | Atmosphere/mood keywords |
| Conflict | Text | Central conflict description |
| Strange Factor | Text | The unique twist or hook |
| Protagonist | Text | Player character role |
| Must Include | Text | Required elements |
| Avoid | Text | Elements to exclude |
| Hard Rules | Text | Non-negotiable constraints |
| Token Cap | Slider | 100-10000, controls output size |

#### Speculative Mode (Realism Gate)
Three-way toggle:
- **Auto:** System auto-detects from genre keywords whether speculative elements are appropriate
- **Off (Strict Realism):** Forbids all supernatural/fantasy/sci-fi elements
- **On (Speculative Allowed):** Enables speculative elements with intensity slider:
  - Minimal (magical realism / one impossible thing)
  - Moderate (low fantasy / soft sci-fi)
  - Full (traditional fantasy/sci-fi)

#### AI Premise Generation
- **Generate Premise** button calls `generatePremiseFromWizard()`:
  - Uses the massive `PREMISE_GENERATOR_SYSTEM_PROMPT` (400+ lines)
  - Outputs a structured JSON "scenario seed" with sections: CORE, USER, WORLD_SEEDS, WORLD_BOUNDARIES, LOCATION_SEEDS, FACTION_SEEDS, NPC_SEEDS, NPC_RELATIONSHIPS, ITEM_SEEDS, SECRET_SEEDS, HISTORY_SEEDS, AESTHETIC_SEEDS, SPEECH_PATTERNS, NAMING_TOUCHSTONES, PLOT_HOOKS, OPENING, EXPANSION_NOTES
  - Converts structured seed to markdown for display
  - Includes robust JSON repair for truncated outputs
  - Token clamping to prevent API failures
  - Handles speculative mode enforcement

#### Premise History
- Maintains a history of generated premises
- Restore from any previous attempt
- Shows timestamps and wizard state snapshots

#### Premise Analysis (Stage 0.5)
- **Analyze** button calls `analyzePremise()`:
  - Extracts entities (characters, locations, factions, items, concepts, rules) with importance levels
  - Calculates tone metrics (darkness, complexity, danger, hope, mystery) on 0-100 scale
  - Detects worldbuilding gaps
  - Maps relationships between entities
  - Extracts "World DNA" core data (logline, setting, conflict, themes, stakes)
  - Generates hooks (mysteries, tensions, set pieces, rumors)
  - Computes vibe metrics: clarity, originality, conflictDensity, mysteryLoad, castPressure, complexityRisk
  - Produces recommendations for config changes with impact ratings
  - Lists warnings

#### Apply Recommendations
- Shows AI recommendations as cards with impact badges
- User can select/deselect recommendations
- **Apply Selected** auto-adjusts config values based on AI suggestions

### 9.2 Stage 2: Configuration

Organized into collapsible sections:

#### World Settings
- **Genre / Secondary Genre:** Text inputs
- **Tone:** Free text
- **Prose Style:** Selection
- **Narrator:** Selection
- **Scale:** World scope (intimate → epic)
- **Scope:** Narrative breadth
- **Timeline Span / Historical Depth:** How much time the world covers

#### Tone Sliders (9 axes, 0-100 each)
- Darkness, Complexity, Humor, Violence, Romance, Mystery, Hope, Realism, Politics

#### Character Settings
- **Cast Size:** Number (how many NPCs)
- **Relationship Density:** Low/Medium/High/Ultra
- **Secret Density:** Low/Medium/High
- **Use Character Filter:** Toggle
- **Generate Hidden Keys:** Toggle

#### Narrative Settings
- **Mystery Ratio:** 0-100
- **Plot Threads:** Number
- **MacGuffins:** Number
- **Foreshadowing:** 0-100

#### Technical Settings
- **Token Budget:** Number (lorebook-level)
- **Scan Depth:** Number
- **Recursive Scanning:** Toggle
- **Key Strategy:** Smart / Balanced / Aggressive
- **Key Strictness:** Broad / Balanced / Strict
- **Vectorization:** Toggle
- **Temperature:** Number (AI generation temperature)

#### Module Selection (15 toggleable modules)
Each module controls what types of entries are generated:
- foundation, characters, locations, factions, history, objects, systems, hazards, secrets, dynamics, lexicon, cosmology, sociology, biology, law

#### Generation Size
- Minimal (10-20 entries)
- Balanced (30-50 entries)
- Deep (60-100 entries)
- Massive (150-300 entries)

#### Target Count
- Calculated from gen size selection
- Manual override available

#### Detail Level
- Controls token budget per entry

#### Maturity Settings
- Violence: None / Mild / Moderate / Graphic
- Sexual: None / Fade-to-black / Moderate / Explicit
- Language: Clean / Mild / Strong

#### Procedural Systems (Optional Addon)
Toggle-able systems that generate weighted random event entries:
- Random Encounters, Weather, Loot & Treasure, Ambient NPCs, Complications, Rumors, Mood & Relationships, Quest Hooks

Procedural Config:
- Randomness Level: low / medium / high
- Content Density: sparse / standard / abundant
- Persistence: ephemeral / standard / lasting
- Weight Distribution: flat / realistic / curated

#### Configuration Templates
- **Save Current Config** to template library with name and description
- **Load Template** to restore saved configurations
- **Delete Template**
- Templates persist in localStorage

#### Policy & Constraints
- Policy ID selection
- Custom constraints as text list

### 9.3 Stage 3: Blueprint Generation

- **Generate Blueprint** button calls `generateBlueprint()`:
  - Uses `ARCHITECT_SYSTEM_PROMPT` (140+ lines)
  - AI creates a structural plan with:
    - Metadata (title, total entries, estimated tokens, generation time)
    - Distribution: entry counts per module
    - Layers: hierarchical grouping of entry blueprints by module
    - Batches: groups of entries to generate together
    - Linking strategy
  - Each blueprint entry has: id, title, category, group, importance, instructions, shouldMention, keys, order, position, constant, module
  - Includes expansion loop if AI under-generates entries
  - Adds procedural system entries if configured
  - Progress callback with phases: initializing → generating → parsing → recovering → expanding → complete

- **Blueprint Display:**
  - Layer breakdown showing modules and entry titles
  - Total entry count
  - Batch structure

### 9.4 Stage 4: Forge (Batch Generation)

- **Forge All** button calls `handleForgeAll()`:
  - Processes blueprint batches sequentially
  - Each batch calls `generateEntryBatch()`:
    - Uses `WRITER_SYSTEM_PROMPT` (150+ lines) with strict formatting rules
    - Includes world state context, plot threads, and existing entry summaries
    - Character Naming rules (NENS v3.0) with banned AI-default names
    - Generates structured content with bracketed data format
    - Robust JSON parsing with brace-counting extraction for truncated outputs
  - Progress display: "Forging batch X/Y — Entry title..."
  - Auto-retries failed batches up to 3 times
  - Deduplication of generated entries across batches

### 9.5 Stage 5: Review

#### Validation Report
- Calls `validateLorebookContent()` → `runValidation()`:
  - Uses `VALIDATOR_SYSTEM_PROMPT`
  - Checks content quality, keyword quality, linking, and configuration
  - Reports issues with severity and suggested fixes
  - Metrics: procedural count, plot threads requested vs generated, tagged entries count

#### Lorebook Linter 2.0
- **Run Linter** button performs heuristic + optional AI checks:
  - **Heuristic Checks (always run):**
    - TRUNCATED: Content ends with dangling word/punctuation
    - MISSING_BRACKET: Missing `[` or `]` bracketed data
    - PLACEHOLDER_TITLE: Title contains "Entry", "Untitled", "TODO"
    - TOO_SHORT: Content below minimum character threshold (200 for core, 120 for procedural)
    - NO_KEYWORDS: Empty primary keywords
    - BROAD_KEYS: Contains overly generic keywords
    - BAD_POSITION: Position doesn't match entry type
    - MISSING_THREAD_TAG: Content doesn't reference expected plot threads
    - EMPTY_CONTENT: Zero content
    - STALE_GROUP: Group field doesn't match expected module
  - **AI Validator (optional):** Deep content quality assessment
  - Severity levels: CRITICAL, MAJOR, MINOR
  - Counts by severity displayed

#### Issue Resolution
- **Per-issue Fix button:** AI repairs individual entries via `repairEntry()`
- **Auto-Fix All:** Repairs all CRITICAL issues
- **Individual Entry Repair:** Select and repair specific entries
- **Regenerate Entry:** Generate fresh content with custom instructions

#### Draft Management
- View all generated drafts organized by batch
- Remove duplicates button
- Edit individual draft content inline
- Entry-level lint status indicators

### 9.6 Stage 6: Finish

- **Compile & Add** button:
  - Calls `compileDraftsToLorebook()` which:
    - Converts EntryDraft objects to full STEntry objects
    - Assigns proper UIDs (max existing + 1, incrementing)
    - Applies recommended overrides from blueprints
    - Maps blueprint configs (position, order, constant, preventRecursion, etc.)
    - Sets group from blueprint module
    - Handles selective logic for secondary keys
    - Assigns displayIndex values
  - Generates project metadata (title, genre, description) via AI
  - Updates project with new entries and metadata
  - Switches to Editor tab

---

## 10. Simulator Panel (Sim Tab)

### 10.1 Activation Simulator
- **Test Input:** Large textarea for entering sample chat text
- **Simulate** button runs `simulateActivation()`:
  - Scans text against all entry keywords
  - Supports recursive activation (entry A triggers, its content triggers entry B)
  - Configurable max recursion depth
  - Returns list of activated entries with:
    - Entry reference
    - Trigger type (keyword match)
    - Recursion depth level
    - Which keyword triggered it

### 10.2 Results Display
- Color-coded depth indicators (depth 0 = direct, higher = recursive)
- Shows activated entry titles, triggering keywords, and recursion chain
- Clear results button

---

## 11. Oracle Chat (Floating Widget)

### 11.1 Functionality
- **Floating chat button** (bottom-right corner) that expands into a chat panel
- AI assistant with full knowledge of the active project's lorebook
- System instruction includes:
  - Complete project context (all entries summarized)
  - Project name, genre, description
  - Entry count and group distribution
  - Role: "Expert lorebook consultant"
- Chat history maintained in component state (not persisted)
- Messages displayed with user/bot avatars and timestamps
- Clear chat history button
- Close button collapses back to floating icon

### 11.2 Capabilities
The Oracle can:
- Answer questions about the lorebook's content
- Suggest improvements
- Identify gaps
- Explain SillyTavern lorebook best practices
- Help with keyword strategy

---

## 12. Event Group Creator

### 12.1 Purpose
Creates "Active Lorebook" event groups — sets of weighted random entries that share a trigger keyword, causing SillyTavern to randomly select one outcome.

### 12.2 Preset Templates
- **Combat Roll:** Friendly(30%) / Neutral(40%) / Suspicious(20%) / Hostile(10%)
- **NPC Reaction:** Similar weighted outcomes
- **Weather Change:** Clear/Light Rain/Storm/Fog/Snow
- **Random Encounter:** Safe Path/Minor Event/Danger/Mystery

### 12.3 Custom Creation
- **Group Name** and **Trigger Keyword** inputs
- **Description** textarea
- **Sticky Duration** slider
- **Add Outcome** button to create entries with:
  - Label (short name)
  - Content (instruction text, typically using `will instantly` pattern)
  - Weight (0-100, visual slider)
- **Equalize Weights** button distributes evenly
- **Remove Outcome** per-entry
- **Generate Entries** compiles outcomes into lorebook entries with:
  - Shared group name
  - Same trigger keyword
  - `useProbability: true` with weight-based probability
  - Position 3, appropriate sticky values

---

## 13. Procedural Generator

### 13.1 Available Systems (8 types)
| System | Description | Default Config |
|--------|-------------|----------------|
| Random Encounters | Combat, discovery, social events | Position 3, probability 60-100, sticky 5-10 |
| Weather | Dynamic weather changes | Position 0, probability 60-90, sticky 3-5, cooldown 8-15 |
| Loot & Treasure | Variable rewards | Position 0, probability 30-80 |
| Ambient NPCs | Background characters | Position 1, probability 40-80, sticky 5-8 |
| Complications | Plot twists and challenges | Position 3, probability 20-60 |
| Rumors | Information and gossip | Position 0, probability 40-70 |
| Mood & Relationships | NPC emotional states | Position 1, probability 50-80 |
| Quest Hooks | Adventure opportunities | Position 3, probability 30-60 |

### 13.2 Configuration
- **System Selection:** Toggle grid with icons and descriptions
- **Randomness Level:** low / medium / high (affects probability ranges)
- **Content Density:** sparse (3-5 entries) / standard (8-12) / abundant (15-25)
- **Weight Distribution:** flat / realistic / curated
- **Persistence:** ephemeral (-3 sticky) / standard (base) / lasting (+5 sticky)

### 13.3 Generation Flow
1. Select systems → Configure → Generate via AI → Preview entries → Commit to lorebook
2. Uses `buildProceduralPrompt()` to create system-specific AI prompts
3. Entries get proper SillyTavern configs via `buildProceduralEntry()`

---

## 14. AI Service Layer

### 14.1 Multi-Provider Architecture
All AI calls route through `callAI()` in `aiService.ts`:
- Reads current provider/model/key from Zustand store
- Routes to provider-specific handler:
  - `callGoogleAI()` — Uses `@google/genai` SDK directly
  - `callOpenAICompatible()` — REST API for OpenRouter and NanoGPT
- **Request Deduplication:** Hashes requests and deduplicates in-flight identical calls
- **Safety Settings:** All Google AI harm categories set to `BLOCK_NONE`

### 14.2 Default Models Per Provider
**Google:** gemini-3-pro-preview, gemini-3-flash-preview, gemini-2.5-pro, gemini-2.5-flash, gemini-2.0-flash

**OpenRouter:** claude-opus-4-5, claude-sonnet-4-5, gpt-5.2-pro, grok-4.1-fast, deepseek-v3.2, glm-4.7, and more

**NanoGPT:** deepseek-r1, deepseek-v3.2 variants, glm-4.7 variants, kimi-k2.5 variants

### 14.3 Retry & Error Handling
- `withRetry()` wrapper: 3 retries with exponential backoff (2s initial delay)
- `parseAIJson()` / `cleanAndParse()`: Robust JSON parsing with:
  - Markdown fence stripping
  - Brace-counting extraction
  - Quote repair
  - Trailing comma removal
  - `attemptJsonRepair()` for common JSON syntax errors
  - `repairTruncatedJson()` via AI call for severely broken JSON

### 14.4 Token Management
- `clampOutputTokens()`: Prevents requesting more output tokens than the model window allows
- Estimates input token count from prompt length
- Adjusts maxOutputTokens to stay within safe limits

---

## 15. Settings Modal

### 15.1 Provider Configuration
- **Provider Selection:** Radio buttons for Google AI Studio, OpenRouter, NanoGPT
- **API Key Input:** Password field with show/hide toggle
- **Model Selection:** Dropdown populated from `DEFAULT_MODELS` + custom models
- **Custom Model:** Text input to add custom model identifiers
- Shows provider-specific docs URL link

### 15.2 Saved API Keys
- **Save Key** with a display name
- List of saved keys per provider
- Click to activate a saved key
- Delete saved keys
- Keys stored in `apiSettings.savedKeys` in localStorage

---

## 16. Optimizer Engine

### 16.1 Validation
`validateLorebook()` checks:
- Missing required fields
- Invalid position/order combinations
- Keyword quality issues
- Content length issues
- Configuration conflicts

### 16.2 Optimization
`optimizeLorebook()` performs:
- **Entry Type Classification:** auto-classifies entries as character, location, faction, item, rule, trigger, concept
- **Reference Graph Building:** identifies which entries mention which other entries
- **Hub Detection:** entries referenced by many others
- **Configurable Fixes:**
  - `enforceSettings`: Set lorebook-level defaults
  - `fixOrdering`: Correct position/order for entry types
  - `normalizeKeys`: Clean up keyword formatting
  - `fixMissingFields`: Fill in required field defaults
  - `fixPositionConstraints`: Correct position based on entry type
  - `removeBroadKeys`: Remove overly generic keywords
  - `applySmartHeuristics`: Type-specific intelligence
  - `deduplicateEntries`: Remove exact duplicate entries

---

## 17. Lore Graph Visualization

### 17.1 Force-Directed Graph
- SVG-based visualization of entry interconnections
- **Nodes:** Entries sized by connection count
- **Edges:** Lines between entries that reference each other via keywords
- **Force Simulation:** Custom physics with repulsion, center gravity, and link attraction (150 iterations)
- **Interactions:**
  - Zoom in/out buttons
  - Pan by click-and-drag
  - Reset view button
  - Click node to select entry in editor
  - Selected node highlighted in indigo

---

## 18. State Management (Zustand Store)

### 18.1 Persisted State
Stored in `localStorage` under key `lorebook-forge-storage`:
- `projects`: All project data
- `activeProjectId`: Currently selected project
- `apiSettings`: Provider, key, model, saved keys, custom models
- `configTemplates`: Saved forge configuration templates

### 18.2 Session State (Not Persisted)
- `activeEntryId`: Currently editing entry
- `selectedEntryIds`: Multi-selected entries (Set)
- `past` / `future`: Undo/redo history stacks

### 18.3 Undo/Redo System
- `pushHistory()` snapshots current `projects` array before mutations
- Max 50 history states
- `Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z` keyboard shortcuts
- Undo/Redo buttons in header

### 18.4 Key Actions
- `createProject`, `selectProject`, `deleteProject`, `updateProject`
- `updateLorebookRoot`, `replaceLorebook`, `updateSettings`
- `addEntry`, `addEntries`, `updateEntry`, `updateEntries`, `deleteEntry`, `duplicateEntry`
- `selectEntry` (with multi-select support), `clearSelection`
- `importLorebook` (with format auto-detection and repair)
- `repairActiveProject`
- `updateApiSettings`
- `saveConfigTemplate`, `deleteConfigTemplate`

---

## 19. Visual Design System

### 19.1 Color Palette
- **Background:** Pure black (#000000) with zinc-900 card surfaces
- **Cards:** `bg-[#1c1c1e]/40` with `backdrop-blur-xl` and `border-white/5`
- **Accent:** Indigo-500/600 for primary actions
- **Text:** White for primary, zinc-400/500 for secondary, zinc-600 for tertiary
- **Success:** Green-400/500
- **Warning:** Orange-400/500
- **Error:** Red-400/500
- **Info:** Blue-400/500

### 19.2 Component Styling
- **Buttons:** Rounded-full pill shapes, ghost/primary/secondary variants
- **Cards:** Rounded-2xl/3xl with subtle borders and blur
- **Inputs:** Dark backgrounds with white/10 borders, rounded-lg/xl
- **Dialogs:** Centered modal with backdrop blur overlay
- **Tags/Chips:** Small rounded pills with colored backgrounds
- **Toasts:** Glass-effect with blur, rounded-2xl, dark theme

### 19.3 Typography
- Font weights: bold for headers, semibold for labels, medium for body
- Size scale: text-[10px] for micro labels, text-xs for details, text-sm for body, text-base/lg/xl for headers
- Uppercase tracking-wider for category labels

### 19.4 Animations
- `animate-in slide-in-from-bottom-4` for content appearance
- `animate-pulse` for loading states
- Transition-all with duration-300 for interactions
- Hover effects with opacity and background changes

---

## 20. System Prompts (Critical AI Behavior)

The app uses 4 major system prompts that define AI behavior:

### 20.1 ANALYST_SYSTEM_PROMPT
- Used by: `analyzePremise()`
- Purpose: Analyze creative premises to extract entities, tone, gaps, vibes, and recommendations
- Output: Strict JSON with `schemaVersion: "premise_analysis_v2"`

### 20.2 ARCHITECT_SYSTEM_PROMPT (BLUEPRINT)
- Used by: `generateBlueprint()`
- Purpose: Design structural lorebook plan with module-driven layer architecture
- Enforces: Entry type configurations, module-to-type mapping, naming variety rules
- Output: JSON blueprint with layers, entries, batches

### 20.3 WRITER_SYSTEM_PROMPT
- Used by: `generateEntryBatch()`
- Purpose: Transform blueprint instructions into rich lorebook content
- Enforces: Bracketed data format, prose structure rules, token limits, keyword rules, recursive linking
- Includes: NENS v3.0 (Narrative Entity Naming System) with banned AI-default names
- Output: JSON with entry drafts

### 20.4 VALIDATOR_SYSTEM_PROMPT
- Used by: `validateLorebookContent()`
- Purpose: Quality check and repair of generated content
- Checks: Content issues, keyword issues, linking issues, configuration issues
- Output: JSON validation results with fixes

### 20.5 PREMISE_GENERATOR_SYSTEM_PROMPT (Scenario Seed Generator)
- Used by: `generatePremiseFromWizard()`
- Purpose: Generate condensed, structured scenario seeds from wizard inputs
- Enforces: Speculative element gate, naming rules, dynamic section selection, token budget scaling, seed quality standards
- Output: Structured JSON scenario seed

---

## 21. Key Utility Functions

| Function | Purpose |
|----------|---------|
| `createEntry()` | Create STEntry with defaults |
| `ensureUniqueUids()` | Fix duplicate UIDs |
| `detectDuplicates()` | Find exact/keyword/content duplicates |
| `detectOrphanEntries()` | Find unreachable entries |
| `detectCircularReferences()` | Find recursion loops |
| `exportProject()` | Generate and download V2 JSON |
| `classifyEntryTemplate()` | Auto-detect entry type from content |
| `classifyEntryType()` | Classify with confidence score |
| `compileDraftsToLorebook()` | Convert drafts to STEntry array |
| `countTokens()` / `estimateTokens()` | Token estimation (~4 chars/token) |
| `validateForExport()` | Pre-export validation |
| `normalizeForExport()` | Clean entries for export |
| `repairLorebookEntries()` | Auto-fix common issues |
| `simulateActivation()` | Test keyword activation |
| `buildLinkGraph()` | Build entry reference graph |
| `analyzeBudget()` | Token budget analysis |
| `validateKeywords()` | Check for broad/generic keywords |
| `configureSceneEvent()` | Apply scene event defaults |
| `configureSingleEvent()` | Apply single-use event config |
| `generateRandomProjectName()` | Random adjective+noun project name |

---

## 22. Error Handling & Resilience

### 22.1 JSON Parsing
- Multi-stage JSON repair pipeline:
  1. Try direct `JSON.parse()`
  2. Strip markdown fences and retry
  3. Extract JSON from mixed text via regex
  4. Attempt structural repair (missing brackets, trailing commas, quote issues)
  5. Brace-counting extraction for partial content
  6. AI-assisted repair as last resort

### 22.2 API Resilience
- Exponential backoff retry (3 attempts)
- Request deduplication prevents duplicate API calls
- Token clamping prevents exceeding model limits
- Graceful degradation with partial content salvaging

### 22.3 UI Error Handling
- ErrorBoundary component wraps GeneratorPanel
- Toast notifications for all user-facing errors
- Loading states with spinner indicators

---

## 23. Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Z` | Undo |
| `Ctrl+Y` or `Ctrl+Shift+Z` | Redo |
| `Enter` (in rename) | Confirm rename |
| `Escape` (in rename) | Cancel rename |
