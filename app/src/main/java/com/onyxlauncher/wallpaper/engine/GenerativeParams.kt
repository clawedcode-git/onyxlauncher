package com.onyxlauncher.wallpaper.engine

/**
 * Aggregate, non-identifying usage signals derived from UsageStatsManager.
 * Never holds per-app content — only counts and totals.
 */
data class UsageSignal(
    val totalForegroundMinutes: Int = 0,
    val distinctApps: Int = 0,
    val busiestHour: Int = -1,      // 0..23, -1 = unknown
    val intensity: Float = 0f,      // 0..1, "how active right now"
) {
    companion object {
        /** Calm defaults used when usage access is denied or unavailable. */
        val CALM = UsageSignal(totalForegroundMinutes = 0, distinctApps = 0, busiestHour = -1, intensity = 0f)
    }
}

/**
 * The knobs every wallpaper style reads. All in [0,1].
 *
 *  complexity      → noise octaves / facet count / mesh resolution
 *  contrast        → spread between palette extremes, value range
 *  energy          → flow speed, movement amplitude, animation rate
 *  particleDensity → number of particles / streamlines
 *  warmth          → warm↔cool palette bias (from time of day)
 */
data class GenerativeParams(
    val complexity: Float = 0.5f,
    val contrast: Float = 0.5f,
    val energy: Float = 0.4f,
    val particleDensity: Float = 0.5f,
    val warmth: Float = 0.5f,
) {
    fun clamped() = GenerativeParams(
        complexity = complexity.coerceIn(0f, 1f),
        contrast = contrast.coerceIn(0f, 1f),
        energy = energy.coerceIn(0f, 1f),
        particleDensity = particleDensity.coerceIn(0f, 1f),
        warmth = warmth.coerceIn(0f, 1f),
    )
}

/**
 * Maps aggregate usage → generative parameters.
 *
 * Design intent (and what the tests assert):
 *   heavier usage  → more energetic flow, higher contrast, more particles
 *   a calm day     → smoother gradients, softer movement
 *
 * Pure function, fully unit-testable, no Android dependencies.
 */
object UsageSignalMapper {

    // Reference ceilings used for normalisation.
    private const val HEAVY_FOREGROUND_MIN = 360f  // 6h of screen time → "heavy"
    private const val MANY_APPS = 25f              // 25 distinct apps → "busy"

    fun map(signal: UsageSignal, warmth: Float = 0.5f): GenerativeParams {
        val usageLoad = (signal.totalForegroundMinutes / HEAVY_FOREGROUND_MIN).coerceIn(0f, 1f)
        val appSpread = (signal.distinctApps / MANY_APPS).coerceIn(0f, 1f)
        val intensity = signal.intensity.coerceIn(0f, 1f)

        // Energy is driven mostly by live intensity, lifted by total usage.
        val energy = lerp(0.25f, 1f, (0.6f * intensity + 0.4f * usageLoad))
        // Contrast rises with overall load.
        val contrast = lerp(0.30f, 0.95f, (0.5f * usageLoad + 0.5f * intensity))
        // Complexity grows with the variety of apps used.
        val complexity = lerp(0.30f, 1f, appSpread)
        // More particles/streamlines when busier.
        val particles = lerp(0.30f, 1f, (0.5f * usageLoad + 0.5f * appSpread))

        return GenerativeParams(
            complexity = complexity,
            contrast = contrast,
            energy = energy,
            particleDensity = particles,
            warmth = warmth.coerceIn(0f, 1f),
        ).clamped()
    }

    /** Parameters when usage signals are disabled/unavailable — calm and soft. */
    fun calm(warmth: Float = 0.5f) = GenerativeParams(
        complexity = 0.35f,
        contrast = 0.35f,
        energy = 0.30f,
        particleDensity = 0.35f,
        warmth = warmth.coerceIn(0f, 1f),
    )

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}
