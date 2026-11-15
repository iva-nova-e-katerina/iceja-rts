package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.genome.BehaviorSpec
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.genome.PartPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvancedBehaviorTest {
    private val hiveA = Hive(1, 0, 20_000, 64_000, 3_000, 50, 4_000)
    private val hiveB = Hive(2, 1, 108_000, 64_000, 3_000, 50, 4_000)
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }

    @Test
    fun berserkPrefersWoundedTarget() {
        val berserk = creature(3, 0, brawler, 60_000, 64_000)
        val healthy = creature(4, 1, brawler, 65_000, 64_000)
        val wounded = creature(5, 1, brawler, 68_000, 64_000)
        wounded.hp = 1

        val decision = BehaviorEngine.decide(
            creature = berserk,
            friends = listOf(berserk),
            enemies = listOf(healthy, wounded),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals(wounded.id, decision.attackTargetId)
    }

    @Test
    fun packFocusesAllyTarget() {
        val pack = creature(3, 0, brawler.copy(behavior = BehaviorSpec(presetId = "PACK")), 60_000, 64_000)
        val friend = creature(4, 0, brawler.copy(behavior = BehaviorSpec(presetId = "PACK")), 62_000, 64_000)
        val nearby = creature(5, 1, brawler, 65_000, 64_000)
        val focused = creature(6, 1, brawler, 69_000, 64_000)
        friend.attackTargetId = focused.id

        val decision = BehaviorEngine.decide(
            creature = pack,
            friends = listOf(pack, friend),
            enemies = listOf(nearby, focused),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 10
        )

        assertEquals(focused.id, decision.attackTargetId)
    }

    @Test
    fun rangedSkirmisherKitesWhenEnemyIsTooClose() {
        val skirmisherGenome = Genome(
            chassisId = "CRAWLER",
            name = "Kiter",
            parts = listOf(
                PartPlacement("GANGLION", SocketType.CORE),
                PartPlacement("HEART", SocketType.CORE),
                PartPlacement("SCUTTLER", SocketType.LEFT, mirrored = true),
                PartPlacement("FACET", SocketType.FRONT),
                PartPlacement("ACIDSPIT", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "SKIRMISHER")
        )
        val skirmisher = creature(3, 0, skirmisherGenome, 60_000, 64_000)
        val enemy = creature(4, 1, brawler, 63_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = skirmisher,
            friends = listOf(skirmisher),
            enemies = listOf(enemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("KITE", decision.mode)
        assertEquals(0, decision.attackTargetId)
        assertTrue(decision.moveTarget!!.x < skirmisher.x)
    }

    @Test
    fun flankerApproachesFromTheSide() {
        val flanker = creature(4, 0, brawler.copy(behavior = BehaviorSpec(presetId = "FLANKER")), 60_000, 64_000)
        val enemy = creature(5, 1, brawler, 75_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = flanker,
            friends = listOf(flanker),
            enemies = listOf(enemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("FLANK", decision.mode)
        assertTrue(
            decision.moveTarget!!.x != enemy.x || decision.moveTarget!!.y != enemy.y,
            "flank point should differ from the target position"
        )
    }

    private fun creature(
        id: Int,
        ownerId: Int,
        genome: Genome,
        x: Int,
        y: Int
    ): Creature = Creature(
        id = id,
        ownerId = ownerId,
        genomeIndex = 0,
        genome = genome,
        stats = GenomeAnalyzer.analyze(genome).stats,
        x = x,
        y = y
    )
}
