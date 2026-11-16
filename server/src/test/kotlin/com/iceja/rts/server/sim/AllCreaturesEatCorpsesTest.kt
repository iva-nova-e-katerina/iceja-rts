package com.iceja.rts.server.sim

import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllCreaturesEatCorpsesTest {
    private val hiveA = Hive(1, 0, 20_000, 64_000, 3_000, 50, 4_000)
    private val hiveB = Hive(2, 1, 108_000, 64_000, 3_000, 50, 4_000)
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
    private val scout = GenomeTemplates.all.first { it.name == "Scout" }

    @Test
    fun nonScavengerDoesNotEatWhileEnemyIsVisible() {
        val creature = creature(3, 0, brawler, 60_000, 64_000)
        creature.energy = 20
        creature.hp = creature.stats.maxHp / 2
        val enemy = creature(4, 1, brawler, 65_000, 64_000)
        val corpse = Corpse(50_001, 1, 0, 65_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = creature,
            friends = listOf(creature),
            enemies = listOf(enemy),
            corpses = listOf(corpse),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("ATTACK", decision.mode)
        assertEquals(0, decision.scavengeTargetId)
    }

    @Test
    fun woundedCreatureEatsCorpseInsteadOfSeekingNectar() {
        val creature = creature(3, 0, brawler, 60_000, 64_000)
        creature.hp = creature.stats.maxHp / 5
        val corpse = Corpse(50_006, 1, 0, 65_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = creature,
            friends = listOf(creature),
            enemies = emptyList(),
            corpses = listOf(corpse),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 30
        )

        assertEquals("SCAVENGE", decision.mode)
        assertEquals(corpse.id, decision.scavengeTargetId)
    }

    @Test
    fun fullHealthCreatureDoesNotEatCorpse() {
        val creature = creature(3, 0, brawler, 60_000, 64_000)
        val corpse = Corpse(50_005, 1, 0, 65_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = creature,
            friends = listOf(creature),
            enemies = emptyList(),
            corpses = listOf(corpse),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 30
        )

        assertEquals("PATROL", decision.mode)
        assertEquals(0, decision.scavengeTargetId)
    }

    @Test
    fun nonScavengerEatsCorpseInCalmSituation() {
        val creature = creature(3, 0, brawler, 60_000, 64_000)
        creature.energy = 20
        creature.hp = creature.stats.maxHp / 2
        val corpse = Corpse(50_003, 1, 0, 65_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = creature,
            friends = listOf(creature),
            enemies = emptyList(),
            corpses = listOf(corpse),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 30
        )

        assertEquals("SCAVENGE", decision.mode)
        assertEquals(corpse.id, decision.scavengeTargetId)
    }

    @Test
    fun creatureDoesNotEatImmediatelyAfterTakingDamage() {
        val creature = creature(3, 0, brawler, 60_000, 64_000)
        creature.energy = 20
        creature.hp = creature.stats.maxHp / 2
        creature.lastDamageTick = 20
        val corpse = Corpse(50_004, 1, 0, 65_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = creature,
            friends = listOf(creature),
            enemies = emptyList(),
            corpses = listOf(corpse),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 30
        )

        assertEquals("PATROL", decision.mode)
    }

    @Test
    fun scoutCanEatCorpseWhenLowOnEnergy() {
        val creature = creature(3, 0, scout, 60_000, 64_000)
        creature.energy = 15
        creature.hp = creature.stats.maxHp / 2
        val corpse = Corpse(50_002, 1, 0, 64_000, 64_000)

        val decision = BehaviorEngine.decide(
            creature = creature,
            friends = listOf(creature),
            enemies = emptyList(),
            corpses = listOf(corpse),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0
        )

        assertEquals("SCAVENGE", decision.mode)
    }

    private fun creature(id: Int, ownerId: Int, genome: com.iceja.rts.server.genome.Genome, x: Int, y: Int): Creature {
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

class CalmCorpseEatingSimulationTest {
    @Test
    fun fullHealthBrawlerDoesNotEatCorpseAfterCombat() {
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val swarm = GenomeTemplates.all.first { it.name == "Swarm" }
        val brawlerStats = GenomeAnalyzer.analyze(brawler).stats
        val swarmStats = GenomeAnalyzer.analyze(swarm).stats
        val simulation = Simulation(
            matchId = "calm-corpse",
            seed = 131L,
            config = SimulationConfig(
                map = com.iceja.rts.server.catalog.GameCatalog.map("duel-arena"),
                maxTicks = 1_500,
                overtimeTicks = 0
            ),
            players = listOf(
                PlayerSetup(0, "Alpha", listOf(ArmyPlacement(brawler, brawlerStats, 60_000, 64_000))),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(swarm, swarmStats, 61_000, 64_000)))
            )
        )
        val events = mutableListOf<SimEvent>()
        repeat(1_500) {
            simulation.step()
            events += simulation.events()
        }
        val brawlerUnit = simulation.creatures().first { it.ownerId == 0 }
        if (brawlerUnit.hp >= brawlerUnit.stats.maxHp) {
            assertTrue(
                events.none { it.type == "EAT" || (it.type == "RESOURCE" && it.text == "CORPSE") },
                "a full-health creature should not eat a corpse"
            )
        }
    }
}
