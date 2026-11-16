package com.iceja.rts.server.sim

import kotlin.test.Test
import kotlin.test.assertEquals

class CorpseLifetimeTest {
    @Test
    fun corpseLifetimeIsFiveTimesLonger() {
        assertEquals(4_500, Corpse(1, 0, 0, 10_000, 10_000).remainingTicks)
    }
}
