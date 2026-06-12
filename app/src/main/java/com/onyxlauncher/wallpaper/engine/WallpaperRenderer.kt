package com.onyxlauncher.wallpaper.engine

import android.graphics.Canvas

/** Everything a style needs to render one frame deterministically. */
data class RenderContext(
    val seed: Long,
    val palette: Palette,
    val params: GenerativeParams,
    /** Animation phase in seconds. 0 for a static render; advances for live wallpaper. */
    val time: Float = 0f,
)

/** A non-representational wallpaper style. Implementations must be deterministic. */
interface WallpaperRenderer {
    fun render(canvas: Canvas, width: Int, height: Int, ctx: RenderContext)
}
