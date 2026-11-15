package com.iceja.rts.server.sim

object PatrolRoute {
    private val points = listOf(
        Vec2(48_000, 48_000),
        Vec2(48_000, 144_000),
        Vec2(76_000, 40_000),
        Vec2(76_000, 152_000),
        Vec2(96_000, 60_000),
        Vec2(96_000, 132_000),
        Vec2(120_000, 40_000),
        Vec2(120_000, 152_000)
    )

    fun point(creature: Creature, ownHive: Hive, enemyHive: Hive, tick: Int): Vec2 {
        val base = points[Math.floorMod(creature.id + tick / 600, points.size)]
        if (creature.ownerId == 0) {
            return base
        }
        return Vec2(
            x = ownHive.x + enemyHive.x - base.x,
            y = base.y
        )
    }
}
