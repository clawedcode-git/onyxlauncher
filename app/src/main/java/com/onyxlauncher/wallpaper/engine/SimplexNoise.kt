package com.onyxlauncher.wallpaper.engine

import kotlin.math.floor

/**
 * Deterministic 2D/3D simplex-style gradient noise.
 *
 * Seeded from a [Long] so a given seed always reproduces the same field — this is
 * what makes a generated wallpaper "look" reproducible. Pure Kotlin, no Android
 * dependencies, so it is trivially unit-testable and runs off the main thread.
 *
 * Based on Stefan Gustavson's public-domain simplex noise, with the permutation
 * table shuffled deterministically from the seed.
 */
class SimplexNoise(seed: Long) {

    private val perm = IntArray(512)
    private val permMod12 = IntArray(512)

    init {
        val p = IntArray(256) { it }
        // Deterministic Fisher–Yates shuffle driven by a xorshift PRNG.
        var state = seed xor 0x2545F4914F6CDD1DuL.toLong()
        if (state == 0L) state = 0x9E3779B97F4A7C15uL.toLong()
        for (i in 255 downTo 1) {
            state = xorshift(state)
            val j = ((state ushr 1) % (i + 1)).toInt()
            val tmp = p[i]; p[i] = p[j]; p[j] = tmp
        }
        for (i in 0 until 512) {
            perm[i] = p[i and 255]
            permMod12[i] = perm[i] % 12
        }
    }

    private fun xorshift(x0: Long): Long {
        var x = x0
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return x
    }

    /** 2D simplex noise in roughly [-1, 1]. */
    fun noise(xin: Double, yin: Double): Double {
        val n0: Double; val n1: Double; val n2: Double
        val s = (xin + yin) * F2
        val i = floor(xin + s).toInt()
        val j = floor(yin + s).toInt()
        val t = (i + j) * G2
        val x0 = xin - (i - t)
        val y0 = yin - (j - t)

        val i1: Int; val j1: Int
        if (x0 > y0) { i1 = 1; j1 = 0 } else { i1 = 0; j1 = 1 }

        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1.0 + 2.0 * G2
        val y2 = y0 - 1.0 + 2.0 * G2

        val ii = i and 255
        val jj = j and 255
        val gi0 = permMod12[ii + perm[jj]]
        val gi1 = permMod12[ii + i1 + perm[jj + j1]]
        val gi2 = permMod12[ii + 1 + perm[jj + 1]]

        var t0 = 0.5 - x0 * x0 - y0 * y0
        n0 = if (t0 < 0) 0.0 else { t0 *= t0; t0 * t0 * dot(GRAD3[gi0], x0, y0) }

        var t1 = 0.5 - x1 * x1 - y1 * y1
        n1 = if (t1 < 0) 0.0 else { t1 *= t1; t1 * t1 * dot(GRAD3[gi1], x1, y1) }

        var t2 = 0.5 - x2 * x2 - y2 * y2
        n2 = if (t2 < 0) 0.0 else { t2 *= t2; t2 * t2 * dot(GRAD3[gi2], x2, y2) }

        return 70.0 * (n0 + n1 + n2)
    }

    /**
     * Fractal Brownian motion: sum [octaves] of noise at increasing frequency.
     * Returns roughly [-1, 1]. Higher [octaves] adds fine detail.
     */
    fun fbm(x: Double, y: Double, octaves: Int = 4, lacunarity: Double = 2.0, gain: Double = 0.5): Double {
        var amp = 0.5
        var freq = 1.0
        var sum = 0.0
        var norm = 0.0
        repeat(octaves) {
            sum += amp * noise(x * freq, y * freq)
            norm += amp
            amp *= gain
            freq *= lacunarity
        }
        return if (norm == 0.0) 0.0 else sum / norm
    }

    private fun dot(g: IntArray, x: Double, y: Double): Double = g[0] * x + g[1] * y

    companion object {
        private val F2 = 0.5 * (Math.sqrt(3.0) - 1.0)
        private val G2 = (3.0 - Math.sqrt(3.0)) / 6.0
        private val GRAD3 = arrayOf(
            intArrayOf(1, 1, 0), intArrayOf(-1, 1, 0), intArrayOf(1, -1, 0), intArrayOf(-1, -1, 0),
            intArrayOf(1, 0, 1), intArrayOf(-1, 0, 1), intArrayOf(1, 0, -1), intArrayOf(-1, 0, -1),
            intArrayOf(0, 1, 1), intArrayOf(0, -1, 1), intArrayOf(0, 1, -1), intArrayOf(0, -1, -1),
        )
    }
}

/** Small deterministic PRNG for picking seeds / jittering positions. */
class SeededRandom(seed: Long) {
    private var state: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        return state
    }

    /** Uniform double in [0, 1). */
    fun nextDouble(): Double = ((nextLong() ushr 11).toDouble()) / (1L shl 53).toDouble()

    /** Uniform float in [0, 1). */
    fun nextFloat(): Float = nextDouble().toFloat()

    /** Uniform int in [from, until). */
    fun nextInt(from: Int, until: Int): Int {
        if (until <= from) return from
        val range = (until - from).toLong()
        return (from + ((nextLong() ushr 1) % range)).toInt()
    }
}
