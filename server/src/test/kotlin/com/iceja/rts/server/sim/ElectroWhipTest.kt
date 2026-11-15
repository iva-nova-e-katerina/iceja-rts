package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElectroWhipTest {
    @Test
    fun whipForcesRandomMovementForFiveSeconds() {
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val stats = GenomeAnalyzer.analyze(brawler).stats
        val simulation = Simulation(
            matchId = "whip",
            seed = 101L,
            config = SimulationConfig(map = GameCatalog.map("duel-arena"), maxTicks = 600, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(0, "Alpha", listOf(ArmyPlacement(brawler, stats, 40_000, 96_000))),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(brawler, stats, 180_000, 20_000)))
            )
        )
        val unit = simulation.creatures().first { it.ownerId == 0 }

        assertTrue(simulation.whipCreature(unit.id, 0))
        assertFalse(simulation.whipCreature(unit.id, 1))
        simulation.step()

        assertEquals("PAIN", unit.aiMode)
        assertEquals("ELECTRO_WHIP", unit.aiReason)
        assertTrue(unit.painTicks > 0)
        assertTrue(unit.desiredX != unit.x || unit.desiredY != unit.y)

        repeat(149) {
            simulation.step()
        }
        assertEquals(0, unit.painTicks)
        assertFalse(unit.aiMode == "PAIN")
    }
}
