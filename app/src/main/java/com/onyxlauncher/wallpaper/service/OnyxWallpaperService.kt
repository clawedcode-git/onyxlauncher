package com.onyxlauncher.wallpaper.service

import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.onyxlauncher.domain.model.WallpaperPreset
import com.onyxlauncher.domain.model.WallpaperStyle
import com.onyxlauncher.wallpaper.engine.GenerativeParams
import com.onyxlauncher.wallpaper.engine.Palette
import com.onyxlauncher.wallpaper.engine.TimeOfDayPalette
import com.onyxlauncher.wallpaper.engine.UsageSignalMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Live wallpaper that slowly evolves and re-tints across the day.
 *
 * Battery guards:
 *   - renders on a dedicated background HandlerThread (no main-thread jank)
 *   - low frame rate (~12 fps) — the evolution is meant to be slow
 *   - fully pauses when not visible (no callbacks scheduled)
 *   - palette + usage params recomputed only once per minute, not per frame
 */
class OnyxWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = OnyxEngine()

    inner class OnyxEngine : Engine() {

        private val app get() = applicationContext as com.onyxlauncher.OnyxLauncherApp
        private val generator get() = app.wallpaperGenerator

        private val renderThread = HandlerThread("onyx-wallpaper").apply { start() }
        private val handler = Handler(renderThread.looper)

        private val frameIntervalMs = 83L          // ≈ 12 fps
        private val recomputeIntervalMs = 60_000L   // palette / usage refresh cadence

        private var visible = false
        private var width = 1
        private var height = 1
        private var startUptime = 0L
        private var lastRecompute = 0L

        private var preset: WallpaperPreset =
            WallpaperPreset(seed = 1234L, style = WallpaperStyle.GRADIENT_MESH, isLive = true)
        private var params: GenerativeParams = UsageSignalMapper.calm(0.5f)
        private var palette: Palette = TimeOfDayPalette.paletteFor(TimeOfDayPalette.nowFraction())

        private val drawRunnable = Runnable { drawFrame() }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            width = w; height = h
            // All blocking work (DataStore, UsageStats, rendering) stays off the
            // main thread — surface callbacks arrive on the main thread.
            handler.post {
                recompute(force = true)
                renderOnce()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                startUptime = android.os.SystemClock.uptimeMillis()
                handler.post {
                    loadActivePresetBlocking()
                    recompute(force = true)
                }
                scheduleNext()
            } else {
                handler.removeCallbacks(drawRunnable)   // pause completely
            }
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            renderThread.quitSafely()
        }

        /** Blocking read of the active preset — must run on the render thread. */
        private fun loadActivePresetBlocking() {
            runCatching {
                runBlocking {
                    val activeId = app.settingsRepository.settings.first().activePresetId
                    if (activeId != null) {
                        app.wallpaperRepository.presets.first()
                            .firstOrNull { it.id == activeId }
                            ?.let { preset = it }
                    }
                }
            }
        }

        private fun scheduleNext() {
            handler.removeCallbacks(drawRunnable)
            handler.postDelayed(drawRunnable, frameIntervalMs)
        }

        private fun recompute(force: Boolean) {
            val now = android.os.SystemClock.uptimeMillis()
            if (!force && now - lastRecompute < recomputeIntervalMs) return
            lastRecompute = now

            val dayFraction = TimeOfDayPalette.nowFraction()
            palette = if (preset.lockedPalette != null) {
                Palette(preset.lockedPalette!!)
            } else {
                TimeOfDayPalette.paletteFor(dayFraction)
            }
            val warmth = generator.warmthFor(dayFraction)
            params = runCatching {
                val useUsage = runBlocking {
                    app.settingsRepository.settings.first().useUsageSignal
                } && app.wallpaperRepository.hasUsageAccess()
                if (useUsage) UsageSignalMapper.map(runBlocking { app.wallpaperRepository.readUsageSignal() }, warmth)
                else UsageSignalMapper.calm(warmth)
            }.getOrDefault(UsageSignalMapper.calm(warmth))
        }

        /** Render a single frame to the surface. Render-thread only. */
        private fun renderOnce() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val t = (android.os.SystemClock.uptimeMillis() - startUptime) / 1000f
                    generator.renderTo(canvas, width, height, preset, params, palette, t)
                }
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        /** Animation tick: render, then schedule the next frame while visible. */
        private fun drawFrame() {
            if (!visible) return
            recompute(force = false)
            renderOnce()
            if (visible) scheduleNext()
        }
    }
}
