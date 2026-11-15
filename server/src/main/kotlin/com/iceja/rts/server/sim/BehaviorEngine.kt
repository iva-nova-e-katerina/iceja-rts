package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.BehaviorPresetDefinition
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.PartFlag

data class AiDecision(
    val mode: String,
    val reason: String,
    val targetId: Int,
    val moveTarget: Vec2?,
    val attackTargetId: Int,
    val attackHive: Boolean,
    val fleeFrom: Vec2?,
    val nodeId: String = "",
    val scavengeTargetId: Int = 0
)

object BehaviorEngine {
    fun decide(
        creature: Creature,
        friends: List<Creature>,
        enemies: List<Creature>,
        corpses: List<Corpse> = emptyList(),
        ownHive: Hive,
        enemyHive: Hive,
        tick: Int,
        enemyHiveVisible: Boolean = false
    ): AiDecision {
        val preset = GameCatalog.presetsById[creature.genome.behavior.presetId]
            ?: GameCatalog.presetsById.getValue("BERSERK")
        val hpPercent = creature.hp * 100 / creature.stats.maxHp.coerceAtLeast(1)
        val energyPercent = creature.energy * 100 / creature.stats.maxEnergy.coerceAtLeast(1)
        val visionRange = creature.stats.visionRangeMm
        val target = selectTarget(creature, friends, enemies, preset, tick)
        val nearestFriend = nearestFriend(creature, friends)
        val targetDistance = target?.let {
            IntegerMath.distance(creature.x, creature.y, it.x, it.y)
        } ?: Int.MAX_VALUE
        val hiveUnderAttack = ownHive.hp < ownHive.maxHp
        val surviveThreshold = 10 + preset.caution / 5
        val guardRadius = if (preset.id == "DEFENDER") 25_000 else 20_000
        val rangedWeapon = creature.stats.weapons
            .filter { it.ranged }
            .maxByOrNull { it.rangeMm }

        if (visionRange == 0 && preset.id != "GUARD" && preset.id != "DEFENDER") {
            return holdDecision(ownHive, "NO_SENSORS")
        }

        if (hpPercent <= surviveThreshold && preset.caution >= 30) {
            return retreatDecision(creature, preset, ownHive, nearestFriend, target, "LOW_HP")
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

        if ((preset.id == "GUARD" || preset.id == "DEFENDER") && hiveUnderAttack) {
            val attacker = target ?: return holdDecision(ownHive, "HIVE_UNDER_ATTACK")
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

        if (preset.id == "SCAVENGER" && creature.stats.flags.contains(PartFlag.SCAVENGER)) {
            val corpse = nearestCorpse(creature, corpses)
            if (corpse != null) {
                return AiDecision(
                    mode = "SCAVENGE",
                    reason = "CORPSE_NEARBY",
                    targetId = corpse.id,
                    moveTarget = Vec2(corpse.x, corpse.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    scavengeTargetId = corpse.id
                )
            }
        }

        if (preset.id == "SIEGE" && enemyHiveVisible) {
            if (target != null && targetDistance < 8_000) {
                return attackDecision(target, "SELF_DEFENSE")
            }
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

        if (preset.id == "SCOUT" && target != null) {
            val weapon = rangedWeapon
            if (weapon != null) {
                val safeDistance = maxOf(4_000, weapon.rangeMm * 35 / 100)
                if (targetDistance < safeDistance) {
                    return kiteDecision(creature, target, weapon.rangeMm)
                }
                if (targetDistance <= weapon.rangeMm) {
                    return attackDecision(target, "SCOUT_POTSHOT")
                }
            }
            return AiDecision(
                mode = "SURVIVE",
                reason = "SCOUT_EVADE",
                targetId = 0,
                moveTarget = Vec2(ownHive.x, ownHive.y),
                attackTargetId = 0,
                attackHive = false,
                fleeFrom = Vec2(target.x, target.y)
            )
        }

        if (preset.id == "SKIRMISHER" && target != null) {
            if (rangedWeapon != null) {
                val idealRange = maxOf(5_000, rangedWeapon.rangeMm * 3 / 4)
                if (targetDistance * 100 < idealRange * 55) {
                    return kiteDecision(creature, target, rangedWeapon.rangeMm)
                }
                if (targetDistance * 100 > idealRange * 130) {
                    return attackDecision(target, "APPROACHING")
                }
                return attackDecision(target, "KEEPING_RANGE")
            }
            if (targetDistance < 5_000) {
                return kiteDecision(creature, target, 5_000)
            }
        }

        if (preset.id == "PACK") {
            if (nearestFriend != null && target == null) {
                val friendDistance = IntegerMath.distance(
                    creature.x,
                    creature.y,
                    nearestFriend.x,
                    nearestFriend.y
                )
                if (friendDistance > 12_000) {
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
        }

        if ((preset.id == "GUARD" || preset.id == "DEFENDER") &&
            (target == null || targetDistance > guardRadius)
        ) {
            return holdDecision(ownHive, "GUARD_POINT")
        }

        if (target != null) {
            if (preset.id == "FLANKER" && targetDistance > 7_000) {
                return flankDecision(creature, target)
            }
            return attackDecision(target, "TARGET_VISIBLE")
        }

        val maxWeaponRange = creature.stats.weapons.maxOfOrNull { it.rangeMm } ?: 0
        if (enemyHiveVisible &&
            maxWeaponRange > 0 &&
            IntegerMath.distance(creature.x, creature.y, enemyHive.x, enemyHive.y) <=
            maxWeaponRange + enemyHive.radiusMm
        ) {
            return AiDecision(
                mode = "ATTACK",
                reason = "HIVE_IN_RANGE",
                targetId = enemyHive.id,
                moveTarget = Vec2(enemyHive.x, enemyHive.y),
                attackTargetId = enemyHive.id,
                attackHive = true,
                fleeFrom = null
            )
        }

        return patrolDecision(creature, ownHive, enemyHive, tick)
    }

    private fun selectTarget(
        creature: Creature,
        friends: List<Creature>,
        enemies: List<Creature>,
        preset: BehaviorPresetDefinition,
        tick: Int
    ): Creature? {
        if (enemies.isEmpty()) {
            return null
        }
        val focusedIds = friends
            .asSequence()
            .filter { it.alive && it.id != creature.id && it.attackTargetId != 0 }
            .map { it.attackTargetId }
            .toSet()
        val attackerId = if (tick - creature.lastDamageTick <= 90) {
            creature.lastAttackerId
        } else {
            0
        }
        return enemies
            .asSequence()
            .filter { it.alive }
            .maxWithOrNull(
                compareBy<Creature> { enemy ->
                    targetScore(creature, enemy, preset, focusedIds, attackerId)
                }
            )
    }

    private fun targetScore(
        creature: Creature,
        enemy: Creature,
        preset: BehaviorPresetDefinition,
        focusedIds: Set<Int>,
        attackerId: Int
    ): Int {
        val distance = IntegerMath.distance(creature.x, creature.y, enemy.x, enemy.y)
        val vision = creature.stats.visionRangeMm.coerceAtLeast(1)
        val hpPercent = enemy.hp * 100 / enemy.stats.maxHp.coerceAtLeast(1)
        var score = 1_000 - (distance * 900 / vision)
        score += (100 - hpPercent) * 4
        if (enemy.id == creature.attackTargetId) {
            score += 260
        }
        if (enemy.id == attackerId) {
            score += 340
        }
        if (focusedIds.contains(enemy.id)) {
            score += if (preset.id == "PACK") 420 else 120
        }
        if (enemy.stats.weapons.any { it.ranged }) {
            score += 140
        }
        if (enemy.stats.flags.contains(PartFlag.SYMBIOTE)) {
            score += 130
        }
        if (enemy.attackTargetId == creature.id) {
            score += 90
        }
        score += (enemy.stats.weapons.maxOfOrNull { it.damage } ?: 0).coerceAtMost(40)
        return score
    }

    private fun attackDecision(target: Creature, reason: String): AiDecision = AiDecision(
        mode = "ATTACK",
        reason = reason,
        targetId = target.id,
        moveTarget = Vec2(target.x, target.y),
        attackTargetId = target.id,
        attackHive = false,
        fleeFrom = null
    )

    private fun kiteDecision(creature: Creature, enemy: Creature, idealRange: Int): AiDecision {
        val dx = if (creature.x == enemy.x) 1 else creature.x - enemy.x
        val dy = if (creature.y == enemy.y) 1 else creature.y - enemy.y
        val length = IntegerMath.isqrt(dx.toLong() * dx + dy.toLong() * dy).coerceAtLeast(1)
        val distance = maxOf(5_000, idealRange * 3 / 4)
        return AiDecision(
            mode = "KITE",
            reason = "KEEPING_RANGE",
            targetId = enemy.id,
            moveTarget = Vec2(
                creature.x + dx * distance / length,
                creature.y + dy * distance / length
            ),
            attackTargetId = 0,
            attackHive = false,
            fleeFrom = Vec2(enemy.x, enemy.y)
        )
    }

    private fun flankDecision(creature: Creature, target: Creature): AiDecision {
        val dx = target.x - creature.x
        val dy = target.y - creature.y
        val length = IntegerMath.isqrt(dx.toLong() * dx + dy.toLong() * dy).coerceAtLeast(1)
        val side = if (creature.id % 2 == 0) 1 else -1
        val offset = 5_000
        return AiDecision(
            mode = "FLANK",
            reason = "FLANKING",
            targetId = target.id,
            moveTarget = Vec2(
                target.x - dy * side * offset / length,
                target.y + dx * side * offset / length
            ),
            attackTargetId = 0,
            attackHive = false,
            fleeFrom = null
        )
    }

    private fun retreatDecision(
        creature: Creature,
        preset: BehaviorPresetDefinition,
        ownHive: Hive,
        nearestFriend: Creature?,
        target: Creature?,
        reason: String
    ): AiDecision {
        val moveTarget = if (preset.id == "PACK" && nearestFriend != null) {
            Vec2(nearestFriend.x, nearestFriend.y)
        } else {
            Vec2(ownHive.x, ownHive.y)
        }
        return AiDecision(
            mode = "SURVIVE",
            reason = reason,
            targetId = 0,
            moveTarget = moveTarget,
            attackTargetId = 0,
            attackHive = false,
            fleeFrom = target?.let { Vec2(it.x, it.y) }
        )
    }

    private fun patrolDecision(
        creature: Creature,
        ownHive: Hive,
        enemyHive: Hive,
        tick: Int
    ): AiDecision = AiDecision(
        mode = "PATROL",
        reason = "NO_TARGET",
        targetId = 0,
        moveTarget = PatrolRoute.point(creature, ownHive, enemyHive, tick),
        attackTargetId = 0,
        attackHive = false,
        fleeFrom = null
    )

    private fun holdDecision(ownHive: Hive, reason: String): AiDecision = AiDecision(
        mode = "DEFEND_HIVE",
        reason = reason,
        targetId = 0,
        moveTarget = Vec2(ownHive.x, ownHive.y),
        attackTargetId = 0,
        attackHive = false,
        fleeFrom = null
    )

    private fun nearestCorpse(creature: Creature, corpses: List<Corpse>): Corpse? {
        val searchRadius = kotlin.math.max(20_000, creature.stats.visionRangeMm)
        return corpses
            .asSequence()
            .map { corpse ->
                corpse to IntegerMath.distanceSq(creature.x, creature.y, corpse.x, corpse.y)
            }
            .filter { (_, distanceSq) -> distanceSq <= searchRadius.toLong() * searchRadius }
            .minWithOrNull(
                compareBy<Pair<Corpse, Long>> { it.second }
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
