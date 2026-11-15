package com.iceja.rts.server.sim

data class Vec2(
    val x: Int,
    val y: Int
)

data class Step(
    val x: Int,
    val y: Int
)

object IntegerMath {
    fun distanceSq(ax: Int, ay: Int, bx: Int, by: Int): Long {
        val dx = bx.toLong() - ax.toLong()
        val dy = by.toLong() - ay.toLong()
        return dx * dx + dy * dy
    }

    fun distance(ax: Int, ay: Int, bx: Int, by: Int): Int =
        isqrt(distanceSq(ax, ay, bx, by))

    fun isqrt(value: Long): Int {
        if (value <= 0L) {
            return 0
        }
        var x = value
        var y = (x + 1L) / 2L
        while (y < x) {
            x = y
            y = (x + value / x) / 2L
        }
        return x.toInt()
    }

    fun stepToward(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        maxStep: Int
    ): Step {
        val dx = toX.toLong() - fromX.toLong()
        val dy = toY.toLong() - fromY.toLong()
        val distance = isqrt(dx * dx + dy * dy)
        if (distance == 0) {
            return Step(fromX, fromY)
        }
        if (distance <= maxStep) {
            return Step(toX, toY)
        }
        val nextX = fromX + (dx * maxStep / distance).toInt()
        val nextY = fromY + (dy * maxStep / distance).toInt()
        return Step(nextX, nextY)
    }

    fun clamp(minimum: Int, maximum: Int, value: Int): Int =
        value.coerceIn(minimum, maximum)
}
