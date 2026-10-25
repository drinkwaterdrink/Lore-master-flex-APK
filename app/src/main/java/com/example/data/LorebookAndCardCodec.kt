package com.example.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Base64
import com.example.model.BlueprintEntryPlan
import com.example.model.CardConceptType
import com.example.model.CharacterCardV2
import com.example.model.CharxAssetDescriptor
import com.example.model.CharxExpressionAsset
import com.example.model.ConfigRecommendation
import com.example.model.EntryTemplateType
import com.example.model.ForgeBlueprint
import com.example.model.ForgeConfig
import com.example.model.ForgeGenerationTarget
import com.example.model.ForgeModule
import com.example.model.GenerationSizePreset
import com.example.model.LoreKeywordValidator
import com.example.model.LumiverseCustomAction
import com.example.model.LumiverseModulesConfig
import com.example.model.PremiseWizardState
import com.example.model.ProceduralDensity
import com.example.model.ProceduralPersistence
import com.example.model.ProceduralSystemType
import com.example.model.ProceduralWeightMode
import com.example.model.STCharacterFilter
import com.example.model.STEntry
import com.example.model.STLorebookRoot
import com.example.model.STSettings
import com.example.model.SpeculativeIntensity
import com.example.model.SpeculativeMode
import com.example.model.UniverseProject
import com.example.model.WorldDnaAnalysis
import com.example.model.WorldDnaEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExportValidationReport(
    val isValid: Boolean,
    val blockingErrors: List<String>,
    val warnings: List<String>,
    val totalEntries: Int,
    val constantEntries: Int,
    val estimatedTokens: Int
)

object LorebookAndCardCodec {

    fun validateForExport(lorebook: STLorebookRoot): ExportValidationReport {
        val blocking = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val seenUids = mutableSetOf<Int>()

        lorebook.entries.forEach { entry ->
            val label = entry.comment.ifBlank { "Entry #${entry.uid}" }
            if (entry.isSectionDivider()) {
                // Section dividers are 0-token organizational entries (e.g. === RULES ===)
                return@forEach
            }
            if (!seenUids.add(entry.uid)) {
                blocking.add("Duplicate UID #${entry.uid} on '$label'.")
            }
            if (entry.content.isBlank()) {
                blocking.add("'$label' has empty content.")
            }
            if (!entry.constant && entry.key.isEmpty()) {
                blocking.add("'$label' has no primary keywords and is not marked Constant.")
            }
            val broad = LoreKeywordValidator.findBroadKeywords(entry.key)
            if (broad.isNotEmpty()) {
                warnings.add("'$label' uses overly broad keys: ${broad.joinToString(", ")}.")
            }
            if (!entry.hasFourDashDelimiter() && entry.content.length > 50) {
                warnings.add("'$label' lacks the Lumiverse/ST '----' delimiter on line 1 for prompt token boundary protection.")
            }
            if (entry.classifyType() == EntryTemplateType.CHARACTER && entry.estimateTokens() > 320) {
                warnings.add("'$label' is an oversized NPC entry (~${entry.estimateTokens()} tokens). Consider the 3-Part Split (Identity ~50t, Presence ~200t, Secrets).")
            }
            if (entry.group.isNotBlank() && entry.groupOverride && entry.order <= 0) {
                warnings.add("'$label' uses Group Override without an explicit priority Order.")
            }
        }

        val totalTokens = lorebook.entries.sumOf { it.estimateTokens() }
        val constantCount = lorebook.entries.count { it.constant }
        return ExportValidationReport(
            isValid = blocking.isEmpty(),
            blockingErrors = blocking,
            warnings = warnings,
            totalEntries = lorebook.entries.size,
            constantEntries = constantCount,
            estimatedTokens = totalTokens
        )
    }

    fun repairForExport(lorebook: STLorebookRoot): STLorebookRoot {
        var nextUid = 100
        val usedUids = mutableSetOf<Int>()
        val repaired = lorebook.entries.mapIndexed { idx, entry ->
            if (entry.isSectionDivider()) {
                return@mapIndexed entry.copy(displayIndex = idx)
            }
            val safeUid = if (usedUids.add(entry.uid)) entry.uid else {
                while (!usedUids.add(nextUid)) nextUid++
                nextUid
            }
            val cleanedKeys = entry.key
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.lowercase() !in LoreKeywordValidator.BROAD_KEYS }
                .ifEmpty {
                    val fallback = entry.comment
                        .substringBefore("—")
                        .substringBefore(":")
                        .trim()
                        .ifBlank { "lore_entry_$safeUid" }
                    listOf(fallback)
                }
            val rawContent = entry.content.ifBlank {
                "----\nName: ${entry.comment.ifBlank { "Entry #$safeUid" }}\nType: Lore\nDetails: Core canonical record in ${lorebook.name}."
            }
            val safeContent = if (rawContent.trimStart().startsWith("----")) rawContent else "----\n$rawContent"
            entry.copy(
                uid = safeUid,
                key = if (entry.constant) entry.key else cleanedKeys,
                content = safeContent,
                displayIndex = idx
            )
        }
        return lorebook.copy(entries = repaired)
    }

    /**
     * Serializes a Lorebook into strict SillyTavern V2 JSON format (object-keyed `entries` map + root metadata).
     */
    fun exportLorebookV2Json(lorebook: STLorebookRoot): String {
        val root = JSONObject()
        root.put("name", lorebook.name)
        root.put("description", lorebook.description)
        root.put("scanDepth", lorebook.scanDepth)
        root.put("tokenBudget", lorebook.tokenBudget)
        root.put("recursiveScanning", lorebook.recursiveScanning)

        val entriesObj = JSONObject()
        lorebook.entries.forEachIndexed { index, entry ->
            val normalized = entry.copy(displayIndex = index)
            entriesObj.put(index.toString(), entryToJson(normalized))
        }
        root.put("entries", entriesObj)

        val settingsObj = JSONObject().apply {
            put("scanDepth", lorebook.settings.scanDepth)
            put("tokenBudget", lorebook.settings.tokenBudget)
            put("recursiveScanning", lorebook.settings.recursiveScanning)
            put("caseInsensitiveKeys", lorebook.settings.caseInsensitiveKeys)
            put("matchWholeWords", lorebook.settings.matchWholeWords)
            put("useRomanNumerals", lorebook.settings.useRomanNumerals)
        }
        root.put("settings", settingsObj)
        root.put("extensions", JSONObject().put("generator", "LoreMaster Flex V3 (Lumiverse & STV2)"))
        return root.toString(2)
    }

    fun entryToJson(entry: STEntry): JSONObject = JSONObject().apply {
        put("uid", entry.uid)
        put("key", JSONArray(entry.key))
        put("keysecondary", JSONArray(entry.keysecondary))
        put("content", entry.content)
        put("comment", entry.comment)
        put("probability", entry.probability)
        put("useProbability", entry.useProbability)
        put("depth", entry.depth)
        put("group", entry.group)
        put("groupWeight", entry.groupWeight)
        put("groupOverride", entry.groupOverride)
        put("displayIndex", entry.displayIndex)
        put("disable", entry.disable)
        put("excludeRecursion", entry.excludeRecursion)
        put("preventRecursion", entry.preventRecursion)
        put("delayUntilRecursion", entry.delayUntilRecursion)
        put("delay", entry.delay)
        put("sticky", entry.sticky)
        put("cooldown", entry.cooldown)
        put("selective", entry.selective)
        put("selectiveLogic", entry.selectiveLogic)
        if (entry.scanDepth != null) put("scanDepth", entry.scanDepth) else put("scanDepth", JSONObject.NULL)
        if (entry.matchWholeWords != null) put("matchWholeWords", entry.matchWholeWords) else put("matchWholeWords", JSONObject.NULL)
        put("caseSensitive", entry.caseSensitive)
        put("constant", entry.constant)
        put("position", entry.position)
        put("order", entry.order)
        put("addMemo", entry.addMemo)
        put("triggers", JSONArray(entry.triggers))
        put("characterFilter", JSONObject().apply {
            put("isExclude", entry.characterFilter.isExclude)
            put("names", JSONArray(entry.characterFilter.names))
            put("tags", JSONArray(entry.characterFilter.tags))
        })
        put("outletName", entry.outletName)
        put("vectorized", entry.vectorized)
        put("ignoreBudget", entry.ignoreBudget)
        put("useGroupScoring", entry.useGroupScoring)
        put("excludeGreeting", entry.excludeGreeting)
        put("role", entry.role)
        put("sceneEventType", entry.sceneEventType)
        put("sceneSeverity", entry.sceneSeverity)
        put("activeInstructionEnabled", entry.activeInstructionEnabled)
    }

    fun entryFromJson(obj: JSONObject, fallbackUid: Int): STEntry {
        fun jsonArrayToStrings(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        val filterObj = obj.optJSONObject("characterFilter")
        val filter = if (filterObj != null) {
            STCharacterFilter(
                isExclude = filterObj.optBoolean("isExclude", false),
                names = jsonArrayToStrings(filterObj.optJSONArray("names")),
                tags = jsonArrayToStrings(filterObj.optJSONArray("tags"))
            )
        } else STCharacterFilter()

        return STEntry(
            uid = obj.optInt("uid", fallbackUid),
            key = jsonArrayToStrings(obj.optJSONArray("key") ?: obj.optJSONArray("keys")),
            keysecondary = jsonArrayToStrings(obj.optJSONArray("keysecondary") ?: obj.optJSONArray("secondary_keys")),
            content = obj.optString("content", ""),
            comment = obj.optString("comment", obj.optString("name", "Imported Entry")),
            probability = obj.optInt("probability", 100),
            useProbability = obj.optBoolean("useProbability", true),
            depth = obj.optInt("depth", 4),
            group = obj.optString("group", ""),
            groupWeight = obj.optInt("groupWeight", 100),
            groupOverride = obj.optBoolean("groupOverride", obj.optBoolean("group_override", obj.optBoolean("prioritizeInclusion", false))),
            displayIndex = obj.optInt("displayIndex", 0),
            disable = obj.optBoolean("disable", !obj.optBoolean("enabled", true)),
            excludeRecursion = obj.optBoolean("excludeRecursion", false),
            preventRecursion = obj.optBoolean("preventRecursion", false),
            delayUntilRecursion = obj.optBoolean("delayUntilRecursion", obj.optBoolean("delay_until_recursion", false)),
            delay = obj.optInt("delay", 0),
            sticky = obj.optInt("sticky", 0),
            cooldown = obj.optInt("cooldown", 0),
            selective = obj.optBoolean("selective", false),
            selectiveLogic = obj.optInt("selectiveLogic", 0),
            scanDepth = if (obj.isNull("scanDepth")) null else obj.optInt("scanDepth", 100),
            matchWholeWords = if (obj.isNull("matchWholeWords")) null else obj.optBoolean("matchWholeWords", true),
            caseSensitive = obj.optBoolean("caseSensitive", false),
            constant = obj.optBoolean("constant", false),
            position = obj.optInt("position", 0),
            order = obj.optInt("order", obj.optInt("insertion_order", 100)),
            addMemo = obj.optBoolean("addMemo", true),
            triggers = jsonArrayToStrings(obj.optJSONArray("triggers")),
            characterFilter = filter,
            outletName = obj.optString("outletName", ""),
            vectorized = obj.optBoolean("vectorized", false),
            ignoreBudget = obj.optBoolean("ignoreBudget", false),
            useGroupScoring = obj.optBoolean("useGroupScoring", false),
            excludeGreeting = obj.optBoolean("excludeGreeting", obj.optBoolean("exclude_greeting", false)),
            role = obj.optString("role", ""),
            sceneEventType = obj.optString("sceneEventType", ""),
            sceneSeverity = obj.optString("sceneSeverity", ""),
            activeInstructionEnabled = obj.optBoolean("activeInstructionEnabled", false)
        )
    }

    /**
     * Serializes a CharacterCardV2 into CCV3 (`chara_card_v3`) or CCV2 (`chara_card_v2`) JSON,
     * optionally embedding the project's Lorebook as `character_book`.
     */
    fun exportCharacterCardV2Json(
        card: CharacterCardV2,
        lorebook: STLorebookRoot? = null,
        useCcv3Spec: Boolean = card.specVersion.startsWith("3")
    ): String {
        val root = JSONObject()
        root.put("spec", if (useCcv3Spec) "chara_card_v3" else "chara_card_v2")
        root.put("spec_version", if (useCcv3Spec) "3.0" else "2.0")

        val data = JSONObject().apply {
            put("name", card.name)
            put("nickname", card.nickname.ifBlank { card.name })
            put("description", card.description)
            put("personality", card.personality)
            put("scenario", card.scenario)
            put("first_mes", card.firstMes)
            put("mes_example", card.mesExample)
            put("creator_notes", card.creatorNotes)
            put("system_prompt", card.systemPrompt)
            put("post_history_instructions", card.postHistoryInstructions)
            put("alternate_greetings", JSONArray(card.alternateGreetings))
            put("group_only_greetings", JSONArray(card.groupOnlyGreetings))
            put("tags", JSONArray(card.tags))
            put("creator", card.creator)
            put("character_version", card.characterVersion)
            put("creation_date", card.creationDate)
            put("modification_date", System.currentTimeMillis() / 1000L)

            // CCV3 Assets array
            val assetsArr = JSONArray()
            val effectiveAssets = CharacterCardV2.defaultAssetDescriptors(card.expressions)
            effectiveAssets.forEach { asset ->
                assetsArr.put(JSONObject().apply {
                    put("type", asset.type)
                    put("uri", asset.uri)
                    put("name", asset.name)
                    put("ext", asset.ext)
                })
            }
            put("assets", assetsArr)

            if (card.embedLorebookOnExport && lorebook != null) {
                val bookObj = JSONObject().apply {
                    put("name", lorebook.name)
                    put("description", lorebook.description)
                    put("scan_depth", lorebook.scanDepth)
                    put("token_budget", lorebook.tokenBudget)
                    put("recursive_scanning", lorebook.recursiveScanning)
                    val entriesArr = JSONArray()
                    lorebook.entries.forEach { entry ->
                        entriesArr.put(entryToJson(entry).apply {
                            put("keys", JSONArray(entry.key))
                            put("secondary_keys", JSONArray(entry.keysecondary))
                            put("enabled", !entry.disable)
                            put("insertion_order", entry.order)
                        })
                    }
                    put("entries", entriesArr)
                }
                put("character_book", bookObj)
            }

            put("extensions", JSONObject().apply {
                put("lumiverse", buildLumiverseModulesJson(card))
            })
        }
        root.put("data", data)
        // Top-level compatibility fields expected by SillyTavern & Lumiverse importers
        root.put("name", card.name)
        root.put("description", card.description)
        root.put("personality", card.personality)
        root.put("scenario", card.scenario)
        root.put("first_mes", card.firstMes)
        root.put("mes_example", card.mesExample)
        return root.toString(2)
    }

    /**
     * Builds the `lumiverse_modules.json` structure for CHARX archives and `data.extensions.lumiverse`.
     */
    fun buildLumiverseModulesJson(card: CharacterCardV2): JSONObject = JSONObject().apply {
        val mod = card.lumiverseModules
        put("module_name", mod.moduleName.ifBlank { "${card.name} Lumiverse Module" })
        put("version", mod.version)
        put("role_title", card.roleTitle)
        put("faction_affiliation", card.factionAffiliation)
        put("archetype", card.archetype)
        put("voice_cadence", card.voiceCadence)
        put("hidden_agenda", card.hiddenAgenda)
        put("avatar_color_hex", card.avatarColorHex)
        if (card.linkedLoreEntryUid != null) {
            put("linked_lore_uid", card.linkedLoreEntryUid)
        }
        put("ui_settings", JSONObject().apply {
            put("theme_accent", mod.uiThemeAccent)
            put("immersive_hud_enabled", mod.immersiveHudEnabled)
            put("expression_pack_enabled", mod.expressionPackEnabled)
            put("auto_sync_worldbook", mod.autoSyncWorldbook)
            put("state_tracker_fields", JSONArray(mod.stateTrackerFields))
        })
        val exprArr = JSONArray()
        card.expressions.forEach { expr ->
            val clean = expr.emotion.lowercase().replace(" ", "_")
            exprArr.put(JSONObject().apply {
                put("emotion", expr.emotion)
                put("trigger_cue", expr.triggerCue)
                put("accent_hex", expr.accentHex)
                put("asset_uri", "embeded://assets/emotion/images/$clean.png")
            })
        }
        put("expression_mappings", exprArr)

        val actionsArr = JSONArray()
        mod.customActions.forEach { act ->
            actionsArr.put(JSONObject().apply {
                put("id", act.id)
                put("label", act.label)
                put("macro_command", act.macroCommand)
                put("description", act.description)
            })
        }
        put("custom_actions", actionsArr)
        put("system_extensions", JSONObject().apply {
            put("extension_script", mod.systemExtensionScript)
        })
    }

    /**
     * Generates a genuine Lumiverse / CCV3 `.charx` ZIP Archive bundle containing:
     * 1. `card.json` (CCV3 specification `chara_card_v3`)
     * 2. `lumiverse_modules.json` (Lumiverse custom actions, UI settings, expression mappings & system extensions)
     * 3. `lorebooks/<world_name>.json` (Embedded standalone Lorebook V2/V3 if enabled)
     * 4. `assets/icon/images/main.png` (Main avatar portrait PNG)
     * 5. `assets/emotion/images/<emotion>.png` (Expression sprite assets for each emotion)
     */
    fun exportCharxArchiveBytes(card: CharacterCardV2, lorebook: STLorebookRoot? = null): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // 1. card.json (CCV3)
            val cardJsonBytes = exportCharacterCardV2Json(card, lorebook, useCcv3Spec = true)
                .toByteArray(StandardCharsets.UTF_8)
            zip.putNextEntry(ZipEntry("card.json"))
            zip.write(cardJsonBytes)
            zip.closeEntry()

            // 2. lumiverse_modules.json
            val lumiverseBytes = buildLumiverseModulesJson(card).toString(2)
                .toByteArray(StandardCharsets.UTF_8)
            zip.putNextEntry(ZipEntry("lumiverse_modules.json"))
            zip.write(lumiverseBytes)
            zip.closeEntry()

            // 3. Embedded lorebooks/<name>.json
            if (card.embedLorebookOnExport && lorebook != null) {
                val safeBookName = lorebook.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase().ifBlank { "worldbook" }
                val lorebookBytes = exportLorebookV2Json(lorebook).toByteArray(StandardCharsets.UTF_8)
                zip.putNextEntry(ZipEntry("lorebooks/$safeBookName.json"))
                zip.write(lorebookBytes)
                zip.closeEntry()
            }

            // 4. Main Avatar PNG in assets/icon/images/main.png
            val avatarPngBytes = renderCharacterPortraitPng(
                title = card.name,
                subtitle = card.roleTitle,
                badge = "CHARX • CCV3",
                accentHex = card.lumiverseModules.uiThemeAccent
            )
            zip.putNextEntry(ZipEntry("assets/icon/images/main.png"))
            zip.write(avatarPngBytes)
            zip.closeEntry()

            // 5. Expression Sprite PNGs in assets/emotion/images/<emotion>.png
            card.expressions.forEach { expr ->
                val cleanName = expr.emotion.lowercase().replace(" ", "_").ifBlank { "neutral" }
                val exprPng = renderCharacterPortraitPng(
                    title = card.name,
                    subtitle = "Expression: ${expr.emotion.uppercase()}",
                    badge = expr.triggerCue.take(28),
                    accentHex = expr.accentHex,
                    width = 320,
                    height = 320
                )
                zip.putNextEntry(ZipEntry("assets/emotion/images/$cleanName.png"))
                zip.write(exprPng)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun renderCharacterPortraitPng(
        title: String,
        subtitle: String,
        badge: String,
        accentHex: String = "#06B6D4",
        width: Int = 512,
        height: Int = 768
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val accentColor = runCatching { Color.parseColor(accentHex) }.getOrDefault(0xFF06B6D4.toInt())

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF06070B.toInt(), 0xFF121629.toInt(), 0xFF1E1B38.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = accentColor
        }
        canvas.drawRoundRect(20f, 20f, width - 20f, height - 20f, 26f, 26f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF8FAFC.toInt()
            textSize = if (width >= 500) 40f else 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title.take(22), width / 2f, height / 2f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = if (width >= 500) 22f else 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle.take(30), width / 2f, height / 2f + 44f, subPaint)

        if (badge.isNotBlank()) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF94A3B8.toInt()
                textSize = if (width >= 500) 18f else 13f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(badge.take(32), width / 2f, height - 48f, badgePaint)
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Generates a valid SillyTavern / Lumiverse PNG Character Card with embedded `tEXt` ("chara" AND "ccv3") base64 metadata chunks.
     */
    fun exportCharacterCardPngBytes(card: CharacterCardV2, lorebook: STLorebookRoot? = null): ByteArray {
        val jsonV2Payload = exportCharacterCardV2Json(card, lorebook, useCcv3Spec = false)
        val jsonV3Payload = exportCharacterCardV2Json(card, lorebook, useCcv3Spec = true)
        val base64Chara = Base64.encodeToString(jsonV2Payload.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val base64Ccv3 = Base64.encodeToString(jsonV3Payload.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

        val rawPngBytes = renderCharacterPortraitPng(
            title = card.name,
            subtitle = card.roleTitle,
            badge = "CCV2 + CCV3 PNG",
            accentHex = card.avatarColorHex
        )
        val withChara = injectPngTextChunk(rawPngBytes, "chara", base64Chara)
        return injectPngTextChunk(withChara, "ccv3", base64Ccv3)
    }

    private fun injectPngTextChunk(pngBytes: ByteArray, keyword: String, text: String): ByteArray {
        val iendIndex = findIendChunkIndex(pngBytes)
        if (iendIndex < 8) return pngBytes

        val keyBytes = keyword.toByteArray(StandardCharsets.ISO_8859_1)
        val textBytes = text.toByteArray(StandardCharsets.ISO_8859_1)
        val chunkData = ByteArray(keyBytes.size + 1 + textBytes.size)
        System.arraycopy(keyBytes, 0, chunkData, 0, keyBytes.size)
        chunkData[keyBytes.size] = 0
        System.arraycopy(textBytes, 0, chunkData, keyBytes.size + 1, textBytes.size)

        val typeBytes = "tEXt".toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(chunkData)
        val crcValue = crc.value.toInt()

        val out = ByteArrayOutputStream(pngBytes.size + chunkData.size + 12)
        out.write(pngBytes, 0, iendIndex)
        out.write(ByteBuffer.allocate(4).putInt(chunkData.size).array())
        out.write(typeBytes)
        out.write(chunkData)
        out.write(ByteBuffer.allocate(4).putInt(crcValue).array())
        out.write(pngBytes, iendIndex, pngBytes.size - iendIndex)
        return out.toByteArray()
    }

    private fun findIendChunkIndex(pngBytes: ByteArray): Int {
        var offset = 8
        while (offset + 8 <= pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, offset, 4).int
            val type = String(pngBytes, offset + 4, 4, StandardCharsets.US_ASCII)
            if (type == "IEND") return offset
            offset += 12 + length
        }
        return -1
    }

    /**
     * Extracts embedded `ccv3` or `chara` JSON from a PNG Character Card's `tEXt` chunk.
     */
    fun extractCharaJsonFromPng(pngBytes: ByteArray): String? {
        if (pngBytes.size < 16) return null
        var offset = 8
        var fallbackChara: String? = null
        while (offset + 8 <= pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, offset, 4).int
            if (length < 0 || offset + 12 + length > pngBytes.size) break
            val type = String(pngBytes, offset + 4, 4, StandardCharsets.US_ASCII)
            if (type == "tEXt") {
                val dataStart = offset + 8
                var nullSep = dataStart
                while (nullSep < dataStart + length && pngBytes[nullSep].toInt() != 0) {
                    nullSep++
                }
                val keyword = String(pngBytes, dataStart, nullSep - dataStart, StandardCharsets.ISO_8859_1)
                if (keyword.equals("ccv3", ignoreCase = true) || keyword.equals("chara", ignoreCase = true)) {
                    val base64Str = String(
                        pngBytes,
                        nullSep + 1,
                        (dataStart + length) - (nullSep + 1),
                        StandardCharsets.ISO_8859_1
                    )
                    val decoded = String(Base64.decode(base64Str.trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
                    if (keyword.equals("ccv3", ignoreCase = true)) return decoded
                    fallbackChara = decoded
                }
            }
            offset += 12 + length
        }
        return fallbackChara
    }

    /**
     * Locates the start of a ZIP archive inside `bytes`.
     * Supports standard `.charx` / `.zip` (offset 0) AND RisuAI / Lumiverse JPEG+ZIP or PNG+ZIP polyglot files!
     */
    fun findZipHeaderOffset(bytes: ByteArray): Int {
        for (i in 0..(bytes.size - 4)) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() &&
                bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    /**
     * Universal importer that supports:
     * 1. `.charx` ZIP archives (`card.json` CCV3 + `lumiverse_modules.json` + embedded `lorebooks/` JSON + `assets/`)
     * 2. RisuAI / Lumiverse JPEG+ZIP or PNG+ZIP polyglot bundles
     * 3. PNG Character Cards (`ccv3` and `chara` tEXt chunks)
     * 4. Raw JSON (`chara_card_v3`, `chara_card_v2`, or SillyTavern V2 Lorebook)
     */
    fun importUniversalBytes(bytes: ByteArray, fileName: String): Pair<STLorebookRoot?, CharacterCardV2?> {
        val zipOffset = findZipHeaderOffset(bytes)
        if (zipOffset >= 0 && (fileName.endsWith(".charx", ignoreCase = true) ||
                fileName.endsWith(".zip", ignoreCase = true) ||
                zipOffset == 0 ||
                zipOffset > 16)
        ) {
            val zipResult = runCatching { parseCharxZipSlice(bytes, zipOffset) }.getOrNull()
            if (zipResult != null && (zipResult.first != null || zipResult.second != null)) {
                return zipResult
            }
        }

        // Check PNG tEXt chunk
        if (fileName.endsWith(".png", ignoreCase = true) ||
            (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte())
        ) {
            val extractedJson = extractCharaJsonFromPng(bytes)
                ?: throw IllegalArgumentException("PNG file does not contain an embedded 'ccv3' or 'chara' chunk or CHARX polyglot archive.")
            return parseImportedJson(extractedJson)
        }

        val rawJson = String(bytes, StandardCharsets.UTF_8)
        return parseImportedJson(rawJson)
    }

    private fun parseCharxZipSlice(bytes: ByteArray, offset: Int): Pair<STLorebookRoot?, CharacterCardV2?> {
        var cardJsonStr: String? = null
        var lumiverseModulesStr: String? = null
        val extraLorebooks = mutableListOf<STLorebookRoot>()
        val discoveredEmotionNames = mutableListOf<String>()

        ZipInputStream(ByteArrayInputStream(bytes, offset, bytes.size - offset)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    val entryBytes = zis.readBytes()
                    when {
                        entryName.equals("card.json", ignoreCase = true) || entryName.endsWith("/card.json", ignoreCase = true) -> {
                            cardJsonStr = String(entryBytes, StandardCharsets.UTF_8)
                        }
                        entryName.equals("lumiverse_modules.json", ignoreCase = true) || entryName.endsWith("/lumiverse_modules.json", ignoreCase = true) -> {
                            lumiverseModulesStr = String(entryBytes, StandardCharsets.UTF_8)
                        }
                        entryName.startsWith("lorebooks/", ignoreCase = true) && entryName.endsWith(".json", ignoreCase = true) -> {
                            runCatching {
                                val bookObj = JSONObject(String(entryBytes, StandardCharsets.UTF_8))
                                extraLorebooks.add(parseLorebookObject(bookObj, entryName.substringAfterLast("/").removeSuffix(".json")))
                            }
                        }
                        entryName.contains("assets/emotion/", ignoreCase = true) || entryName.contains("expressions/", ignoreCase = true) -> {
                            val emotionName = entryName.substringAfterLast("/").substringBeforeLast(".")
                            if (emotionName.isNotBlank()) discoveredEmotionNames.add(emotionName)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        var (parsedLorebook, parsedCard) = if (cardJsonStr != null) {
            parseImportedJson(cardJsonStr!!)
        } else {
            null to null
        }

        // Merge standalone lumiverse_modules.json if present in the .charx bundle
        if (lumiverseModulesStr != null && parsedCard != null) {
            val lumObj = runCatching { JSONObject(lumiverseModulesStr!!) }.getOrNull()
            if (lumObj != null) {
                parsedCard = applyLumiverseJsonToCard(parsedCard, lumObj)
            }
        }

        // Add any discovered emotion sprite files from the .charx zip that weren't in JSON
        if (parsedCard != null && discoveredEmotionNames.isNotEmpty()) {
            val existingEmotions = parsedCard.expressions.map { it.emotion.lowercase() }.toSet()
            val additional = discoveredEmotionNames
                .filter { it.lowercase() !in existingEmotions }
                .map { CharxExpressionAsset(emotion = it, triggerCue = "Bundled CHARX emotion sprite") }
            if (additional.isNotEmpty()) {
                val mergedExpr = parsedCard.expressions + additional
                parsedCard = parsedCard.copy(
                    expressions = mergedExpr,
                    assets = CharacterCardV2.defaultAssetDescriptors(mergedExpr)
                )
            }
        }

        // Merge any standalone lorebooks/*.json from the .charx bundle
        if (extraLorebooks.isNotEmpty()) {
            val combinedEntries = (parsedLorebook?.entries.orEmpty() + extraLorebooks.flatMap { it.entries })
            val baseBook = parsedLorebook ?: extraLorebooks.first()
            parsedLorebook = baseBook.copy(entries = combinedEntries)
        }

        return parsedLorebook to parsedCard
    }

    private fun applyLumiverseJsonToCard(card: CharacterCardV2, ext: JSONObject): CharacterCardV2 {
        val uiObj = ext.optJSONObject("ui_settings")
        val sysObj = ext.optJSONObject("system_extensions")

        val customActions = mutableListOf<LumiverseCustomAction>()
        val actArr = ext.optJSONArray("custom_actions")
        if (actArr != null) {
            for (i in 0 until actArr.length()) {
                val a = actArr.optJSONObject(i) ?: continue
                customActions.add(
                    LumiverseCustomAction(
                        id = a.optString("id", "act_$i"),
                        label = a.optString("label", "Action ${i + 1}"),
                        macroCommand = a.optString("macro_command", ""),
                        description = a.optString("description", "")
                    )
                )
            }
        }

        val expressions = mutableListOf<CharxExpressionAsset>()
        val exprArr = ext.optJSONArray("expression_mappings")
        if (exprArr != null) {
            for (i in 0 until exprArr.length()) {
                val e = exprArr.optJSONObject(i) ?: continue
                expressions.add(
                    CharxExpressionAsset(
                        emotion = e.optString("emotion", "neutral"),
                        triggerCue = e.optString("trigger_cue", ""),
                        accentHex = e.optString("accent_hex", "#38BDF8")
                    )
                )
            }
        }

        val trackerFields = mutableListOf<String>()
        val tfArr = uiObj?.optJSONArray("state_tracker_fields")
        if (tfArr != null) {
            for (i in 0 until tfArr.length()) {
                val s = tfArr.optString(i)
                if (s.isNotBlank()) trackerFields.add(s)
            }
        }

        val updatedModules = card.lumiverseModules.copy(
            moduleName = ext.optString("module_name", card.lumiverseModules.moduleName),
            version = ext.optString("version", card.lumiverseModules.version),
            uiThemeAccent = uiObj?.optString("theme_accent") ?: ext.optString("avatar_color_hex", card.avatarColorHex),
            immersiveHudEnabled = uiObj?.optBoolean("immersive_hud_enabled", true) ?: true,
            expressionPackEnabled = uiObj?.optBoolean("expression_pack_enabled", true) ?: true,
            autoSyncWorldbook = uiObj?.optBoolean("auto_sync_worldbook", true) ?: true,
            stateTrackerFields = trackerFields.ifEmpty { card.lumiverseModules.stateTrackerFields },
            customActions = customActions.ifEmpty { card.lumiverseModules.customActions },
            systemExtensionScript = sysObj?.optString("extension_script") ?: card.lumiverseModules.systemExtensionScript
        )

        return card.copy(
            roleTitle = ext.optString("role_title", card.roleTitle).ifBlank { card.roleTitle },
            factionAffiliation = ext.optString("faction_affiliation", card.factionAffiliation),
            archetype = ext.optString("archetype", card.archetype),
            voiceCadence = ext.optString("voice_cadence", card.voiceCadence),
            hiddenAgenda = ext.optString("hidden_agenda", card.hiddenAgenda),
            avatarColorHex = ext.optString("avatar_color_hex", card.avatarColorHex),
            linkedLoreEntryUid = if (ext.has("linked_lore_uid")) ext.optInt("linked_lore_uid") else card.linkedLoreEntryUid,
            expressions = expressions.ifEmpty { card.expressions },
            lumiverseModules = updatedModules
        )
    }

    /**
     * Universal JSON parser that handles SillyTavern V2 Lorebook JSON OR Character Card V2/V3 JSON.
     */
    fun parseImportedJson(rawJson: String): Pair<STLorebookRoot?, CharacterCardV2?> {
        val obj = JSONObject(rawJson.trim())
        val spec = obj.optString("spec")
        if (spec == "chara_card_v3" || spec == "chara_card_v2" || obj.has("first_mes") || obj.optJSONObject("data")?.has("first_mes") == true) {
            val data = obj.optJSONObject("data") ?: obj
            val ext = data.optJSONObject("extensions")?.optJSONObject("lumiverse")

            fun toStrList(arr: JSONArray?): List<String> =
                if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }

            val greetings = toStrList(data.optJSONArray("alternate_greetings"))
            val groupGreetings = toStrList(data.optJSONArray("group_only_greetings"))
            val tags = toStrList(data.optJSONArray("tags"))

            val assetsArr = data.optJSONArray("assets")
            val parsedAssets = mutableListOf<CharxAssetDescriptor>()
            if (assetsArr != null) {
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.optJSONObject(i) ?: continue
                    parsedAssets.add(
                        CharxAssetDescriptor(
                            type = a.optString("type", "icon"),
                            uri = a.optString("uri", ""),
                            name = a.optString("name", "main"),
                            ext = a.optString("ext", "png")
                        )
                    )
                }
            }

            var card = CharacterCardV2(
                name = data.optString("name", "Imported Character"),
                nickname = data.optString("nickname", ""),
                roleTitle = ext?.optString("role_title") ?: "Imported Persona",
                factionAffiliation = ext?.optString("faction_affiliation") ?: "",
                description = data.optString("description", ""),
                personality = data.optString("personality", ""),
                scenario = data.optString("scenario", ""),
                firstMes = data.optString("first_mes", ""),
                mesExample = data.optString("mes_example", ""),
                creatorNotes = data.optString("creator_notes", ""),
                systemPrompt = data.optString("system_prompt", ""),
                postHistoryInstructions = data.optString("post_history_instructions", ""),
                alternateGreetings = greetings,
                groupOnlyGreetings = groupGreetings,
                tags = tags,
                creator = data.optString("creator", "Lumiverse / ST Import"),
                characterVersion = data.optString("character_version", "3.0"),
                specVersion = if (spec == "chara_card_v2") "2.0" else "3.0",
                assets = parsedAssets.ifEmpty { CharacterCardV2.defaultAssetDescriptors(CharacterCardV2.defaultExpressions()) }
            )

            if (ext != null) {
                card = applyLumiverseJsonToCard(card, ext)
            }

            val bookObj = data.optJSONObject("character_book")
            val embeddedLorebook = if (bookObj != null) parseLorebookObject(bookObj, "${card.name} Lorebook") else null
            return embeddedLorebook to card
        }

        return parseLorebookObject(obj, "Imported Lorebook") to null
    }

    private fun parseLorebookObject(obj: JSONObject, fallbackName: String): STLorebookRoot {
        val entriesList = mutableListOf<STEntry>()
        val entriesNode = obj.opt("entries")
        when (entriesNode) {
            is JSONArray -> {
                for (i in 0 until entriesNode.length()) {
                    val item = entriesNode.optJSONObject(i) ?: continue
                    entriesList.add(entryFromJson(item, 100 + i))
                }
            }
            is JSONObject -> {
                val keys = entriesNode.keys().asSequence().toList()
                keys.forEachIndexed { idx, key ->
                    val item = entriesNode.optJSONObject(key) ?: return@forEachIndexed
                    entriesList.add(entryFromJson(item, 100 + idx))
                }
            }
        }
        val used = mutableSetOf<Int>()
        var nextUid = 100
        val deduplicated = entriesList.mapIndexed { index, entry ->
            val uid = if (used.add(entry.uid)) entry.uid else {
                while (!used.add(nextUid)) nextUid++
                nextUid
            }
            entry.copy(uid = uid, displayIndex = index)
        }

        val name = obj.optString("name", fallbackName).ifBlank { fallbackName }
        val desc = obj.optString("description", "")
        val scanDepth = obj.optInt("scanDepth", obj.optInt("scan_depth", 100))
        val tokenBudget = obj.optInt("tokenBudget", obj.optInt("token_budget", 3000))
        val recursive = obj.optBoolean("recursiveScanning", obj.optBoolean("recursive_scanning", true))

        return STLorebookRoot(
            name = name,
            description = desc,
            scanDepth = scanDepth,
            tokenBudget = tokenBudget,
            recursiveScanning = recursive,
            entries = deduplicated,
            settings = STSettings(
                scanDepth = scanDepth,
                tokenBudget = tokenBudget,
                recursiveScanning = recursive
            )
        )
    }

    /**
     * Full UniverseProject serialization for Room SQLite storage.
     * Persists ALL fields including Wizard State (Speculative Gate & Intensity),
     * ForgeConfig (Preset, Modules, Procedural, 9 Tone Sliders, Procedural Tuning),
     * WorldDnaAnalysis, ForgeBlueprint, and CHARX/CCV3 Character Cards.
     */
    fun serializeProject(project: UniverseProject): String {
        val obj = JSONObject()
        obj.put("id", project.id)
        obj.put("name", project.name)
        obj.put("description", project.description)
        obj.put("genre", project.genre)
        obj.put("tone", project.tone)
        obj.put("createdAt", project.createdAt)
        obj.put("updatedAt", project.updatedAt)
        obj.put("lorebook", JSONObject(exportLorebookV2Json(project.lorebook)))

        val cardsArr = JSONArray()
        project.characterCards.forEach { card ->
            cardsArr.put(JSONObject().apply {
                put("id", card.id)
                put("name", card.name)
                put("nickname", card.nickname)
                put("roleTitle", card.roleTitle)
                put("factionAffiliation", card.factionAffiliation)
                put("description", card.description)
                put("personality", card.personality)
                put("scenario", card.scenario)
                put("firstMes", card.firstMes)
                put("mesExample", card.mesExample)
                put("creatorNotes", card.creatorNotes)
                put("systemPrompt", card.systemPrompt)
                put("postHistoryInstructions", card.postHistoryInstructions)
                put("alternateGreetings", JSONArray(card.alternateGreetings))
                put("groupOnlyGreetings", JSONArray(card.groupOnlyGreetings))
                put("tags", JSONArray(card.tags))
                put("creator", card.creator)
                put("characterVersion", card.characterVersion)
                put("specVersion", card.specVersion)
                put("avatarColorHex", card.avatarColorHex)
                if (card.linkedLoreEntryUid != null) put("linkedLoreEntryUid", card.linkedLoreEntryUid)
                put("embedLorebookOnExport", card.embedLorebookOnExport)
                put("archetype", card.archetype)
                put("voiceCadence", card.voiceCadence)
                put("hiddenAgenda", card.hiddenAgenda)
                put("lumiverseModules", buildLumiverseModulesJson(card))
            })
        }
        obj.put("characterCards", cardsArr)

        // Complete PremiseWizardState serialization
        obj.put("wizardState", JSONObject().apply {
            put("isDetailedMode", project.wizardState.isDetailedMode)
            put("freeformScenario", project.wizardState.freeformScenario)
            put("genre", project.wizardState.genre)
            put("subgenre", project.wizardState.subgenre)
            put("era", project.wizardState.era)
            put("vibes", project.wizardState.vibes)
            put("conflict", project.wizardState.conflict)
            put("strangeFactor", project.wizardState.strangeFactor)
            put("protagonist", project.wizardState.protagonist)
            put("mustInclude", project.wizardState.mustInclude)
            put("avoid", project.wizardState.avoid)
            put("hardRules", project.wizardState.hardRules)
            put("tokenCap", project.wizardState.tokenCap)
            put("speculativeMode", project.wizardState.speculativeMode.name)
            put("speculativeIntensity", project.wizardState.speculativeIntensity.name)
        })

        // Complete ForgeConfig serialization
        val cfg = project.forgeConfig
        obj.put("forgeConfig", JSONObject().apply {
            put("generationTarget", cfg.generationTarget.name)
            put("cardConceptType", cfg.cardConceptType.name)
            put("targetEntryCount", cfg.targetEntryCount.coerceIn(10, 250))
            put("lorebookNpcCount", cfg.lorebookNpcCount.coerceIn(0, 250))
            put("sizePreset", cfg.sizePreset.name)
            put("maxTokenCap", cfg.maxTokenCap)
            put("enabledModules", JSONArray(cfg.enabledModules.map { it.name }))
            put("enabledProcedural", JSONArray(cfg.enabledGenerators.map { it.name }))
            val toneObj = JSONObject()
            cfg.toneAxes.forEach { (k, v) -> toneObj.put(k, v) }
            put("toneAxes", toneObj)
            put("castSize", cfg.castSize)
            put("relationshipDensity", cfg.relationshipDensity)
            put("secretDensity", cfg.secretDensity)
            put("keyStrategy", cfg.keyStrategy)
            put("keyStrictness", cfg.keyStrictness)
            put("temperature", cfg.temperature.toDouble())
            put("autoGenerateCharacterCards", cfg.autoGenerateCharacterCards)
            put("enforceNensNaming", cfg.enforceNensNaming)
            put("randomnessLevel", cfg.randomnessLevel)
            put("contentDensity", cfg.contentDensity.name)
            put("persistenceMode", cfg.persistenceMode.name)
            put("weightDistribution", cfg.weightDistribution.name)
        })

        // Complete WorldDnaAnalysis serialization
        project.dnaAnalysis?.let { dna ->
            obj.put("dnaAnalysis", JSONObject().apply {
                put("logline", dna.logline)
                put("settingSummary", dna.settingSummary)
                put("coreConflict", dna.coreConflict)
                put("themes", JSONArray(dna.themes))
                put("stakes", dna.stakes)
                put("darkness", dna.darkness)
                put("complexity", dna.complexity)
                put("danger", dna.danger)
                put("hope", dna.hope)
                put("mystery", dna.mystery)
                put("clarity", dna.clarity)
                put("originality", dna.originality)
                put("conflictDensity", dna.conflictDensity)
                put("mysteryLoad", dna.mysteryLoad)
                put("castPressure", dna.castPressure)
                put("complexityRisk", dna.complexityRisk)
                put("narrativeHooks", JSONArray(dna.narrativeHooks))
                put("missingPillars", JSONArray(dna.missingPillars))
                val entArr = JSONArray()
                dna.extractedEntities.forEach { e ->
                    entArr.put(JSONObject().apply {
                        put("name", e.name)
                        put("type", e.type.name)
                        put("importance", e.importance)
                        put("summary", e.summary)
                    })
                }
                put("extractedEntities", entArr)
            })
        }

        // Complete ForgeBlueprint serialization
        project.activeBlueprint?.let { bp ->
            obj.put("activeBlueprint", JSONObject().apply {
                put("universeTitle", bp.universeTitle)
                put("linkingStrategySummary", bp.linkingStrategySummary)
                put("generationTarget", bp.generationTarget.name)
                put("cardConceptType", bp.cardConceptType.name)
                put("batchSize", bp.batchSize)
                bp.plannedPrimaryCard?.let { pc ->
                    put("plannedPrimaryCard", JSONObject().apply {
                        put("id", pc.id)
                        put("name", pc.name)
                        put("nickname", pc.nickname)
                        put("roleTitle", pc.roleTitle)
                        put("factionAffiliation", pc.factionAffiliation)
                        put("description", pc.description)
                        put("personality", pc.personality)
                        put("scenario", pc.scenario)
                        put("firstMes", pc.firstMes)
                        put("mesExample", pc.mesExample)
                        put("creatorNotes", pc.creatorNotes)
                        put("systemPrompt", pc.systemPrompt)
                        put("postHistoryInstructions", pc.postHistoryInstructions)
                        put("alternateGreetings", JSONArray(pc.alternateGreetings))
                        put("groupOnlyGreetings", JSONArray(pc.groupOnlyGreetings))
                        put("tags", JSONArray(pc.tags))
                        put("archetype", pc.archetype)
                        put("voiceCadence", pc.voiceCadence)
                        put("hiddenAgenda", pc.hiddenAgenda)
                        put("embedLorebookOnExport", pc.embedLorebookOnExport)
                    })
                }
                val plansArr = JSONArray()
                bp.plannedEntries.forEach { p ->
                    plansArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("title", p.title)
                        put("module", p.module.name)
                        put("templateType", p.templateType.name)
                        put("primaryKeys", JSONArray(p.primaryKeys))
                        put("secondaryKeys", JSONArray(p.secondaryKeys))
                        put("shouldMentionEntities", JSONArray(p.shouldMentionEntities))
                        put("briefDirective", p.briefDirective)
                        put("isCharacterCardCandidate", p.isCharacterCardCandidate)
                        put("forgedContent", p.forgedContent)
                        put("isForged", p.isForged)
                    })
                }
                put("plannedEntries", plansArr)
            })
        }

        return obj.toString()
    }

    fun deserializeProject(jsonStr: String): UniverseProject {
        val obj = JSONObject(jsonStr)
        val id = obj.optString("id", "universe-default")
        val name = obj.optString("name", "Untitled Universe")
        val description = obj.optString("description", "")
        val genre = obj.optString("genre", "Speculative Fiction")
        val tone = obj.optString("tone", "Atmospheric")
        val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

        val lorebookObj = obj.optJSONObject("lorebook")
        val lorebook = if (lorebookObj != null) {
            parseLorebookObject(lorebookObj, name)
        } else {
            STLorebookRoot(name = name, description = description)
        }

        fun jsonArrStrings(arr: JSONArray?): List<String> =
            if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }

        val cards = mutableListOf<CharacterCardV2>()
        val cardsArr = obj.optJSONArray("characterCards")
        if (cardsArr != null) {
            for (i in 0 until cardsArr.length()) {
                val c = cardsArr.optJSONObject(i) ?: continue
                var card = CharacterCardV2(
                    id = c.optString("id"),
                    name = c.optString("name"),
                    nickname = c.optString("nickname", ""),
                    roleTitle = c.optString("roleTitle"),
                    factionAffiliation = c.optString("factionAffiliation"),
                    description = c.optString("description"),
                    personality = c.optString("personality"),
                    scenario = c.optString("scenario"),
                    firstMes = c.optString("firstMes"),
                    mesExample = c.optString("mesExample"),
                    creatorNotes = c.optString("creatorNotes"),
                    systemPrompt = c.optString("systemPrompt"),
                    postHistoryInstructions = c.optString("postHistoryInstructions"),
                    alternateGreetings = jsonArrStrings(c.optJSONArray("alternateGreetings")),
                    groupOnlyGreetings = jsonArrStrings(c.optJSONArray("groupOnlyGreetings")),
                    tags = jsonArrStrings(c.optJSONArray("tags")),
                    creator = c.optString("creator", "LoreMaster Flex"),
                    characterVersion = c.optString("characterVersion", "3.0"),
                    specVersion = c.optString("specVersion", "3.0"),
                    avatarColorHex = c.optString("avatarColorHex", "#06B6D4"),
                    linkedLoreEntryUid = if (c.has("linkedLoreEntryUid")) c.optInt("linkedLoreEntryUid") else null,
                    embedLorebookOnExport = c.optBoolean("embedLorebookOnExport", true),
                    archetype = c.optString("archetype", ""),
                    voiceCadence = c.optString("voiceCadence", ""),
                    hiddenAgenda = c.optString("hiddenAgenda", "")
                )
                c.optJSONObject("lumiverseModules")?.let { lumObj ->
                    card = applyLumiverseJsonToCard(card, lumObj)
                }
                cards.add(card)
            }
        }

        val wObj = obj.optJSONObject("wizardState")
        val wizardState = if (wObj != null) {
            val specMode = runCatching {
                SpeculativeMode.valueOf(wObj.optString("speculativeMode", SpeculativeMode.SPECULATIVE.name))
            }.getOrDefault(SpeculativeMode.SPECULATIVE)
            val specIntensity = runCatching {
                SpeculativeIntensity.valueOf(wObj.optString("speculativeIntensity", SpeculativeIntensity.MODERATE.name))
            }.getOrDefault(SpeculativeIntensity.MODERATE)

            PremiseWizardState(
                isDetailedMode = wObj.optBoolean("isDetailedMode", true),
                freeformScenario = wObj.optString("freeformScenario", ""),
                genre = wObj.optString("genre", genre),
                subgenre = wObj.optString("subgenre", ""),
                era = wObj.optString("era", ""),
                vibes = wObj.optString("vibes", ""),
                conflict = wObj.optString("conflict", ""),
                strangeFactor = wObj.optString("strangeFactor", ""),
                protagonist = wObj.optString("protagonist", ""),
                mustInclude = wObj.optString("mustInclude", ""),
                avoid = wObj.optString("avoid", ""),
                hardRules = wObj.optString("hardRules", ""),
                tokenCap = wObj.optInt("tokenCap", 3500),
                speculativeMode = specMode,
                speculativeIntensity = specIntensity
            )
        } else PremiseWizardState(genre = genre)

        // Deserialize ForgeConfig
        val cObj = obj.optJSONObject("forgeConfig")
        val defaultCfg = ForgeConfig()
        val forgeConfig = if (cObj != null) {
            val genTarget = runCatching {
                ForgeGenerationTarget.valueOf(cObj.optString("generationTarget", defaultCfg.generationTarget.name))
            }.getOrDefault(defaultCfg.generationTarget)

            val conceptType = runCatching {
                CardConceptType.valueOf(cObj.optString("cardConceptType", defaultCfg.cardConceptType.name))
            }.getOrDefault(defaultCfg.cardConceptType)

            val preset = runCatching {
                GenerationSizePreset.valueOf(cObj.optString("sizePreset", defaultCfg.sizePreset.name))
            }.getOrDefault(defaultCfg.sizePreset)

            val targetEntries = cObj.optInt("targetEntryCount", preset.targetEntries).coerceIn(10, 250)
            val npcCount = cObj.optInt("lorebookNpcCount", defaultCfg.lorebookNpcCount).coerceIn(0, targetEntries)

            val modArr = cObj.optJSONArray("enabledModules")
            val modules = if (modArr != null) {
                (0 until modArr.length()).mapNotNull { idx ->
                    runCatching { ForgeModule.valueOf(modArr.optString(idx)) }.getOrNull()
                }.toSet()
            } else defaultCfg.enabledModules

            val procArr = cObj.optJSONArray("enabledProcedural")
            val procedurals = if (procArr != null) {
                (0 until procArr.length()).mapNotNull { idx ->
                    runCatching { ProceduralSystemType.valueOf(procArr.optString(idx)) }.getOrNull()
                }.toSet()
            } else defaultCfg.enabledProcedural

            val toneObj = cObj.optJSONObject("toneAxes")
            val tones = if (toneObj != null) {
                val map = defaultCfg.toneAxes.toMutableMap()
                toneObj.keys().forEach { k -> map[k] = toneObj.optInt(k, map[k] ?: 50) }
                map
            } else defaultCfg.toneAxes

            val density = runCatching {
                ProceduralDensity.valueOf(cObj.optString("contentDensity", defaultCfg.contentDensity.name))
            }.getOrDefault(defaultCfg.contentDensity)

            val persistence = runCatching {
                ProceduralPersistence.valueOf(cObj.optString("persistenceMode", defaultCfg.persistenceMode.name))
            }.getOrDefault(defaultCfg.persistenceMode)

            val weightDist = runCatching {
                ProceduralWeightMode.valueOf(cObj.optString("weightDistribution", defaultCfg.weightDistribution.name))
            }.getOrDefault(defaultCfg.weightDistribution)

            ForgeConfig(
                generationTarget = genTarget,
                cardConceptType = conceptType,
                targetEntryCount = targetEntries,
                lorebookNpcCount = npcCount,
                sizePreset = preset,
                maxTokenCap = cObj.optInt("maxTokenCap", 0),
                enabledModules = modules,
                enabledProcedural = procedurals,
                enabledGenerators = procedurals,
                toneAxes = tones,
                castSize = npcCount.coerceAtLeast(1),
                relationshipDensity = cObj.optString("relationshipDensity", defaultCfg.relationshipDensity),
                secretDensity = cObj.optString("secretDensity", defaultCfg.secretDensity),
                keyStrategy = cObj.optString("keyStrategy", defaultCfg.keyStrategy),
                keyStrictness = cObj.optString("keyStrictness", defaultCfg.keyStrictness),
                temperature = cObj.optDouble("temperature", defaultCfg.temperature.toDouble()).toFloat(),
                autoGenerateCharacterCards = cObj.optBoolean("autoGenerateCharacterCards", defaultCfg.autoGenerateCharacterCards),
                enforceNensNaming = cObj.optBoolean("enforceNensNaming", defaultCfg.enforceNensNaming),
                randomnessLevel = cObj.optInt("randomnessLevel", defaultCfg.randomnessLevel),
                contentDensity = density,
                persistenceMode = persistence,
                weightDistribution = weightDist
            )
        } else defaultCfg

        // Deserialize WorldDnaAnalysis
        val dObj = obj.optJSONObject("dnaAnalysis")
        val dnaAnalysis = if (dObj != null) {
            val entArr = dObj.optJSONArray("extractedEntities")
            val entities = mutableListOf<WorldDnaEntity>()
            if (entArr != null) {
                for (i in 0 until entArr.length()) {
                    val eo = entArr.optJSONObject(i) ?: continue
                    val eType = runCatching { EntryTemplateType.valueOf(eo.optString("type")) }
                        .getOrDefault(EntryTemplateType.CONCEPT)
                    entities.add(
                        WorldDnaEntity(
                            name = eo.optString("name"),
                            type = eType,
                            importance = eo.optString("importance", "MAJOR"),
                            summary = eo.optString("summary", "")
                        )
                    )
                }
            }
            WorldDnaAnalysis(
                logline = dObj.optString("logline"),
                settingSummary = dObj.optString("settingSummary"),
                coreConflict = dObj.optString("coreConflict"),
                themes = jsonArrStrings(dObj.optJSONArray("themes")),
                stakes = dObj.optString("stakes"),
                darkness = dObj.optInt("darkness", 72),
                complexity = dObj.optInt("complexity", 84),
                danger = dObj.optInt("danger", 68),
                hope = dObj.optInt("hope", 38),
                mystery = dObj.optInt("mystery", 88),
                clarity = dObj.optInt("clarity", 90),
                originality = dObj.optInt("originality", 86),
                conflictDensity = dObj.optInt("conflictDensity", 82),
                mysteryLoad = dObj.optInt("mysteryLoad", 78),
                castPressure = dObj.optInt("castPressure", 64),
                complexityRisk = dObj.optInt("complexityRisk", 42),
                extractedEntities = entities,
                narrativeHooks = jsonArrStrings(dObj.optJSONArray("narrativeHooks")),
                missingPillars = jsonArrStrings(dObj.optJSONArray("missingPillars")),
                recommendations = listOf(
                    ConfigRecommendation(
                        title = "Enable Lexicon & Dynamic Triggers",
                        rationale = "Adds authentic cultural idioms and active '{{char}} will instantly' behavioral hooks.",
                        impact = "HIGH",
                        moduleToEnable = ForgeModule.LEXICON
                    )
                )
            )
        } else if (id == "universe-vaelis-synod") {
            StarterUniverseSeeds.createDefaultUniverses().firstOrNull()?.dnaAnalysis
        } else null

        // Deserialize ForgeBlueprint
        val bpObj = obj.optJSONObject("activeBlueprint")
        val activeBlueprint = if (bpObj != null) {
            val plansArr = bpObj.optJSONArray("plannedEntries")
            val plans = mutableListOf<BlueprintEntryPlan>()
            if (plansArr != null) {
                for (i in 0 until plansArr.length()) {
                    val po = plansArr.optJSONObject(i) ?: continue
                    val mod = runCatching { ForgeModule.valueOf(po.optString("module")) }
                        .getOrDefault(ForgeModule.FOUNDATION)
                    val tpl = runCatching { EntryTemplateType.valueOf(po.optString("templateType")) }
                        .getOrDefault(mod.defaultType)
                    plans.add(
                        BlueprintEntryPlan(
                            id = po.optString("id", "plan_$i"),
                            title = po.optString("title"),
                            module = mod,
                            templateType = tpl,
                            primaryKeys = jsonArrStrings(po.optJSONArray("primaryKeys")),
                            secondaryKeys = jsonArrStrings(po.optJSONArray("secondaryKeys")),
                            shouldMentionEntities = jsonArrStrings(po.optJSONArray("shouldMentionEntities")),
                            briefDirective = po.optString("briefDirective"),
                            isCharacterCardCandidate = po.optBoolean("isCharacterCardCandidate", false),
                            forgedContent = po.optString("forgedContent", ""),
                            isForged = po.optBoolean("isForged", false)
                        )
                    )
                }
            }
            val bpTarget = runCatching {
                ForgeGenerationTarget.valueOf(bpObj.optString("generationTarget", forgeConfig.generationTarget.name))
            }.getOrDefault(forgeConfig.generationTarget)
            val bpConcept = runCatching {
                CardConceptType.valueOf(bpObj.optString("cardConceptType", forgeConfig.cardConceptType.name))
            }.getOrDefault(forgeConfig.cardConceptType)
            val pcObj = bpObj.optJSONObject("plannedPrimaryCard")
            val plannedCard = if (pcObj != null) {
                CharacterCardV2(
                    id = pcObj.optString("id", "primary-card"),
                    name = pcObj.optString("name", name),
                    nickname = pcObj.optString("nickname", ""),
                    roleTitle = pcObj.optString("roleTitle", bpConcept.defaultRoleLabel),
                    factionAffiliation = pcObj.optString("factionAffiliation", ""),
                    description = pcObj.optString("description", ""),
                    personality = pcObj.optString("personality", ""),
                    scenario = pcObj.optString("scenario", ""),
                    firstMes = pcObj.optString("firstMes", ""),
                    mesExample = pcObj.optString("mesExample", ""),
                    creatorNotes = pcObj.optString("creatorNotes", ""),
                    systemPrompt = pcObj.optString("systemPrompt", ""),
                    postHistoryInstructions = pcObj.optString("postHistoryInstructions", ""),
                    alternateGreetings = jsonArrStrings(pcObj.optJSONArray("alternateGreetings")),
                    groupOnlyGreetings = jsonArrStrings(pcObj.optJSONArray("groupOnlyGreetings")),
                    tags = jsonArrStrings(pcObj.optJSONArray("tags")),
                    archetype = pcObj.optString("archetype", bpConcept.label),
                    voiceCadence = pcObj.optString("voiceCadence", ""),
                    hiddenAgenda = pcObj.optString("hiddenAgenda", ""),
                    embedLorebookOnExport = pcObj.optBoolean("embedLorebookOnExport", bpTarget.includesLorebook)
                )
            } else null

            ForgeBlueprint(
                universeTitle = bpObj.optString("universeTitle", name),
                linkingStrategySummary = bpObj.optString("linkingStrategySummary"),
                plannedEntries = plans,
                plannedPrimaryCard = plannedCard,
                generationTarget = bpTarget,
                cardConceptType = bpConcept,
                batchSize = bpObj.optInt("batchSize", 5)
            )
        } else null

        return UniverseProject(
            id = id,
            name = name,
            description = description,
            genre = genre,
            tone = tone,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lorebook = lorebook,
            characterCards = cards,
            wizardState = wizardState,
            forgeConfig = forgeConfig,
            dnaAnalysis = dnaAnalysis,
            activeBlueprint = activeBlueprint
        )
    }
}
