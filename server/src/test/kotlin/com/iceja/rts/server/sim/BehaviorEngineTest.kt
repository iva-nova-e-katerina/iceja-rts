package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.genome.PartPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BehaviorEngineTest {
    private val hiveA = Hive(1, 0, 20_000, 64_000, 3_000, 50, 4_000)
    private val hiveB = Hive(2, 1, 108_000, 64_000, 3_000, 50, 4_000)

    @Test
    fun berserkAttacksVisibleEnemy() {
        val berserkGenome = GenomeTemplates.all.first { it.name == "Brawler" }.let { genome ->
            genome.copy(parts = genome.parts + PartPlacement("FACET", SocketType.FRONT))
        }
        val berserk = creature(3, 0, berserkGenome, 60_000, 64_000)
        val enemy = creature(4, 1, GenomeTemplates.all.first { it.name == "Scout" }, 61_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = berserk,
            friends = listOf(berserk),
            enemies = listOf(enemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("ATTACK", decision.mode)
        assertEquals(enemy.id, decision.attackTargetId)
        assertTrue(decision.attackHive.not())
    }

    @Test
    fun guardHoldsPositionWithoutVisibleEnemy() {
        val guard = creature(3, 0, GenomeTemplates.all.first { it.name == "Healer" }, 25_000, 64_000)
        val farEnemy = creature(4, 1, GenomeTemplates.all.first { it.name == "Brawler" }, 100_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = guard,
            friends = listOf(guard),
            enemies = listOf(farEnemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("DEFEND_HIVE", decision.mode)
        assertEquals("GUARD_POINT", decision.reason)
        assertEquals(0, decision.attackTargetId)
    }

    @Test
    fun siegeTargetsEnemyHive() {
        val siege = creature(3, 0, GenomeTemplates.all.first { it.name == "Artillery" }, 60_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = siege,
            friends = listOf(siege),
            enemies = emptyList(),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0,
            enemyHiveVisible = true
        )

        assertEquals("SIEGE", decision.mode)
        assertTrue(decision.attackHive)
        assertEquals(hiveB.id, decision.attackTargetId)
    }

    @Test
    fun berserkPatrolsWithoutVisibleEnemy() {
        val berserk = creature(3, 0, GenomeTemplates.all.first { it.name == "Brawler" }, 60_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = berserk,
            friends = listOf(berserk),
            enemies = emptyList(),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("PATROL", decision.mode)
        assertEquals(0, decision.attackTargetId)
        assertTrue(decision.attackHive.not())
    }

    private fun creature(id: Int, ownerId: Int, genome: Genome, x: Int, y: Int): Creature {
        val stats = GenomeAnalyzer.analyze(genome).stats
        return Creature(
            id = id,
            ownerId = ownerId,
            genomeIndex = 0,
            genome = genome,
            stats = stats,
            x = x,
            y = y
        )
    }
}
