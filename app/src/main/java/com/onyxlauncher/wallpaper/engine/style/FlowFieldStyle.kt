package com.onyxlauncher.wallpaper.engine.style

import android.graphics.Canvas
import android.graphics.Paint
import com.onyxlauncher.wallpaper.engine.RenderContext
import com.onyxlauncher.wallpaper.engine.SeededRandom
import com.onyxlauncher.wallpaper.engine.SimplexNoise
import com.onyxlauncher.wallpaper.engine.WallpaperRenderer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Layered noise flow field.
 *
 * Seeds many particles, each advected through a simplex-noise vector field, leaving
 * soft fading streamlines. Heavier usage (energy / particleDensity) → more, longer,
 * faster streamlines and higher contrast; a calm day → few soft strokes.
 */
class FlowFieldStyle : WallpaperRenderer {

    override fun render(canvas: Canvas, width: Int, height: Int, ctx: RenderContext) {
        val noise = SimplexNoise(ctx.seed)
        val rnd = SeededRandom(ctx.seed xor 0x5DEECE66DL)
        val colors = ctx.palette.colors

        // Soft dark base from the darkest palette colour so strokes read clearly.
        canvas.drawColor(colors.first())

        val particleCount = (120 + ctx.params.particleDensity * 520).toInt()
        val steps = (40 + ctx.params.energy * 120).toInt()
        val stepLen = (width * 0.0015f) * (0.6f + ctx.params.energy)
        val freq = 0.0016 * (0.7 + ctx.params.complexity)
        val timePhase = ctx.time * 0.06

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = (width * 0.0016f) * (0.8f + ctx.params.contrast)
            style = Paint.Style.STROKE
        }

        val alpha = (16 + ctx.params.contrast * 44).toInt().coerceIn(10, 90)

        for (p in 0 until particleCount) {
            var x = rnd.nextFloat() * width
            var y = rnd.nextFloat() * height
            // Strokes use the brighter palette entries; index 0 is the dark base.
            val firstStrokeColor = if (colors.size > 1) 1 else 0
            val color = colors[rnd.nextInt(firstStrokeColor, colors.size)]
            paint.color = (alpha shl 24) or (color and 0x00FFFFFF)

            var px = x
            var py = y
            for (s in 0 until steps) {
                val angle = noise.fbm(
                    x * freq + timePhase,
                    y * freq - timePhase,
                    octaves = 3,
                ) * Math.PI * 2.0
                x += (cos(angle) * stepLen).toFloat()
                y += (sin(angle) * stepLen).toFloat()
                if (x < -10 || x > width + 10 || y < -10 || y > height + 10) break
                canvas.drawLine(px, py, x, y, paint)
                px = x; py = y
            }
        }
    }
}
