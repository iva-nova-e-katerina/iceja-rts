package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.DamageType
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.MapDefinition
import com.iceja.rts.server.catalog.PartFlag
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.PresetGraphs
import com.iceja.rts.server.genome.WeaponStats
import java.util.concurrent.ConcurrentLinkedQueue

class Simulation(
    val matchId: String,
    val seed: Long,
    val config: SimulationConfig,
    val players: List<PlayerSetup>
) {
    private val navigation = NavigationGrid(config.map)
    private val worldRandom = Xoshiro128.fromSeeds(seed, 1L)
    private val combatRandom = Xoshiro128.fromSeeds(seed, 2L)
    private val aiRandom = Xoshiro128.fromSeeds(seed, 3L)
    private val mapWidth = config.map.widthMm
    private val mapHeight = config.map.heightMm
    private val hives: List<Hive> = listOf(
        Hive(
            id = 1,
            ownerId = 0,
            x = config.map.spawnA.x,
            y = config.map.spawnA.y,
            maxHp = GameCatalog.ruleset.hiveHp,
            armor = GameCatalog.ruleset.hiveArmor,
            radiusMm = GameCatalog.ruleset.hiveRadiusMm
        ),
        Hive(
            id = 2,
            ownerId = 1,
            x = config.map.spawnB.x,
            y = config.map.spawnB.y,
            maxHp = GameCatalog.ruleset.hiveHp,
            armor = GameCatalog.ruleset.hiveArmor,
            radiusMm = GameCatalog.ruleset.hiveRadiusMm
        )
    )
    private val objectives = config.map.objectives.map { definition ->
        ObjectivePoint(
            id = definition.id,
            name = definition.name,
            x = definition.x,
            y = definition.y,
            radiusMm = definition.radiusMm,
            captureTicks = definition.captureTicks,
            scorePerSecond = definition.scorePerSecond,
            healPerSecond = definition.healPerSecond
        )
    }
    private val controlScores = IntArray(2)
    private val creatures = mutableListOf<Creature>()
    private val projectiles = mutableListOf<Projectile>()
    private val corpses = mutableListOf<Corpse>()
    private val eventBuffer = mutableListOf<SimEvent>()
    private val telepathicAlerts = arrayOfNulls<TelepathicAlert>(2)
    private val lastTelepathyTick = IntArray(2)
    private val lastHiveCallTick = IntArray(2)
    private val enemyHiveKnown = BooleanArray(2)
    private val pendingWhips = ConcurrentLinkedQueue<Pair<Int, Int>>()
    private val pendingMoveOrders = ConcurrentLinkedQueue<MoveOrder>()
    private val damageByPlayer = LongArray(2)
    private val killsByPlayer = IntArray(2)
    private val lossesByPlayer = IntArray(2)
    private val genomes: List<Genome>
    private var nextProjectileId = 100_000
    private var nextCorpseId = 50_000
    private var nextEventId = 1L

    var tick: Int = 0
        private set
    var result: MatchResult? = null
        private set

    init {
        require(players.size == 2) { "Exactly two players are required" }
        genomes = players.flatMap { player -> player.units.map { unit -> unit.genome } }.distinct()
        spawnCreatures()
    }

    fun genomes(): List<Genome> = genomes

    fun hives(): List<Hive> = hives

    fun creatures(): List<Creature> = creatures

    fun livingCreatures(ownerId: Int): List<Creature> =
        creatures.filter { it.alive && it.ownerId == ownerId }

    fun moveCreature(entityId: Int, ownerId: Int, targetX: Int, targetY: Int): Boolean {
        val target = creatureById(entityId) ?: return false
        if (!target.alive || target.ownerId != ownerId) {
            return false
        }
        pendingMoveOrders += MoveOrder(
            entityId = entityId,
            ownerId = ownerId,
            x = targetX.coerceIn(1_000, mapWidth - 1_000),
            y = targetY.coerceIn(1_000, mapHeight - 1_000)
        )
        return true
    }

    fun whipCreature(entityId: Int, ownerId: Int): Boolean {
        val target = creatureById(entityId) ?: return false
        if (!target.alive || target.ownerId != ownerId) {
            return false
        }
        pendingWhips += entityId to ownerId
        return true
    }

    fun projectiles(): List<Projectile> = projectiles

    fun corpses(): List<Corpse> = corpses

    fun events(): List<SimEvent> = eventBuffer

    fun step() {
        if (result != null) {
            return
        }
        eventBuffer.clear()
        tick += 1
        if (tick > config.maxTicks + config.overtimeTicks) {
            finishTimeout()
            return
        }
        processMoveOrders()
        processWhipCommands()
        creatures.filter { it.alive && it.painTicks > 0 }.forEach { it.painTicks -= 1 }
        creatures.filter { it.alive && it.manualOrderTicks > 0 }.forEach { it.manualOrderTicks -= 1 }
        updateAi()
        updateMovement()
        resolveCollisions()
        updateObjectives()
        applyHazards()
        resolveCombat()
        updateProjectiles()
        applyStatusEffects()
        updateEnergy()
        updateRegeneration()
        updateScavenging()
        updateCorpses()
        removeDead()
        checkVictory()
    }

    fun snapshot(serverTimeMs: Long = 0L): Snapshot {
        val checksum = stateHash()
        val entities = creatures.map { creature ->
            EntitySnapshot(
                id = creature.id,
                kind = "CREATURE",
                owner = creature.ownerId,
                x = creature.x,
                y = creature.y,
                angle = creature.angle,
                hp = creature.hp.coerceIn(0, 65_535),
                maxHp = creature.stats.maxHp.coerceIn(0, 65_535),
                energy = creature.energy.coerceIn(0, 65_535),
                flags = entityFlags(creature),
                state = creature.state.code,
                targetId = creature.attackTargetId,
                genomeId = creature.genomeIndex,
                mode = creature.aiMode,
                reason = creature.aiReason,
                nodeId = creature.aiNodeId
            )
        }
        val projectileSnapshots = projectiles.map { projectile ->
            ProjectileSnapshot(
                id = projectile.id,
                owner = projectile.ownerId,
                x = projectile.x,
                y = projectile.y,
                angle = projectile.angle,
                kind = if (projectile.hit) 1 else 0,
                ttl = projectile.ttlTicks.coerceAtLeast(0)
            )
        }
        val corpseSnapshots = corpses.map { corpse ->
            CorpseSnapshot(
                id = corpse.id,
                owner = corpse.ownerId,
                x = corpse.x,
                y = corpse.y,
                genomeId = corpse.genomeIndex,
                remainingTicks = corpse.remainingTicks.coerceAtLeast(0)
            )
        }
        val hiveSnapshots = hives.map { hive ->
            HiveSnapshot(
                id = hive.id,
                owner = hive.ownerId,
                x = hive.x,
                y = hive.y,
                hp = hive.hp.coerceIn(0, 65_535),
                maxHp = hive.maxHp.coerceIn(0, 65_535),
                flags = if (hive.alive) 1 else 0
            )
        }
        val objectiveSnapshots = objectives.map { objective ->
            ObjectiveSnapshot(
                id = objective.id,
                name = objective.name,
                x = objective.x,
                y = objective.y,
                radiusMm = objective.radiusMm,
                owner = objective.ownerId,
                progress = objective.progress.coerceIn(0, objective.captureTicks),
                captureTicks = objective.captureTicks,
                contested = objective.contested
            )
        }
        return Snapshot(
            tick = tick,
            serverTimeMs = serverTimeMs,
            matchId = matchId,
            entities = entities,
            projectiles = projectileSnapshots,
            corpses = corpseSnapshots,
            hives = hiveSnapshots,
            objectives = objectiveSnapshots,
            scores = listOf(
                damageByPlayer[0].coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                damageByPlayer[1].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            ),
            controlScores = listOf(controlScores[0], controlScores[1]),
            flags = if (result == null) 0 else 1,
            checksum = checksum
        )
    }

    fun stateHash(): Long {
        var hash = -3_750_763_034_362_895_579L
        fun mix(value: Long) {
            hash = (hash xor value) * 1_099_511_628_211L
        }
        mix(tick.toLong())
        mix((result?.winner ?: -2).toLong())
        hives.forEach { hive ->
            mix(hive.id.toLong())
            mix(hive.hp.toLong())
            mix(if (hive.alive) 1L else 0L)
        }
        objectives.forEach { objective ->
            mix(objective.ownerId.toLong())
            mix(objective.progress.toLong())
            mix(if (objective.contested) 1L else 0L)
        }
        mix(controlScores[0].toLong())
        mix(controlScores[1].toLong())
        creatures.forEach { creature ->
            mix(creature.id.toLong())
            mix(creature.x.toLong())
            mix(creature.y.toLong())
            mix(creature.hp.toLong())
            mix(creature.energy.toLong())
            mix(creature.attackCooldownTicks.toLong())
            mix(creature.state.code.toLong())
            mix(creature.attackTargetId.toLong())
        }
        projectiles.forEach { projectile ->
            mix(projectile.id.toLong())
            mix(projectile.x.toLong())
            mix(projectile.y.toLong())
            mix(projectile.ttlTicks.toLong())
        }
        corpses.forEach { corpse ->
            mix(corpse.id.toLong())
            mix(corpse.x.toLong())
            mix(corpse.y.toLong())
            mix(corpse.remainingTicks.toLong())
        }
        return hash
    }

    private fun spawnCreatures() {
        var entityId = 3
        players.forEach { player ->
            val hive = hiveFor(player.playerId)
            player.units.forEachIndexed { index, unit ->
                val fallback = defaultSpawnPosition(player.playerId, index, hive)
                val x = if (unit.x == 0 && unit.y == 0) fallback.x else unit.x.coerceIn(1_000, mapWidth - 1_000)
                val y = if (unit.x == 0 && unit.y == 0) fallback.y else unit.y.coerceIn(1_000, mapHeight - 1_000)
                val creature = Creature(
                    id = entityId,
                    ownerId = player.playerId,
                    genomeIndex = genomes.indexOf(unit.genome),
                    genome = unit.genome,
                    stats = unit.stats,
                    x = x,
                    y = y,
                    canFly = GameCatalog.chassisById[unit.genome.chassisId]?.canFly ?: false,
                    sourceCreatureId = unit.sourceCreatureId
                )
                creature.desiredX = x
                creature.desiredY = y
                creature.fleeFromX = x
                creature.fleeFromY = y
                creature.angle = if (player.playerId == 0) 0 else 32_768
                creatures += creature
                emit(
                    type = "SPAWN",
                    sourceId = creature.id,
                    x = x,
                    y = y,
                    value = creature.genomeIndex,
                    text = unit.genome.name
                )
                entityId += 1
            }
        }
    }

    private fun defaultSpawnPosition(playerId: Int, index: Int, hive: Hive): Vec2 {
        val direction = if (playerId == 0) 1 else -1
        val column = index % 6
        val row = index / 6
        val x = hive.x + direction * (4_000 + column * 2_500)
        val y = hive.y + (row - 2) * 3_000
        return Vec2(
            x = x.coerceIn(1_000, mapWidth - 1_000),
            y = y.coerceIn(1_000, mapHeight - 1_000)
        )
    }

    private fun updateAi() {
        creatures.filter { it.alive }.forEach { creature ->
            val shouldDecide = creature.lastDecisionTick == 0 ||
                (creature.id + tick) % 3 == 0
            if (!shouldDecide) {
                return@forEach
            }
            if (creature.manualOrderTicks > 0) {
                val distanceSq = IntegerMath.distanceSq(
                    creature.x,
                    creature.y,
                    creature.manualTargetX,
                    creature.manualTargetY
                )
                if (distanceSq <= 50L * 50) {
                    creature.manualOrderTicks = 0
                } else {
                    creature.aiMode = "MANUAL"
                    creature.aiReason = "PLAYER_ORDER"
                    creature.aiNodeId = "player:move-order"
                    creature.targetId = 0
                    creature.attackTargetId = 0
                    creature.attackHive = false
                    creature.scavengeTargetId = 0
                    creature.desiredX = creature.manualTargetX
                    creature.desiredY = creature.manualTargetY
                    creature.lastDecisionTick = tick
                    return@forEach
                }
            }
            if (creature.painTicks > 0) {
                val distanceSq = IntegerMath.distanceSq(
                    creature.x,
                    creature.y,
                    creature.painTargetX,
                    creature.painTargetY
                )
                if (distanceSq <= 2_000L * 2_000) {
                    choosePainTarget(creature)
                }
                creature.aiMode = "PAIN"
                creature.aiReason = "ELECTRO_WHIP"
                creature.aiNodeId = "pain:electro-whip"
                creature.targetId = 0
                creature.attackTargetId = 0
                creature.attackHive = false
                creature.scavengeTargetId = 0
                creature.desiredX = creature.painTargetX
                creature.desiredY = creature.painTargetY
                creature.lastDecisionTick = tick
                return@forEach
            }
            val friends = creatures.filter { it.ownerId == creature.ownerId }
            val enemies = creatures.filter { it.ownerId != creature.ownerId }
            val visibleEnemies = enemies.filter { enemy -> Vision.canSee(creature, enemy, navigation) }
            val scout = isScout(creature)
            val outnumbered = scout && visibleEnemies.size > 1
            val ownerIndex = creature.ownerId.coerceIn(0, 1)
            val existingAlert = telepathicAlerts[ownerIndex]
            if (existingAlert != null && tick > existingAlert.expiresTick) {
                telepathicAlerts[ownerIndex] = null
            }
            if (visibleEnemies.isNotEmpty() && creature.stats.flags.contains(PartFlag.TELEPATHIC)) {
                val target = visibleEnemies.minWithOrNull(
                    compareBy<Creature> { IntegerMath.distanceSq(creature.x, creature.y, it.x, it.y) }
                        .thenBy { it.id }
                )
                if (target != null) {
                    val previous = telepathicAlerts[ownerIndex]
                    val alertX = if (outnumbered) creature.x else target.x
                    val alertY = if (outnumbered) creature.y else target.y
                    telepathicAlerts[ownerIndex] = TelepathicAlert(
                        ownerId = creature.ownerId,
                        sourceId = creature.id,
                        x = alertX,
                        y = alertY,
                        targetId = target.id,
                        expiresTick = tick + 240,
                        updatedTick = tick
                    )
                    if (previous == null || previous.targetId != target.id ||
                        tick - lastTelepathyTick[ownerIndex] >= 300
                    ) {
                        emit(
                            "TELEPATHY",
                            creature.id,
                            target.id,
                            alertX,
                            alertY,
                            0,
                            if (outnumbered) "HELP" else "CALL"
                        )
                        lastTelepathyTick[ownerIndex] = tick
                    }
                }
            }
            val ownHive = hiveFor(creature.ownerId)
            val enemyHive = enemyHiveFor(creature.ownerId)
            val enemyHiveVisible = Vision.canSeeHive(creature, enemyHive, navigation)
            if (scout && enemyHiveVisible && !enemyHiveKnown[ownerIndex]) {
                enemyHiveKnown[ownerIndex] = true
                emit("HIVE_FOUND", creature.id, enemyHive.id, enemyHive.x, enemyHive.y, 0, "ENEMY_HIVE")
            }
            val behaviorGraph = creature.genome.behavior.graph
            val decision = if (behaviorGraph == null || !usesCustomGraph(creature)) {
                BehaviorEngine.decide(
                    creature = creature,
                    friends = friends,
                    enemies = visibleEnemies,
                    corpses = corpses,
                    ownHive = ownHive,
                    enemyHive = enemyHive,
                    tick = tick,
                    enemyHiveVisible = enemyHiveVisible
                )
            } else {
                BehaviorGraphInterpreter.decide(
                    graph = behaviorGraph,
                    creature = creature,
                    friends = friends,
                    enemies = visibleEnemies,
                    corpses = corpses,
                    ownHive = ownHive,
                    enemyHive = enemyHive,
                    tick = tick,
                    random = aiRandom,
                    enemyHiveVisible = enemyHiveVisible
                )
            }
            val alert = telepathicAlerts[ownerIndex]?.takeIf {
                tick <= it.expiresTick && it.sourceId != creature.id
            }
            val recentAttacker = creatureById(creature.lastAttackerId)?.takeIf {
                tick - creature.lastDamageTick <= GameCatalog.ruleset.tickRate * 3 &&
                    it.alive
            }
            val alreadyEngaged = creature.attackTargetId != 0 ||
                decision.attackTargetId != 0
            val damageDecision = if (
                !scout &&
                recentAttacker != null &&
                !alreadyEngaged &&
                visibleEnemies.none { it.id == recentAttacker.id }
            ) {
                AiDecision(
                    mode = "COUNTERATTACK",
                    reason = "DAMAGE_RESPONSE",
                    targetId = recentAttacker.id,
                    moveTarget = Vec2(creature.lastAttackerX, creature.lastAttackerY),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    nodeId = "instinct:damage-response"
                )
            } else {
                null
            }
            val calledDecision = if (
                !scout &&
                alert != null &&
                visibleEnemies.isEmpty() &&
                decision.mode != "SURVIVE" &&
                decision.mode != "REST"
            ) {
                AiDecision(
                    mode = "RESPOND",
                    reason = "TELEPATHIC_CALL",
                    targetId = 0,
                    moveTarget = Vec2(alert.x, alert.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    nodeId = "telepathy"
                )
            } else {
                decision
            }
            val healThreshold = if (scout) 80 else 35
            val criticallyWounded = creature.hp * 100 / creature.stats.maxHp.coerceAtLeast(1) <= healThreshold
            val nectarPoint = if (criticallyWounded) nearestNectar(creature) else null
            val standDecision = if (scout) null else lastStandDecision(creature, visibleEnemies)
            val healDecision = if (standDecision == null && nectarPoint != null) {
                AiDecision(
                    mode = "HEAL",
                    reason = "SEEK_NECTAR",
                    targetId = 0,
                    moveTarget = Vec2(nectarPoint.x, nectarPoint.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    nodeId = "survival:nectar"
                )
            } else {
                null
            }
            val effectiveDecision = standDecision ?: healDecision ?: damageDecision ?: calledDecision
            val assaultDecision = if (
                enemyHiveKnown[ownerIndex] &&
                !scout &&
                visibleEnemies.isEmpty() &&
                effectiveDecision.mode == "PATROL"
            ) {
                AiDecision(
                    mode = "SIEGE",
                    reason = "HIVE_KNOWN",
                    targetId = enemyHive.id,
                    moveTarget = Vec2(enemyHive.x, enemyHive.y),
                    attackTargetId = enemyHive.id,
                    attackHive = true,
                    fleeFrom = null,
                    nodeId = "team:hive-assault"
                )
            } else {
                effectiveDecision
            }
            val scoutDecision = when {
                outnumbered -> AiDecision(
                    mode = "RETREAT",
                    reason = "OUTNUMBERED",
                    targetId = 0,
                    moveTarget = if (criticallyWounded && nectarPoint != null) {
                        Vec2(nectarPoint.x, nectarPoint.y)
                    } else {
                        Vec2(ownHive.x, ownHive.y)
                    },
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    nodeId = "scout:fear"
                )
                scout && visibleEnemies.isEmpty() && effectiveDecision.mode == "PATROL" -> {
                    val nextPoint = nextScoutPoint(creature)
                    if (nextPoint == null) {
                        effectiveDecision
                    } else {
                        AiDecision(
                            mode = "SCOUT",
                            reason = "RECON",
                            targetId = 0,
                            moveTarget = nextPoint,
                            attackTargetId = 0,
                            attackHive = false,
                            fleeFrom = null,
                            nodeId = "scout:bfs"
                        )
                    }
                }
                else -> assaultDecision
            }
            val objectivePoint = if (scoutDecision.mode == "PATROL") {
                nearestObjective(creature)
            } else {
                null
            }
            val finalDecision = if (objectivePoint == null) {
                scoutDecision
            } else {
                AiDecision(
                    mode = "CAPTURE",
                    reason = "OBJECTIVE_CONTROL",
                    targetId = 0,
                    moveTarget = Vec2(objectivePoint.x, objectivePoint.y),
                    attackTargetId = 0,
                    attackHive = false,
                    fleeFrom = null,
                    nodeId = "objective:${objectivePoint.id}"
                )
            }
            creature.aiMode = finalDecision.mode
            creature.aiReason = finalDecision.reason
            creature.aiNodeId = finalDecision.nodeId
            creature.targetId = finalDecision.targetId
            creature.attackTargetId = finalDecision.attackTargetId
            creature.attackHive = finalDecision.attackHive
            creature.scavengeTargetId = finalDecision.scavengeTargetId
            finalDecision.moveTarget?.let { target ->
                creature.desiredX = target.x.coerceIn(1_000, mapWidth - 1_000)
                creature.desiredY = target.y.coerceIn(1_000, mapHeight - 1_000)
            }
            finalDecision.fleeFrom?.let { source ->
                creature.fleeFromX = source.x
                creature.fleeFromY = source.y
            }
            creature.lastDecisionTick = tick
        }
    }

    private fun updateObjectives() {
        if (objectives.isEmpty()) {
            return
        }
        objectives.forEach { point ->
            var firstPresence = false
            var secondPresence = false
            creatures.filter { it.alive }.forEach { creature ->
                val distance = IntegerMath.distance(creature.x, creature.y, point.x, point.y)
                if (distance <= point.radiusMm + creature.stats.radiusMm) {
                    if (creature.ownerId == 0) {
                        firstPresence = true
                    } else if (creature.ownerId == 1) {
                        secondPresence = true
                    }
                }
            }
            point.contested = firstPresence && secondPresence
            val claimant = when {
                firstPresence && !secondPresence -> 0
                secondPresence && !firstPresence -> 1
                else -> -1
            }
            if (point.contested) {
                point.progress = (point.progress - 2).coerceAtLeast(0)
                if (point.progress == 0) {
                    point.ownerId = -1
                }
            } else if (claimant >= 0) {
                if (point.ownerId == claimant) {
                    point.progress = (point.progress + 2).coerceAtMost(point.captureTicks)
                } else if (point.ownerId == -1) {
                    point.progress = (point.progress + 2).coerceAtMost(point.captureTicks)
                    if (point.progress >= point.captureTicks) {
                        point.ownerId = claimant
                        emit("CAPTURE", claimant, 0, point.x, point.y, claimant, point.name)
                    }
                } else {
                    point.progress = (point.progress - 2).coerceAtLeast(0)
                    if (point.progress == 0) {
                        val previousOwner = point.ownerId
                        point.ownerId = -1
                        emit("OBJECTIVE_LOST", previousOwner, 0, point.x, point.y, previousOwner, point.name)
                    }
                }
            } else {
                point.progress = (point.progress - 1).coerceAtLeast(0)
                if (point.progress == 0) {
                    point.ownerId = -1
                }
            }
        }
        if (tick % GameCatalog.ruleset.tickRate != 0) {
            return
        }
        objectives.forEach { point ->
            val owner = point.ownerId
            if (owner >= 0) {
                controlScores[owner] += point.scorePerSecond
            }
            creatures.filter { it.alive }.forEach { creature ->
                val distance = IntegerMath.distance(creature.x, creature.y, point.x, point.y)
                if (distance <= point.radiusMm + creature.stats.radiusMm) {
                    val bonus = if (owner >= 0 && creature.ownerId == owner) 1 else 0
                    val healed = point.healPerSecond + bonus
                    creature.hp = (creature.hp + healed).coerceAtMost(creature.stats.maxHp)
                }
            }
        }
    }

    private fun applyHazards() {
        if (config.map.hazards.isEmpty() || tick % GameCatalog.ruleset.tickRate != 0) {
            return
        }
        creatures.filter { it.alive }.forEach { creature ->
            val hazard = config.map.hazards.firstOrNull { zone ->
                IntegerMath.distance(creature.x, creature.y, zone.x, zone.y) <=
                    zone.radiusMm + creature.stats.radiusMm
            } ?: return@forEach
            val damage = hazard.damagePerSecond.coerceAtLeast(1)
            creature.hp -= damage
            creature.lastDamageTick = tick
            emit("HIT", 0, creature.id, creature.x, creature.y, damage, "ACID")
            if (creature.hp <= 0) {
                killCreature(creature, -1, 0)
            }
        }
    }

    private fun processMoveOrders() {
        while (true) {
            val order = pendingMoveOrders.poll() ?: break
            val creature = creatureById(order.entityId) ?: continue
            if (!creature.alive || creature.ownerId != order.ownerId) {
                continue
            }
            val adjusted = adjustedMoveTarget(creature, order.x, order.y)
            creature.manualTargetX = adjusted.x
            creature.manualTargetY = adjusted.y
            creature.manualOrderTicks = GameCatalog.ruleset.tickRate * 30
        }
    }

    private fun adjustedMoveTarget(creature: Creature, targetX: Int, targetY: Int): Vec2 {
        val enemyHive = hives.firstOrNull { hive ->
            hive.ownerId != creature.ownerId &&
                IntegerMath.distanceSq(hive.x, hive.y, targetX, targetY) <=
                (hive.radiusMm + 500L) * (hive.radiusMm + 500)
        } ?: return Vec2(
            targetX.coerceIn(1_000, mapWidth - 1_000),
            targetY.coerceIn(1_000, mapHeight - 1_000)
        )
        var dx = targetX - enemyHive.x
        var dy = targetY - enemyHive.y
        if (dx == 0 && dy == 0) {
            dx = creature.x - enemyHive.x
            dy = creature.y - enemyHive.y
        }
        val length = IntegerMath.isqrt(dx.toLong() * dx + dy.toLong() * dy).coerceAtLeast(1)
        val surfaceDistance = enemyHive.radiusMm + creature.stats.radiusMm + 200
        return Vec2(
            (enemyHive.x + dx * surfaceDistance / length).coerceIn(1_000, mapWidth - 1_000),
            (enemyHive.y + dy * surfaceDistance / length).coerceIn(1_000, mapHeight - 1_000)
        )
    }

    private fun processWhipCommands() {
        while (true) {
            val command = pendingWhips.poll() ?: break
            val creature = creatureById(command.first) ?: continue
            if (!creature.alive || creature.ownerId != command.second) {
                continue
            }
            creature.painTicks = GameCatalog.ruleset.tickRate * 5
            choosePainTarget(creature)
            emit("WHIP", 0, creature.id, creature.x, creature.y, 0, "ELECTRO_WHIP")
        }
    }

    private fun choosePainTarget(creature: Creature) {
        val directions = listOf(
            1 to 0,
            -1 to 0,
            0 to 1,
            0 to -1,
            1 to 1,
            1 to -1,
            -1 to 1,
            -1 to -1
        )
        val (dx, dy) = directions[aiRandom.nextInt(directions.size)]
        val distance = 18_000 + aiRandom.nextInt(14_000)
        val length = if (dx == 0 || dy == 0) 1 else 1
        creature.painTargetX = (creature.x + dx * distance / length).coerceIn(1_000, mapWidth - 1_000)
        creature.painTargetY = (creature.y + dy * distance / length).coerceIn(1_000, mapHeight - 1_000)
    }

    private fun isScout(creature: Creature): Boolean =
        creature.genome.behavior.presetId == "SCOUT" || creature.genome.name == "Scout"

    private fun nextScoutPoint(creature: Creature): Vec2? {
        if (creature.scoutTargetX != 0 || creature.scoutTargetY != 0) {
            val distanceSq = IntegerMath.distanceSq(
                creature.x,
                creature.y,
                creature.scoutTargetX,
                creature.scoutTargetY
            )
            if (distanceSq > 1_500L * 1_500) {
                return Vec2(creature.scoutTargetX, creature.scoutTargetY)
            }
            creature.scoutTargetX = 0
            creature.scoutTargetY = 0
        }
        val next = navigation.nextExplorationPoint(
            startX = creature.x,
            startY = creature.y,
            visited = creature.scoutVisitedCells
        ) ?: return null
        creature.scoutTargetX = next.x
        creature.scoutTargetY = next.y
        return next
    }

    private fun nearestNectar(creature: Creature): ObjectivePoint? =
        objectives
            .asSequence()
            .minWithOrNull(
                compareBy<ObjectivePoint> {
                    IntegerMath.distanceSq(creature.x, creature.y, it.x, it.y)
                }.thenBy { it.id }
            )

    private fun nearestObjective(creature: Creature): ObjectivePoint? =
        objectives
            .asSequence()
            .filter { it.ownerId != creature.ownerId }
            .minWithOrNull(
                compareBy<ObjectivePoint> {
                    IntegerMath.distanceSq(creature.x, creature.y, it.x, it.y)
                }.thenBy { it.id }
            )

    private fun usesCustomGraph(creature: Creature): Boolean {
        val graph = creature.genome.behavior.graph ?: return false
        val presetGraph = PresetGraphs.forPreset(creature.genome.behavior.presetId) ?: return true
        return graph != presetGraph
    }

    private fun updateMovement() {
        creatures.filter { it.alive }.forEach { creature ->
            if (creature.attackCooldownTicks > 0) {
                creature.attackCooldownTicks -= 1
            }
            if (creature.stats.speedMmPerTick <= 0) {
                creature.state = EntityState.IDLE
                return@forEach
            }

            val scavengeTarget = corpseById(creature.scavengeTargetId)
            val targetPoint = attackTargetPoint(creature)
            val goal = when {
                scavengeTarget != null -> Vec2(scavengeTarget.x, scavengeTarget.y)
                targetPoint != null -> targetPoint
                creature.desiredX != 0 || creature.desiredY != 0 -> Vec2(creature.desiredX, creature.desiredY)
                else -> null
            } ?: run {
                creature.state = EntityState.IDLE
                return@forEach
            }

            if (scavengeTarget != null && isInScavengeRange(creature, scavengeTarget)) {
                creature.angle = AngleMath.of(scavengeTarget.x - creature.x, scavengeTarget.y - creature.y)
                creature.state = EntityState.HARVESTING
                return@forEach
            }

            if (targetPoint != null && isInAttackRange(creature, goal)) {
                creature.angle = AngleMath.of(targetPoint.x - creature.x, targetPoint.y - creature.y)
                creature.state = EntityState.ATTACKING
                return@forEach
            }

            val nextPoint = nextPathPoint(creature, goal)
            val speed = if (creature.energy <= 0) {
                kotlin.math.max(8, creature.stats.speedMmPerTick / 2)
            } else {
                creature.stats.speedMmPerTick
            }
            val step = IntegerMath.stepToward(
                creature.x,
                creature.y,
                nextPoint.x,
                nextPoint.y,
                speed
            )
            if (step.x != creature.x || step.y != creature.y) {
                creature.angle = AngleMath.of(step.x - creature.x, step.y - creature.y)
                creature.x = step.x
                creature.y = step.y
                creature.state = EntityState.MOVING
            } else {
                creature.state = EntityState.IDLE
                if (
                    creature.aiMode == "PATROL" ||
                    creature.aiMode == "DEFEND_HIVE" ||
                    creature.aiMode == "RESPOND" ||
                    creature.aiMode == "COUNTERATTACK" ||
                    creature.aiMode == "CAPTURE" ||
                    creature.aiMode == "SCOUT" ||
                    creature.aiMode == "RETREAT" ||
                    creature.aiMode == "HEAL" ||
                    creature.aiMode == "PAIN" ||
                    creature.aiMode == "MANUAL"
                ) {
                    creature.angle = (creature.angle + 384) and 0xFFFF
                }
            }
        }
    }

    private fun lastStandDecision(creature: Creature, enemies: List<Creature>): AiDecision? {
        if (creature.attackTargetId != 0) {
            val currentTarget = if (creature.attackHive) {
                hives.firstOrNull { it.id == creature.attackTargetId && it.alive }
            } else {
                creatureById(creature.attackTargetId)?.takeIf { it.alive }
            }
            if (currentTarget != null) {
                return null
            }
        }
        val severelyWounded = creature.hp * 100 / creature.stats.maxHp.coerceAtLeast(1) <= 35
        val recentlyHit = tick - creature.lastDamageTick <= GameCatalog.ruleset.tickRate * 3
        if (!severelyWounded && !recentlyHit) {
            return null
        }
        val weaponRange = creature.stats.weapons.maxOfOrNull { it.rangeMm } ?: return null
        val target = enemies
            .asSequence()
            .filter { it.alive }
            .map { enemy ->
                enemy to IntegerMath.distanceSq(creature.x, creature.y, enemy.x, enemy.y)
            }
            .filter { (enemy, distanceSq) ->
                val range = weaponRange + enemy.stats.radiusMm + 200
                distanceSq <= range.toLong() * range
            }
            .minWithOrNull(
                compareBy<Pair<Creature, Long>> { it.second }
                    .thenBy { it.first.id }
            )
            ?.first
            ?: return null
        if (creature.attackTargetId == target.id) {
            return null
        }
        return AiDecision(
            mode = "ATTACK",
            reason = "LAST_STAND",
            targetId = target.id,
            moveTarget = Vec2(target.x, target.y),
            attackTargetId = target.id,
            attackHive = false,
            fleeFrom = null,
            nodeId = "instinct:last-stand"
        )
    }

    private fun updateScavenging() {
        creatures.filter { it.alive && it.scavengeTargetId != 0 }.forEach { creature ->
            val corpse = corpseById(creature.scavengeTargetId)
            if (corpse == null || !creature.stats.flags.contains(PartFlag.SCAVENGER)) {
                creature.scavengeTargetId = 0
                creature.scavengeTicks = 0
                return@forEach
            }
            if (!isInScavengeRange(creature, corpse)) {
                creature.scavengeTicks = 0
                return@forEach
            }
            creature.state = EntityState.HARVESTING
            creature.scavengeTicks += 1
            if (creature.scavengeTicks >= GameCatalog.ruleset.tickRate * 2) {
                val energyGain = kotlin.math.min(corpse.nutritionEnergy, creature.stats.maxEnergy - creature.energy)
                val hpGain = kotlin.math.min(corpse.nutritionHp, creature.stats.maxHp - creature.hp)
                creature.energy += energyGain.coerceAtLeast(0)
                creature.hp += hpGain.coerceAtLeast(0)
                emit(
                    "RESOURCE",
                    creature.id,
                    corpse.id,
                    corpse.x,
                    corpse.y,
                    energyGain.coerceAtLeast(0),
                    "CORPSE"
                )
                corpses.remove(corpse)
                creature.scavengeTargetId = 0
                creature.scavengeTicks = 0
                creature.state = EntityState.IDLE
            }
        }
    }

    private fun updateCorpses() {
        corpses.forEach { corpse -> corpse.remainingTicks -= 1 }
        val expired = corpses.filter { it.remainingTicks <= 0 }.map { it.id }.toSet()
        if (expired.isEmpty()) {
            return
        }
        corpses.removeAll { it.id in expired }
        creatures.forEach { creature ->
            if (creature.scavengeTargetId in expired) {
                creature.scavengeTargetId = 0
                creature.scavengeTicks = 0
            }
        }
    }

    private fun isInScavengeRange(creature: Creature, corpse: Corpse): Boolean =
        IntegerMath.distance(creature.x, creature.y, corpse.x, corpse.y) <= creature.stats.radiusMm + 800

    private fun corpseById(corpseId: Int): Corpse? =
        corpses.firstOrNull { it.id == corpseId }

    private fun attackTargetPoint(creature: Creature): Vec2? {
        if (creature.attackTargetId == 0) {
            return null
        }
        if (creature.attackHive) {
            val hive = hives.firstOrNull { it.id == creature.attackTargetId && it.alive }
                ?: return null
            return Vec2(hive.x, hive.y)
        }
        val target = creatureById(creature.attackTargetId) ?: return null
        return Vec2(target.x, target.y)
    }

    private fun isInAttackRange(creature: Creature, goal: Vec2): Boolean {
        val targetId = creature.attackTargetId
        if (targetId == 0) {
            return false
        }
        val weapon = maxWeaponRange(creature)
        if (weapon <= 0) {
            return false
        }
        return if (creature.attackHive) {
            val hive = hives.firstOrNull { it.id == targetId } ?: return false
            IntegerMath.distance(creature.x, creature.y, hive.x, hive.y) <= weapon + hive.radiusMm &&
                navigation.hasLineOfSight(creature.x, creature.y, hive.x, hive.y)
        } else {
            val target = creatureById(targetId) ?: return false
            IntegerMath.distance(creature.x, creature.y, goal.x, goal.y) <= weapon + target.stats.radiusMm &&
                navigation.hasLineOfSight(creature.x, creature.y, target.x, target.y)
        }
    }

    private fun nextPathPoint(creature: Creature, goal: Vec2): Vec2 {
        if (creature.canFly || config.map.obstacles.isEmpty()) {
            return goal
        }
        val goalChanged = IntegerMath.distanceSq(goal.x, goal.y, creature.pathGoalX, creature.pathGoalY) > 1_000_000L
        if (goalChanged || creature.path.isEmpty() || creature.pathIndex >= creature.path.size ||
            tick >= creature.nextRepathTick
        ) {
            val path = navigation.findPath(creature.x, creature.y, goal.x, goal.y)
            creature.path = path ?: emptyList()
            creature.pathIndex = 0
            creature.pathGoalX = goal.x
            creature.pathGoalY = goal.y
            creature.nextRepathTick = tick + 15
        }
        if (creature.pathIndex < creature.path.size) {
            var waypoint = creature.path[creature.pathIndex]
            while (creature.pathIndex < creature.path.size &&
                IntegerMath.distance(creature.x, creature.y, waypoint.x, waypoint.y) < 500
            ) {
                creature.pathIndex += 1
                if (creature.pathIndex < creature.path.size) {
                    waypoint = creature.path[creature.pathIndex]
                }
            }
            if (creature.pathIndex < creature.path.size) {
                return waypoint
            }
        }
        return goal
    }

    private fun resolveCollisions() {
        repeat(2) {
            for (firstIndex in creatures.indices) {
                val first = creatures[firstIndex]
                if (!first.alive) {
                    continue
                }
                for (secondIndex in firstIndex + 1 until creatures.size) {
                    val second = creatures[secondIndex]
                    if (!second.alive) {
                        continue
                    }
                    val minDistance = first.stats.radiusMm + second.stats.radiusMm
                    val distanceSq = IntegerMath.distanceSq(first.x, first.y, second.x, second.y)
                    if (distanceSq >= minDistance.toLong() * minDistance.toLong() || distanceSq == 0L) {
                        continue
                    }
                    val distance = IntegerMath.isqrt(distanceSq).coerceAtLeast(1)
                    val overlap = minDistance - distance
                    val dx = (second.x - first.x).toLong()
                    val dy = (second.y - first.y).toLong()
                    val pushX = (dx * overlap / (2L * distance)).toInt()
                    val pushY = (dy * overlap / (2L * distance)).toInt()
                    first.x -= pushX
                    first.y -= pushY
                    second.x += pushX
                    second.y += pushY
                }
            }

            creatures.filter { it.alive }.forEach { creature ->
                hives.forEach { hive ->
                    val minDistance = hive.radiusMm + creature.stats.radiusMm
                    val distance = IntegerMath.distance(creature.x, creature.y, hive.x, hive.y)
                    if (distance < minDistance) {
                        val directionX = if (creature.x == hive.x) 1 else creature.x - hive.x
                        val directionY = if (creature.y == hive.y) 1 else creature.y - hive.y
                        val length = IntegerMath.isqrt(
                            directionX.toLong() * directionX + directionY.toLong() * directionY
                        ).coerceAtLeast(1)
                        val push = minDistance - distance
                        creature.x += directionX * push / length
                        creature.y += directionY * push / length
                    }
                }
                creature.x = creature.x.coerceIn(1_000, mapWidth - 1_000)
                creature.y = creature.y.coerceIn(1_000, mapHeight - 1_000)
            }
        }
    }

    private fun resolveCombat() {
        creatures.filter { it.alive }.forEach { creature ->
            if (creature.attackTargetId == 0 || creature.attackCooldownTicks > 0 || creature.energy <= 0) {
                return@forEach
            }
            if (creature.attackHive) {
                val hive = hives.firstOrNull { it.id == creature.attackTargetId && it.alive } ?: return@forEach
                if (!Vision.canSeeHive(creature, hive, navigation)) {
                    return@forEach
                }
                val distance = IntegerMath.distance(creature.x, creature.y, hive.x, hive.y)
                val weapon = selectWeapon(creature, distance, hive.radiusMm) ?: return@forEach
                creature.attackCooldownTicks = weapon.cooldownTicks
                creature.energy = (creature.energy - 1).coerceAtLeast(0)
                emit(
                    "ATTACK",
                    creature.id,
                    hive.id,
                    creature.x,
                    creature.y,
                    weapon.damage,
                    if (weapon.ranged) "RANGED:${weapon.partId}" else "MELEE:${weapon.partId}"
                )
                fireWeapon(creature, weapon, hive.id, hive.x, hive.y, hive.radiusMm, true)
            } else {
                val target = creatureById(creature.attackTargetId) ?: return@forEach
                if (!Vision.canSee(creature, target, navigation)) {
                    return@forEach
                }
                val distance = IntegerMath.distance(creature.x, creature.y, target.x, target.y)
                val weapon = selectWeapon(creature, distance, target.stats.radiusMm) ?: return@forEach
                creature.attackCooldownTicks = weapon.cooldownTicks
                creature.energy = (creature.energy - 1).coerceAtLeast(0)
                emit(
                    "ATTACK",
                    creature.id,
                    target.id,
                    creature.x,
                    creature.y,
                    weapon.damage,
                    if (weapon.ranged) "RANGED:${weapon.partId}" else "MELEE:${weapon.partId}"
                )
                fireWeapon(creature, weapon, target.id, target.x, target.y, target.stats.radiusMm, false)
            }
        }
    }

    private fun fireWeapon(
        source: Creature,
        weapon: WeaponStats,
        targetId: Int,
        targetX: Int,
        targetY: Int,
        targetRadius: Int,
        targetIsHive: Boolean
    ) {
        source.angle = AngleMath.of(targetX - source.x, targetY - source.y)
        val evasion = if (targetIsHive) 0 else creatureById(targetId)?.stats?.evasion ?: 0
        val accuracy = weapon.accuracy + source.stats.accuracyBonus
        val hitChance = (accuracy * 10_000 / (accuracy + evasion).coerceAtLeast(1)).coerceIn(500, 9_500)
        val hit = combatRandom.nextInt(10_000) < hitChance

        if (weapon.ranged) {
            val distance = IntegerMath.distance(source.x, source.y, targetX, targetY)
            val speed = weapon.projectileSpeedMmPerTick.coerceAtLeast(100)
            val ttl = distance / speed + 20
            projectiles += Projectile(
                id = nextProjectileId,
                ownerId = source.ownerId,
                sourceId = source.id,
                targetId = targetId,
                x = source.x,
                y = source.y,
                speedMmPerTick = speed,
                ttlTicks = ttl,
                damage = weapon.damage,
                damageType = weapon.damageType,
                hit = hit,
                splashRadiusMm = weapon.splashRadiusMm,
                dotDamagePerSecond = weapon.dotDamagePerSecond,
                dotDurationTicks = weapon.dotDurationTicks,
                penetration = weapon.penetration
            ).also { projectile ->
                projectile.angle = AngleMath.of(targetX - source.x, targetY - source.y)
            }
            nextProjectileId += 1
            if (!hit) {
                emit("MISS", source.id, targetId, targetX, targetY, 0, weapon.partId)
            }
            return
        }

        if (!hit) {
            emit("MISS", source.id, targetId, targetX, targetY, 0, weapon.partId)
            return
        }
        if (targetIsHive) {
            val hive = hives.firstOrNull { it.id == targetId } ?: return
            applyHiveDamage(source, hive, weapon.damage, weapon.damageType, weapon.penetration)
        } else {
            val target = creatureById(targetId) ?: return
            applyCreatureDamage(source.ownerId, source.id, target, weapon.damage, weapon.damageType, weapon.penetration)
            applyDot(target, weapon, source.id)
        }
    }

    private fun selectWeapon(creature: Creature, distance: Int, targetRadius: Int): WeaponStats? =
        creature.stats.weapons
            .filter { weapon -> distance <= weapon.rangeMm + targetRadius }
            .maxWithOrNull(
                compareBy<WeaponStats> { it.damage }
                    .thenBy { it.partId }
            )

    private fun maxWeaponRange(creature: Creature): Int =
        creature.stats.weapons.maxOfOrNull { it.rangeMm } ?: 0

    private fun updateProjectiles() {
        projectiles.filter { it.alive }.forEach { projectile ->
            projectile.ttlTicks -= 1
            if (projectile.ttlTicks <= 0) {
                projectile.alive = false
                return@forEach
            }
            val targetX: Int
            val targetY: Int
            val targetRadius: Int
            if (projectile.targetId == hives[0].id || projectile.targetId == hives[1].id) {
                val hive = hives.firstOrNull { it.id == projectile.targetId && it.alive }
                if (hive == null) {
                    projectile.alive = false
                    return@forEach
                }
                targetX = hive.x
                targetY = hive.y
                targetRadius = hive.radiusMm
            } else {
                val target = creatureById(projectile.targetId)
                if (target == null || !target.alive) {
                    projectile.alive = false
                    return@forEach
                }
                targetX = target.x
                targetY = target.y
                targetRadius = target.stats.radiusMm
            }

            val distance = IntegerMath.distance(projectile.x, projectile.y, targetX, targetY)
            if (distance <= targetRadius + projectile.radiusMm) {
                projectile.alive = false
                if (projectile.hit) {
                    val source = creatureById(projectile.sourceId)
                    val sourceOwner = source?.ownerId ?: projectile.ownerId
                    if (projectile.targetId == hives[0].id || projectile.targetId == hives[1].id) {
                        val hive = hives.firstOrNull { it.id == projectile.targetId } ?: return@forEach
                        applyHiveDamage(
                            sourceOwner = sourceOwner,
                            sourceId = projectile.sourceId,
                            hive = hive,
                            baseDamage = projectile.damage,
                            damageType = projectile.damageType,
                            penetration = projectile.penetration
                        )
                    } else {
                        val target = creatureById(projectile.targetId) ?: return@forEach
                        applyCreatureDamage(
                            sourceOwner = sourceOwner,
                            sourceId = projectile.sourceId,
                            target = target,
                            baseDamage = projectile.damage,
                            damageType = projectile.damageType,
                            penetration = projectile.penetration
                        )
                        if (projectile.dotDamagePerSecond > 0 && projectile.dotDurationTicks > 0) {
                            target.statuses += StatusEffect(
                                type = if (projectile.damageType == DamageType.ACID) EffectType.ACID else EffectType.TOXIC,
                                remainingTicks = projectile.dotDurationTicks,
                                damagePerSecond = projectile.dotDamagePerSecond,
                                sourceId = projectile.sourceId
                            )
                        }
                    }
                    if (projectile.splashRadiusMm > 0) {
                        applySplash(
                            sourceOwner = sourceOwner,
                            sourceId = projectile.sourceId,
                            x = projectile.x,
                            y = projectile.y,
                            radiusMm = projectile.splashRadiusMm,
                            baseDamage = projectile.damage / 2,
                            damageType = projectile.damageType,
                            penetration = projectile.penetration,
                            ignoredId = projectile.targetId
                        )
                    }
                }
            } else {
                val previousX = projectile.x
                val previousY = projectile.y
                val step = IntegerMath.stepToward(
                    projectile.x,
                    projectile.y,
                    targetX,
                    targetY,
                    projectile.speedMmPerTick
                )
                projectile.x = step.x
                projectile.y = step.y
                projectile.angle = AngleMath.of(step.x - previousX, step.y - previousY)
            }
        }
    }

    private fun applySplash(
        sourceOwner: Int,
        sourceId: Int,
        x: Int,
        y: Int,
        radiusMm: Int,
        baseDamage: Int,
        damageType: DamageType,
        penetration: Int,
        ignoredId: Int
    ) {
        creatures
            .filter { creature ->
                creature.alive &&
                    creature.ownerId != sourceOwner &&
                    creature.id != ignoredId &&
                    IntegerMath.distance(x, y, creature.x, creature.y) <= radiusMm + creature.stats.radiusMm
            }
            .forEach { creature ->
                applyCreatureDamage(sourceOwner, sourceId, creature, baseDamage, damageType, penetration)
            }
    }

    private fun telepathicHiveCall(hive: Hive, sourceOwner: Int, sourceId: Int) {
        if (hive.ownerId == sourceOwner) {
            return
        }
        val ownerIndex = hive.ownerId.coerceIn(0, 1)
        telepathicAlerts[ownerIndex] = TelepathicAlert(
            ownerId = hive.ownerId,
            sourceId = hive.id,
            x = hive.x,
            y = hive.y,
            targetId = sourceId,
            expiresTick = tick + 240,
            updatedTick = tick
        )
        if (lastHiveCallTick[ownerIndex] != 0 && tick - lastHiveCallTick[ownerIndex] < 300) {
            return
        }
        lastHiveCallTick[ownerIndex] = tick
        emit("TELEPATHY", hive.id, sourceId, hive.x, hive.y, 0, "HIVE_CALL")
    }

    private fun recordDamage(target: Creature, sourceId: Int) {
        target.lastDamageTick = tick
        if (sourceId <= 0 || sourceId == target.id) {
            return
        }
        val source = creatureById(sourceId) ?: return
        if (!source.alive || source.ownerId == target.ownerId) {
            return
        }
        target.lastAttackerId = sourceId
        target.lastAttackerX = source.x
        target.lastAttackerY = source.y
        if (target.attackTargetId == 0 && !target.attackHive) {
            target.angle = AngleMath.of(source.x - target.x, source.y - target.y)
        }
    }

    private fun applyCreatureDamage(
        sourceOwner: Int,
        sourceId: Int,
        target: Creature,
        baseDamage: Int,
        damageType: DamageType,
        penetration: Int
    ): Int {
        if (!target.alive) {
            return 0
        }
        val armorValue = target.stats.armor.forType(damageType)
        val effectiveArmor = armorValue * (100 - penetration).coerceIn(0, 100) / 100
        var damage = baseDamage * 100 / (effectiveArmor + 100)
        if (combatRandom.nextInt(100) < 10) {
            damage = damage * 3 / 2
        }
        damage = damage.coerceAtLeast(1)
        target.hp -= damage
        recordDamage(target, sourceId)
        creatureById(sourceId)?.let { source -> source.damageDealt += damage.toLong() }
        damageByPlayer[sourceOwner] = damageByPlayer[sourceOwner] + damage
        emit("HIT", sourceId, target.id, target.x, target.y, damage, damageType.name)
        if (target.hp <= 0) {
            killCreature(target, sourceOwner, sourceId)
        }
        return damage
    }

    private fun applyHiveDamage(
        source: Creature,
        hive: Hive,
        baseDamage: Int,
        damageType: DamageType,
        penetration: Int
    ) {
        applyHiveDamage(
            sourceOwner = source.ownerId,
            sourceId = source.id,
            hive = hive,
            baseDamage = baseDamage,
            damageType = damageType,
            penetration = penetration
        )
    }

    private fun applyHiveDamage(
        sourceOwner: Int,
        sourceId: Int,
        hive: Hive,
        baseDamage: Int,
        damageType: DamageType,
        penetration: Int
    ) {
        if (!hive.alive) {
            return
        }
        val effectiveArmor = hive.armor * (100 - penetration).coerceIn(0, 100) / 100
        val damage = (baseDamage * 100 / (effectiveArmor + 100)).coerceAtLeast(1)
        hive.hp -= damage
        damageByPlayer[sourceOwner] = damageByPlayer[sourceOwner] + damage
        emit("OBJECTIVE", sourceId, hive.id, hive.x, hive.y, damage, damageType.name)
        telepathicHiveCall(hive, sourceOwner, sourceId)
        if (hive.hp <= 0) {
            hive.hp = 0
            hive.alive = false
            emit("DEATH", 0, hive.id, hive.x, hive.y, 0, "HIVE_DESTROYED")
            checkVictory()
        }
    }

    private fun applyDot(target: Creature, weapon: WeaponStats, sourceId: Int) {
        if (weapon.dotDamagePerSecond <= 0 || weapon.dotDurationTicks <= 0) {
            return
        }
        target.statuses += StatusEffect(
            type = if (weapon.damageType == DamageType.ACID) EffectType.ACID else EffectType.TOXIC,
            remainingTicks = weapon.dotDurationTicks,
            damagePerSecond = weapon.dotDamagePerSecond,
            sourceId = sourceId
        )
    }

    private fun applyStatusEffects() {
        creatures.filter { it.alive }.forEach { creature ->
            val active = creature.statuses.filter { it.remainingTicks > 0 }
            active.forEach { effect ->
                if (tick % GameCatalog.ruleset.tickRate == 0 && effect.damagePerSecond > 0) {
                    creature.hp -= effect.damagePerSecond
                    recordDamage(creature, effect.sourceId)
                    emit(
                        "HIT",
                        effect.sourceId,
                        creature.id,
                        creature.x,
                        creature.y,
                        effect.damagePerSecond,
                        effect.type.name
                    )
                }
                effect.remainingTicks -= 1
            }
            creature.statuses.removeAll { it.remainingTicks <= 0 }
            if (creature.hp <= 0) {
                killCreature(creature, -1, 0)
            }
        }
    }

    private fun updateEnergy() {
        creatures.filter { it.alive }.forEach { creature ->
            if (creature.energy <= 0) {
                creature.starvationTicks += 1
                if (creature.starvationTicks > 300 && tick % 30 == 0) {
                    val damage = kotlin.math.max(1, creature.stats.maxHp / 100)
                    creature.hp -= damage
                    emit(
                        "HIT",
                        0,
                        creature.id,
                        creature.x,
                        creature.y,
                        damage,
                        "STARVATION"
                    )
                    if (creature.hp <= 0) {
                        killCreature(creature, -1, 0)
                    }
                }
            } else {
                creature.starvationTicks = 0
            }

            creature.energyCarryMilli += creature.stats.regenMilliPerSecond - creature.stats.upkeepMilliPerSecond
            while (creature.energyCarryMilli >= 1_000 && creature.energy < creature.stats.maxEnergy) {
                creature.energy += 1
                creature.energyCarryMilli -= 1_000
            }
            while (creature.energyCarryMilli <= -1_000 && creature.energy > 0) {
                creature.energy -= 1
                creature.energyCarryMilli += 1_000
            }
            if (creature.energy >= creature.stats.maxEnergy) {
                creature.energyCarryMilli = creature.energyCarryMilli.coerceAtMost(0)
            }
        }
    }

    private fun updateRegeneration() {
        if (tick % 30 != 0) {
            return
        }
        creatures.filter { it.alive }.forEach { creature ->
            if (creature.stats.hpRegenPercentPerSecond > 0) {
                healCreature(
                    target = creature,
                    amount = kotlin.math.max(1, creature.stats.maxHp * creature.stats.hpRegenPercentPerSecond / 100),
                    sourceId = creature.id
                )
            }
            if (creature.stats.flags.contains(PartFlag.SYMBIOTE)) {
                creatures
                    .filter { ally ->
                        ally.alive &&
                            ally.ownerId == creature.ownerId &&
                            IntegerMath.distance(creature.x, creature.y, ally.x, ally.y) <= 5_000
                    }
                    .forEach { ally ->
                        healCreature(
                            target = ally,
                            amount = kotlin.math.max(1, ally.stats.maxHp / 100),
                            sourceId = creature.id
                        )
                    }
            }
        }
    }

    private fun healCreature(target: Creature, amount: Int, sourceId: Int) {
        if (!target.alive || target.hp >= target.stats.maxHp) {
            return
        }
        val healed = kotlin.math.min(amount, target.stats.maxHp - target.hp)
        target.hp += healed
        emit("HEAL", sourceId, target.id, target.x, target.y, healed)
    }

    private fun killCreature(target: Creature, sourceOwner: Int, sourceId: Int) {
        if (!target.alive) {
            return
        }
        target.alive = false
        target.hp = 0
        target.state = EntityState.DEAD
        if (sourceId > 0) {
            creatureById(sourceId)?.let { source -> source.kills += 1 }
        }
        lossesByPlayer[target.ownerId] += 1
        if (sourceOwner >= 0) {
            killsByPlayer[sourceOwner] += 1
        }
        corpses += Corpse(
            id = nextCorpseId,
            ownerId = target.ownerId,
            genomeIndex = target.genomeIndex,
            x = target.x,
            y = target.y
        )
        nextCorpseId += 1
        emit("DEATH", sourceId, target.id, target.x, target.y, 0, "KILLED")
    }

    private fun removeDead() {
        projectiles.removeAll { !it.alive }
        creatures.removeAll { !it.alive }
    }

    private fun checkVictory() {
        if (result != null) {
            return
        }
        val firstHiveAlive = hives[0].alive
        val secondHiveAlive = hives[1].alive
        when {
            !firstHiveAlive && !secondHiveAlive -> finish(-1, "SIMULTANEOUS_HIVE_DESTRUCTION")
            !firstHiveAlive -> finish(1, "HIVE_DESTROYED")
            !secondHiveAlive -> finish(0, "HIVE_DESTROYED")
            else -> {
                if (tick >= config.maxTicks + config.overtimeTicks) {
                    finishTimeout()
                }
            }
        }
    }

    private fun finishTimeout() {
        finish(-1, "TIMEOUT_DRAW")
    }

    private fun finish(winner: Int, reason: String) {
        if (result != null) {
            return
        }
        result = MatchResult(
            winner = winner,
            reason = reason,
            durationTicks = tick,
            stats = players.map { player ->
                val surviving = creatures.count { it.alive && it.ownerId == player.playerId }
                PlayerMatchStats(
                    playerId = player.playerId,
                    nickname = player.nickname,
                    damageDealt = damageByPlayer[player.playerId],
                    kills = killsByPlayer[player.playerId],
                    losses = lossesByPlayer[player.playerId],
                    survivingCreatures = surviving,
                    hiveHp = hives[player.playerId].hp
                )
            }
        )
        emit("MATCH_END", winner, 0, 0, 0, winner, reason)
    }

    private fun entityFlags(creature: Creature): Int {
        var flags = 0
        if (creature.alive) flags = flags or 1
        if (creature.state == EntityState.MOVING) flags = flags or 2
        if (creature.state == EntityState.ATTACKING) flags = flags or 4
        if (creature.stunTicks > 0) flags = flags or 8
        if (creature.statuses.any { it.type == EffectType.ACID }) flags = flags or 16
        if (creature.state == EntityState.BURROWED) flags = flags or 32
        if (creature.canFly) flags = flags or 64
        if (creature.lastDamageTick > tick - 30) flags = flags or 256
        if (creature.aiMode == "SURVIVE") flags = flags or 512
        return flags
    }

    private fun creatureById(entityId: Int): Creature? =
        creatures.firstOrNull { it.id == entityId && it.alive }

    private fun hiveFor(ownerId: Int): Hive =
        hives.firstOrNull { it.ownerId == ownerId } ?: hives.first()

    private fun enemyHiveFor(ownerId: Int): Hive =
        hives.firstOrNull { it.ownerId != ownerId } ?: hives.last()

    private fun emit(
        type: String,
        sourceId: Int = 0,
        targetId: Int = 0,
        x: Int = 0,
        y: Int = 0,
        value: Int = 0,
        text: String = ""
    ) {
        eventBuffer += SimEvent(
            id = nextEventId,
            tick = tick,
            type = type,
            sourceId = sourceId,
            targetId = targetId,
            x = x,
            y = y,
            value = value,
            text = text
        )
        nextEventId += 1
    }
}
