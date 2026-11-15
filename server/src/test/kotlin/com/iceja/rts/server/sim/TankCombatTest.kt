package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertTrue

class TankCombatTest {
    @Test
    fun tankFiresAtVisibleTarget() {
        val tank = GenomeTemplates.all.first { it.name == "Tank" }
        val scout = GenomeTemplates.all.first { it.name == "Scout" }
        val tankStats = GenomeAnalyzer.analyze(tank).stats
        val scoutStats = GenomeAnalyzer.analyze(scout).stats
        val simulation = Simulation(
            matchId = "tank-fire",
            seed = 42L,
            config = SimulationConfig(
                map = GameCatalog.map("duel-arena"),
                maxTicks = 300,
                overtimeTicks = 0
            ),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Tank",
                    units = listOf(ArmyPlacement(tank, tankStats, 60_000, 64_000))
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Target",
                    units = listOf(ArmyPlacement(scout, scoutStats, 61_000, 64_000))
                )
            )
        )

        val events = mutableListOf<SimEvent>()
        repeat(300) {
            if (simulation.result == null) {
                simulation.step()
                events += simulation.events()
            }
        }

        val fired = events.any { event ->
            event.type == "ATTACK" && event.sourceId == 3 && event.text.startsWith("RANGED")
        }
        val attackCount = events.count { it.type == "ATTACK" && it.sourceId == 3 }
        assertTrue(fired, "tank should fire a ranged weapon, attack events=$attackCount")
    }
}
