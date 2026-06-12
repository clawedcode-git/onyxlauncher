package com.onyxlauncher

import com.onyxlauncher.wallpaper.engine.DayPhase
import com.onyxlauncher.wallpaper.engine.SeededRandom
import com.onyxlauncher.wallpaper.engine.SimplexNoise
import com.onyxlauncher.wallpaper.engine.TimeOfDayPalette
import com.onyxlauncher.wallpaper.engine.UsageSignal
import com.onyxlauncher.wallpaper.engine.UsageSignalMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-Kotlin engine tests — no Android, no Robolectric needed. */
class WallpaperEngineTest {

    // ── determinism ──────────────────────────────────────────────────────────
    @Test
    fun `same seed reproduces identical noise field`() {
        val a = SimplexNoise(42L)
        val b = SimplexNoise(42L)
        for (i in 0 until 50) {
            val x = i * 0.137; val y = i * 0.091
            assertEquals(a.noise(x, y), b.noise(x, y), 1e-12)
            assertEquals(a.fbm(x, y), b.fbm(x, y), 1e-12)
        }
    }

    @Test
    fun `different seeds diverge`() {
        val a = SimplexNoise(1L)
        val b = SimplexNoise(2L)
        var anyDifferent = false
        for (i in 0 until 50) {
            if (a.noise(i * 0.3, i * 0.7) != b.noise(i * 0.3, i * 0.7)) { anyDifferent = true; break }
        }
        assertTrue("Different seeds should produce different fields", anyDifferent)
    }

    @Test
    fun `noise stays within expected bounds`() {
        val n = SimplexNoise(7L)
        for (i in 0 until 1000) {
            val v = n.noise(i * 0.05, i * 0.03)
            assertTrue("noise out of range: $v", v in -1.2..1.2)
        }
    }

    @Test
    fun `seeded random is reproducible and bounded`() {
        val r1 = SeededRandom(99L)
        val r2 = SeededRandom(99L)
        repeat(100) {
            val d = r1.nextDouble()
            assertEquals(d, r2.nextDouble(), 1e-12)
            assertTrue(d in 0.0..1.0)
        }
        repeat(100) {
            val i = r1.nextInt(5, 10)
            assertTrue(i in 5..9)
        }
    }

    // ── time of day palette ──────────────────────────────────────────────────
    @Test
    fun `palette has four colors at every time`() {
        for (frac in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.79, 0.95)) {
            assertEquals(4, TimeOfDayPalette.paletteFor(frac).colors.size)
        }
    }

    @Test
    fun `palette changes across the day`() {
        val night = TimeOfDayPalette.paletteFor(0.02).colors
        val noon = TimeOfDayPalette.paletteFor(0.5).colors
        assertNotEquals(night, noon)
    }

    @Test
    fun `phase classification is sensible`() {
        assertEquals(DayPhase.NIGHT, TimeOfDayPalette.phaseFor(0.02))
        assertEquals(DayPhase.DAWN, TimeOfDayPalette.phaseFor(0.25))
        assertEquals(DayPhase.DAY, TimeOfDayPalette.phaseFor(0.5))
        assertEquals(DayPhase.NIGHT, TimeOfDayPalette.phaseFor(0.95))
    }

    @Test
    fun `lerpColor endpoints and midpoint`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(black, TimeOfDayPalette.lerpColor(black, white, 0.0))
        assertEquals(white, TimeOfDayPalette.lerpColor(black, white, 1.0))
        val mid = TimeOfDayPalette.lerpColor(black, white, 0.5)
        assertEquals(127, mid and 0xFF)
    }

    @Test
    fun `nowFraction stays within a day`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        val f = TimeOfDayPalette.nowFraction(cal)
        assertEquals(0.5, f, 0.01)
    }

    // ── usage signal mapping ─────────────────────────────────────────────────
    @Test
    fun `heavier usage yields more energy contrast and particles`() {
        val calm = UsageSignalMapper.map(UsageSignal(totalForegroundMinutes = 10, distinctApps = 2, intensity = 0.05f))
        val heavy = UsageSignalMapper.map(UsageSignal(totalForegroundMinutes = 400, distinctApps = 30, intensity = 0.9f))

        assertTrue("energy", heavy.energy > calm.energy)
        assertTrue("contrast", heavy.contrast > calm.contrast)
        assertTrue("particles", heavy.particleDensity > calm.particleDensity)
        assertTrue("complexity", heavy.complexity > calm.complexity)
    }

    @Test
    fun `params are always clamped to unit range`() {
        val extreme = UsageSignalMapper.map(
            UsageSignal(totalForegroundMinutes = 99999, distinctApps = 9999, intensity = 5f),
        )
        listOf(extreme.energy, extreme.contrast, extreme.complexity, extreme.particleDensity, extreme.warmth)
            .forEach { assertTrue("clamped: $it", it in 0f..1f) }
    }

    @Test
    fun `calm defaults are low energy`() {
        val calm = UsageSignalMapper.calm()
        assertTrue(calm.energy < 0.5f)
        assertTrue(calm.contrast < 0.5f)
    }

    @Test
    fun `warmth is carried through to params`() {
        val p = UsageSignalMapper.map(UsageSignal.CALM, warmth = 0.9f)
        assertEquals(0.9f, p.warmth, 1e-6f)
    }
}
