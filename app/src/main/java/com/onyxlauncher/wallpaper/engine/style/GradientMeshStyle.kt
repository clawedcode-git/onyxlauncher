package com.onyxlauncher.wallpaper.engine.style

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.onyxlauncher.wallpaper.engine.RenderContext
import com.onyxlauncher.wallpaper.engine.SimplexNoise
import com.onyxlauncher.wallpaper.engine.TimeOfDayPalette
import com.onyxlauncher.wallpaper.engine.WallpaperRenderer

/**
 * Flowing gradient mesh.
 *
 * Renders a small low-resolution colour field (each cell's colour chosen from the
 * palette via fbm noise), then upscales it with bilinear filtering so it becomes a
 * smooth, organic gradient. Cheap and 120 Hz-friendly because the heavy per-pixel
 * work happens on a tiny grid.
 */
class GradientMeshStyle : WallpaperRenderer {

    override fun render(canvas: Canvas, width: Int, height: Int, ctx: RenderContext) {
        val noise = SimplexNoise(ctx.seed)
        val colors = ctx.palette.colors

        // Grid resolution scales gently with complexity (kept small for speed).
        val cols = (10 + (ctx.params.complexity * 26)).toInt().coerceIn(8, 48)
        val rows = (cols * height / width).coerceAtLeast(8)

        val small = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        val px = IntArray(cols * rows)

        val freq = 1.4 + ctx.params.complexity * 2.2
        val t = ctx.time * (0.05 + ctx.params.energy * 0.12)
        val contrast = 0.5 + ctx.params.contrast * 0.9

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val nx = x.toDouble() / cols
                val ny = y.toDouble() / rows
                // Two offset fbm fields give a swirling, non-banded blend.
                val a = noise.fbm(nx * freq + t, ny * freq, octaves = 4)
                val b = noise.fbm(nx * freq + 5.2, ny * freq - t + 1.3, octaves = 3)
                var v = 0.5 + 0.5 * (a * 0.65 + b * 0.35)
                // Push values toward extremes as contrast rises.
                v = ((v - 0.5) * contrast + 0.5).coerceIn(0.0, 1.0)
                px[y * cols + x] = samplePalette(colors, v)
            }
        }
        small.setPixels(px, 0, cols, 0, 0, cols, rows)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(small, Rect(0, 0, cols, rows), Rect(0, 0, width, height), paint)
        small.recycle()
    }

    /** Sample a continuous position across the palette with smooth interpolation. */
    private fun samplePalette(colors: List<Int>, v: Double): Int {
        if (colors.size == 1) return colors[0]
        val scaled = v.coerceIn(0.0, 1.0) * (colors.size - 1)
        val i = scaled.toInt().coerceIn(0, colors.size - 2)
        val frac = scaled - i
        return TimeOfDayPalette.lerpColor(colors[i], colors[i + 1], frac)
    }
}
