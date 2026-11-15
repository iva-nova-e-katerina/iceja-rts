package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoutBehaviorTest {
    private val map = GameCatalog.map("duel-arena")
    private val scout = GenomeTemplates.all.first { it.name == "Scout" }
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }

    @Test
    fun scoutExploresMapWhilePatrolling() {
        val simulation = simulation(
            alpha = listOf(placement(scout, 40_000, 96_000)),
            beta = listOf(placement(brawler, 180_000, 20_000))
        )

        repeat(12) {
            simulation.step()
        }

        val unit = simulation.creatures().first { it.ownerId == 0 }
        assertEquals("SCOUT", unit.aiMode)
        assertEquals("RECON", unit.aiReason)
        assertTrue(unit.scoutVisitedCells.isNotEmpty())
        assertTrue(unit.desiredX != unit.x || unit.desiredY != unit.y)
    }

    @Test
    fun scoutRetreatsAndCallsForHelpWhenOutnumbered() {
        val simulation = simulation(
            alpha = listOf(
                placement(scout, 60_000, 110_000),
                placement(brawler, 30_000, 110_000)
            ),
            beta = listOf(
                placement(brawler, 66_000, 110_000),
                placement(brawler, 64_000, 106_000)
            )
        )
        val events = mutableListOf<SimEvent>()
        repeat(8) {
            simulation.step()
            events += simulation.events()
        }

        val scoutUnit = simulation.creatures().first { it.id == 3 }
        val ally = simulation.creatures().first { it.id == 4 }
        assertEquals("RETREAT", scoutUnit.aiMode)
        assertEquals("OUTNUMBERED", scoutUnit.aiReason)
        assertTrue(events.any { it.type == "TELEPATHY" && it.text == "HELP" })
        assertEquals("RESPOND", ally.aiMode)
    }

    private fun simulation(alpha: List<ArmyPlacement>, beta: List<ArmyPlacement>): Simulation =
        Simulation(
            matchId = "scout-test",
            seed = 83L,
            config = SimulationConfig(map = map, maxTicks = 600, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(0, "Alpha", alpha),
                PlayerSetup(1, "Beta", beta)
            )
        )

    private fun placement(
        genome: com.iceja.rts.server.genome.Genome,
        x: Int,
        y: Int
    ): ArmyPlacement = ArmyPlacement(
        genome = genome,
        stats = GenomeAnalyzer.analyze(genome).stats,
        x = x,
        y = y
    )
}
