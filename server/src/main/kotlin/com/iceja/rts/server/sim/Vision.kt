package com.iceja.rts.server.sim

object Vision {
    fun canSee(observer: Creature, target: Creature, navigation: NavigationGrid): Boolean {
        if (!target.alive) {
            return false
        }
        return canSeePoint(
            observer = observer,
            targetX = target.x,
            targetY = target.y,
            navigation = navigation
        )
    }

    fun canSeeHive(observer: Creature, hive: Hive, navigation: NavigationGrid): Boolean {
        if (!hive.alive) {
            return false
        }
        return canSeePoint(
            observer = observer,
            targetX = hive.x,
            targetY = hive.y,
            navigation = navigation
        )
    }

    private fun canSeePoint(
        observer: Creature,
        targetX: Int,
        targetY: Int,
        navigation: NavigationGrid
    ): Boolean {
        val visionRange = observer.stats.visionRangeMm
        if (visionRange <= 0) {
            return false
        }
        val dx = targetX - observer.x
        val dy = targetY - observer.y
        val distanceSq = dx.toLong() * dx + dy.toLong() * dy
        if (distanceSq > visionRange.toLong() * visionRange) {
            return false
        }
        val visionAngle = observer.stats.visionAngleDeg
        if (visionAngle <= 0) {
            return false
        }
        if (visionAngle < 360) {
            val targetAngle = AngleMath.of(dx, dy)
            val halfAngle = (visionAngle.toLong() * 65_536L / 720L).toInt()
            if (angleDistance(observer.angle, targetAngle) > halfAngle) {
                return false
            }
        }
        return navigation.hasLineOfSight(observer.x, observer.y, targetX, targetY)
    }

    private fun angleDistance(first: Int, second: Int): Int {
        val direct = kotlin.math.abs(first - second) % 65_536
        return kotlin.math.min(direct, 65_536 - direct)
    }
}
