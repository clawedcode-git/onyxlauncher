package com.onyxlauncher.wallpaper.engine.style

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.onyxlauncher.wallpaper.engine.RenderContext
import com.onyxlauncher.wallpaper.engine.SeededRandom
import com.onyxlauncher.wallpaper.engine.SimplexNoise
import com.onyxlauncher.wallpaper.engine.TimeOfDayPalette
import com.onyxlauncher.wallpaper.engine.WallpaperRenderer

/**
 * Soft geometric facets — overlapping low-poly triangles echoing the faceted
 * "onyx" aesthetic. Each facet is filled with a palette colour sampled by noise,
 * with gentle translucency so overlaps build depth. complexity → facet count.
 */
class GeometricFacetsStyle : WallpaperRenderer {

    override fun render(canvas: Canvas, width: Int, height: Int, ctx: RenderContext) {
        val noise = SimplexNoise(ctx.seed)
        val rnd = SeededRandom(ctx.seed xor 0x123456789L)
        val colors = ctx.palette.colors

        canvas.drawColor(colors.first())

        val facetCount = (24 + ctx.params.complexity * 90).toInt()
        val maxSize = width * (0.28f + ctx.params.energy * 0.22f)
        val baseAlpha = (40 + ctx.params.contrast * 70).toInt().coerceIn(30, 140)
        val drift = ctx.time * 6f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()

        for (i in 0 until facetCount) {
            val cx = rnd.nextFloat() * width
            val cy = rnd.nextFloat() * height
            val size = maxSize * (0.35f + rnd.nextFloat() * 0.65f)

            // Three vertices around the centre, jittered.
            val a0 = rnd.nextFloat() * (Math.PI * 2).toFloat()
            path.reset()
            for (v in 0 until 3) {
                val ang = a0 + v * (Math.PI * 2f / 3f).toFloat() + rnd.nextFloat() * 0.6f
                val rad = size * (0.5f + rnd.nextFloat() * 0.5f)
                val vx = cx + (Math.cos(ang.toDouble()) * rad).toFloat() + drift
                val vy = cy + (Math.sin(ang.toDouble()) * rad).toFloat()
                if (v == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
            }
            path.close()

            // Colour from palette via noise at the facet centre.
            val n = 0.5 + 0.5 * noise.fbm(cx * 0.0012, cy * 0.0012, octaves = 2)
            val color = samplePalette(colors, n)
            paint.color = (baseAlpha shl 24) or (color and 0x00FFFFFF)
            canvas.drawPath(path, paint)
        }
    }

    private fun samplePalette(colors: List<Int>, v: Double): Int {
        if (colors.size == 1) return colors[0]
        val scaled = v.coerceIn(0.0, 1.0) * (colors.size - 1)
        val i = scaled.toInt().coerceIn(0, colors.size - 2)
        return TimeOfDayPalette.lerpColor(colors[i], colors[i + 1], scaled - i)
    }
}
