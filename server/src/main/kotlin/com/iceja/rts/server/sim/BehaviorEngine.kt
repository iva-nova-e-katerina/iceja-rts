package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog

data class AiDecision(
    val mode: String,
    val reason: String,
    val targetId: Int,
    val moveTarget: Vec2?,
    val attackTargetId: Int,
    val attackHive: Boolean,
    val fleeFrom: Vec2?,
    val nodeId: String = ""
)

object BehaviorEngine {
    fun decide(
        creature: Creature,
        friends: List<Creature>,
        enemies: List<Creature>,
        ownHive: Hive,
        enemyHive: Hive,
        tick: Int
    ): AiDecision {
        val preset = GameCatalog.presetsById[creature.genome.behavior.presetId]
            ?: GameCatalog.presetsById.getValue("BERSERK")
        val hpPercent = creature.hp * 100 / creature.stats.maxHp.coerceAtLeast(1)
        val energyPercent = creature.energy * 100 / creature.stats.maxEnergy.coerceAtLeast(1)
        val visionRange = creature.stats.visionRangeMm
        val nearestEnemy = nearestEnemy(creature, enemies, visionRange)
        val nearestFriend = nearestFriend(creature, friends)
        val nearestEnemyDistance = nearestEnemy?.let { enemy ->
            IntegerMath.distance(creature.x, creature.y, enemy.x, enemy.y)
        } ?: Int.MAX_VALUE
        val hiveUnderAttack = ownHive.hp < ownHive.maxHp
        val surviveThreshold = 10 + preset.caution / 5

        if (visionRange == 0 && preset.id != "GUARD" && preset.id != "DEFENDER") {
            return holdDecision(ownHive, "NO_SENSORS")
        }

        if (hpPercent <= surviveThreshold && preset.caution >= 30) {
            return AiDecision(
                mode = "SURVIVE",
                reason = "LOW_HP",
                targetId = 0,
                moveTarget = Vec2(ownHive.x, ownHive.y),
                attackTargetId = 0,
                attackHive = false,
                fleeFrom = nearestEnemy?.let { Vec2(it.x, it.y) }
            )
        }

        if (energyPercent <= 5) {
            return AiDecision(
                mode = "REST",
                reason = "LOW_ENERGY",
                targetId = 0,
                moveTarget = Vec2(ownHive.x, ownHive.y),
                attackTargetId = 0,
                attackHive = false,
                fleeFrom = null
            )
        }

        if (preset.id == "SIEGE") {
            return AiDecision(
                mode = "SIEGE",
                reason = "SIEGE_ORDER",
                targetId = enemyHive.id,
                moveTarget = Vec2(enemyHive.x, enemyHive.y),
                attackTargetId = enemyHive.id,
                attackHive = true,
                fleeFrom = null
            )
        }

        if ((preset.id == "GUARD" || preset.id == "DEFENDER") && hiveUnderAttack) {
            val attacker = nearestEnemy ?: return holdDecision(ownHive, "HIVE_UNDER_ATTACK")
            return AiDecision(
                mode = "DEFEND_HIVE",
                reason = "HIVE_UNDER_ATTACK",
                targetId = attacker.id,
                moveTarget = Vec2(attacker.x, attacker.y),
                attackTargetId = attacker.id,
                attackHive = false,
                fleeFrom = null
            )
        }

        if ((preset.id == "GUARD" || preset.id == "DEFENDER") &&
            (nearestEnemy == null || nearestEnemyDistance > 20_000)
        ) {
            return holdDecision(ownHive, "GUARD_POINT")
        }

        if (preset.id == "PACK" && nearestFriend != null) {
            val friendDistance = IntegerMath.distance(
                creature.x,
                creature.y,
                nearestFriend.x,
                nearestFriend.y
            )
            if (friendDistance > 12_000 && nearestEnemy == null) {
                return AiDecision(
                    mode = "REGROUP",
                    reason = "REGROUPING",
                    targetId = 0,
                    moveTarget = Vec2(nearestFriend.x, nearestFriend.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null
                )
            }
        }

        if (nearestEnemy != null) {
            if (preset.id == "SKIRMISHER" && nearestEnemyDistance < 5_000) {
                return AiDecision(
                    mode = "SURVIVE",
                    reason = "KEEP_DISTANCE",
                    targetId = 0,
                    moveTarget = Vec2(ownHive.x, ownHive.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = Vec2(nearestEnemy.x, nearestEnemy.y)
                )
            }
            return AiDecision(
                mode = "ATTACK",
                reason = if (nearestEnemyDistance <= creature.stats.weapons.maxOfOrNull { it.rangeMm } ?: 0) {
                    "TARGET_IN_RANGE"
                } else {
                    "TARGET_VISIBLE"
                },
                targetId = nearestEnemy.id,
                moveTarget = Vec2(nearestEnemy.x, nearestEnemy.y),
                attackTargetId = nearestEnemy.id,
                attackHive = false,
                fleeFrom = null
            )
        }

        if (preset.id == "SCAVENGER") {
            return AiDecision(
                mode = "PATROL",
                reason = "NO_TARGET",
                targetId = 0,
                moveTarget = Vec2((ownHive.x + enemyHive.x) / 2, (ownHive.y + enemyHive.y) / 2),
                attackTargetId = 0,
                attackHive = false,
                fleeFrom = null
            )
        }

        return AiDecision(
            mode = "HUNT",
            reason = "NO_TARGET",
            targetId = enemyHive.id,
            moveTarget = Vec2(enemyHive.x, enemyHive.y),
            attackTargetId = enemyHive.id,
            attackHive = true,
            fleeFrom = null
        )
    }

    private fun holdDecision(ownHive: Hive, reason: String): AiDecision = AiDecision(
        mode = "DEFEND_HIVE",
        reason = reason,
        targetId = 0,
        moveTarget = Vec2(ownHive.x, ownHive.y),
        attackTargetId = 0,
        attackHive = false,
        fleeFrom = null
    )

    private fun nearestEnemy(
        creature: Creature,
        enemies: List<Creature>,
        visionRange: Int
    ): Creature? {
        if (visionRange <= 0) {
            return null
        }
        return enemies
            .asSequence()
            .filter { enemy -> enemy.alive }
            .map { enemy ->
                enemy to IntegerMath.distanceSq(creature.x, creature.y, enemy.x, enemy.y)
            }
            .filter { (_, distanceSq) -> distanceSq <= visionRange.toLong() * visionRange.toLong() }
            .minWithOrNull(
                compareBy<Pair<Creature, Long>> { it.second }
                    .thenBy { it.first.id }
            )
            ?.first
    }

    private fun nearestFriend(creature: Creature, friends: List<Creature>): Creature? =
        friends
            .asSequence()
            .filter { friend -> friend.alive && friend.id != creature.id }
            .map { friend ->
                friend to IntegerMath.distanceSq(creature.x, creature.y, friend.x, friend.y)
            }
            .minWithOrNull(
                compareBy<Pair<Creature, Long>> { it.second }
                    .thenBy { it.first.id }
            )
            ?.first
}
