package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.BehaviorGraph
import com.iceja.rts.server.genome.BehaviorNode

data class BehaviorBlackboard(
    val nearestEnemy: Creature?,
    val nearestAlly: Creature?,
    val nearestCorpse: Corpse?,
    val nearestCorpseDistanceMm: Int,
    val ownHive: Hive,
    val enemyHive: Hive,
    val hpPercent: Int,
    val energyPercent: Int,
    val heat: Int,
    val targetDistanceMm: Int,
    val targetHpPercent: Int,
    val enemyCount: Int,
    val allyCount: Int,
    val ownHiveHpPercent: Int,
    val enemyHiveHpPercent: Int,
    val hiveUnderAttack: Boolean,
    val underAttack: Boolean,
    val enemyHiveDistanceMm: Int,
    val enemyHiveVisible: Boolean,
    val tick: Int,
    val lastDamageTick: Int,
    val visionRangeMm: Int
)

object BehaviorGraphInterpreter {
    fun decide(
        graph: BehaviorGraph,
        creature: Creature,
        friends: List<Creature>,
        enemies: List<Creature>,
        corpses: List<Corpse> = emptyList(),
        ownHive: Hive,
        enemyHive: Hive,
        tick: Int,
        random: Xoshiro128,
        enemyHiveVisible: Boolean = false
    ): AiDecision {
        val blackboard = buildBlackboard(
            creature = creature,
            friends = friends,
            enemies = enemies,
            corpses = corpses,
            ownHive = ownHive,
            enemyHive = enemyHive,
            tick = tick,
            enemyHiveVisible = enemyHiveVisible
        )
        val nodesById = graph.nodes.associateBy { it.id }
        val result = Executor(
            nodesById = nodesById,
            creature = creature,
            friends = friends,
            enemies = enemies,
            blackboard = blackboard,
            ownHive = ownHive,
            enemyHive = enemyHive,
            random = random
        ).execute(graph.rootId)
        return result.decision ?: holdDecision(creature, "NO_ACTION", "")
    }

    private fun buildBlackboard(
        creature: Creature,
        friends: List<Creature>,
        enemies: List<Creature>,
        corpses: List<Corpse> = emptyList(),
        ownHive: Hive,
        enemyHive: Hive,
        tick: Int,
        enemyHiveVisible: Boolean
    ): BehaviorBlackboard {
        val visionRange = creature.stats.visionRangeMm
        val nearestEnemy = if (visionRange > 0) {
            enemies
                .asSequence()
                .filter { it.alive && IntegerMath.distanceSq(creature.x, creature.y, it.x, it.y) <= visionRange.toLong() * visionRange }
                .minWithOrNull(
                    compareBy<Creature> { IntegerMath.distanceSq(creature.x, creature.y, it.x, it.y) }
                        .thenBy { it.id }
                )
        } else {
            null
        }
        val nearestAlly = friends
            .asSequence()
            .filter { it.alive && it.id != creature.id }
            .minWithOrNull(
                compareBy<Creature> { IntegerMath.distanceSq(creature.x, creature.y, it.x, it.y) }
                    .thenBy { it.id }
            )
        val targetDistance = nearestEnemy?.let {
            IntegerMath.distance(creature.x, creature.y, it.x, it.y)
        } ?: Int.MAX_VALUE
        val corpseSearchRadius = kotlin.math.max(20_000, visionRange)
        val nearestCorpse = corpses
            .asSequence()
            .map { corpse ->
                corpse to IntegerMath.distanceSq(creature.x, creature.y, corpse.x, corpse.y)
            }
            .filter { (_, distanceSq) ->
                distanceSq <= corpseSearchRadius.toLong() * corpseSearchRadius
            }
            .minWithOrNull(
                compareBy<Pair<Corpse, Long>> { it.second }
                    .thenBy { it.first.id }
            )
            ?.first
        val nearestCorpseDistance = nearestCorpse?.let {
            IntegerMath.distance(creature.x, creature.y, it.x, it.y)
        } ?: Int.MAX_VALUE
        return BehaviorBlackboard(
            nearestEnemy = nearestEnemy,
            nearestAlly = nearestAlly,
            nearestCorpse = nearestCorpse,
            nearestCorpseDistanceMm = nearestCorpseDistance,
            ownHive = ownHive,
            enemyHive = enemyHive,
            hpPercent = creature.hp * 100 / creature.stats.maxHp.coerceAtLeast(1),
            energyPercent = creature.energy * 100 / creature.stats.maxEnergy.coerceAtLeast(1),
            heat = 0,
            targetDistanceMm = targetDistance,
            targetHpPercent = nearestEnemy?.let {
                it.hp * 100 / it.stats.maxHp.coerceAtLeast(1)
            } ?: 0,
            enemyCount = countEnemiesWithin(creature, enemies, visionRange, visionRange),
            allyCount = countAlliesWithin(creature, friends, visionRange),
            ownHiveHpPercent = ownHive.hp * 100 / ownHive.maxHp.coerceAtLeast(1),
            enemyHiveHpPercent = enemyHive.hp * 100 / enemyHive.maxHp.coerceAtLeast(1),
            hiveUnderAttack = ownHive.hp < ownHive.maxHp,
            underAttack = tick - creature.lastDamageTick <= GameCatalog.ruleset.tickRate * 3,
            enemyHiveDistanceMm = IntegerMath.distance(creature.x, creature.y, enemyHive.x, enemyHive.y),
            enemyHiveVisible = enemyHiveVisible,
            tick = tick,
            lastDamageTick = creature.lastDamageTick,
            visionRangeMm = visionRange
        )
    }

    private fun countEnemiesWithin(
        creature: Creature,
        enemies: List<Creature>,
        radiusMm: Int,
        visionRangeMm: Int
    ): Int {
        if (visionRangeMm <= 0) {
            return 0
        }
        val effectiveRadius = kotlin.math.min(radiusMm, visionRangeMm).toLong()
        return enemies.count { enemy ->
            enemy.alive && IntegerMath.distanceSq(creature.x, creature.y, enemy.x, enemy.y) <= effectiveRadius * effectiveRadius
        }
    }

    private fun countAlliesWithin(
        creature: Creature,
        friends: List<Creature>,
        radiusMm: Int
    ): Int {
        if (radiusMm <= 0) {
            return 0
        }
        return friends.count { friend ->
            friend.alive && friend.id != creature.id &&
                IntegerMath.distanceSq(creature.x, creature.y, friend.x, friend.y) <= radiusMm.toLong() * radiusMm
        }
    }

    private data class NodeResult(
        val success: Boolean,
        val decision: AiDecision? = null
    )

    private class Executor(
        private val nodesById: Map<String, BehaviorNode>,
        private val creature: Creature,
        private val friends: List<Creature>,
        private val enemies: List<Creature>,
        private val blackboard: BehaviorBlackboard,
        private val ownHive: Hive,
        private val enemyHive: Hive,
        private val random: Xoshiro128
    ) {
        private var transitions = 0

        fun execute(nodeId: String): NodeResult {
            transitions += 1
            if (transitions > 200) {
                return NodeResult(false)
            }
            val node = nodesById[nodeId] ?: return NodeResult(false)
            return when (node.type) {
                "Selector", "UtilitySelector" -> select(node)
                "Sequence" -> sequence(node)
                "RandomSelector" -> randomSelect(node)
                "Parallel" -> parallel(node)
                "Cooldown" -> cooldown(node)
                "Repeat" -> repeat(node)
                "Inverter" -> invert(node)
                else -> when (node.type) {
                    "HasTarget" -> condition(blackboard.nearestEnemy != null)
                    "TargetInRange" -> condition(
                        blackboard.targetDistanceMm <= param(node, "range", 0) * 1000
                    )
                    "TargetVisible" -> condition(blackboard.nearestEnemy != null)
                    "HpBelow" -> condition(blackboard.hpPercent <= param(node, "pct", 100))
                    "HpAbove" -> condition(blackboard.hpPercent >= param(node, "pct", 0))
                    "EnergyBelow" -> condition(blackboard.energyPercent <= param(node, "pct", 100))
                    "EnergyAbove" -> condition(blackboard.energyPercent >= param(node, "pct", 0))
                    "HeatAbove" -> condition(blackboard.heat >= param(node, "value", 100))
                    "EnemyCount" -> {
                        val count = countEnemies(param(node, "radius", 30) * 1000)
                        condition(count >= param(node, "min", 0) && count <= param(node, "max", 99))
                    }
                    "AllyCount" -> {
                        val count = countAllies(param(node, "radius", 30) * 1000)
                        condition(count >= param(node, "min", 0) && count <= param(node, "max", 99))
                    }
                    "IsUnderAttack" -> condition(
                        blackboard.tick - blackboard.lastDamageTick <= param(node, "seconds", 3) * GameCatalog.ruleset.tickRate
                    )
                    "HiveUnderAttack" -> condition(blackboard.hiveUnderAttack)
                    "HiveHpBelow" -> condition(blackboard.ownHiveHpPercent <= param(node, "pct", 100))
                    "EnemyHiveInRange" -> condition(
                        blackboard.enemyHiveVisible &&
                            blackboard.enemyHiveDistanceMm <= param(node, "range", 0) * 1000
                    )
                    "HasStatus" -> condition(creature.statuses.any { status -> status.type.name == node.text })
                    "TimerElapsed" -> condition(
                        blackboard.tick >= param(node, "seconds", 0) * GameCatalog.ruleset.tickRate
                    )
                    "RandomChance" -> condition(random.nextPercent() < param(node, "pct", 0))
                    "ResourceNearby" -> condition(false)
                    "CorpseNearby" -> condition(
                        blackboard.nearestCorpseDistanceMm <= param(node, "radius", 20) * 1000
                    )
                    "IsRole" -> condition(node.text == creature.genome.behavior.presetId)
                    "HasLineOfSight" -> condition(blackboard.nearestEnemy != null)
                    "AttackTarget" -> attackTarget(node)
                    "MoveTo" -> moveTo(node)
                    "MoveToPoint" -> moveToPoint(node)
                    "AttackMove" -> attackMove(node)
                    "Flee" -> flee(node)
                    "Follow", "Regroup" -> follow(node)
                    "HoldPosition" -> NodeResult(true, holdDecision(creature, "HOLD_POSITION", node.id))
                    "Patrol" -> NodeResult(true, patrolDecision(node))
                    "Guard" -> NodeResult(
                        true,
                        moveDecision(
                            mode = "DEFEND_HIVE",
                            reason = "GUARD_POINT",
                            targetId = 0,
                            moveTarget = Vec2(ownHive.x, ownHive.y),
                            nodeId = node.id
                        )
                    )
                    "Harvest" -> NodeResult(true, searchDecision("HARVEST", "RESOURCE_SEARCH", node.id))
                    "EatCorpse" -> eatCorpseDecision(node)
                    "Wait" -> NodeResult(true, holdDecision(creature, "WAITING", node.id))
                    "UseAbility", "Burrow", "Unburrow", "Reproduce", "Taunt" ->
                        NodeResult(true, holdDecision(creature, "ABILITY_UNAVAILABLE", node.id))
                    else -> NodeResult(false)
                }
            }
        }

        private fun select(node: BehaviorNode): NodeResult {
            for (childId in node.children) {
                val result = execute(childId)
                if (result.success) {
                    return result
                }
            }
            return NodeResult(false)
        }

        private fun sequence(node: BehaviorNode): NodeResult {
            var last = NodeResult(true)
            for (childId in node.children) {
                val result = execute(childId)
                if (!result.success) {
                    return result
                }
                last = result
            }
            return last
        }

        private fun randomSelect(node: BehaviorNode): NodeResult {
            if (node.children.isEmpty()) {
                return NodeResult(false)
            }
            return execute(node.children[random.nextInt(node.children.size)])
        }

        private fun parallel(node: BehaviorNode): NodeResult {
            var firstSuccess: NodeResult? = null
            for (childId in node.children) {
                val result = execute(childId)
                if (result.success && firstSuccess == null) {
                    firstSuccess = result
                }
            }
            return firstSuccess ?: NodeResult(false)
        }

        private fun executeFirstChild(node: BehaviorNode): NodeResult =
            node.children.firstOrNull()?.let { execute(it) } ?: NodeResult(false)

        private fun cooldown(node: BehaviorNode): NodeResult {
            val readyTick = creature.behaviorCooldowns[node.id] ?: 0
            if (blackboard.tick < readyTick) {
                return NodeResult(false)
            }
            val result = executeFirstChild(node)
            if (result.success) {
                creature.behaviorCooldowns[node.id] = blackboard.tick + param(node, "ticks", 30)
            }
            return result
        }

        private fun repeat(node: BehaviorNode): NodeResult {
            val count = param(node, "count", 2).coerceIn(1, 10)
            var last = NodeResult(false)
            repeat(count) {
                val result = executeFirstChild(node)
                if (!result.success) {
                    return result
                }
                last = result
            }
            return last
        }

        private fun invert(node: BehaviorNode): NodeResult {
            val result = executeFirstChild(node)
            return NodeResult(!result.success)
        }

        private fun attackTarget(node: BehaviorNode): NodeResult {
            val target = targetForSelector(node.text)
            return when (target) {
                is Target.Enemy -> NodeResult(true, attackDecision(target.creature, node.id))
                Target.EnemyHive -> NodeResult(true, attackHiveDecision(node.id))
                is Target.Ally, Target.OwnHive -> NodeResult(false)
                null -> NodeResult(false)
            }
        }

        private fun moveTo(node: BehaviorNode): NodeResult {
            val target = targetForSelector(node.text)
            return when (target) {
                is Target.Enemy -> NodeResult(
                    true,
                    moveDecision(
                        mode = "HUNT",
                        reason = "TARGET_VISIBLE",
                        targetId = target.creature.id,
                        moveTarget = Vec2(target.creature.x, target.creature.y),
                        nodeId = node.id
                    )
                )
                Target.EnemyHive -> NodeResult(
                    true,
                    moveDecision(
                        mode = "HUNT",
                        reason = "HIVE_TARGET",
                        targetId = enemyHive.id,
                        moveTarget = Vec2(enemyHive.x, enemyHive.y),
                        nodeId = node.id
                    )
                )
                Target.OwnHive -> NodeResult(
                    true,
                    moveDecision(
                        mode = "DEFEND_HIVE",
                        reason = "RETURN_TO_HIVE",
                        targetId = 0,
                        moveTarget = Vec2(ownHive.x, ownHive.y),
                        nodeId = node.id
                    )
                )
                is Target.Ally -> NodeResult(
                    true,
                    moveDecision(
                        mode = "REGROUP",
                        reason = "REGROUPING",
                        targetId = target.creature.id,
                        moveTarget = Vec2(target.creature.x, target.creature.y),
                        nodeId = node.id
                    )
                )
                null -> NodeResult(false)
            }
        }

        private fun moveToPoint(node: BehaviorNode): NodeResult {
            val x = node.params["x"] ?: ((ownHive.x + enemyHive.x) / 2)
            val y = node.params["y"] ?: ((ownHive.y + enemyHive.y) / 2)
            return NodeResult(
                true,
                moveDecision(
                    mode = "PATROL",
                    reason = "MOVE_TO_POINT",
                    targetId = 0,
                    moveTarget = Vec2(x, y),
                    nodeId = node.id
                )
            )
        }

        private fun attackMove(node: BehaviorNode): NodeResult {
            if (blackboard.nearestEnemy != null) {
                return NodeResult(true, attackDecision(blackboard.nearestEnemy, node.id))
            }
            return NodeResult(true, attackHiveDecision(node.id))
        }

        private fun flee(node: BehaviorNode): NodeResult {
            val from = blackboard.nearestEnemy
            return NodeResult(
                true,
                AiDecision(
                    mode = "SURVIVE",
                    reason = "LOW_HP",
                    targetId = 0,
                    moveTarget = Vec2(ownHive.x, ownHive.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = from?.let { Vec2(it.x, it.y) },
                    nodeId = node.id
                )
            )
        }

        private fun follow(node: BehaviorNode): NodeResult {
            val ally = blackboard.nearestAlly ?: return NodeResult(false)
            return NodeResult(
                true,
                moveDecision(
                    mode = "REGROUP",
                    reason = "REGROUPING",
                    targetId = 0,
                    moveTarget = Vec2(ally.x, ally.y),
                    nodeId = node.id
                )
            )
        }

        private fun patrolDecision(node: BehaviorNode): AiDecision {
            val routePoint = PatrolRoute.point(creature, ownHive, enemyHive, blackboard.tick)
            val x = node.params["x"] ?: routePoint.x
            val y = node.params["y"] ?: routePoint.y
            return moveDecision(
                mode = "PATROL",
                reason = "PATROL",
                targetId = 0,
                moveTarget = Vec2(x, y),
                nodeId = node.id
            )
        }

        private fun searchDecision(mode: String, reason: String, nodeId: String): AiDecision =
            moveDecision(
                mode = mode,
                reason = reason,
                targetId = 0,
                moveTarget = Vec2((ownHive.x + enemyHive.x) / 2, (ownHive.y + enemyHive.y) / 2),
                nodeId = nodeId
            )

        private fun eatCorpseDecision(node: BehaviorNode): NodeResult {
            val corpse = blackboard.nearestCorpse ?: return NodeResult(false)
            return NodeResult(
                true,
                AiDecision(
                    mode = "SCAVENGE",
                    reason = "CORPSE_NEARBY",
                    targetId = corpse.id,
                    moveTarget = Vec2(corpse.x, corpse.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    nodeId = node.id,
                    scavengeTargetId = corpse.id
                )
            )
        }

        private fun attackDecision(target: Creature, nodeId: String): AiDecision {
            val weaponRange = creature.stats.weapons.maxOfOrNull { it.rangeMm } ?: 0
            val distance = IntegerMath.distance(creature.x, creature.y, target.x, target.y)
            return AiDecision(
                mode = "ATTACK",
                reason = if (distance <= weaponRange + target.stats.radiusMm) "TARGET_IN_RANGE" else "TARGET_VISIBLE",
                targetId = target.id,
                moveTarget = Vec2(target.x, target.y),
                attackTargetId = target.id,
                attackHive = false,
                fleeFrom = null,
                nodeId = nodeId
            )
        }

        private fun attackHiveDecision(nodeId: String): AiDecision = AiDecision(
            mode = "SIEGE",
            reason = "SIEGE_ORDER",
            targetId = enemyHive.id,
            moveTarget = Vec2(enemyHive.x, enemyHive.y),
            attackTargetId = enemyHive.id,
            attackHive = true,
            fleeFrom = null,
            nodeId = nodeId
        )

        private fun targetForSelector(selector: String): Target? = when (selector) {
            "enemyHive" -> Target.EnemyHive
            "hive" -> Target.OwnHive
            "ally.nearest" -> blackboard.nearestAlly?.let { Target.Ally(it) }
            "enemy.nearest", "target", "" -> blackboard.nearestEnemy?.let { Target.Enemy(it) }
            else -> blackboard.nearestEnemy?.let { Target.Enemy(it) }
        }

        private fun countEnemies(radiusMm: Int): Int {
            if (blackboard.visionRangeMm <= 0) {
                return 0
            }
            val effectiveRadius = kotlin.math.min(radiusMm, blackboard.visionRangeMm).toLong()
            return enemies.count { enemy ->
                enemy.alive &&
                    IntegerMath.distanceSq(creature.x, creature.y, enemy.x, enemy.y) <= effectiveRadius * effectiveRadius
            }
        }

        private fun countAllies(radiusMm: Int): Int =
            friends.count { friend ->
                friend.alive && friend.id != creature.id &&
                    IntegerMath.distanceSq(creature.x, creature.y, friend.x, friend.y) <= radiusMm.toLong() * radiusMm
            }

        private fun condition(value: Boolean): NodeResult = NodeResult(value)

        private fun param(node: BehaviorNode, key: String, default: Int): Int =
            node.params[key] ?: default
    }

    private sealed class Target {
        data class Enemy(val creature: Creature) : Target()
        data class Ally(val creature: Creature) : Target()
        data object EnemyHive : Target()
        data object OwnHive : Target()
    }

    private fun moveDecision(
        mode: String,
        reason: String,
        targetId: Int,
        moveTarget: Vec2,
        nodeId: String
    ): AiDecision = AiDecision(
        mode = mode,
        reason = reason,
        targetId = targetId,
        moveTarget = moveTarget,
        attackTargetId = 0,
        attackHive = false,
        fleeFrom = null,
        nodeId = nodeId
    )

    private fun holdDecision(creature: Creature, reason: String, nodeId: String): AiDecision = AiDecision(
        mode = "IDLE",
        reason = reason,
        targetId = 0,
        moveTarget = Vec2(creature.x, creature.y),
        attackTargetId = 0,
        attackHive = false,
        fleeFrom = null,
        nodeId = nodeId
    )
}
