package com.onyxlauncher.wallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import com.onyxlauncher.domain.model.WallpaperPreset
import com.onyxlauncher.domain.model.WallpaperStyle
import com.onyxlauncher.wallpaper.engine.style.FlowFieldStyle
import com.onyxlauncher.wallpaper.engine.style.GeometricFacetsStyle
import com.onyxlauncher.wallpaper.engine.style.GradientMeshStyle

/**
 * Orchestrates palette + parameters + style into a rendered frame.
 *
 * Deterministic from (preset.seed, palette, params, time). All heavy work happens
 * off the main thread by the caller; this class only touches Canvas/Bitmap.
 */
class WallpaperGenerator {

    private val renderers = mapOf(
        WallpaperStyle.GRADIENT_MESH to GradientMeshStyle(),
        WallpaperStyle.FLOW_FIELD to FlowFieldStyle(),
        WallpaperStyle.GEOMETRIC_FACETS to GeometricFacetsStyle(),
    )

    /**
     * Build the palette for a preset:
     *   locked palette if the preset pins one, otherwise the time-of-day palette.
     */
    fun paletteFor(
        preset: WallpaperPreset,
        dayFraction: Double,
        sunrise: Double = 0.25,
        sunset: Double = 0.79,
    ): Palette = preset.lockedPalette
        ?.takeIf { it.isNotEmpty() }
        ?.let { Palette(it) }
        ?: TimeOfDayPalette.paletteFor(dayFraction, sunrise, sunset)

    /** Warmth bias 0..1 derived from the day phase (warm at golden hour/dawn). */
    fun warmthFor(dayFraction: Double, sunrise: Double = 0.25, sunset: Double = 0.79): Float =
        when (TimeOfDayPalette.phaseFor(dayFraction, sunrise, sunset)) {
            DayPhase.DAWN -> 0.8f
            DayPhase.GOLDEN_HOUR -> 0.95f
            DayPhase.DAY -> 0.5f
            DayPhase.DUSK -> 0.35f
            DayPhase.NIGHT -> 0.15f
        }

    /**
     * Render a full frame into a freshly allocated [Bitmap].
     * Caller owns/recycles the result.
     */
    fun render(
        preset: WallpaperPreset,
        width: Int,
        height: Int,
        params: GenerativeParams,
        palette: Palette,
        time: Float = 0f,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ctx = RenderContext(seed = preset.seed, palette = palette, params = params.clamped(), time = time)
        (renderers[preset.style] ?: renderers.getValue(WallpaperStyle.GRADIENT_MESH))
            .render(canvas, width, height, ctx)
        return bmp
    }

    /** Render onto an existing canvas (used by the live wallpaper engine). */
    fun renderTo(
        canvas: Canvas,
        width: Int,
        height: Int,
        preset: WallpaperPreset,
        params: GenerativeParams,
        palette: Palette,
        time: Float,
    ) {
        val ctx = RenderContext(seed = preset.seed, palette = palette, params = params.clamped(), time = time)
        (renderers[preset.style] ?: renderers.getValue(WallpaperStyle.GRADIENT_MESH))
            .render(canvas, width, height, ctx)
    }
}
