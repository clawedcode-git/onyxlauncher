package com.onyxlauncher.data.wallpaper

import android.app.AppOpsManager
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import com.onyxlauncher.data.db.WallpaperPresetMapper.toDomain
import com.onyxlauncher.data.db.WallpaperPresetMapper.toEntity
import com.onyxlauncher.data.db.dao.WallpaperPresetDao
import com.onyxlauncher.domain.model.WallpaperPreset
import com.onyxlauncher.wallpaper.engine.UsageSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Wallpaper persistence + system integration:
 *   - saved presets / favourites (Room)
 *   - applying a bitmap to the system wallpaper (home / lock)
 *   - reading aggregate usage signals (UsageStatsManager), with graceful denial
 */
class WallpaperRepository(
    private val context: Context,
    private val presetDao: WallpaperPresetDao,
) {
    // ── presets ──────────────────────────────────────────────────────────────
    val presets: Flow<List<WallpaperPreset>> =
        presetDao.observeAll().map { list -> list.map { it.toDomain() } }

    val favorites: Flow<List<WallpaperPreset>> =
        presetDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    suspend fun save(preset: WallpaperPreset): Long = withContext(Dispatchers.IO) {
        presetDao.insert(preset.toEntity())
    }

    suspend fun update(preset: WallpaperPreset) = withContext(Dispatchers.IO) {
        presetDao.update(preset.toEntity())
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { presetDao.deleteById(id) }

    suspend fun setFavorite(preset: WallpaperPreset, favorite: Boolean) =
        update(preset.copy(isFavorite = favorite))

    // ── apply to system ───────────────────────────────────────────────────────
    enum class Target { HOME, LOCK, BOTH }

    /** Set [bitmap] as the wallpaper for the chosen [target]. Off the main thread. */
    suspend fun applyWallpaper(bitmap: Bitmap, target: Target) = withContext(Dispatchers.IO) {
        val wm = WallpaperManager.getInstance(context)
        when (target) {
            Target.HOME -> wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            Target.LOCK -> wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            Target.BOTH -> {
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            }
        }
    }

    // ── usage signals ──────────────────────────────────────────────────────────

    /** True if the user has granted Usage Access to this app. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = @Suppress("DEPRECATION") appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Aggregate, non-identifying usage for today. Returns [UsageSignal.CALM] when
     * access is denied — the generator simply falls back to time-of-day only.
     */
    suspend fun readUsageSignal(): UsageSignal = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext UsageSignal.CALM

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext UsageSignal.CALM

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            ?: return@withContext UsageSignal.CALM

        var totalMs = 0L
        var distinct = 0
        val hourBuckets = IntArray(24)
        for (s in stats) {
            val fg = s.totalTimeInForeground
            if (fg > 0) {
                totalMs += fg
                distinct++
                val h = Calendar.getInstance().apply { timeInMillis = s.lastTimeUsed }
                    .get(Calendar.HOUR_OF_DAY)
                hourBuckets[h] += (fg / 60000L).toInt()
            }
        }
        val busiest = hourBuckets.indices.maxByOrNull { hourBuckets[it] } ?: -1
        val totalMin = (totalMs / 60000L).toInt()

        // "intensity" = share of the last hour spent in the foreground.
        val lastHourStart = now - 3_600_000L
        val recent = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, lastHourStart, now)
            ?.sumOf { if (it.lastTimeUsed >= lastHourStart) it.totalTimeInForeground else 0L } ?: 0L
        val intensity = (recent.toFloat() / 3_600_000f).coerceIn(0f, 1f)

        UsageSignal(
            totalForegroundMinutes = totalMin,
            distinctApps = distinct,
            busiestHour = busiest,
            intensity = intensity,
        )
    }
}
