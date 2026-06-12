package com.onyxlauncher.data

import android.content.Context
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.HomeItemMapper.toEntity
import com.onyxlauncher.data.db.dao.HomeItemDao
import com.onyxlauncher.data.`package`.PackageMonitor
import com.onyxlauncher.data.wallpaper.WallpaperRepository
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.HomeItem
import com.onyxlauncher.domain.model.Settings
import com.onyxlauncher.domain.model.WallpaperPreset
import com.onyxlauncher.domain.model.WallpaperStyle
import com.onyxlauncher.wallpaper.engine.TimeOfDayPalette
import com.onyxlauncher.wallpaper.engine.UsageSignalMapper
import com.onyxlauncher.wallpaper.engine.WallpaperGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-time first-run setup so the launcher never greets the user with an
 * empty black screen:
 *   - dock: dialer / messages / browser / camera / settings (best-effort picks)
 *   - home page 0: remaining apps, alphabetical, row-major
 *   - a freshly generated Onyx wallpaper applied to home + lock
 *
 * Guarded by the homeSeeded settings flag; never touches a non-empty layout.
 */
class HomeSeeder(
    private val context: Context,
    private val homeItemDao: HomeItemDao,
    private val settingsRepository: SettingsRepository,
    private val packageMonitor: PackageMonitor,
    private val wallpaperRepository: WallpaperRepository,
    private val generator: WallpaperGenerator,
) {

    suspend fun seedIfFirstRun() {
        val settings = settingsRepository.settings.first()
        if (settings.homeSeeded) return

        // A layout already exists (e.g. restored DB) — mark done, never stomp it.
        if (homeItemDao.observeAll().first().isNotEmpty()) {
            settingsRepository.update { homeSeeded = true }
            return
        }

        // Wait for the package monitor's first real app list (≤10 s, else retry next launch).
        val apps = withTimeoutOrNull(10_000L) {
            packageMonitor.apps.first { it.isNotEmpty() }
        } ?: return

        seedLayout(apps.filter { !it.isHidden }, settings)
        applyFirstWallpaper()
        settingsRepository.update { homeSeeded = true }
    }

    // ── layout ───────────────────────────────────────────────────────────────

    private suspend fun seedLayout(apps: List<App>, settings: Settings) {
        if (apps.isEmpty()) return
        val cols = settings.homeColumns
        val rows = settings.homeRows
        val dockSlots = settings.dockSlots

        // Best-effort role picks for the dock; package-name heuristics are
        // intentionally loose — any miss just leaves room for alphabetical fill.
        fun pick(vararg needles: String): App? = apps.firstOrNull { app ->
            needles.any { app.packageName.contains(it, ignoreCase = true) }
        }

        val rolePicks = listOfNotNull(
            pick("dialer", "com.android.phone"),
            pick("messaging", ".messages", "mms"),
            pick("chrome", "browser", "firefox"),
            pick("camera"),
            pick("com.android.settings"),
        ).distinctBy { it.key }

        // Top-up the dock alphabetically if role picks came up short.
        val dock = (rolePicks + apps.filter { a -> rolePicks.none { it.key == a.key } })
            .distinctBy { it.key }
            .take(dockSlots)
        val dockKeys = dock.map { it.key }.toSet()

        // Page 0: everything else, alphabetical (PackageMonitor pre-sorts), row-major.
        val pageApps = apps.filter { it.key !in dockKeys }.take(cols * rows)

        val entities = buildList {
            dock.forEachIndexed { slot, app ->
                add(
                    HomeItem.Shortcut(
                        id = 0, page = -1, gridX = slot, gridY = 0,
                        componentName = app.componentName, userSerial = app.userSerial,
                    ).toEntity()
                )
            }
            pageApps.forEachIndexed { idx, app ->
                add(
                    HomeItem.Shortcut(
                        id = 0, page = 0, gridX = idx % cols, gridY = idx / cols,
                        componentName = app.componentName, userSerial = app.userSerial,
                    ).toEntity()
                )
            }
        }
        homeItemDao.insertAll(entities)
    }

    // ── signature wallpaper ──────────────────────────────────────────────────

    private suspend fun applyFirstWallpaper() {
        runCatching {
            val dm = context.resources.displayMetrics
            val preset = WallpaperPreset(
                seed = System.currentTimeMillis(),
                style = WallpaperStyle.GRADIENT_MESH,
            )
            val day = TimeOfDayPalette.nowFraction()
            val palette = generator.paletteFor(preset, day)
            val params = UsageSignalMapper.calm(generator.warmthFor(day))
            val bmp = generator.render(
                preset,
                dm.widthPixels.coerceAtLeast(720),
                dm.heightPixels.coerceAtLeast(1280),
                params,
                palette,
            )
            wallpaperRepository.applyWallpaper(bmp, WallpaperRepository.Target.BOTH)
            val id = wallpaperRepository.save(preset)
            settingsRepository.update { activePresetId = id }
        }
    }
}
