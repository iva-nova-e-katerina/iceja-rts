package com.iceja.rts.server.match

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.PartVisibility
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenomeRenderFactoryTest {
    @Test
    fun internalAndExternalOrgansAreSeparated() {
        assertEquals(PartVisibility.INTERNAL, GameCatalog.part("GANGLION").visual.visibility)
        assertEquals(PartVisibility.INTERNAL, GameCatalog.part("HEART").visual.visibility)
        assertEquals(PartVisibility.INTERNAL, GameCatalog.part("REGEN").visual.visibility)
        assertEquals(PartVisibility.EXTERNAL, GameCatalog.part("SCUTTLER").visual.visibility)
        assertEquals(PartVisibility.EXTERNAL, GameCatalog.part("SPINE").visual.visibility)
        assertEquals(PartVisibility.EXTERNAL, GameCatalog.part("SPIKETAIL").visual.visibility)
        assertEquals(PartVisibility.EXTERNAL, GameCatalog.part("MANDIBLE").visual.visibility)
    }

    @Test
    fun scoutRenderContainsOnlyExternalParts() {
        val genome = GenomeTemplates.all.first { it.name == "Scout" }
        val stats = GenomeAnalyzer.analyze(genome).stats
        val render = GenomeRenderFactory.build(genome, stats)
        val partIds = render.parts.map { it.partId }.toSet()

        assertTrue(render.bodyRadiusMm > 0)
        assertTrue(partIds.contains("SCUTTLER"))
        assertTrue(partIds.contains("FACET"))
        assertTrue(partIds.contains("SPINE"))
        assertFalse(partIds.contains("GANGLION"))
        assertFalse(partIds.contains("HEART"))
        assertTrue(render.parts.first { it.partId == "SCUTTLER" }.mirrored)
        assertTrue(render.parts.all { part ->
            GameCatalog.part(part.partId).visual.visibility == PartVisibility.EXTERNAL
        })
    }

    @Test
    fun everyCatalogPartHasVisualClassification() {
        GameCatalog.parts.forEach { part ->
            assertTrue(
                part.visual.visibility == PartVisibility.INTERNAL || part.visual.visibility == PartVisibility.EXTERNAL
            )
        }
    }
}
