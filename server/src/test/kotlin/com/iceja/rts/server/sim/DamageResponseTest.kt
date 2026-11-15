package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DamageResponseTest {
    private val map = GameCatalog.map("duel-arena").copy(objectives = emptyList(), hazards = emptyList())
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }

    @Test
    fun hitFromBehindTriggersCounterattack() {
        val simulation = Simulation(
            matchId = "damage-response",
            seed = 31L,
            config = SimulationConfig(map = map, maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Target",
                    units = listOf(ArmyPlacement(brawler, stats(), 60_000, 110_000))
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Attacker",
                    units = listOf(ArmyPlacement(brawler, stats(), 58_000, 110_000))
                )
            )
        )
        val target = simulation.creatures().first { it.ownerId == 0 }
        val attacker = simulation.creatures().first { it.ownerId == 1 }
        target.angle = 0
        attacker.angle = 0

        val events = mutableListOf<SimEvent>()
        repeat(24) {
            simulation.step()
            events += simulation.events()
        }

        assertEquals(attacker.id, target.lastAttackerId)
        assertTrue(
            events.any { it.type == "ATTACK" && it.sourceId == target.id && it.targetId == attacker.id },
            "target should counterattack the attacker"
        )
    }

    @Test
    fun counterattackDoesNotReplaceCurrentTarget() {
        val simulation = Simulation(
            matchId = "damage-response-engaged",
            seed = 32L,
            config = SimulationConfig(map = map, maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Target",
                    units = listOf(ArmyPlacement(brawler, stats(), 60_000, 110_000))
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Enemies",
                    units = listOf(
                        ArmyPlacement(brawler, stats(), 66_000, 110_000),
                        ArmyPlacement(brawler, stats(), 59_000, 110_000)
                    )
                )
            )
        )
        val target = simulation.creatures().first { it.ownerId == 0 }
        val visibleEnemy = simulation.creatures().first { it.ownerId == 1 && it.x == 66_000 }
        val hiddenAttacker = simulation.creatures().first { it.ownerId == 1 && it.x == 59_000 }
        target.angle = 0
        visibleEnemy.angle = 32_768
        hiddenAttacker.angle = 0

        repeat(24) {
            simulation.step()
        }

        assertEquals(hiddenAttacker.id, target.lastAttackerId)
        assertEquals(visibleEnemy.id, target.attackTargetId)
        assertEquals("ATTACK", target.aiMode)
    }

    private fun stats() = GenomeAnalyzer.analyze(brawler).stats
}
