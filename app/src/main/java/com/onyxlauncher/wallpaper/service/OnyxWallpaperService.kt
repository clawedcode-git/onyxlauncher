package com.onyxlauncher.wallpaper.service

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Live wallpaper service stub — full implementation in Phase 5.
 * Declares the service so the APK is valid for Phase 1 install.
 */
class OnyxWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = OnyxEngine()

    inner class OnyxEngine : Engine() {
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            drawBackground(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) drawBackground(surfaceHolder)
        }

        private fun drawBackground(holder: SurfaceHolder) {
            val canvas = holder.lockCanvas() ?: return
            canvas.drawColor(android.graphics.Color.parseColor("#111114"))
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
