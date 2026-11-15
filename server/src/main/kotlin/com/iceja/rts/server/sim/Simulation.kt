package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.DamageType
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.MapDefinition
import com.iceja.rts.server.catalog.PartFlag
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.WeaponStats

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
    private val creatures = mutableListOf<Creature>()
    private val projectiles = mutableListOf<Projectile>()
    private val eventBuffer = mutableListOf<SimEvent>()
    private val damageByPlayer = LongArray(2)
    private val killsByPlayer = IntArray(2)
    private val lossesByPlayer = IntArray(2)
    private val genomes: List<Genome>
    private var nextProjectileId = 100_000
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

    fun projectiles(): List<Projectile> = projectiles

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
        updateAi()
        updateMovement()
        resolveCollisions()
        resolveCombat()
        updateProjectiles()
        applyStatusEffects()
        updateEnergy()
        updateRegeneration()
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
                angle = 0,
                kind = if (projectile.hit) 1 else 0,
                ttl = projectile.ttlTicks.coerceAtLeast(0)
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
        return Snapshot(
            tick = tick,
            serverTimeMs = serverTimeMs,
            matchId = matchId,
            entities = entities,
            projectiles = projectileSnapshots,
            hives = hiveSnapshots,
            scores = listOf(
                damageByPlayer[0].coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                damageByPlayer[1].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            ),
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
                    canFly = GameCatalog.chassisById[unit.genome.chassisId]?.canFly ?: false
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
            val friends = creatures.filter { it.ownerId == creature.ownerId }
            val enemies = creatures.filter { it.ownerId != creature.ownerId }
            val ownHive = hiveFor(creature.ownerId)
            val enemyHive = enemyHiveFor(creature.ownerId)
            val decision = if (creature.genome.behavior.graph == null) {
                BehaviorEngine.decide(
                    creature = creature,
                    friends = friends,
                    enemies = enemies,
                    ownHive = ownHive,
                    enemyHive = enemyHive,
                    tick = tick
                )
            } else {
                BehaviorGraphInterpreter.decide(
                    graph = creature.genome.behavior.graph,
                    creature = creature,
                    friends = friends,
                    enemies = enemies,
                    ownHive = ownHive,
                    enemyHive = enemyHive,
                    tick = tick,
                    random = aiRandom
                )
            }
            creature.aiMode = decision.mode
            creature.aiReason = decision.reason
            creature.aiNodeId = decision.nodeId
            creature.targetId = decision.targetId
            creature.attackTargetId = decision.attackTargetId
            creature.attackHive = decision.attackHive
            decision.moveTarget?.let { target ->
                creature.desiredX = target.x
                creature.desiredY = target.y
            }
            decision.fleeFrom?.let { source ->
                creature.fleeFromX = source.x
                creature.fleeFromY = source.y
            }
            creature.lastDecisionTick = tick
        }
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

            val targetPoint = attackTargetPoint(creature)
            val goal = when {
                targetPoint != null -> targetPoint
                creature.desiredX != 0 || creature.desiredY != 0 -> Vec2(creature.desiredX, creature.desiredY)
                else -> null
            } ?: run {
                creature.state = EntityState.IDLE
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
            }
        }
    }

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
            IntegerMath.distance(creature.x, creature.y, hive.x, hive.y) <= weapon + hive.radiusMm
        } else {
            val target = creatureById(targetId) ?: return false
            IntegerMath.distance(creature.x, creature.y, goal.x, goal.y) <= weapon + target.stats.radiusMm
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
            )
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
                val step = IntegerMath.stepToward(
                    projectile.x,
                    projectile.y,
                    targetX,
                    targetY,
                    projectile.speedMmPerTick
                )
                projectile.x = step.x
                projectile.y = step.y
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
        target.lastDamageTick = tick
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
        lossesByPlayer[target.ownerId] += 1
        if (sourceOwner >= 0) {
            killsByPlayer[sourceOwner] += 1
        }
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
                val firstAlive = creatures.any { it.alive && it.ownerId == 0 }
                val secondAlive = creatures.any { it.alive && it.ownerId == 1 }
                when {
                    !firstAlive && !secondAlive -> finish(-1, "ANNIHILATION")
                    !firstAlive -> finish(1, "ANNIHILATION")
                    !secondAlive -> finish(0, "ANNIHILATION")
                    tick >= config.maxTicks + config.overtimeTicks -> finishTimeout()
                }
            }
        }
    }

    private fun finishTimeout() {
        val firstHiveHp = hives[0].hp
        val secondHiveHp = hives[1].hp
        if (firstHiveHp != secondHiveHp) {
            finish(if (firstHiveHp > secondHiveHp) 0 else 1, "TIMEOUT_HIVE_HP")
            return
        }
        val firstCreatureHp = creatures.filter { it.ownerId == 0 }.sumOf { it.hp }
        val secondCreatureHp = creatures.filter { it.ownerId == 1 }.sumOf { it.hp }
        if (firstCreatureHp != secondCreatureHp) {
            finish(if (firstCreatureHp > secondCreatureHp) 0 else 1, "TIMEOUT_CREATURE_HP")
            return
        }
        if (damageByPlayer[0] != damageByPlayer[1]) {
            finish(if (damageByPlayer[0] > damageByPlayer[1]) 0 else 1, "TIMEOUT_DAMAGE")
            return
        }
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
