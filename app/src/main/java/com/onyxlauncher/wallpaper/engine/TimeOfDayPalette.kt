package com.onyxlauncher.wallpaper.engine

import kotlin.math.abs

/** A small ordered set of ARGB colors that a style samples across the canvas. */
data class Palette(val colors: List<Int>) {
    init { require(colors.isNotEmpty()) { "Palette must have at least one color" } }
}

enum class DayPhase { NIGHT, DAWN, DAY, GOLDEN_HOUR, DUSK }

/**
 * Continuously interpolates a colour palette across the day:
 *   night → dawn → day → golden hour → dusk → night
 *
 * Warm, soft tones near sunrise; cool, deep tones at night. Pure Kotlin (ARGB
 * int math) so the mapping is unit-testable without Android's Color class.
 */
object TimeOfDayPalette {

    // Anchor palettes (warm sunrise … cool night). Each is 4 colours.
    private val NIGHT = intArrayOf(0xFF0B1026.toInt(), 0xFF14182E.toInt(), 0xFF1B1B3A.toInt(), 0xFF241B3D.toInt())
    private val DAWN = intArrayOf(0xFF2A2342.toInt(), 0xFF7A4A6B.toInt(), 0xFFE0907E.toInt(), 0xFFF2C39A.toInt())
    private val DAY = intArrayOf(0xFF1E5A8A.toInt(), 0xFF3E8EC4.toInt(), 0xFF7FC2D9.toInt(), 0xFFCDE8EC.toInt())
    private val GOLDEN = intArrayOf(0xFF3A2A4D.toInt(), 0xFFB5563E.toInt(), 0xFFE8924A.toInt(), 0xFFF2C56B.toInt())
    private val DUSK = intArrayOf(0xFF161033.toInt(), 0xFF3D2358.toInt(), 0xFF7A2F6B.toInt(), 0xFFB85C7A.toInt())

    /**
     * @param dayFraction time of day in [0,1): 0 = midnight, 0.5 = noon.
     * @param sunriseFraction time of sunrise in [0,1) (default 0.25 ≈ 06:00).
     * @param sunsetFraction  time of sunset  in [0,1) (default 0.79 ≈ 19:00).
     */
    fun paletteFor(
        dayFraction: Double,
        sunriseFraction: Double = 0.25,
        sunsetFraction: Double = 0.79,
    ): Palette {
        val f = ((dayFraction % 1.0) + 1.0) % 1.0

        // Build anchor stops around the actual sunrise/sunset.
        val dawnCenter = sunriseFraction
        val goldenCenter = sunsetFraction - 0.06
        val duskCenter = sunsetFraction + 0.03
        val dayCenter = (sunriseFraction + sunsetFraction) / 2.0

        // Ordered (position, palette) stops across the [0,1) day, wrapping at night.
        val stops = listOf(
            0.0 to NIGHT,
            (dawnCenter - 0.06).coerceIn(0.0, 1.0) to NIGHT,
            dawnCenter to DAWN,
            (dawnCenter + 0.07).coerceIn(0.0, 1.0) to DAY,
            dayCenter to DAY,
            goldenCenter.coerceIn(0.0, 1.0) to GOLDEN,
            duskCenter.coerceIn(0.0, 1.0) to DUSK,
            (duskCenter + 0.06).coerceIn(0.0, 1.0) to NIGHT,
            1.0 to NIGHT,
        ).sortedBy { it.first }

        // Find the two stops surrounding f and lerp between them per-channel.
        var lo = stops.first()
        var hi = stops.last()
        for (k in 0 until stops.size - 1) {
            if (f >= stops[k].first && f <= stops[k + 1].first) {
                lo = stops[k]; hi = stops[k + 1]; break
            }
        }
        val span = (hi.first - lo.first).takeIf { it > 1e-9 } ?: 1.0
        val t = ((f - lo.first) / span).coerceIn(0.0, 1.0)

        val out = IntArray(4) { idx -> lerpColor(lo.second[idx], hi.second[idx], t) }
        return Palette(out.toList())
    }

    /** Discrete phase label for UI / logic. */
    fun phaseFor(
        dayFraction: Double,
        sunriseFraction: Double = 0.25,
        sunsetFraction: Double = 0.79,
    ): DayPhase {
        val f = ((dayFraction % 1.0) + 1.0) % 1.0
        return when {
            f < sunriseFraction - 0.04 || f > sunsetFraction + 0.06 -> DayPhase.NIGHT
            abs(f - sunriseFraction) <= 0.05 -> DayPhase.DAWN
            f in (sunsetFraction - 0.10)..(sunsetFraction - 0.01) -> DayPhase.GOLDEN_HOUR
            f >= sunsetFraction - 0.01 -> DayPhase.DUSK
            else -> DayPhase.DAY
        }
    }

    // ── ARGB helpers (no android.graphics dependency) ───────────────────────
    fun lerpColor(a: Int, b: Int, t: Double): Int {
        val tt = t.coerceIn(0.0, 1.0)
        val aa = (a ushr 24) and 0xFF; val ab = (b ushr 24) and 0xFF
        val ra = (a ushr 16) and 0xFF; val rb = (b ushr 16) and 0xFF
        val ga = (a ushr 8) and 0xFF;  val gb = (b ushr 8) and 0xFF
        val ba = a and 0xFF;           val bb = b and 0xFF
        val al = (aa + (ab - aa) * tt).toInt()
        val r = (ra + (rb - ra) * tt).toInt()
        val g = (ga + (gb - ga) * tt).toInt()
        val bl = (ba + (bb - ba) * tt).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Current local time as a day fraction in [0,1). */
    fun nowFraction(calendar: java.util.Calendar = java.util.Calendar.getInstance()): Double {
        val h = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val m = calendar.get(java.util.Calendar.MINUTE)
        val s = calendar.get(java.util.Calendar.SECOND)
        return (h * 3600 + m * 60 + s) / 86400.0
    }
}
