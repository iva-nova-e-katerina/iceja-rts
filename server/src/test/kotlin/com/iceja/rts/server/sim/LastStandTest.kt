package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertTrue

class LastStandTest {
    @Test
    fun woundedUnitFightsBackWhenEnemyIsInRange() {
        val scout = GenomeTemplates.all.first { it.name == "Scout" }
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val scoutStats = GenomeAnalyzer.analyze(scout).stats
        val brawlerStats = GenomeAnalyzer.analyze(brawler).stats
        val simulation = Simulation(
            matchId = "last-stand",
            seed = 7L,
            config = SimulationConfig(
                map = GameCatalog.map("duel-arena"),
                maxTicks = 30,
                overtimeTicks = 0
            ),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Wounded",
                    units = listOf(ArmyPlacement(scout, scoutStats, 60_000, 110_000))
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Finisher",
                    units = listOf(ArmyPlacement(brawler, brawlerStats, 70_000, 110_000))
                )
            )
        )
        simulation.creatures().first { it.ownerId == 0 }.hp = 1

        val events = mutableListOf<SimEvent>()
        repeat(8) {
            simulation.step()
            events += simulation.events()
        }

        val foughtBack = events.any { event -> event.type == "ATTACK" && event.sourceId == 3 }
        assertTrue(foughtBack, "wounded unit should counterattack instead of only fleeing")
    }
}
