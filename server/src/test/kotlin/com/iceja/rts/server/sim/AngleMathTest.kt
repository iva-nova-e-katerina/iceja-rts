package com.iceja.rts.server.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AngleMathTest {
    @Test
    fun cardinalDirectionsUseFullTurnUnits() {
        assertEquals(0, AngleMath.of(100, 0))
        assertEquals(16_384, AngleMath.of(0, 100))
        assertEquals(32_768, AngleMath.of(-100, 0))
        assertEquals(49_152, AngleMath.of(0, -100))
    }

    @Test
    fun diagonalDirectionsAreCorrect() {
        assertEquals(8_192, AngleMath.of(100, 100))
        assertEquals(24_576, AngleMath.of(-100, 100))
        assertEquals(40_960, AngleMath.of(-100, -100))
        assertEquals(57_344, AngleMath.of(100, -100))
    }

    @Test
    fun zeroVectorIsStable() {
        assertEquals(0, AngleMath.of(0, 0))
    }

    @Test
    fun angleIsAlwaysUint16() {
        val values = listOf(
            AngleMath.of(1, 1),
            AngleMath.of(-1, 3),
            AngleMath.of(0, -5),
            AngleMath.of(8, -2)
        )
        assertTrue(values.all { it in 0..65_535 })
    }
}
