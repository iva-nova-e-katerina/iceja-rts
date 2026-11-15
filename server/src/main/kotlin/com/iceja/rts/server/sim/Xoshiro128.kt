package com.iceja.rts.server.sim

class Xoshiro128 private constructor(
    private var state0: Int,
    private var state1: Int,
    private var state2: Int,
    private var state3: Int
) {
    fun nextInt(): Int {
        val result = Integer.rotateLeft(state1 * 5, 7) * 9
        val temporary = state1 shl 9
        state2 = state2 xor state0
        state3 = state3 xor state1
        state1 = state1 xor state2
        state0 = state0 xor state3
        state2 = state2 xor temporary
        state3 = Integer.rotateLeft(state3, 11)
        return result
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive" }
        val threshold = Int.MAX_VALUE - (Int.MAX_VALUE % bound)
        while (true) {
            val value = nextInt() ushr 1
            if (value < threshold) {
                return value % bound
            }
        }
    }

    fun nextPercent(): Int = nextInt(100)

    companion object {
        fun fromSeed(seed: Long): Xoshiro128 {
            var state = seed
            fun splitMix64(): Long {
                state += -7046029254386353131L
                var z = state
                z = (z xor (z ushr 30)) * -4658895280553007687L
                z = (z xor (z ushr 27)) * -7723592293110705685L
                return z xor (z ushr 31)
            }

            val first = splitMix64()
            val second = splitMix64()
            return Xoshiro128(
                state0 = first.toInt(),
                state1 = (first ushr 32).toInt(),
                state2 = second.toInt(),
                state3 = (second ushr 32).toInt()
            )
        }

        fun fromSeeds(vararg seeds: Long): Xoshiro128 {
            var mixed = 0x9E3779B97F4A7C15uL.toLong()
            seeds.forEach { seed ->
                mixed = mixed xor seed
                mixed *= 0xBF58476D1CE4E5B9uL.toLong()
                mixed = mixed xor (mixed ushr 27)
            }
            return fromSeed(mixed)
        }
    }
}
