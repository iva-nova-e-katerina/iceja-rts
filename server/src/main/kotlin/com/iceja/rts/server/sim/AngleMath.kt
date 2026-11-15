package com.iceja.rts.server.sim

import kotlin.math.atan2
import kotlin.math.roundToInt

object AngleMath {
    private const val FULL_TURN = 65_536.0
    private const val TWO_PI = Math.PI * 2.0

    fun of(dx: Int, dy: Int): Int {
        if (dx == 0 && dy == 0) {
            return 0
        }
        val radians = atan2(dy.toDouble(), dx.toDouble())
        val units = (radians / TWO_PI * FULL_TURN).roundToInt()
        return units and 0xFFFF
    }
}
