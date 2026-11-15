package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.PartFlag
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelepathyTest {
    private val map = GameCatalog.map("duel-arena")
    private val scout = GenomeTemplates.all.first { it.name == "Scout" }
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }

    @Test
    fun scoutHasExpensiveTelepathicNode() {
        val node = GameCatalog.partsById.getValue("TELEPATHIC_NODE")
        val scoutStats = GenomeAnalyzer.analyze(scout).stats

        assertTrue(node.flags.contains(PartFlag.TELEPATHIC))
        assertTrue(node.cost >= 200)
        assertTrue(scoutStats.flags.contains(PartFlag.TELEPATHIC))
    }

    @Test
    fun telepathCallsAllyToBattle() {
        val simulation = simulation(scout)
        val events = mutableListOf<SimEvent>()
        repeat(30) {
            simulation.step()
            events += simulation.events()
        }

        val ally = simulation.creatures().first { it.id == 4 }
        assertTrue(events.any { it.type == "TELEPATHY" })
        assertEquals("RESPOND", ally.aiMode)
        assertEquals("TELEPATHIC_CALL", ally.aiReason)
        assertTrue(ally.x > 40_000)
    }

    @Test
    fun withoutNodeThereIsNoCall() {
        val noNode = scout.copy(parts = scout.parts.filterNot { it.partId == "TELEPATHIC_NODE" })
        val simulation = simulation(noNode)
        val events = mutableListOf<SimEvent>()
        repeat(30) {
            simulation.step()
            events += simulation.events()
        }

        val ally = simulation.creatures().first { it.id == 4 }
        assertFalse(events.any { it.type == "TELEPATHY" })
        assertFalse(ally.aiMode == "RESPOND")
    }

    private fun simulation(scoutGenome: com.iceja.rts.server.genome.Genome): Simulation {
        val scoutStats = GenomeAnalyzer.analyze(scoutGenome).stats
        val brawlerStats = GenomeAnalyzer.analyze(brawler).stats
        return Simulation(
            matchId = "telepathy",
            seed = 5L,
            config = SimulationConfig(map = map, maxTicks = 600, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Callers",
                    units = listOf(
                        ArmyPlacement(scoutGenome, scoutStats, 60_000, 110_000),
                        ArmyPlacement(brawler, brawlerStats, 40_000, 110_000)
                    )
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Target",
                    units = listOf(
                        ArmyPlacement(brawler, brawlerStats, 70_000, 110_000)
                    )
                )
            )
        )
    }
}
