package com.iceja.rts.server.genome

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.SocketType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenomeAnalyzerTest {
    @Test
    fun allTemplatesAreValid() {
        GenomeTemplates.all.forEach { genome ->
            val analysis = GenomeAnalyzer.analyze(genome)
            assertTrue(
                analysis.valid,
                "${genome.name} must be valid, errors=${analysis.errors.map { it.code }}"
            )
        }
    }

    @Test
    fun brainIsRequired() {
        val genome = Genome(
            chassisId = "CRAWLER",
            name = "NoBrain",
            parts = listOf(
                PartPlacement("HEART", SocketType.CORE),
                PartPlacement("SCUTTLER", SocketType.LEFT, mirrored = true)
            )
        )
        val analysis = GenomeAnalyzer.analyze(genome)
        assertFalse(analysis.valid)
        assertTrue(analysis.errors.any { it.code == "NO_BRAIN" })
    }

    @Test
    fun mirroredPartConsumesBothSideSockets() {
        val genome = Genome(
            chassisId = "CRAWLER",
            name = "Mirrored",
            parts = listOf(
                PartPlacement("GANGLION", SocketType.CORE),
                PartPlacement("HEART", SocketType.CORE),
                PartPlacement("SCUTTLER", SocketType.LEFT, mirrored = true),
                PartPlacement("FACET", SocketType.FRONT)
            )
        )
        val analysis = GenomeAnalyzer.analyze(genome)
        val left = analysis.stats.sockets.first { it.type == SocketType.LEFT }
        val right = analysis.stats.sockets.first { it.type == SocketType.RIGHT }
        assertEquals(1, left.used)
        assertEquals(1, right.used)
    }

    @Test
    fun mirroredPartOnTopSocketIsRejected() {
        val genome = Genome(
            chassisId = "CRAWLER",
            name = "BadMirror",
            parts = listOf(
                PartPlacement("GANGLION", SocketType.CORE),
                PartPlacement("HEART", SocketType.CORE),
                PartPlacement("SCUTTLER", SocketType.LEFT, mirrored = true),
                PartPlacement("FACET", SocketType.FRONT, mirrored = true)
            )
        )
        val analysis = GenomeAnalyzer.analyze(genome)
        assertTrue(analysis.errors.any { it.code == "SOCKET_MISMATCH" })
    }

    @Test
    fun derivedStatsArePositive() {
        val genome = GenomeTemplates.all.first()
        val stats = GenomeAnalyzer.analyze(genome).stats
        assertTrue(stats.maxHp > 0)
        assertTrue(stats.costBm > 0)
        assertTrue(stats.speedMmPerTick > 0)
        assertTrue(stats.weapons.isNotEmpty())
    }

    @Test
    fun graphCycleIsRejected() {
        val graph = BehaviorGraph(
            rootId = "a",
            nodes = listOf(
                BehaviorNode("a", "Selector", children = listOf("b")),
                BehaviorNode("b", "Sequence", children = listOf("a"))
            )
        )
        val genome = Genome(
            chassisId = "CRAWLER",
            name = "Cycle",
            parts = GenomeTemplates.all.first().parts,
            behavior = BehaviorSpec(presetId = "BERSERK", graph = graph)
        )
        val analysis = GenomeAnalyzer.analyze(genome)
        assertTrue(analysis.errors.any { it.code == "CYCLE" })
    }

    @Test
    fun catalogContainsAllMvpParts() {
        assertTrue(GameCatalog.parts.size >= 30)
        assertTrue(GameCatalog.chassis.size >= 6)
        assertTrue(GameCatalog.behaviorPresets.size >= 8)
        assertTrue(GameCatalog.maps.size >= 3)
    }
}
