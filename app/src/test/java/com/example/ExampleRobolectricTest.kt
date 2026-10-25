package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LorebookAndCardCodec
import com.example.data.StarterUniverseSeeds
import com.example.domain.ActivationSimulatorEngine
import com.example.domain.CrossRefAndAuditEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LoreMaster Flex", appName)
    }

    @Test
    fun `starter universe cross-references and recursive simulator work end-to-end`() {
        val vaelis = StarterUniverseSeeds.createDefaultUniverses().first()
        val mentions = CrossRefAndAuditEngine.scanCrossReferences(vaelis)
        assertTrue("Expected cross-references in Vaelis universe", mentions.isNotEmpty())

        val sim = ActivationSimulatorEngine.simulateActivation(
            project = vaelis,
            sampleInput = "Seraphina Vance steps into the Sunken Ward carrying a contraband chime.",
            maxRecursionDepth = 3
        )
        assertEquals(2, sim.constantEntries.size)
        assertTrue("Expected direct and recursive activations", sim.activatedSteps.size >= 3)
        assertTrue("Expected recursive Depth 1+ step", sim.activatedSteps.any { it.depthLevel >= 1 })
    }

    @Test
    fun `character card PNG tEXt chunk and Lorebook V2 JSON round-trip cleanly`() {
        val vaelis = StarterUniverseSeeds.createDefaultUniverses().first()
        val card = vaelis.characterCards.first()

        val pngBytes = LorebookAndCardCodec.exportCharacterCardPngBytes(card, vaelis.lorebook)
        val extractedJson = LorebookAndCardCodec.extractCharaJsonFromPng(pngBytes)
        assertNotNull("Expected embedded chara JSON in PNG tEXt chunk", extractedJson)

        val (embeddedBook, parsedCard) = LorebookAndCardCodec.parseImportedJson(extractedJson!!)
        assertNotNull(parsedCard)
        assertEquals(card.name, parsedCard?.name)
        assertNotNull(embeddedBook)
        assertEquals(vaelis.lorebook.entries.size, embeddedBook?.entries?.size)
    }
}
