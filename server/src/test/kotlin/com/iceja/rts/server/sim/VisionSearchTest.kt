package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionSearchTest {
    @Test
    fun unitsDoNotAttackAcrossTheMap() {
        val scout = GenomeTemplates.all.first { it.name == "Scout" }
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val map = GameCatalog.map("duel-arena")
        val simulation = Simulation(
            matchId = "vision-search",
            seed = 99L,
            config = SimulationConfig(map = map, maxTicks = 120, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Alpha",
                    units = listOf(
                        ArmyPlacement(
                            genome = scout,
                            stats = GenomeAnalyzer.analyze(scout).stats,
                            x = map.spawnA.x,
                            y = map.spawnA.y
                        )
                    )
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Beta",
                    units = listOf(
                        ArmyPlacement(
                            genome = brawler,
                            stats = GenomeAnalyzer.analyze(brawler).stats,
                            x = map.spawnB.x,
                            y = map.spawnB.y
                        )
                    )
                )
            )
        )

        val events = mutableListOf<SimEvent>()
        repeat(120) {
            if (simulation.result == null) {
                simulation.step()
                events += simulation.events()
            }
        }

        assertFalse(events.any { event -> event.type == "ATTACK" })
    }

    @Test
    fun unitsEventuallyFindAndFight() {
        val scout = GenomeTemplates.all.first { it.name == "Scout" }
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val map = GameCatalog.map("duel-arena")
        val simulation = Simulation(
            matchId = "vision-contact",
            seed = 100L,
            config = SimulationConfig(map = map, maxTicks = 6_000, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Alpha",
                    units = listOf(
                        ArmyPlacement(
                            genome = scout,
                            stats = GenomeAnalyzer.analyze(scout).stats,
                            x = map.spawnA.x,
                            y = map.spawnA.y
                        )
                    )
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Beta",
                    units = listOf(
                        ArmyPlacement(
                            genome = brawler,
                            stats = GenomeAnalyzer.analyze(brawler).stats,
                            x = map.spawnB.x,
                            y = map.spawnB.y
                        )
                    )
                )
            )
        )

        val events = mutableListOf<SimEvent>()
        repeat(6_000) {
            if (simulation.result == null) {
                simulation.step()
                events += simulation.events()
            }
        }

        assertTrue(events.any { event -> event.type == "ATTACK" }, "units should find each other and fight")
    }
}
