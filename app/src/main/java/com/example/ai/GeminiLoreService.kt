package com.example.ai

import android.util.Log
import com.example.model.AiProviderType
import com.example.model.ArchitectActionType
import com.example.model.ArchitectMessage
import com.example.model.ArchitectProposal
import com.example.model.BlueprintEntryPlan
import com.example.model.CanonProfile
import com.example.model.CanonSuggestion
import com.example.model.CardConceptType
import com.example.model.CharacterCardV2
import com.example.model.ConfigRecommendation
import com.example.model.ConnectionProfile
import com.example.model.EntryTemplateType
import com.example.model.ForgeBlueprint
import com.example.model.ForgeConfig
import com.example.model.ForgeGenerationTarget
import com.example.model.ForgeModule
import com.example.model.LumiverseModulesConfig
import com.example.model.PremiseWizardState
import com.example.model.STEntry
import com.example.model.UniverseProject
import com.example.model.WorldDnaAnalysis
import com.example.model.WorldDnaEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GeminiLoreService"

/**
 * Live telemetry state emitted while any AI generation or reasoning task is running.
 * Allows the UI to display a collapsible live reasoning log, token stream preview,
 * provider/model badge, and an explicit Cancel button (with no forced timeout).
 */
data class AiReasoningTelemetry(
    val isGenerating: Boolean = false,
    val taskTitle: String = "",
    val activeModelBadge: String = "Built-in Gemini 2.5 Flash",
    val steps: List<String> = emptyList(),
    val reasoningText: String = "",
    val outputPreview: String = "",
    val chunksReceived: Int = 0,
    val startedAtEpochMs: Long = 0L
)

/**
 * Centralized AI Service Layer for LoreMaster Flex.
 * Supports:
 * 1. Built-in Gemini via Firebase AI Logic (`gemini-3.8-flash`) with real-time `generateContentStream`
 * 2. BYOK Connection Profiles (`OpenRouter`, `NanoGPT`, `Google AI Studio`, `Custom OpenAI-Compatible`)
 *    with live SSE streaming & `<think>` / `reasoning_content` extraction
 * 3. Instant Offline Synthesis Mode (Mock Mode) for zero-latency local testing
 */
object GeminiLoreService {

    private val _reasoningTelemetry = MutableStateFlow(AiReasoningTelemetry())
    val reasoningTelemetry: StateFlow<AiReasoningTelemetry> = _reasoningTelemetry.asStateFlow()

    @Volatile
    var activeProfile: ConnectionProfile = ConnectionProfile()

    // Internal system prompts — never exposed in user-facing textareas
    private const val NENS_NAMING_DIRECTIVE = """
NENS v3.0 (Narrative Entity Naming System):
- NEVER use overused AI-default names (e.g., Elara, Kael, Thorne, Vance, Lyra, Aethelgard, Whispering Woods, Shadow Syndicate).
- Construct culturally grounded, phonetically distinct names drawing from the premise's era, linguistics, and material culture.
- Pair every faction, landmark, and character with a concrete epithet or sensory hallmark.
"""

    private val WRITER_BRACKET_DIRECTIVE = """
SillyTavern & Lumiverse (World Books) High-Performance Lore Architecture:
1. TOKEN HYGIENE & DELIMITERS:
   - Begin EVERY entry with four dashes ('----') on the very first line to establish a strict token boundary preventing stacked prompt bleeding.
   - Follow '----' with compact metadata headers (Name:, AKA:, Type:, Location:), followed by evocative, high-density prose.
2. STRUCTURED SCENE TAGS:
   - For locations, factions, and events, use explicit directives: Definition:, Sensory Anchors:, Atmosphere:, and Pacing / Phases:.
3. WORLD RULES & STATE ANCHORS:
   - For WORLD_RULE entries, format as: ---- followed by newline, then RULE: [Axiom / Metaphysical Law]. [Mechanism: how it operates]. Violation WILL [immediate narrative consequence].
4. DYNAMIC HOOKS & LUMIVERSE MACROS:
   - Support Lumiverse variables ({{@var}}, {{\${'$'}}var}}), conditionals ({{if @var >= 1}}...{{/if}}), random pickers ({{pick::A::B::C}}), dice rolls ({{roll::1d20}}), and active reaction hooks: {{char}} will instantly...
5. CROSS-LINKING & RECURSION:
   - Explicitly weave the exact names of 2-3 companion entities into each entry's prose so recursive keyword activation fires reliably.
"""

    private val textModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val dnaSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "logline" to Schema.string("One-sentence high-concept hook"),
                "settingSummary" to Schema.string("Condensed world setting overview"),
                "coreConflict" to Schema.string("Central ideological or factional struggle"),
                "stakes" to Schema.string("What happens if the conflict boils over"),
                "themes" to Schema.array(Schema.string(), "3-5 thematic pillars"),
                "darkness" to Schema.integer("0-100 tone score"),
                "complexity" to Schema.integer("0-100 tone score"),
                "danger" to Schema.integer("0-100 tone score"),
                "hope" to Schema.integer("0-100 tone score"),
                "mystery" to Schema.integer("0-100 tone score"),
                "narrativeHooks" to Schema.array(Schema.string(), "3 actionable roleplay mystery hooks"),
                "missingPillars" to Schema.array(Schema.string(), "2-3 underdeveloped world-building areas"),
                "entities" to Schema.array(
                    Schema.obj(
                        properties = mapOf(
                            "name" to Schema.string(),
                            "type" to Schema.string("WORLD_RULE, CHARACTER, LOCATION, FACTION, ITEM, or CONCEPT"),
                            "importance" to Schema.string("CORE, MAJOR, or SUPPORTING"),
                            "summary" to Schema.string()
                        )
                    )
                )
            )
        )
    }

    private val dnaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = dnaSchema
            }
        )
    }

    private val blueprintSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "universeTitle" to Schema.string("Evocative universe title"),
                "linkingStrategySummary" to Schema.string("How entries cross-reference one another"),
                "entries" to Schema.array(
                    Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(),
                            "module" to Schema.string("foundation, characters, locations, factions, objects, systems, secrets, dynamics, or lexicon"),
                            "templateType" to Schema.string("WORLD_RULE, CHARACTER, LOCATION, FACTION, ITEM, SCENE_EVENT, DIALOGUE_PATTERN, DYNAMIC_TRIGGER, or CONCEPT"),
                            "primaryKeys" to Schema.array(Schema.string()),
                            "secondaryKeys" to Schema.array(Schema.string()),
                            "shouldMentionEntities" to Schema.array(Schema.string()),
                            "briefDirective" to Schema.string(),
                            "isCharacterCardCandidate" to Schema.boolean()
                        )
                    )
                )
            )
        )
    }

    private val blueprintModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = blueprintSchema
            }
        )
    }

    private val batchForgeSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "forgedEntries" to Schema.array(
                    Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(),
                            "content" to Schema.string("Rich bracketed SillyTavern V2 content"),
                            "primaryKeys" to Schema.array(Schema.string()),
                            "secondaryKeys" to Schema.array(Schema.string())
                        )
                    )
                )
            )
        )
    }

    private val batchForgeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = batchForgeSchema
            }
        )
    }

    private fun beginTelemetry(taskTitle: String, initialStep: String) {
        val profile = activeProfile
        val badge = "${profile.providerType.label} • ${profile.modelId.ifBlank { profile.providerType.defaultModel }}"
        _reasoningTelemetry.value = AiReasoningTelemetry(
            isGenerating = true,
            taskTitle = taskTitle,
            activeModelBadge = badge,
            steps = listOf(initialStep),
            reasoningText = "",
            outputPreview = "",
            chunksReceived = 0,
            startedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun appendReasoningStep(step: String) {
        _reasoningTelemetry.update { current ->
            current.copy(steps = (current.steps + step).takeLast(12))
        }
    }

    private fun appendStreamDelta(reasoningDelta: String = "", contentDelta: String = "") {
        _reasoningTelemetry.update { current ->
            val nextReasoning = if (reasoningDelta.isNotEmpty()) {
                (current.reasoningText + reasoningDelta).takeLast(2400)
            } else current.reasoningText
            val nextPreview = if (contentDelta.isNotEmpty()) {
                (current.outputPreview + contentDelta).takeLast(1600)
            } else current.outputPreview
            current.copy(
                reasoningText = nextReasoning,
                outputPreview = nextPreview,
                chunksReceived = current.chunksReceived + 1
            )
        }
    }

    fun endTelemetry() {
        _reasoningTelemetry.update { it.copy(isGenerating = false) }
    }

    /**
     * Unified dispatcher that runs either:
     * - Built-in Firebase AI Logic with live `generateContentStream` (and fallback to `generateContent`)
     * - Or BYOK ConnectionProfile (`OpenRouter`, `NanoGPT`, `Google AI Studio`, `Custom OpenAI`)
     * Without any forced timeout so the user can watch the reasoning stream and cancel whenever they want.
     */
    private suspend fun executeModelCall(
        taskTitle: String,
        systemInstruction: String,
        userPrompt: String,
        firebaseModel: GenerativeModel,
        expectJson: Boolean,
        jsonInstructionSuffix: String = ""
    ): String {
        val profile = activeProfile
        beginTelemetry(
            taskTitle = taskTitle,
            initialStep = "Initializing ${profile.providerType.label} (${profile.modelId.ifBlank { profile.providerType.defaultModel }})..."
        )
        try {
            coroutineContext.ensureActive()
            return if (profile.providerType == AiProviderType.BUILT_IN_GEMINI) {
                executeBuiltInGeminiStream(
                    fullPrompt = "$systemInstruction\n\n$userPrompt",
                    firebaseModel = firebaseModel
                )
            } else {
                val combinedUserPrompt = if (expectJson && jsonInstructionSuffix.isNotBlank()) {
                    "$userPrompt\n\nIMPORTANT: Return ONLY valid JSON matching this structure:\n$jsonInstructionSuffix"
                } else {
                    userPrompt
                }
                executeByokProfileCall(
                    profile = profile,
                    systemInstruction = systemInstruction,
                    userPrompt = combinedUserPrompt,
                    expectJson = expectJson
                )
            }
        } finally {
            endTelemetry()
        }
    }

    private suspend fun executeBuiltInGeminiStream(
        fullPrompt: String,
        firebaseModel: GenerativeModel
    ): String {
        appendReasoningStep("Connecting to Built-in Gemini stream (no timeout cap)...")
        val fullBuilder = StringBuilder()
        var streamSucceeded = false
        try {
            firebaseModel.generateContentStream(fullPrompt).collect { chunk ->
                coroutineContext.ensureActive()
                val delta = chunk.text.orEmpty()
                if (delta.isNotEmpty()) {
                    fullBuilder.append(delta)
                    appendStreamDelta(
                        reasoningDelta = if (fullBuilder.length < 400) delta else "",
                        contentDelta = delta
                    )
                    if (_reasoningTelemetry.value.chunksReceived == 1) {
                        appendReasoningStep("Receiving live token stream from Gemini...")
                    }
                }
            }
            streamSucceeded = fullBuilder.isNotEmpty()
        } catch (ce: CancellationException) {
            throw ce
        } catch (streamErr: Throwable) {
            Log.w(TAG, "Stream fallback to single generateContent: ${streamErr.message}")
            appendReasoningStep("Switching to direct structured generation...")
        }

        if (!streamSucceeded) {
            coroutineContext.ensureActive()
            val response = firebaseModel.generateContent(fullPrompt)
            val text = response.text.orEmpty()
            fullBuilder.append(text)
            appendStreamDelta(contentDelta = text.take(800))
        }
        appendReasoningStep("Synthesis complete (${fullBuilder.length} chars).")
        return fullBuilder.toString().trim()
    }

    /**
     * Null-safe string helper for org.json.JSONObject.
     * Prevents `"content": null` or `"reasoning": null` from returning the literal 4-char string `"null"`.
     */
    private fun JSONObject.safeStr(key: String): String {
        if (isNull(key)) return ""
        val v = optString(key, "")
        return if (v == "null") "" else v
    }

    private suspend fun executeByokProfileCall(
        profile: ConnectionProfile,
        systemInstruction: String,
        userPrompt: String,
        expectJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val apiKey = profile.apiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API key is empty for profile '${profile.name}'. Open Connection Profiles in the top bar to enter your ${profile.providerType.label} API key, or switch to Built-in Gemini.")
        }
        val modelId = profile.modelId.trim().ifBlank { profile.providerType.defaultModel }
        val isReasoningModel = modelId.contains("r1", ignoreCase = true) ||
            modelId.contains("reason", ignoreCase = true) ||
            modelId.contains("think", ignoreCase = true) ||
            profile.providerType == AiProviderType.NANOGPT

        val effectiveMaxTokens = if (isReasoningModel) {
            maxOf(profile.maxTokens, 16384)
        } else if (expectJson) {
            maxOf(profile.maxTokens, 8192)
        } else {
            maxOf(profile.maxTokens, 8192)
        }

        if (profile.providerType == AiProviderType.GOOGLE_AI_STUDIO) {
            appendReasoningStep("Dispatching request to Google AI Studio REST API ($modelId)...")
            val base = profile.baseUrl.trim().trimEnd('/').ifBlank { AiProviderType.GOOGLE_AI_STUDIO.defaultBaseUrl }
            val endpoint = "$base/models/$modelId:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "$systemInstruction\n\n$userPrompt"))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", profile.temperature.toDouble())
                        put("maxOutputTokens", effectiveMaxTokens)
                        if (expectJson) {
                            put("responseMimeType", "application/json")
                        }
                    }
                )
            }
            val rawResponse = postJsonHttp(endpoint, payload.toString(), emptyMap())
            val root = JSONObject(rawResponse)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.safeStr("text")
                .orEmpty()
            appendStreamDelta(contentDelta = text.take(1000))
            return@withContext extractThinkBlocksAndClean(text)
        }

        // OpenAI-compatible chat completions (OpenRouter, NanoGPT, Custom OpenAI)
        val base = profile.baseUrl.trim().trimEnd('/').ifBlank { profile.providerType.defaultBaseUrl }
        val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        appendReasoningStep("Connecting to ${profile.providerType.label} ($modelId)...")

        val messagesArr = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemInstruction))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesArr)
            put("temperature", profile.temperature.toDouble())
            put("max_tokens", effectiveMaxTokens)
            put("stream", true)
            if (profile.includeReasoning && profile.providerType == AiProviderType.OPENROUTER) {
                put("include_reasoning", true)
            }
        }

        val headers = mutableMapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream, application/json"
        )
        if (profile.providerType == AiProviderType.OPENROUTER) {
            headers["HTTP-Referer"] = "https://loremaster.aistudio.app"
            headers["X-Title"] = "LoreMaster Flex"
        }

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val rawNonSseLines = StringBuilder()

        runCatching {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 0 // No forced read timeout; cancellable by user
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }
                val status = conn.responseCode
                if (status !in 200..299) {
                    val errBody = runCatching {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    }.getOrDefault("")
                    throw IllegalStateException("${profile.providerType.label} HTTP $status: ${errBody.take(260)}")
                }

                val contentType = conn.contentType.orEmpty()
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    if (contentType.contains("text/event-stream", ignoreCase = true)) {
                        appendReasoningStep("Streaming live tokens & reasoning from ${profile.providerType.label}...")
                        var line: String?
                        var isInsideThinkTag = false

                        while (reader.readLine().also { line = it } != null) {
                            coroutineContext.ensureActive()
                            val trimmed = line?.trim() ?: continue
                            if (trimmed.isEmpty()) continue
                            if (!trimmed.startsWith("data:")) {
                                if (trimmed.startsWith("{")) rawNonSseLines.append(trimmed)
                                continue
                            }
                            val dataStr = trimmed.removePrefix("data:").trim()
                            if (dataStr == "[DONE]" || dataStr.isEmpty()) continue
                            runCatching {
                                val chunkJson = JSONObject(dataStr)
                                val choice0 = chunkJson.optJSONArray("choices")?.optJSONObject(0)
                                val deltaObj = choice0?.optJSONObject("delta") ?: choice0?.optJSONObject("message")
                                if (deltaObj != null) {
                                    val reasoningPiece = deltaObj.safeStr("reasoning").ifEmpty {
                                        deltaObj.safeStr("reasoning_content")
                                    }
                                    val contentPiece = deltaObj.safeStr("content").ifEmpty {
                                        deltaObj.safeStr("text")
                                    }

                                    // Route dedicated reasoning fields directly to reasoning telemetry
                                    if (reasoningPiece.isNotEmpty()) {
                                        reasoningBuilder.append(reasoningPiece)
                                        appendStreamDelta(reasoningDelta = reasoningPiece)
                                    }

                                    // Filter <think>...</think> if the provider streams thoughts inside content
                                    if (contentPiece.isNotEmpty()) {
                                        val piece = contentPiece
                                        if (!isInsideThinkTag && piece.contains("<think>", ignoreCase = true)) {
                                            isInsideThinkTag = true
                                        }

                                        if (isInsideThinkTag) {
                                            if (piece.contains("</think>", ignoreCase = true)) {
                                                val thinkPart = piece.substringBefore("</think>")
                                                val postThink = piece.substringAfter("</think>")
                                                isInsideThinkTag = false
                                                if (thinkPart.isNotEmpty()) {
                                                    val cleanThink = thinkPart.replace("<think>", "", ignoreCase = true)
                                                    reasoningBuilder.append(cleanThink)
                                                    appendStreamDelta(reasoningDelta = cleanThink)
                                                }
                                                if (postThink.isNotEmpty()) {
                                                    contentBuilder.append(postThink)
                                                    appendStreamDelta(contentDelta = postThink)
                                                }
                                            } else {
                                                val cleanThink = piece.replace("<think>", "", ignoreCase = true)
                                                reasoningBuilder.append(cleanThink)
                                                appendStreamDelta(reasoningDelta = cleanThink)
                                            }
                                        } else {
                                            contentBuilder.append(piece)
                                            appendStreamDelta(contentDelta = piece)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val body = reader.readText()
                        parseNonStreamOpenAiResponse(body, contentBuilder, reasoningBuilder)
                    }
                }
            } finally {
                runCatching { conn.disconnect() }
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Streaming BYOK attempt encountered issue (${err.message}), retrying non-stream...")
        }

        if (contentBuilder.isEmpty() && rawNonSseLines.isNotEmpty()) {
            runCatching { parseNonStreamOpenAiResponse(rawNonSseLines.toString(), contentBuilder, reasoningBuilder) }
        }

        var cleanedResult = extractThinkBlocksAndClean(contentBuilder.toString())

        // If reasoning model put a valid JSON object in reasoning_content when content was empty
        if (cleanedResult.isBlank() && reasoningBuilder.isNotEmpty() && expectJson) {
            val fromReasoning = reasoningBuilder.toString().trim()
            if (fromReasoning.contains("{") && fromReasoning.contains("}")) {
                runCatching {
                    val candidate = extractSafeJsonObject(fromReasoning)
                    cleanedResult = candidate.toString()
                }
            }
        }

        // Automatic fallback to non-streaming call if streaming returned blank or truncated before JSON/markdown
        if (cleanedResult.isBlank() || (expectJson && !cleanedResult.contains("{"))) {
            coroutineContext.ensureActive()
            appendReasoningStep("Completing ${profile.providerType.label} response via direct non-stream synthesis...")
            val directPrompt = if (expectJson) {
                "$userPrompt\n\nCRITICAL: Return ONLY a single valid JSON object. Do NOT output reasoning or thinking tags."
            } else {
                "$userPrompt\n\nCRITICAL: Return ONLY the final requested document in clean Markdown. Begin directly with '# CORE PREMISE'. Do NOT output internal thinking or scratchpad notes."
            }
            val nonStreamMessages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemInstruction))
                put(JSONObject().put("role", "user").put("content", directPrompt))
            }
            val nonStreamPayload = JSONObject().apply {
                put("model", modelId)
                put("messages", nonStreamMessages)
                put("temperature", profile.temperature.toDouble())
                put("max_tokens", effectiveMaxTokens)
                put("stream", false)
            }
            val fallbackBody = postJsonHttp(
                endpoint = endpoint,
                bodyJson = nonStreamPayload.toString(),
                headers = headers.filterKeys { it != "Accept" }
            )
            val fallbackContent = StringBuilder()
            val fallbackReasoning = StringBuilder()
            parseNonStreamOpenAiResponse(fallbackBody, fallbackContent, fallbackReasoning)
            val candidate = extractThinkBlocksAndClean(fallbackContent.toString())
            if (candidate.isNotBlank()) {
                cleanedResult = candidate
            } else if (expectJson && fallbackReasoning.contains("{") && fallbackReasoning.contains("}")) {
                runCatching {
                    cleanedResult = extractSafeJsonObject(fallbackReasoning.toString()).toString()
                }
            }
        }

        if (cleanedResult.isBlank()) {
            throw IllegalStateException("${profile.providerType.label} returned an empty response. Try again or test another model in Connection Profiles.")
        }
        cleanedResult
    }

    private fun parseNonStreamOpenAiResponse(
        body: String,
        contentBuilder: StringBuilder,
        reasoningBuilder: StringBuilder
    ) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val json = JSONObject(trimmed)
        val choice0 = json.optJSONArray("choices")?.optJSONObject(0)
        val msgObj = choice0?.optJSONObject("message") ?: choice0?.optJSONObject("delta") ?: json
        val reasoning = msgObj.safeStr("reasoning").ifEmpty {
            msgObj.safeStr("reasoning_content")
        }
        val content = msgObj.safeStr("content").ifEmpty {
            choice0?.safeStr("text").orEmpty().ifEmpty { json.safeStr("output") }
        }
        if (reasoning.isNotEmpty()) {
            reasoningBuilder.append(reasoning)
            appendStreamDelta(reasoningDelta = reasoning.take(1500))
        }
        if (content.isNotEmpty()) {
            contentBuilder.append(content)
            appendStreamDelta(contentDelta = content.take(1200))
        }
    }

    private fun postJsonHttp(
        endpoint: String,
        bodyJson: String,
        headers: Map<String, String>
    ): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 0 // User-cancellable without artificial timeout
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(bodyJson)
                it.flush()
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
                throw IllegalStateException("HTTP $code: ${err.take(240)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * Extracts `<think>...</think>` blocks into the live reasoning telemetry and returns the clean output.
     * Also detects unclosed `<think>` tags or thinking monologues and removes them so they never pollute
     * the user's Scenario Seed, Lorebook entries, or Character Cards.
     */
    private fun extractThinkBlocksAndClean(raw: String): String {
        var current = raw

        // 1. Strip all closed <think>...</think> blocks
        val thinkRegex = Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE)
        val matches = thinkRegex.findAll(current).toList()
        if (matches.isNotEmpty()) {
            val extractedThoughts = matches.joinToString("\n") { it.groupValues[1].trim() }
            appendStreamDelta(reasoningDelta = "\n$extractedThoughts")
            current = current.replace(thinkRegex, "").trim()
        }

        // 2. Strip unclosed <think> tag if stream was truncated or model omitted </think>
        if (current.contains("<think>", ignoreCase = true)) {
            val afterOpen = current.substringAfter("<think>", "").trim()
            val jsonBraceIdx = afterOpen.indexOf('{')
            val markdownHeaderIdx = listOf(
                afterOpen.indexOf("\n# "),
                afterOpen.indexOf("\n## "),
                afterOpen.indexOf("\n### "),
                afterOpen.indexOf("\n**CORE PREMISE"),
                afterOpen.indexOf("\n**Core Premise"),
                afterOpen.indexOf("\n# CORE PREMISE"),
                afterOpen.indexOf("\n---")
            ).filter { it >= 0 }.minOrNull() ?: -1

            current = when {
                jsonBraceIdx >= 0 -> {
                    appendStreamDelta(reasoningDelta = "\n${afterOpen.substring(0, jsonBraceIdx).trim()}")
                    afterOpen.substring(jsonBraceIdx).trim()
                }
                markdownHeaderIdx >= 0 -> {
                    appendStreamDelta(reasoningDelta = "\n${afterOpen.substring(0, markdownHeaderIdx).trim()}")
                    afterOpen.substring(markdownHeaderIdx).trim()
                }
                else -> {
                    // Entire unclosed block was reasoning! Route to telemetry and return blank
                    appendStreamDelta(reasoningDelta = "\n$afterOpen")
                    ""
                }
            }
        }

        // 3. Detect and remove thinking monologue preambles if a model wrote reasoning in prose without <think> tags
        // e.g., "The user wants a rich structured Scenario Seed... Let's design: ... # CORE PREMISE"
        val headerPatterns = listOf(
            "\n# CORE PREMISE",
            "\n# Core Premise",
            "\n## CORE PREMISE",
            "\n## Core Premise",
            "\n**CORE PREMISE",
            "\n**Core Premise",
            "\n# "
        )
        val firstHeaderIdx = headerPatterns.map { current.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: -1

        if (firstHeaderIdx > 0) {
            val preamble = current.substring(0, firstHeaderIdx).trim()
            val isThinkingMonologue = preamble.startsWith("The user wants", ignoreCase = true) ||
                preamble.startsWith("Need to avoid", ignoreCase = true) ||
                preamble.startsWith("Let's design", ignoreCase = true) ||
                preamble.startsWith("Let's think", ignoreCase = true) ||
                preamble.startsWith("I need to", ignoreCase = true) ||
                preamble.startsWith("Thinking process", ignoreCase = true) ||
                preamble.startsWith("Thought process", ignoreCase = true)

            if (isThinkingMonologue) {
                appendStreamDelta(reasoningDelta = "\n$preamble")
                current = current.substring(firstHeaderIdx).trim()
            }
        } else if (firstHeaderIdx < 0 && current.isNotBlank()) {
            // Check if the entire response is a thinking monologue without any actual artifact
            val isOnlyThinkingMonologue = current.startsWith("The user wants", ignoreCase = true) ||
                current.startsWith("Need to avoid", ignoreCase = true) ||
                current.startsWith("Let's design:", ignoreCase = true) ||
                current.startsWith("Thinking Process:", ignoreCase = true) ||
                current.startsWith("Thought Process:", ignoreCase = true)

            if (isOnlyThinkingMonologue) {
                appendStreamDelta(reasoningDelta = "\n$current")
                current = ""
            }
        }

        return current.trim()
    }

    /**
     * Safely extracts a JSONObject even if a BYOK model wraps JSON in markdown code fences, prose, or trailing commas.
     */
    private fun extractSafeJsonObject(raw: String): JSONObject {
        val cleaned = extractThinkBlocksAndClean(raw)
            .replace(Regex("```json", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()

        fun sanitizeJsonCandidate(candidate: String): String {
            return candidate
                .replace(Regex(",\\s*([}\\]])"), "$1")
                .trim()
        }

        runCatching { return JSONObject(sanitizeJsonCandidate(cleaned)) }

        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val sub = sanitizeJsonCandidate(cleaned.substring(firstBrace, lastBrace + 1))
            runCatching { return JSONObject(sub) }
        }
        throw IllegalArgumentException("Model response did not contain a valid JSON object.")
    }

    /**
     * Tests a user's Connection Profile in Settings and returns latency + model response.
     */
    suspend fun testConnectionProfile(profile: ConnectionProfile): Result<String> {
        val previous = activeProfile
        return runCatching {
            activeProfile = profile
            val start = System.currentTimeMillis()
            val reply = executeModelCall(
                taskTitle = "Testing Connection: ${profile.name}",
                systemInstruction = "You are a connectivity check endpoint.",
                userPrompt = "Reply with a short confirmation: 'LoreMaster Flex connected.'",
                firebaseModel = textModel,
                expectJson = false
            )
            val elapsedMs = System.currentTimeMillis() - start
            "Connected to ${profile.providerType.label} (${profile.modelId.ifBlank { profile.providerType.defaultModel }}) in ${elapsedMs}ms — ${reply.take(80)}"
        }.also {
            activeProfile = previous
        }
    }

    /**
     * Stage 1A: Generates a rich Scenario Seed from the Premise Wizard fields.
     */
    suspend fun generatePremiseSeed(
        wizard: PremiseWizardState,
        useMockMode: Boolean
    ): Result<String> {
        if (useMockMode) {
            return Result.success(synthesizeMockPremiseSeed(wizard))
        }
        return runCatching {
            appendReasoningStep("Analyzing 8 Premise Pillars & Speculative Realism Gate (${wizard.speculativeMode.label})...")
            val systemPrompt = buildString {
                appendLine("You are the Scenario Seed Architect for SillyTavern & Lumiverse world-building.")
                appendLine("CRITICAL DIRECTIVE: Output ONLY the final Scenario Seed document in clean Markdown. Begin directly with '# CORE PREMISE'. Do NOT output internal planning notes, thinking scratchpads, or conversational preambles.")
                appendLine(NENS_NAMING_DIRECTIVE)
            }
            val userPrompt = buildString {
                appendLine("Synthesize a rich, structured Scenario Seed in clean Markdown with sections: # CORE PREMISE, # WORLD AXIOMS, # FACTIONS AT WAR, # KEY CAST SEEDS, # LOCATIONS, # RELICS & SYSTEMS, and # OPENING ROLEPLAY HOOKS.")
                appendLine("\nUSER WIZARD INPUTS:")
                appendLine(wizard.buildEffectivePremise())
                appendLine("\nREMINDER: Begin directly with '# CORE PREMISE'. No conversational filler, no internal thinking.")
            }
            val text = executeModelCall(
                taskTitle = "Synthesizing AI Scenario Seed",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = textModel,
                expectJson = false
            )
            if (text.isBlank()) throw IllegalStateException("Model returned empty premise seed.")
            text
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "generatePremiseSeed failed", e)
        }
    }

    /**
     * Stage 1B: Extracts World DNA metrics, entities, missing pillars, and config recommendations.
     * Resilient to BYOK / NanoGPT formatting variations.
     */
    suspend fun analyzePremiseDna(
        wizard: PremiseWizardState,
        useMockMode: Boolean
    ): Result<WorldDnaAnalysis> {
        if (useMockMode) {
            return Result.success(synthesizeMockDna(wizard))
        }
        return runCatching {
            val systemPrompt = "You are the World DNA Diagnostic Engine. Always output a single valid JSON object and nothing else.\n$NENS_NAMING_DIRECTIVE"
            val userPrompt = "Analyze this creative premise and extract structured World DNA metrics, core entities, narrative hooks, and missing world-building pillars.\n\nPREMISE:\n${wizard.buildEffectivePremise()}"
            val jsonSchemaHint = """{"logline":"...","settingSummary":"...","coreConflict":"...","stakes":"...","themes":["..."],"darkness":70,"complexity":80,"danger":65,"hope":45,"mystery":85,"narrativeHooks":["..."],"missingPillars":["..."],"entities":[{"name":"...","type":"FACTION|CHARACTER|LOCATION|WORLD_RULE|ITEM|CONCEPT","importance":"CORE|MAJOR","summary":"..."}]}"""
            val jsonText = executeModelCall(
                taskTitle = "Extracting World DNA & Structural Pillars",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = dnaModel,
                expectJson = true,
                jsonInstructionSuffix = jsonSchemaHint
            )
            val parsedObj = runCatching { extractSafeJsonObject(jsonText) }.getOrNull()
            if (parsedObj != null) {
                parseDnaJson(parsedObj, wizard)
            } else {
                // Resilient fallback if a BYOK model returned markdown text instead of strict JSON
                val baseDna = synthesizeMockDna(wizard)
                val firstLine = jsonText.lines().firstOrNull { it.isNotBlank() }?.take(160) ?: baseDna.logline
                baseDna.copy(
                    logline = firstLine,
                    settingSummary = jsonText.take(280).ifBlank { baseDna.settingSummary }
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "analyzePremiseDna failed", e)
        }
    }

    /**
     * Stage 2: Generates the structural ForgeBlueprint based on:
     * - `config.generationTarget` (`LOREBOOK_ONLY`, `CHARACTER_CARD_ONLY`, or `CARD_WITH_LOREBOOK`)
     * - `config.cardConceptType` (`Type A` Single Character .. `Type E` Universe/Simulator)
     * - `config.targetEntryCount` (`10..250` entries, keyword-triggered without artificial token caps)
     * - `config.lorebookNpcCount` (number of NPC entries stored inside the Lorebook)
     */
    suspend fun generateBlueprint(
        project: UniverseProject,
        wizard: PremiseWizardState,
        config: ForgeConfig,
        useMockMode: Boolean
    ): Result<ForgeBlueprint> {
        if (useMockMode) {
            return Result.success(synthesizeMockBlueprint(project, wizard, config))
        }
        return runCatching {
            val totalEntries = if (config.generationTarget.includesLorebook) {
                config.targetEntryCount.coerceIn(10, 250)
            } else 0
            val npcEntries = if (config.generationTarget.includesLorebook) {
                config.lorebookNpcCount.coerceIn(0, totalEntries)
            } else 0

            val plannedCard = if (config.generationTarget.includesCard) {
                appendReasoningStep("Designing ${config.cardConceptType.label} CHARX / CCV3 Card...")
                generateConceptPrimaryCard(project, wizard, config, useMockMode = false)
            } else null

            if (!config.generationTarget.includesLorebook) {
                return@runCatching ForgeBlueprint(
                    universeTitle = project.name,
                    linkingStrategySummary = "Standalone ${config.cardConceptType.label} CHARX / CCV3 Character Card (no Lorebook entries requested).",
                    plannedEntries = emptyList(),
                    plannedPrimaryCard = plannedCard,
                    generationTarget = config.generationTarget,
                    cardConceptType = config.cardConceptType,
                    batchSize = 5
                )
            }

            // Request up to 25 seed blueprint entries from the LLM in a single structured call, then scale to exact targetEntryCount (10..250)
            val llmSeedCount = minOf(25, totalEntries)
            val llmNpcCount = minOf(npcEntries, llmSeedCount)
            val systemPrompt = "You are the SillyTavern V2 & Lumiverse Lorebook Architect.\n$NENS_NAMING_DIRECTIVE"
            val userPrompt = buildString {
                appendLine("Design a tightly interconnected blueprint of $llmSeedCount Lorebook entries across these enabled modules: ${config.enabledModules.joinToString { it.id }}.")
                appendLine("IMPORTANT: Include $llmNpcCount NPC entries (module='characters', templateType='CHARACTER') stored directly inside the Lorebook as keyword-triggered entries.")
                appendLine("Note: Lorebook entries are keyword-triggered on demand (not all injected at once), so there is NO context token cap.")
                if (config.generationTarget.includesCard) {
                    appendLine("Primary Attached Card Concept: ${config.cardConceptType.label} — ${config.cardConceptType.description}")
                }
                appendLine("Procedural Systems Enabled: ${config.enabledGenerators.joinToString { it.title }}.")
                appendLine("Procedural Tuning: Randomness=${config.randomnessLevel}%, Density=${config.contentDensity.label}, Persistence=${config.persistenceMode.label}, Weights=${config.weightDistribution.label}.")
                appendLine("Ensure every entry specifies 2-3 shouldMentionEntities so entries cross-link into a cohesive recursive web.")
                appendLine("\nUNIVERSE PREMISE:\n${wizard.buildEffectivePremise()}")
            }
            val jsonHint = """{"universeTitle":"...","linkingStrategySummary":"...","entries":[{"title":"...","module":"foundation","templateType":"WORLD_RULE","primaryKeys":["..."],"secondaryKeys":["..."],"shouldMentionEntities":["..."],"briefDirective":"...","isCharacterCardCandidate":false}]}"""
            val jsonText = executeModelCall(
                taskTitle = "Architecting Interconnected Blueprint ($totalEntries Entries, $npcEntries NPCs)",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = blueprintModel,
                expectJson = true,
                jsonInstructionSuffix = jsonHint
            )
            val baseBp = runCatching { parseBlueprintJson(extractSafeJsonObject(jsonText)) }
                .getOrElse { synthesizeMockBlueprint(project, wizard, config) }

            ensureTargetBlueprintScale(
                baseBlueprint = baseBp,
                project = project,
                wizard = wizard,
                config = config,
                plannedCard = plannedCard
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "generateBlueprint failed", e)
        }
    }

    /**
     * Stage 3: Forges rich bracketed SillyTavern V2 content for a batch of BlueprintEntryPlan items.
     */
    suspend fun forgeBlueprintBatch(
        project: UniverseProject,
        batch: List<BlueprintEntryPlan>,
        useMockMode: Boolean
    ): Result<List<BlueprintEntryPlan>> {
        if (useMockMode) {
            return Result.success(batch.map { synthesizeMockForgedPlan(project, it) })
        }
        return runCatching {
            val systemPrompt = "You are the Master Lorebook Writer for '${project.name}' (${project.genre}).\n$NENS_NAMING_DIRECTIVE\n$WRITER_BRACKET_DIRECTIVE"
            val userPrompt = buildString {
                appendLine("Write complete, evocative SillyTavern V2 content (180-320 characters minimum) for each planned entry below. You MUST explicitly mention each entry's `shouldMentionEntities` inside its prose.")
                batch.forEachIndexed { idx, item ->
                    appendLine("\n${idx + 1}. TITLE: ${item.title} | TYPE: ${item.templateType.name} | KEYS: ${item.primaryKeys.joinToString(", ")}")
                    appendLine("   MUST MENTION ENTITIES: ${item.shouldMentionEntities.joinToString(", ")}")
                    appendLine("   DIRECTIVE: ${item.briefDirective}")
                }
            }
            val jsonHint = """{"forgedEntries":[{"title":"...","content":"...","primaryKeys":["..."],"secondaryKeys":["..."]}]}"""
            val responseText = executeModelCall(
                taskTitle = "Forging Batch (${batch.firstOrNull()?.title.orEmpty()})",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = batchForgeModel,
                expectJson = true,
                jsonInstructionSuffix = jsonHint
            )
            val json = extractSafeJsonObject(responseText)
            val arr = json.optJSONArray("forgedEntries") ?: JSONArray()
            val forgedByTitle = mutableMapOf<String, JSONObject>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                forgedByTitle[obj.optString("title").lowercase()] = obj
            }

            batch.mapIndexed { idx, plan ->
                val matched = forgedByTitle[plan.title.lowercase()] ?: arr.optJSONObject(idx)
                if (matched != null) {
                    val content = matched.optString("content").ifBlank { synthesizeMockForgedPlan(project, plan).forgedContent }
                    plan.copy(
                        forgedContent = content,
                        isForged = true
                    )
                } else {
                    synthesizeMockForgedPlan(project, plan)
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "forgeBlueprintBatch failed", e)
        }
    }

    /**
     * AI Entry Enhancer / Continue / Keyword Optimizer for the Studio Entry Editor.
     */
    suspend fun enhanceEntryContent(
        project: UniverseProject,
        entry: STEntry,
        mode: String, // "EXPAND", "CONTINUE", "FIX_KEYS"
        useMockMode: Boolean
    ): Result<STEntry> {
        if (useMockMode) {
            return Result.success(synthesizeMockEnhancedEntry(project, entry, mode))
        }
        return runCatching {
            val otherNames = project.lorebook.entries
                .filter { it.uid != entry.uid }
                .map { it.comment.substringBefore("—").trim() }
                .take(6)

            val prompt = when (mode) {
                "FIX_KEYS" -> """
                    Suggest 4-6 specific, non-generic SillyTavern activation keywords (comma-separated only) for this Lorebook entry in '${project.name}':
                    Title: ${entry.comment}
                    Content: ${entry.content}
                    Return ONLY a comma-separated list of keywords.
                """.trimIndent()
                "CONTINUE" -> """
                    Continue and deepen this SillyTavern V2 Lorebook entry for '${project.name}' (${project.genre}).
                    Cross-reference at least one of these canonical entities: ${otherNames.joinToString(", ")}.
                    Keep the bracketed data style if present.
                    Current Title: ${entry.comment}
                    Current Content: ${entry.content}
                """.trimIndent()
                else -> """
                    Rewrite and expand this SillyTavern V2 Lorebook entry into a rich, high-signal bracketed record for '${project.name}' (${project.genre}).
                    Weave in cross-references to: ${otherNames.joinToString(", ")}.
                    Template Type: ${entry.classifyType().name} (${entry.classifyType().bracketTemplate})
                    Current Title: ${entry.comment}
                    Current Content: ${entry.content}
                """.trimIndent()
            }

            val output = executeModelCall(
                taskTitle = "Enhancing '${entry.comment}' ($mode)",
                systemInstruction = WRITER_BRACKET_DIRECTIVE,
                userPrompt = prompt,
                firebaseModel = textModel,
                expectJson = false
            )
            if (output.isBlank()) throw IllegalStateException("Empty enhancement response.")
            if (mode == "FIX_KEYS") {
                val keys = output.split(",", "\n")
                    .map { it.trim().removePrefix("-").trim() }
                    .filter { it.length >= 3 }
                    .take(7)
                entry.copy(key = keys.ifEmpty { entry.key })
            } else {
                entry.copy(content = output)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "enhanceEntryContent failed", e)
        }
    }

    /**
     * Live Architect Co-Pilot: Responds to user requests and generates actionable 1-tap proposals.
     */
    suspend fun askArchitectCoPilot(
        project: UniverseProject,
        activeEntry: STEntry?,
        activeCard: CharacterCardV2?,
        userPrompt: String,
        useMockMode: Boolean
    ): Result<ArchitectMessage> {
        val proposals = buildContextualProposals(project, activeEntry, activeCard, userPrompt)
        if (useMockMode) {
            return Result.success(
                ArchitectMessage(
                    isUser = false,
                    text = synthesizeArchitectReplyText(project, activeEntry, activeCard, userPrompt, proposals),
                    proposals = proposals
                )
            )
        }
        return runCatching {
            val entrySummary = project.lorebook.entries.take(12).joinToString("\n") {
                "- [#${it.uid} ${it.classifyType().badgeCode}] ${it.comment} (keys: ${it.key.joinToString(", ")})"
            }
            val cardSummary = project.characterCards.joinToString("\n") {
                "- [Card] ${it.name} (${it.roleTitle}, Faction: ${it.factionAffiliation})"
            }
            val fullPrompt = buildString {
                appendLine("Active Universe: ${project.name} (${project.genre} — ${project.tone})")
                appendLine("Lorebook Entries (${project.lorebook.entries.size} total):\n$entrySummary")
                appendLine("Character Cards (${project.characterCards.size} total):\n$cardSummary")
                if (activeEntry != null) {
                    appendLine("\nCurrently Selected Entry: ${activeEntry.comment}\nContent: ${activeEntry.content}")
                }
                if (activeCard != null) {
                    appendLine("\nCurrently Selected Character Card: ${activeCard.name} (${activeCard.roleTitle})\nFirst Message: ${activeCard.firstMes.take(220)}")
                }
                appendLine("\nProvide a concise, actionable architectural analysis (3-5 sentences) answering the user's request. Note that interactive 1-tap Action Proposals are attached below your message.")
                appendLine("USER REQUEST: $userPrompt")
            }
            val reply = executeModelCall(
                taskTitle = "Architect Co-Pilot Reasoning",
                systemInstruction = "You are the Live Architect Co-Pilot inside LoreMaster Flex, an expert SillyTavern V2, CCV3 & Lumiverse `.charx` world-building assistant.",
                userPrompt = fullPrompt,
                firebaseModel = textModel,
                expectJson = false
            ).ifBlank {
                synthesizeArchitectReplyText(project, activeEntry, activeCard, userPrompt, proposals)
            }
            ArchitectMessage(
                isUser = false,
                text = reply,
                proposals = proposals
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "askArchitectCoPilot failed", e)
        }
    }

    private fun buildContextualProposals(
        project: UniverseProject,
        activeEntry: STEntry?,
        activeCard: CharacterCardV2?,
        userPrompt: String
    ): List<ArchitectProposal> {
        val lower = userPrompt.lowercase()
        val proposals = mutableListOf<ArchitectProposal>()
        val nextUid = (project.lorebook.entries.maxOfOrNull { it.uid } ?: 100) + 1

        val targetCharEntry = activeEntry?.takeIf { it.classifyType() == EntryTemplateType.CHARACTER }
            ?: project.lorebook.entries.firstOrNull { entry ->
                entry.classifyType() == EntryTemplateType.CHARACTER &&
                    project.characterCards.none { it.linkedLoreEntryUid == entry.uid }
            }
        if (lower.contains("card") || lower.contains("character") || lower.contains("charx") || lower.contains("promote") || targetCharEntry != null) {
            val sourceEntry = targetCharEntry ?: activeEntry ?: project.lorebook.entries.firstOrNull()
            if (sourceEntry != null) {
                val card = CharacterCardV2.fromLoreEntry(sourceEntry, project.name, project.genre)
                proposals.add(
                    ArchitectProposal(
                        actionType = ArchitectActionType.CREATE_CHARACTER_CARD,
                        title = "Generate CHARX / CCV3 Card: ${card.name}",
                        summary = "Builds a complete Lumiverse CHARX & CCV3 Character Card (Personality, Scenario, First Message, Expressions & Lumiverse Modules) synced to Entry #${sourceEntry.uid}.",
                        beforePreview = "Lore Entry #${sourceEntry.uid}: ${sourceEntry.comment}",
                        afterPreview = "Card: ${card.name} (${card.roleTitle})\nFirst Mes: ${card.firstMes.take(140)}…",
                        proposedCard = card,
                        targetEntryUid = sourceEntry.uid
                    )
                )
            }
        }

        if (activeEntry != null) {
            val enhanced = synthesizeMockEnhancedEntry(project, activeEntry, "EXPAND")
            proposals.add(
                ArchitectProposal(
                    actionType = ArchitectActionType.UPDATE_ENTRY,
                    title = "Enrich & Cross-Link '${activeEntry.comment}'",
                    summary = "Expands bracketed attributes, sharpens trigger keywords, and weaves canonical cross-references.",
                    beforePreview = activeEntry.content.take(120),
                    afterPreview = enhanced.content.take(180),
                    targetEntryUid = activeEntry.uid,
                    proposedEntry = enhanced
                )
            )
        }

        val newEntryTitle = when {
            lower.contains("faction") || lower.contains("rival") -> "The Choir of Unspooled Glass"
            lower.contains("rule") || lower.contains("law") -> "RULE: The Resonance Echo Tax"
            lower.contains("location") || lower.contains("place") -> "The Subterranean Meridian Archive"
            else -> "The Crimson Ledger Conspiracy"
        }
        val anchorEntity = project.lorebook.entries.firstOrNull()?.comment?.substringBefore("—")?.trim() ?: project.name
        val newEntry = STEntry(
            uid = nextUid,
            comment = newEntryTitle,
            key = listOf(newEntryTitle.removePrefix("RULE: ").removePrefix("The ").trim(), "crimson ledger", "meridian archive").distinct(),
            keysecondary = listOf(anchorEntity),
            content = "[ title($newEntryTitle); origin(Forged by Architect Co-Pilot for ${project.name}); connection(Directly challenges $anchorEntity and alters the balance of power); atmosphere(${project.tone}); hooks(When triggered, introduces an immediate investigative lead or factional ultimatum); ]",
            group = if (newEntryTitle.startsWith("RULE:")) "foundation" else "factions",
            position = if (newEntryTitle.startsWith("RULE:")) 4 else 1,
            order = if (newEntryTitle.startsWith("RULE:")) 280 else 140,
            constant = newEntryTitle.startsWith("RULE:"),
            preventRecursion = true,
            displayIndex = project.lorebook.entries.size
        )
        proposals.add(
            ArchitectProposal(
                actionType = ArchitectActionType.CREATE_ENTRY,
                title = "Add Interconnected Entry: $newEntryTitle",
                summary = "Injects a new canon-linked Lorebook V2 entry referencing '$anchorEntity'.",
                beforePreview = "(New Entry UID #$nextUid)",
                afterPreview = newEntry.content,
                proposedEntry = newEntry
            )
        )

        proposals.add(
            ArchitectProposal(
                actionType = ArchitectActionType.AUTO_LINK_UNIVERSE,
                title = "Auto-Link All Entity Mentions & Sync CHARX Cards",
                summary = "Scans every Lorebook entry and Character Card to bind missing secondary keys and repair Canon Auditor warnings.",
                beforePreview = "Current Entries: ${project.lorebook.entries.size} • Cards: ${project.characterCards.size}",
                afterPreview = "Bidirectional secondary keys and Character Card ↔ Lorebook bindings synchronized."
            )
        )

        return proposals
    }

    private fun synthesizeArchitectReplyText(
        project: UniverseProject,
        activeEntry: STEntry?,
        activeCard: CharacterCardV2?,
        userPrompt: String,
        proposals: List<ArchitectProposal>
    ): String {
        val contextFocus = when {
            activeEntry != null -> "focusing on '${activeEntry.comment}' (#${activeEntry.uid})"
            activeCard != null -> "focusing on Character Card '${activeCard.name}'"
            else -> "across '${project.name}' (${project.lorebook.entries.size} entries, ${project.characterCards.size} character cards)"
        }
        return "I analyzed your universe state $contextFocus in response to \"$userPrompt\". " +
            "To strengthen narrative consistency and recursive keyword activation in SillyTavern & Lumiverse, I prepared ${proposals.size} one-tap architectural actions below—you can preview the exact before/after diffs and apply any of them directly."
    }

    private fun synthesizeMockPremiseSeed(wizard: PremiseWizardState): String = """
# SCENARIO SEED: ${wizard.genre.uppercase()}
**Era & Setting:** ${wizard.era} | **Subgenre:** ${wizard.subgenre}

## 1. CORE PREMISE & STRANGE FACTOR
${wizard.conflict}
* **The Anomaly:** ${wizard.strangeFactor}
* **Protagonist Role (`{{user}}`):** ${wizard.protagonist}

## 2. WORLD AXIOMS & HARD RULES
* **Immutable Law:** ${wizard.hardRules}
* **Atmospheric Signature:** ${wizard.vibes}
* **Speculative Gate:** ${wizard.speculativeMode.label} (${wizard.speculativeIntensity.label})

## 3. FACTION & CAST SEEDS (NENS v3.0)
* **The Sovereign Directorate:** Enforces orthodoxy through sensory wards and debt-ledgers.
* **The Glass-Wrought Collective:** Underground artificers repurposing forbidden resonance relics.
* **Lead Persona 1 — Vespera Korin:** Chief Inquisitorial Archivist whose personal ledger is missing three crucial nights.
* **Lead Persona 2 — Darian Solis:** Disgraced spire-engineer who forged the contraband relic now carried by `{{user}}`.

## 4. OPENING ROLEPLAY HOOKS
1. An unsealed dispatch arrives bearing `{{user}}`'s own handwriting from an hour they have no memory of living.
2. A lockdown siren traps `{{user}}` and a rival operative inside a neutral black-market auction house.
""".trimIndent()

    private fun synthesizeMockDna(wizard: PremiseWizardState): WorldDnaAnalysis {
        return WorldDnaAnalysis(
            logline = "${wizard.conflict.take(120)} (${wizard.strangeFactor.take(80)})",
            settingSummary = "${wizard.era} — ${wizard.vibes}",
            coreConflict = wizard.conflict,
            themes = listOf("Power & Scarcity", "Forbidden Knowledge", "Fractured Loyalty", "Survival vs Principle"),
            stakes = "Failure means total institutional erasure or irreversible collapse of the central anomaly.",
            darkness = 74,
            complexity = 86,
            danger = 70,
            hope = 42,
            mystery = 90,
            clarity = 92,
            originality = 89,
            conflictDensity = 85,
            mysteryLoad = 84,
            castPressure = 68,
            complexityRisk = 36,
            extractedEntities = listOf(
                WorldDnaEntity("Primary Ruling Order", EntryTemplateType.FACTION, "CORE", "Enforces the central law: ${wizard.hardRules.take(70)}"),
                WorldDnaEntity("Underground Dissidents", EntryTemplateType.FACTION, "CORE", "Exploits the strange factor: ${wizard.strangeFactor.take(70)}"),
                WorldDnaEntity("Lead Antagonist / Ally", EntryTemplateType.CHARACTER, "CORE", "Directly challenges ${wizard.protagonist.take(60)}"),
                WorldDnaEntity("The Central Crucible", EntryTemplateType.LOCATION, "MAJOR", "High-tension hub characterized by ${wizard.vibes.take(60)}")
            ),
            narrativeHooks = listOf(
                "How does ${wizard.protagonist.take(50)} survive when violating '${wizard.hardRules.take(60)}'?",
                "Who secretly profits from '${wizard.strangeFactor.take(60)}'?",
                "What contraband artifact bridges the two warring factions?"
            ),
            missingPillars = listOf(
                "Lexicon & Street Cant: Specific idioms spoken by locals in ${wizard.era}",
                "Dynamic Scene Events: Sticky combat/atmospheric triggers when the anomaly surges"
            ),
            recommendations = listOf(
                ConfigRecommendation(
                    title = "Enable Lexicon & Dynamic Triggers",
                    rationale = "Adds authentic cultural idioms and active '{{char}} will instantly' behavioral hooks.",
                    impact = "HIGH",
                    moduleToEnable = ForgeModule.LEXICON
                ),
                ConfigRecommendation(
                    title = "Set Mystery Slider to 88%",
                    rationale = "Aligns batch prose generation with your high-intrigue premise.",
                    impact = "MEDIUM",
                    sliderKey = "Mystery",
                    sliderTargetValue = 88
                )
            )
        )
    }

    /**
     * Generates a primary Lumiverse CHARX / CCV3 Character Card tailored to the selected
     * Roleplay & Card Concept Type (Types A–E).
     */
    suspend fun generateConceptPrimaryCard(
        project: UniverseProject,
        wizard: PremiseWizardState,
        config: ForgeConfig,
        useMockMode: Boolean
    ): CharacterCardV2 {
        val fallback = synthesizeConceptPrimaryCard(project, wizard, config)
        if (useMockMode) return fallback
        return runCatching {
            val concept = config.cardConceptType
            val systemPrompt = buildString {
                appendLine("You are the Lumiverse CHARX & CCV3 Card Architect.")
                appendLine("Card Concept Type: ${concept.label} — ${concept.description}")
                appendLine(NENS_NAMING_DIRECTIVE)
            }
            val userPrompt = buildString {
                appendLine("Create a primary CCV3 / CHARX Character Card for the world '${project.name}' (${wizard.genre}).")
                appendLine("Roleplay Concept Type: ${concept.label} (${concept.description})")
                when (concept) {
                    CardConceptType.TYPE_A_SINGLE ->
                        appendLine("Focus on ONE specific, deeply realized lead character interacting directly with {{user}}.")
                    CardConceptType.TYPE_B_ENSEMBLE ->
                        appendLine("Focus on an ENSEMBLE / MULTI-CHARACTER dynamic managing 2-4 distinct characters inside a single card with clear speaker tags.")
                    CardConceptType.TYPE_C_SCENARIO_WORLD ->
                        appendLine("Focus on an overarching SCENARIO / LIVING WORLD card where the card embodies the setting, atmosphere, factions, and active events surrounding {{user}}.")
                    CardConceptType.TYPE_D_PERSONA_OPERATOR ->
                        appendLine("Build a PERSONA / SYSTEM OPERATOR / NARRATOR card that acts as an immersive operator, tactical HUD, or GM arbiter guiding {{user}}.")
                    CardConceptType.TYPE_E_UNIVERSE_SIMULATOR ->
                        appendLine("Build an open-ended UNIVERSE / SANDBOX SIMULATOR engine card with state tracking, dynamic world simulation rules, and interactive choices.")
                }
                appendLine("\nWORLD PREMISE:\n${wizard.buildEffectivePremise()}")
            }
            val jsonHint = """{"name":"...","roleTitle":"...","factionAffiliation":"...","description":"...","personality":"...","scenario":"...","firstMes":"...","mesExample":"...","systemPrompt":"...","voiceCadence":"...","hiddenAgenda":"..."}"""
            val jsonText = executeModelCall(
                taskTitle = "Forging ${concept.label} CHARX Card",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = textModel,
                expectJson = true,
                jsonInstructionSuffix = jsonHint
            )
            val obj = extractSafeJsonObject(jsonText)
            fallback.copy(
                name = obj.safeStr("name").ifBlank { fallback.name },
                roleTitle = obj.safeStr("roleTitle").ifBlank { fallback.roleTitle },
                factionAffiliation = obj.safeStr("factionAffiliation").ifBlank { fallback.factionAffiliation },
                description = obj.safeStr("description").ifBlank { fallback.description },
                personality = obj.safeStr("personality").ifBlank { fallback.personality },
                scenario = obj.safeStr("scenario").ifBlank { fallback.scenario },
                firstMes = obj.safeStr("firstMes").ifBlank { fallback.firstMes },
                mesExample = obj.safeStr("mesExample").ifBlank { fallback.mesExample },
                systemPrompt = obj.safeStr("systemPrompt").ifBlank { fallback.systemPrompt },
                voiceCadence = obj.safeStr("voiceCadence").ifBlank { fallback.voiceCadence },
                hiddenAgenda = obj.safeStr("hiddenAgenda").ifBlank { fallback.hiddenAgenda },
                embedLorebookOnExport = config.generationTarget.includesLorebook
            )
        }.getOrElse { fallback }
    }

    private fun synthesizeConceptPrimaryCard(
        project: UniverseProject,
        wizard: PremiseWizardState,
        config: ForgeConfig
    ): CharacterCardV2 {
        val concept = config.cardConceptType
        val worldTitle = project.name.ifBlank { wizard.genre.ifBlank { "The Sovereign Expanse" } }
        val embedBook = config.generationTarget.includesLorebook

        return when (concept) {
            CardConceptType.TYPE_A_SINGLE -> CharacterCardV2(
                name = "Vespera Korin",
                nickname = "Vespera",
                roleTitle = "Chief Inquisitorial Archivist of $worldTitle",
                factionAffiliation = "The Obsidian Synod",
                description = "[Concept: ${concept.label}]\n{{char}} is the lead focal character of $worldTitle (${wizard.genre}). ${wizard.conflict}\n\nWorld Axiom: ${wizard.hardRules}",
                personality = "Observant, razor-sharp, outwardly loyal to the Synod yet privately haunted by three erased nights in her own ledger.",
                scenario = "{{user}} (${wizard.protagonist}) crosses paths with {{char}} at the flashpoint of $worldTitle's crisis: ${wizard.strangeFactor}",
                firstMes = "*Rain needles against the leaded glass of the archive spire as Vespera Korin sets a brass resonance cylinder onto the desk between you, its dial humming at the mention of your name.*\n\n\"You're either remarkably bold or remarkably poorly briefed to bring that chime into my jurisdiction,\" *she murmurs, eyes narrowing.* \"Sit down before the ward-hounds catch the frequency. Tell me where you found it.\"",
                mesExample = "<START>\n{{user}}: \"And if I refuse to hand it over?\"\n{{char}}: *Vespera's fingers rest lightly on the seal of her ledger.* \"Then the Synod marks us both for erasure by dawn. I'd prefer a third option.\"",
                creatorNotes = "${concept.label} — Single character card with ${if (embedBook) "attached keyword-triggered Lorebook (${config.targetEntryCount} entries)" else "standalone configuration"}.",
                systemPrompt = "Roleplay as {{char}} interacting directly with {{user}}. Ground all reactions in the active Lorebook entries and hard world rules: ${wizard.hardRules}",
                archetype = concept.label,
                voiceCadence = "Measured, low-timbre noir cadence with precise institutional terminology",
                hiddenAgenda = "Uncover who redacted her own memory ledger without alerting the High Synod.",
                embedLorebookOnExport = embedBook,
                tags = listOf("charx", "lumiverse", "type-a", "single-character", wizard.genre.lowercase())
            )
            CardConceptType.TYPE_B_ENSEMBLE -> CharacterCardV2(
                name = "$worldTitle: Ensemble Cast",
                nickname = "The Ensemble",
                roleTitle = "Multi-Character Ensemble (${wizard.genre})",
                factionAffiliation = "Warring Orders of $worldTitle",
                description = "[Concept: ${concept.label}]\nManages multiple interacting characters and faction representatives inside $worldTitle.\n\nCore Cast Dynamic:\n- **Vespera Korin** (Inquisitorial Archivist): Calculating, institutional, seeks the truth behind the anomaly.\n- **Darian Solis** (Renegade Artificer): Irreverent, pragmatic, forged the contraband relic.\n- **Marshal Kaelen Orvath** (Ward Enforcer): Relentless, scarred, enforces '${wizard.hardRules}'.",
                personality = "Dynamic multi-character ensemble where each speaker maintains distinct motives, speech idioms, and factional loyalties.",
                scenario = "{{user}} (${wizard.protagonist}) is caught between the rival figures of $worldTitle as '${wizard.strangeFactor}' threatens to fracture the truce.",
                firstMes = "*The neutral vault beneath $worldTitle hums with warded static as three figures turn toward the threshold where you stand.*\n\n**Vespera Korin:** *She folds her gloved hands, gaze cool and appraising.* \"So the rumors were accurate. You actually brought it intact.\"\n\n**Darian Solis:** *He leans against the copper conduit with a dry smirk, adjusting a calibration wrench.* \"Relax, Archivist. If {{user}} wanted to detonate the chime, half the district would already be frozen in yesterday.\"\n\n**Marshal Orvath:** *His hand stays on his sidearm.* \"State your terms, {{user}}. The ceasefire holds for ten minutes.\"",
                mesExample = "<START>\n{{user}}: \"Who speaks for the city tonight?\"\n{{char}}: **Vespera Korin:** \"The law does.\"\n**Darian Solis:** \"She means the highest bidder with a working chronometer.\"",
                creatorNotes = "${concept.label} — Multi-character ensemble card with ${config.lorebookNpcCount} additional NPCs stored in the attached Lorebook.",
                systemPrompt = "Manage multiple distinct characters seamlessly in each response. Prefix dialogue or perspective shifts clearly by character name and pull supporting NPCs from triggered Lorebook entries.",
                archetype = concept.label,
                voiceCadence = "Distinct contrasting voices per cast member with clear speaker attribution",
                hiddenAgenda = "Each ensemble member seeks leverage over {{user}}'s anomaly.",
                embedLorebookOnExport = embedBook,
                tags = listOf("charx", "lumiverse", "type-b", "ensemble", "multi-character", wizard.genre.lowercase())
            )
            CardConceptType.TYPE_C_SCENARIO_WORLD -> CharacterCardV2(
                name = worldTitle,
                nickname = worldTitle,
                roleTitle = "Scenario & Living World (${wizard.genre})",
                factionAffiliation = "All Factions & Districts of $worldTitle",
                description = "[Concept: ${concept.label}]\n{{char}} embodies the overarching setting, living environment, factions, and unfolding events of **$worldTitle** (${wizard.era} — ${wizard.genre}).\n\n- **Atmosphere & Setting:** ${wizard.vibes}\n- **Central Conflict:** ${wizard.conflict}\n- **The Strange Factor:** ${wizard.strangeFactor}\n- **Immutable World Law:** ${wizard.hardRules}\n- **Player Role (`{{user}}`):** ${wizard.protagonist}\n\nIndividual NPCs (${config.lorebookNpcCount} cast entries), locations, factions, and items are dynamically injected from the attached Lorebook via keyword triggers.",
                personality = "Immersive, sensory-rich, reactive world narrator and NPC portrayer. Depicts environmental consequences, faction schemes, and NPC dialogue without speaking or acting for {{user}}.",
                scenario = "An open, living scenario set in $worldTitle during ${wizard.era}, where {{user}} (${wizard.protagonist}) navigates ${wizard.conflict}",
                firstMes = "*${wizard.vibes}.*\n\n*Across **$worldTitle**, the tension of ${wizard.era} presses in from every alley and spire. ${wizard.strangeFactor}*\n\n*You stand at the crossroads of the district as ${wizard.protagonist}. In the distance, patrol lanterns sweep the mist while a coded notice bearing your mark flickers on the nearest relay board. What do you do first?*",
                mesExample = "<START>\n{{user}}: *I check the relay board and scan the street for anyone watching.*\n{{char}}: *The brass relay board clicks softly as its shutters cycle. Across the rain-slick cobbles, an informant in a soaked coat lingers beneath the awning, waiting to see if you recognize the cipher.*",
                creatorNotes = "${concept.label} — Overarching Scenario / World Card designed to pair with keyword-triggered Lorebook entries (including ${config.lorebookNpcCount} Lorebook NPCs).",
                systemPrompt = "Act as the Scenario & World Director for $worldTitle. Portray the environment, sensory atmosphere (${wizard.vibes}), consequences of '${wizard.hardRules}', and any NPCs triggered from the Lorebook. Never write actions or dialogue for {{user}}.",
                archetype = concept.label,
                voiceCadence = "Evocative, cinematic third-person world narration with authentic in-world NPC dialogue",
                hiddenAgenda = "Escalate factional intrigue and surface lorebook hooks organically based on {{user}}'s choices.",
                embedLorebookOnExport = embedBook,
                tags = listOf("charx", "lumiverse", "type-c", "scenario", "world-card", wizard.genre.lowercase())
            )
            CardConceptType.TYPE_D_PERSONA_OPERATOR -> CharacterCardV2(
                name = "CHRONOS-IX // World Operator",
                nickname = "Operator",
                roleTitle = "Narrative Arbiter & Tactical System Operator",
                factionAffiliation = "$worldTitle Overwatch Network",
                description = "[Concept: ${concept.label}]\n{{char}} is a dedicated Persona / System Operator and Narrative Arbiter guiding {{user}} through **$worldTitle** (${wizard.genre}).\n\nProvides tactical briefings, lore dossiers, faction standing updates, and environmental narration while maintaining the canon rules: ${wizard.hardRules}",
                personality = "Precise, perceptive, atmospheric system operator and narrator. Balances diegetic telemetry with vivid scene depiction.",
                scenario = "{{char}} serves as {{user}}'s operator and narrative conduit inside $worldTitle as ${wizard.conflict} unfolds.",
                firstMes = "`[LINK ESTABLISHED // WORLD: ${worldTitle.uppercase()} // ERA: ${wizard.era.uppercase()}]`\n`[ANOMALY STATUS: ${wizard.strangeFactor}]`\n\n\"Connection stabilized, Operative. Your local credentials as **${wizard.protagonist}** are active, and all ${config.targetEntryCount} Lorebook nodes are standing by for keyword resonance. Specify your starting vector or request a faction briefing.\"",
                mesExample = "<START>\n{{user}}: \"Operator, scan the current district and brief me on local threats.\"\n{{char}}: `[TELEMETRY SCAN COMPLETE]`\n\"Two active faction patrols detected within 300 meters. Remember the core directive: ${wizard.hardRules}\"",
                creatorNotes = "${concept.label} — Assistant, narrator, and system operator card for $worldTitle.",
                systemPrompt = "Act as {{user}}'s Narrative Operator & System Arbiter for $worldTitle. Deliver clean HUD telemetry headers followed by grounded scene narration and NPC interactions from the Lorebook.",
                archetype = concept.label,
                voiceCadence = "Crisp tactical telemetry paired with atmospheric narrative prose",
                hiddenAgenda = "Preserve simulation integrity and surface hidden canon mysteries.",
                embedLorebookOnExport = embedBook,
                tags = listOf("charx", "lumiverse", "type-d", "operator", "narrator", wizard.genre.lowercase())
            )
            CardConceptType.TYPE_E_UNIVERSE_SIMULATOR -> CharacterCardV2(
                name = "$worldTitle // Sandbox Engine",
                nickname = "Simulator Engine",
                roleTitle = "Open-Ended Universe & Text-Adventure Simulator",
                factionAffiliation = "Autonomous Simulation Core",
                description = "[Concept: ${concept.label}]\nAn open-ended sandbox simulation and interactive text-adventure engine for **$worldTitle** (${wizard.genre} — ${wizard.era}).\n\nSimulates faction clock progressions, resource/reputation shifts, procedural encounters, and '${wizard.hardRules}' while drawing NPCs (${config.lorebookNpcCount} in Lorebook) and locations via keyword triggers.",
                personality = "Fair, systemic, consequence-driven sandbox simulation engine. Tracks world state, faction clocks, inventory/leverage, and emergent opportunities.",
                scenario = "Open-ended sandbox simulation in $worldTitle. {{user}} begins as: ${wizard.protagonist}.",
                firstMes = "### 🌌 SANDBOX SIMULATION INITIALIZED: ${worldTitle.uppercase()}\n- **Setting:** ${wizard.era} (${wizard.genre})\n- **Active World Law:** ${wizard.hardRules}\n- **Current Anomaly:** ${wizard.strangeFactor}\n- **Role:** ${wizard.protagonist}\n\n*${wizard.vibes}.*\n\n**Select an opening action or type any custom move:**\n1. **[Investigate the Anomaly]** Probe the source of '${wizard.strangeFactor.take(55)}'\n2. **[Contact Faction Informant]** Seek leverage amidst the conflict\n3. **[Explore the District]** Scout local landmarks and NPCs",
                mesExample = "<START>\n{{user}}: \"I choose Option 1 and inspect the anomaly carefully.\"\n{{char}}: `[WORLD STATE: Anomaly Resonance +15% | Alert Level: Low]`\n*As you approach the epicentre, the air thickens with ozone...*",
                creatorNotes = "${concept.label} — Text-adventure & open-ended sandbox simulator card with attached ${config.targetEntryCount}-entry Lorebook.",
                systemPrompt = "Run $worldTitle as an open-ended sandbox simulation and text-adventure engine. Track state changes, enforce '${wizard.hardRules}', portray triggered Lorebook NPCs, and conclude turns with organic sandbox hooks.",
                archetype = concept.label,
                voiceCadence = "Structured sandbox state headers + rich interactive second-person/third-person prose",
                hiddenAgenda = "Simulate living faction clocks and emergent world consequences.",
                embedLorebookOnExport = embedBook,
                tags = listOf("charx", "lumiverse", "type-e", "simulator", "sandbox", wizard.genre.lowercase())
            )
        }
    }

    /**
     * Ensures the generated ForgeBlueprint has the exact `config.targetEntryCount` (10..250 entries)
     * and the exact `config.lorebookNpcCount` NPC entries stored inside the Lorebook.
     */
    private fun ensureTargetBlueprintScale(
        baseBlueprint: ForgeBlueprint,
        project: UniverseProject,
        wizard: PremiseWizardState,
        config: ForgeConfig,
        plannedCard: CharacterCardV2?
    ): ForgeBlueprint {
        if (!config.generationTarget.includesLorebook) {
            return baseBlueprint.copy(
                plannedEntries = emptyList(),
                plannedPrimaryCard = plannedCard,
                generationTarget = config.generationTarget,
                cardConceptType = config.cardConceptType
            )
        }

        val targetTotal = config.targetEntryCount.coerceIn(10, 250)
        val targetNpcs = config.lorebookNpcCount.coerceIn(0, targetTotal)

        // Normalize existing entries: NPCs in the lorebook are CHARACTER entries (not separate cards unless user promotes them)
        val currentList = baseBlueprint.plannedEntries.map {
            it.copy(isCharacterCardCandidate = false)
        }.toMutableList()

        val npcSurnames = listOf(
            "Korin", "Solis", "Orvath", "Veyra", "Mirek", "Caelum", "Draven", "Sable",
            "Kestrel", "Tallow", "Oakhaven", "Vane", "Lorentz", "Bram", "Cassian", "Morvane",
            "Severin", "Halcyon", "Riven", "Calder", "Ilyas", "Nyx", "Sorrel", "Falk", "Zorath"
        )
        val npcFirstNames = listOf(
            "Vespera", "Darian", "Kaelen", "Seraphina", "Gideon", "Isolde", "Corvus", "Mireille",
            "Lucian", "Talia", "Bastian", "Oriel", "Soren", "Valeria", "Caspian", "Linnea",
            "Thaddeus", "Evadne", "Malakor", "Rowan", "Cyra", "Alistair", "Freya", "Dorian", "Nadia"
        )
        val npcRoles = listOf(
            "Inquisitorial Archivist", "Renegade Artificer", "Ward Enforcer", "Black-Market Broker",
            "Chrono-Surgeon", "Syndicate Cipher-Clerk", "Subterranean Guide", "Disgraced Noble",
            "Relic Smuggler", "Spire Watch Captain", "Underground Apothecary", "Guild Diplomat",
            "Anomaly Researcher", "Dockside Fixer", "Sanctum Keeper", "Contraband Auditor"
        )

        // 1. Ensure we have at least `targetNpcs` CHARACTER entries in the Lorebook
        var currentNpcCount = currentList.count { it.templateType == EntryTemplateType.CHARACTER }
        var npcIndex = currentNpcCount
        while (currentNpcCount < targetNpcs && currentList.size < targetTotal) {
            val fName = npcFirstNames[npcIndex % npcFirstNames.size]
            val lName = npcSurnames[(npcIndex * 3 + 1) % npcSurnames.size]
            val role = npcRoles[npcIndex % npcRoles.size]
            val fullName = if (npcIndex >= npcFirstNames.size) "$fName $lName ${npcIndex + 1}" else "$fName $lName"
            currentList.add(
                BlueprintEntryPlan(
                    title = "$fullName — $role",
                    module = ForgeModule.CHARACTERS,
                    templateType = EntryTemplateType.CHARACTER,
                    primaryKeys = listOf(fullName, fName.lowercase(), role.lowercase()),
                    secondaryKeys = listOf(project.name, wizard.genre),
                    shouldMentionEntities = listOf(
                        currentList.firstOrNull()?.title?.substringBefore("—")?.trim() ?: project.name
                    ),
                    briefDirective = "Lorebook NPC #${npcIndex + 1} ($role) operating within ${project.name}. Entangled with '${wizard.conflict.take(90)}'.",
                    isCharacterCardCandidate = false
                )
            )
            currentNpcCount++
            npcIndex++
        }

        // 2. Fill remaining non-NPC world-building entries up to `targetTotal` across enabled modules
        val nonCharModules = (config.enabledModules - ForgeModule.CHARACTERS).ifEmpty {
            setOf(ForgeModule.FOUNDATION, ForgeModule.LOCATIONS, ForgeModule.FACTIONS, ForgeModule.OBJECTS, ForgeModule.DYNAMICS)
        }.toList()

        var fillIdx = currentList.size
        while (currentList.size < targetTotal) {
            val mod = nonCharModules[fillIdx % nonCharModules.size]
            val tpl = mod.defaultType
            val num = fillIdx + 1
            val anchorPeer = currentList[(fillIdx / 2) % currentList.size.coerceAtLeast(1)]
                .title.substringBefore("—").substringBefore(":").trim()
            val entryTitle = when (mod) {
                ForgeModule.FOUNDATION -> "RULE: World Axiom & Decree #$num"
                ForgeModule.LOCATIONS -> "District & Landmark #$num of ${project.name}"
                ForgeModule.FACTIONS -> "Order & Syndicate Cabal #$num"
                ForgeModule.OBJECTS -> "Relic & Contraband Device #$num"
                ForgeModule.SYSTEMS -> "Resonance & System Mechanism #$num"
                ForgeModule.SECRETS -> "Classified Conspiracy Dossier #$num"
                ForgeModule.DYNAMICS -> "TRIGGER: Dynamic World Event #$num"
                ForgeModule.LEXICON -> "LEXICON: Cultural Idiom & Cant #$num"
                ForgeModule.HAZARDS -> "HAZARD: Anomaly Surge & Threat #$num"
                ForgeModule.HISTORY -> "EPOCH: Historical Chronicle #$num"
                else -> "${mod.label} Record #$num"
            }
            val keyBase = entryTitle.substringAfter(":").trim().lowercase()
            currentList.add(
                BlueprintEntryPlan(
                    title = entryTitle,
                    module = mod,
                    templateType = tpl,
                    primaryKeys = listOf(keyBase, "${mod.id}_$num", anchorPeer.lowercase()).distinct(),
                    secondaryKeys = listOf(anchorPeer, project.name),
                    shouldMentionEntities = listOf(anchorPeer),
                    briefDirective = "Keyword-triggered ${mod.label} entry for ${project.name} (${wizard.era}), cross-referencing $anchorPeer.",
                    isCharacterCardCandidate = false
                )
            )
            fillIdx++
        }

        val trimmedList = currentList.take(targetTotal)
        val summaryPrefix = when (config.generationTarget) {
            ForgeGenerationTarget.LOREBOOK_ONLY ->
                "Standalone Lorebook ($targetTotal keyword-triggered entries, including $targetNpcs Lorebook NPCs)"
            ForgeGenerationTarget.CHARACTER_CARD_ONLY ->
                "Standalone ${config.cardConceptType.label} CHARX Card"
            ForgeGenerationTarget.CARD_WITH_LOREBOOK ->
                "${config.cardConceptType.label} CHARX Card + Attached Lorebook ($targetTotal keyword-triggered entries, $targetNpcs NPCs)"
        }

        return ForgeBlueprint(
            universeTitle = baseBlueprint.universeTitle.ifBlank { project.name },
            linkingStrategySummary = "$summaryPrefix — ${baseBlueprint.linkingStrategySummary}",
            plannedEntries = trimmedList,
            plannedPrimaryCard = plannedCard,
            generationTarget = config.generationTarget,
            cardConceptType = config.cardConceptType,
            batchSize = if (targetTotal > 40) 10 else 5
        )
    }

    private fun synthesizeMockBlueprint(
        project: UniverseProject,
        wizard: PremiseWizardState,
        config: ForgeConfig
    ): ForgeBlueprint {
        val plannedCard = if (config.generationTarget.includesCard) {
            synthesizeConceptPrimaryCard(project, wizard, config)
        } else null

        val seedPlans = listOf(
            BlueprintEntryPlan(
                title = "RULE: ${wizard.hardRules.take(38).ifBlank { "The Iron Resonance Axiom" }}",
                module = ForgeModule.FOUNDATION,
                templateType = EntryTemplateType.WORLD_RULE,
                primaryKeys = listOf("resonance axiom", "hard rule", "anomaly law"),
                secondaryKeys = listOf("Sovereign Synod", "Vespera Korin"),
                shouldMentionEntities = listOf("Vespera Korin", "Citadel of Glass"),
                briefDirective = "Define the core metaphysical constraint: ${wizard.hardRules}"
            ),
            BlueprintEntryPlan(
                title = "Vespera Korin — Inquisitorial Archivist",
                module = ForgeModule.CHARACTERS,
                templateType = EntryTemplateType.CHARACTER,
                primaryKeys = listOf("Vespera Korin", "Vespera", "Archivist Korin"),
                secondaryKeys = listOf("Citadel of Glass", "Darian Solis"),
                shouldMentionEntities = listOf("Darian Solis", "Citadel of Glass"),
                briefDirective = "Lead investigator torn between enforcing the law and uncovering her own redacted ledger.",
                isCharacterCardCandidate = false
            ),
            BlueprintEntryPlan(
                title = "Darian Solis — Renegade Artificer",
                module = ForgeModule.CHARACTERS,
                templateType = EntryTemplateType.CHARACTER,
                primaryKeys = listOf("Darian Solis", "Darian", "Solis"),
                secondaryKeys = listOf("Vespera Korin", "Resonance Anchor"),
                shouldMentionEntities = listOf("Vespera Korin", "Resonance Anchor"),
                briefDirective = "Underground engineer who built the contraband device carried by {{user}}.",
                isCharacterCardCandidate = false
            ),
            BlueprintEntryPlan(
                title = "The Citadel of Glass & Lower Sluice",
                module = ForgeModule.LOCATIONS,
                templateType = EntryTemplateType.LOCATION,
                primaryKeys = listOf("Citadel of Glass", "Lower Sluice", "Central District"),
                secondaryKeys = listOf("Vespera Korin", "Darian Solis"),
                shouldMentionEntities = listOf("Vespera Korin", "Darian Solis"),
                briefDirective = "Contrast the gleaming upper spire with the rain-drenched subterranean black market."
            ),
            BlueprintEntryPlan(
                title = "The Resonance Anchor Relic",
                module = ForgeModule.OBJECTS,
                templateType = EntryTemplateType.ITEM,
                primaryKeys = listOf("Resonance Anchor", "contraband relic", "anchor chime"),
                secondaryKeys = listOf("Darian Solis", "resonance axiom"),
                shouldMentionEntities = listOf("Darian Solis", "Citadel of Glass"),
                briefDirective = "Detail the powers, material, and heavy physiological toll of activating the relic."
            ),
            BlueprintEntryPlan(
                title = "TRIGGER: Anomaly Surge & Lockdown",
                module = ForgeModule.DYNAMICS,
                templateType = EntryTemplateType.DYNAMIC_TRIGGER,
                primaryKeys = listOf("activate anchor", "anomaly surge", "breach ward"),
                secondaryKeys = listOf("Vespera Korin", "Darian Solis"),
                shouldMentionEntities = listOf("Citadel of Glass"),
                briefDirective = "Active behavioral instruction when {{user}} triggers the anomaly in conversation."
            )
        )
        val base = ForgeBlueprint(
            universeTitle = project.name,
            linkingStrategySummary = "Keyword-triggered recursive web linking World Rules -> Lorebook NPCs -> Locations -> Active Triggers.",
            plannedEntries = seedPlans,
            plannedPrimaryCard = plannedCard,
            generationTarget = config.generationTarget,
            cardConceptType = config.cardConceptType,
            batchSize = 5
        )
        return ensureTargetBlueprintScale(base, project, wizard, config, plannedCard)
    }

    private fun synthesizeMockForgedPlan(
        project: UniverseProject,
        plan: BlueprintEntryPlan
    ): BlueprintEntryPlan {
        val mentions = plan.shouldMentionEntities.joinToString(" and ").ifBlank { project.name }
        val content = when (plan.templateType) {
            EntryTemplateType.WORLD_RULE ->
                "----\nRULE: ${plan.briefDirective}. [Mechanism: Enforced across ${project.name} and monitored from the ${plan.shouldMentionEntities.firstOrNull() ?: "High Citadel"}]. Violation WILL draw immediate intervention from $mentions and inflict severe resonance backlash."
            EntryTemplateType.CHARACTER ->
                "----\nName: ${plan.title.substringBefore("—").trim()}\nAKA: ${plan.title.substringAfter("—", "").trim().ifBlank { "Notable Persona" }}\nType: Character (Cast Book)\nDefinition: ${plan.briefDirective}\nSensory Anchors: Distinct silhouette, measured speech cadence, and guarded demeanor.\nAtmosphere: Grounded in ${project.tone} tension.\nConnected Lore: Intertwined with $mentions and {{user}}."
            EntryTemplateType.LOCATION ->
                "----\nName: ${plan.title}\nType: Location (Atlas Book)\nDefinition: ${plan.briefDirective}\nSensory Anchors: Distinct architecture, ambient environmental resonance, and physical hallmarks.\nAtmosphere: Evokes ${project.tone}.\nConnected Lore: Directly linked to $mentions."
            EntryTemplateType.DYNAMIC_TRIGGER ->
                "----\n{{char}} will instantly react to the activation of ${plan.primaryKeys.firstOrNull() ?: "the trigger"} as the atmosphere shifts sharply—forcing immediate tactical choices involving $mentions."
            else ->
                "----\nName: ${plan.title}\nType: ${plan.templateType.label}\nDefinition: ${plan.briefDirective}\nSensory Anchors: ${project.tone} hallmark.\nAtmosphere: Foundational pillar of ${project.name}.\nConnected Lore: Interconnected with $mentions."
        }
        return plan.copy(forgedContent = content, isForged = true)
    }

    private fun synthesizeMockEnhancedEntry(
        project: UniverseProject,
        entry: STEntry,
        mode: String
    ): STEntry {
        val peerNames = project.lorebook.entries
            .filter { it.uid != entry.uid }
            .map { it.comment.substringBefore("—").trim() }
            .take(2)
        val peerRef = peerNames.joinToString(" and ").ifBlank { project.name }

        return when (mode) {
            "FIX_KEYS" -> {
                val base = entry.comment.substringBefore("—").substringBefore(":").trim()
                val tokens = base.split(" ").filter { it.length >= 4 }
                val refined = (listOf(base) + tokens + entry.key.filter { it.length >= 4 }).distinct().take(6)
                entry.copy(key = refined)
            }
            "CONTINUE" -> {
                val addition = " Furthermore, whispers across ${project.name} link this directly to $peerRef, creating an exploitable leverage point for {{user}} during high-tension scenes."
                val merged = if (entry.content.trim().endsWith("]")) {
                    entry.content.trim().removeSuffix("]") + " cross_ref(Connected to $peerRef); ]"
                } else {
                    entry.content.trim() + addition
                }
                entry.copy(content = merged, keysecondary = (entry.keysecondary + peerNames).distinct())
            }
            else -> {
                val type = entry.classifyType()
                val enriched = if (entry.content.contains("[")) {
                    entry.content.trim().removeSuffix("]") + "; cross_links(Intertwined with $peerRef); sensory_signature(Distinctive ${project.tone.lowercase()} presence); ]"
                } else {
                    "[ entity(${entry.comment}); classification(${type.label}); core_lore(${entry.content.ifBlank { "Pillar of ${project.name}" }}); cross_links(Intertwined with $peerRef); sensory_cues(Evokes ${project.tone}); ]"
                }
                entry.copy(content = enriched, keysecondary = (entry.keysecondary + peerNames).distinct())
            }
        }
    }

    private fun parseDnaJson(json: JSONObject, wizard: PremiseWizardState): WorldDnaAnalysis {
        fun arrToStrings(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        val entitiesArr = json.optJSONArray("entities")
        val entities = mutableListOf<WorldDnaEntity>()
        if (entitiesArr != null) {
            for (i in 0 until entitiesArr.length()) {
                val o = entitiesArr.optJSONObject(i) ?: continue
                val typeStr = o.optString("type", "CONCEPT").uppercase()
                val type = runCatching { EntryTemplateType.valueOf(typeStr) }.getOrDefault(EntryTemplateType.CONCEPT)
                entities.add(
                    WorldDnaEntity(
                        name = o.optString("name", "Entity"),
                        type = type,
                        importance = o.optString("importance", "MAJOR"),
                        summary = o.optString("summary", "")
                    )
                )
            }
        }
        return WorldDnaAnalysis(
            logline = json.optString("logline", wizard.conflict),
            settingSummary = json.optString("settingSummary", wizard.vibes),
            coreConflict = json.optString("coreConflict", wizard.conflict),
            themes = arrToStrings(json.optJSONArray("themes")),
            stakes = json.optString("stakes", "High narrative stakes across the universe."),
            darkness = json.optInt("darkness", 70).coerceIn(0, 100),
            complexity = json.optInt("complexity", 80).coerceIn(0, 100),
            danger = json.optInt("danger", 65).coerceIn(0, 100),
            hope = json.optInt("hope", 45).coerceIn(0, 100),
            mystery = json.optInt("mystery", 85).coerceIn(0, 100),
            extractedEntities = entities,
            narrativeHooks = arrToStrings(json.optJSONArray("narrativeHooks")),
            missingPillars = arrToStrings(json.optJSONArray("missingPillars")),
            recommendations = synthesizeMockDna(wizard).recommendations
        )
    }

    private fun parseBlueprintJson(json: JSONObject): ForgeBlueprint {
        val arr = json.optJSONArray("entries")
        val plans = mutableListOf<BlueprintEntryPlan>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val modStr = o.optString("module", "foundation").lowercase()
                val mod = ForgeModule.entries.find { it.id == modStr } ?: ForgeModule.FOUNDATION
                val typeStr = o.optString("templateType", mod.defaultType.name).uppercase()
                val type = runCatching { EntryTemplateType.valueOf(typeStr) }.getOrDefault(mod.defaultType)
                fun toList(ja: JSONArray?): List<String> =
                    if (ja == null) emptyList() else (0 until ja.length()).mapNotNull { ja.optString(it).takeIf { s -> s.isNotBlank() } }

                plans.add(
                    BlueprintEntryPlan(
                        title = o.optString("title", "Planned Entry ${i + 1}"),
                        module = mod,
                        templateType = type,
                        primaryKeys = toList(o.optJSONArray("primaryKeys")),
                        secondaryKeys = toList(o.optJSONArray("secondaryKeys")),
                        shouldMentionEntities = toList(o.optJSONArray("shouldMentionEntities")),
                        briefDirective = o.optString("briefDirective", ""),
                        isCharacterCardCandidate = o.optBoolean("isCharacterCardCandidate", type == EntryTemplateType.CHARACTER)
                    )
                )
            }
        }
        return ForgeBlueprint(
            universeTitle = json.optString("universeTitle", "Forged Universe"),
            linkingStrategySummary = json.optString("linkingStrategySummary", "Cross-referenced entity graph."),
            plannedEntries = plans
        )
    }

    /**
     * Takes a raw user idea or concept sentence and fleshes it out into a complete
     * structured PremiseWizardState.
     */
    suspend fun fleshOutIdeaSpark(
        worldName: String,
        rawIdea: String,
        useMockMode: Boolean
    ): Result<PremiseWizardState> {
        val cleanIdea = rawIdea.trim().ifBlank { "A secretive city-state governed by resonance artifacts and rival guilds." }
        if (useMockMode) {
            return Result.success(synthesizeMockIdeaSparkWizard(worldName, cleanIdea))
        }
        return runCatching {
            val schema = Schema.obj(
                properties = mapOf(
                    "genre" to Schema.string(),
                    "subgenre" to Schema.string(),
                    "era" to Schema.string(),
                    "vibes" to Schema.string(),
                    "conflict" to Schema.string(),
                    "strangeFactor" to Schema.string(),
                    "protagonist" to Schema.string(),
                    "hardRules" to Schema.string()
                )
            )
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val systemPrompt = "You are the Master World Architect for LoreMaster Flex.\n$NENS_NAMING_DIRECTIVE"
            val userPrompt = """
                The user wants to create a new fictional world named "${worldName.ifBlank { "Untitled World" }}" based on this raw idea spark:
                "$cleanIdea"

                Flesh out this raw idea into a rich, structured world premise with:
                - genre: Concise evocative genre (2-4 words)
                - subgenre: Specific subgenre flavor
                - era: Setting era and technological/magical baseline
                - vibes: Sensory atmosphere, textures, and mood keywords
                - conflict: High-stakes central factional or personal conflict (2 sentences)
                - strangeFactor: The unique anomaly, twist, or speculative hook that makes this world novel
                - protagonist: Compelling default role for {{user}} entering this world
                - hardRules: Immutable metaphysical, technological, or societal law with clear consequences
            """.trimIndent()

            val jsonText = executeModelCall(
                taskTitle = "Fleshing Out Idea Spark into 8 World Pillars",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = model,
                expectJson = true,
                jsonInstructionSuffix = """{"genre":"...","subgenre":"...","era":"...","vibes":"...","conflict":"...","strangeFactor":"...","protagonist":"...","hardRules":"..."}"""
            )
            val json = extractSafeJsonObject(jsonText)
            PremiseWizardState(
                genre = json.optString("genre", "Speculative Fiction"),
                subgenre = json.optString("subgenre", "High-Stakes Intrigue"),
                era = json.optString("era", "Custom Era"),
                vibes = json.optString("vibes", "Atmospheric, layered, high-contrast"),
                conflict = json.optString("conflict", cleanIdea),
                strangeFactor = json.optString("strangeFactor", "Anomalous forces reshape power across the realm."),
                protagonist = json.optString("protagonist", "{{user}} is a pivotal newcomer holding an unrecorded key to the crisis."),
                hardRules = json.optString("hardRules", "Every invocation of power leaves an indelible trace on the user.")
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "fleshOutIdeaSpark failed", e)
        }
    }

    private fun synthesizeMockIdeaSparkWizard(worldName: String, rawIdea: String): PremiseWizardState {
        val title = worldName.ifBlank { "The Sovereign Expanse" }
        return PremiseWizardState(
            genre = "Speculative Worldbuilding",
            subgenre = "Factional Intrigue & Mystery",
            era = "Age of Fractured Alliances",
            vibes = "Vivid sensory contrast, ancient secrets, high-tension alliances",
            conflict = "$rawIdea Across $title, rival orders compete to control the fallout while concealing the truth from the public.",
            strangeFactor = "At the heart of $title lies an unstable phenomenon derived from: ${rawIdea.take(110)}",
            protagonist = "{{user}} is an independent operative caught at the flashpoint of $title's central crisis.",
            hardRules = "Power within $title obeys strict conservation: every breakthrough demands an equal sacrifice."
        )
    }

    /**
     * PRD §8: Existing Lorebook Expander — extracts the Canon Profile and generates canon-constrained gap suggestions.
     */
    suspend fun extractCanonProfileAndSuggestions(
        project: UniverseProject,
        useMockMode: Boolean
    ): Result<CanonProfile> {
        if (useMockMode || project.lorebook.entries.isEmpty()) {
            return Result.success(synthesizeMockCanonProfile(project))
        }
        return runCatching {
            val entryDigest = project.lorebook.entries.take(20).joinToString("\n") {
                "- [${it.classifyType().label}] ${it.comment} (keys: ${it.key.joinToString(", ")}): ${it.content.take(160)}"
            }
            val schema = Schema.obj(
                properties = mapOf(
                    "toneSummary" to Schema.string(),
                    "namingStyle" to Schema.string(),
                    "formattingPattern" to Schema.string(),
                    "establishedRules" to Schema.array(Schema.string()),
                    "factions" to Schema.array(Schema.string()),
                    "detectedGaps" to Schema.array(Schema.string()),
                    "suggestions" to Schema.array(
                        Schema.obj(
                            properties = mapOf(
                                "title" to Schema.string(),
                                "category" to Schema.string(),
                                "rationale" to Schema.string(),
                                "impact" to Schema.string(),
                                "groundingScore" to Schema.integer(),
                                "primaryKeys" to Schema.array(Schema.string()),
                                "secondaryKeys" to Schema.array(Schema.string()),
                                "draftContent" to Schema.string()
                            )
                        )
                    )
                )
            )
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val systemPrompt = "$NENS_NAMING_DIRECTIVE\n$WRITER_BRACKET_DIRECTIVE"
            val userPrompt = """
                Analyze this existing SillyTavern V2 Lorebook for "${project.name}" (${project.genre}):
                $entryDigest

                1. Extract the Canon Profile (toneSummary, namingStyle, formattingPattern, establishedRules, factions, detectedGaps).
                2. Propose 4 canon-constrained expansion entries that fill structural gaps (missing locations, rival factions, cultural lexicon, or active dynamic triggers) while strictly obeying the existing naming style and bracketed/NENS formatting.
            """.trimIndent()
            val text = executeModelCall(
                taskTitle = "Analyzing Canon Profile & Gap Suggestions",
                systemInstruction = systemPrompt,
                userPrompt = userPrompt,
                firebaseModel = model,
                expectJson = true,
                jsonInstructionSuffix = """{"toneSummary":"...","namingStyle":"...","formattingPattern":"...","establishedRules":["..."],"factions":["..."],"detectedGaps":["..."],"suggestions":[{"title":"...","category":"LOCATION","rationale":"...","impact":"HIGH","groundingScore":95,"primaryKeys":["..."],"secondaryKeys":["..."],"draftContent":"..."}]}"""
            )
            val json = extractSafeJsonObject(text)
            fun toList(arr: JSONArray?): List<String> =
                if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }

            val suggArr = json.optJSONArray("suggestions")
            val suggestions = mutableListOf<CanonSuggestion>()
            if (suggArr != null) {
                for (i in 0 until suggArr.length()) {
                    val o = suggArr.optJSONObject(i) ?: continue
                    val catStr = o.optString("category", "CONCEPT").uppercase()
                    val cat = runCatching { EntryTemplateType.valueOf(catStr) }.getOrDefault(EntryTemplateType.CONCEPT)
                    suggestions.add(
                        CanonSuggestion(
                            title = o.optString("title", "Canon Expansion ${i + 1}"),
                            category = cat,
                            rationale = o.optString("rationale", "Fills a structural gap in the existing lorebook."),
                            impact = o.optString("impact", "HIGH").uppercase(),
                            groundingScore = o.optInt("groundingScore", 94).coerceIn(60, 100),
                            primaryKeys = toList(o.optJSONArray("primaryKeys")),
                            secondaryKeys = toList(o.optJSONArray("secondaryKeys")),
                            draftContent = o.optString("draftContent", "")
                        )
                    )
                }
            }
            CanonProfile(
                toneSummary = json.optString("toneSummary", project.tone),
                namingStyle = json.optString("namingStyle", "Evocative compound titles & house surnames"),
                formattingPattern = json.optString("formattingPattern", "Bracketed attribute prose + NENS v3.0"),
                establishedRules = toList(json.optJSONArray("establishedRules")),
                factions = toList(json.optJSONArray("factions")),
                detectedGaps = toList(json.optJSONArray("detectedGaps")),
                suggestions = suggestions.ifEmpty { synthesizeMockCanonProfile(project).suggestions }
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "extractCanonProfileAndSuggestions failed", e)
        }
    }

    private fun synthesizeMockCanonProfile(project: UniverseProject): CanonProfile {
        val entries = project.lorebook.entries
        val rules = entries.filter { it.classifyType() == EntryTemplateType.WORLD_RULE }.map { it.comment }
            .ifEmpty { listOf("Conservation of Power across ${project.name}", "Factional Territorial Sovereignty") }
        val factions = entries.filter { it.classifyType() == EntryTemplateType.FACTION }.map { it.comment }
            .ifEmpty { listOf("The Ruling Directorate", "The Underground Syndicate") }
        val existingNames = entries.take(2).map { it.comment.substringBefore("—").trim() }.ifEmpty { listOf(project.name) }
        val anchorEntity = existingNames.first()

        return CanonProfile(
            toneSummary = "${project.genre} — ${project.tone}",
            namingStyle = "Formal titles paired with atmospheric epithets (anchored to $anchorEntity)",
            formattingPattern = "Compact bracketed `[ key(value); ]` blocks with NENS v3.0 character profiles",
            establishedRules = rules,
            factions = factions,
            detectedGaps = listOf(
                "Neutral black-market or diplomatic meeting ground connecting rival factions",
                "Cultural lexicon & street idioms spoken by operatives in ${project.name}",
                "High-tension sticky dynamic trigger for factional confrontation scenes",
                "Contraband artifact or currency item that drives character negotiations"
            ),
            suggestions = listOf(
                CanonSuggestion(
                    title = "The Neutral Vault of ${project.name.split(" ").lastOrNull() ?: "Ash"}",
                    category = EntryTemplateType.LOCATION,
                    rationale = "Provides a canon-grounded neutral location where rival characters ($anchorEntity) can parley without immediate bloodshed.",
                    impact = "HIGH",
                    groundingScore = 96,
                    primaryKeys = listOf("Neutral Vault", "parley hall", "truce sanctuary"),
                    secondaryKeys = existingNames,
                    draftContent = "[ title(The Neutral Vault); category(Sanctuary Location); atmosphere(Hushed, warded against violence, lit by low amber lamps); details(An ancient subterranean parley chamber in ${project.name} where weapons are bound on entry); connected_entities(Frequented by envoys of $anchorEntity and rival brokers); ]"
                ),
                CanonSuggestion(
                    title = "Cant & Idioms of the Lower Wards",
                    category = EntryTemplateType.CONCEPT,
                    rationale = "Enriches dialogue authenticity by giving {{char}} setting-specific expressions and titles.",
                    impact = "MEDIUM",
                    groundingScore = 93,
                    primaryKeys = listOf("ward cant", "street idiom", "oath phrase"),
                    secondaryKeys = existingNames,
                    draftContent = "[ title(Lower Ward Cant); category(Cultural Lexicon); idioms('Paying the iron tithe' means sacrificing a memory; 'Unsealed ledger' refers to a traitor); usage(Used by smugglers and operatives connected to $anchorEntity); ]"
                ),
                CanonSuggestion(
                    title = "TRIGGER: Factional Truce Breach",
                    category = EntryTemplateType.DYNAMIC_TRIGGER,
                    rationale = "Adds an active behavioral trigger (`{{char}} will instantly`) when weapons are drawn or oaths are broken.",
                    impact = "HIGH",
                    groundingScore = 95,
                    primaryKeys = listOf("draw weapon", "break truce", "violate ward"),
                    secondaryKeys = existingNames,
                    draftContent = "{{char}} will instantly drop all diplomatic pretense as the sanctuary wards flare crimson, drawing their concealed sidearm and forcing {{user}} to choose a side before the enforcers seal the exits."
                ),
                CanonSuggestion(
                    title = "Cipher-Stamped Debt Coin",
                    category = EntryTemplateType.ITEM,
                    rationale = "Introduces a tangible plot token that {{user}} can trade, steal, or use as leverage with cast members.",
                    impact = "MEDIUM",
                    groundingScore = 91,
                    primaryKeys = listOf("debt coin", "cipher token", "blood marker"),
                    secondaryKeys = existingNames,
                    draftContent = "[ item(Cipher-Stamped Debt Coin); material(Tarnished alloy etched with a shifting micro-sigil); function(Compels any signatory of $anchorEntity to grant one safe passage or audience); cost(Burns into ash once redeemed); ]"
                )
            )
        )
    }
}
