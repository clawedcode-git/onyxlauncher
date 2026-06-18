package com.onyxlauncher.data.backup

import android.content.Context
import android.net.Uri
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.AppDatabase
import com.onyxlauncher.data.db.entity.AppOverrideEntity
import com.onyxlauncher.data.db.entity.FolderEntity
import com.onyxlauncher.data.db.entity.HomeItemEntity
import com.onyxlauncher.data.db.entity.WallpaperPresetEntity
import com.onyxlauncher.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports/imports the complete launcher state — settings + home layout + folders
 * + per-app overrides + wallpaper presets — as a single self-describing JSON file
 * through a user-chosen Storage Access Framework [Uri].
 *
 * Format is versioned so future schema changes can migrate on import.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }

    // ── URI-based entry points (UI) ─────────────────────────────────────────
    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = exportToJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray())
            } ?: error("Could not open output stream")
        }
    }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open input stream")
            importFromJson(text).getOrThrow()
        }
    }

    // ── JSON entry points (testable, no Android I/O) ────────────────────────
    suspend fun exportToJson(): String {
        val root = JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("app", "onyx-launcher")
            put("exportedAt", System.currentTimeMillis())
            put("settings", settingsJson(settingsRepository.settings.first()))
            put("homeItems", homeItemsJson(database.homeItemDao().getAll()))
            put("folders", foldersJson(database.folderDao().getAll()))
            put("appOverrides", overridesJson(database.appOverrideDao().getAll()))
            put("wallpaperPresets", presetsJson(database.wallpaperPresetDao().getAll()))
        }
        return root.toString(2)
    }

    suspend fun importFromJson(text: String): Result<Unit> = runCatching {
        val root = JSONObject(text)
        require(root.optString("app") == "onyx-launcher") { "Not an Onyx Launcher backup" }

        root.optJSONObject("settings")?.let { applySettings(it) }

        // Layout tables — replace wholesale (the backup is the source of truth).
        with(database) {
            homeItemDao().clear()
            folderDao().clear()
            appOverrideDao().clear()
            wallpaperPresetDao().clear()

            homeItemDao().insertAll(parseHomeItems(root.optJSONArray("homeItems")))
            folderDao().insertAll(parseFolders(root.optJSONArray("folders")))
            appOverrideDao().upsertAll(parseOverrides(root.optJSONArray("appOverrides")))
            wallpaperPresetDao().insertAll(parsePresets(root.optJSONArray("wallpaperPresets")))
        }
    }

    // ── settings (de)serialization ──────────────────────────────────────────
    private fun settingsJson(s: Settings) = JSONObject().apply {
        put("homeColumns", s.homeColumns); put("homeRows", s.homeRows)
        put("dockSlots", s.dockSlots); put("iconSizeDp", s.iconSizeDp)
        put("showLabels", s.showLabels); put("labelSizeSp", s.labelSizeSp)
        put("pageIndicator", s.pageIndicator.name); put("folderStyle", s.folderStyle.name)
        put("themeMode", s.themeMode.name); put("useDynamicColor", s.useDynamicColor)
        put("activeIconPack", s.activeIconPack ?: "")
        put("swipeUp", s.swipeUp.name); put("swipeDown", s.swipeDown.name)
        put("doubleTap", s.doubleTap.name); put("twoFingerSwipeUp", s.twoFingerSwipeUp.name)
        put("gestureCustomApp", s.gestureCustomApp ?: "")
        put("useTimeOfDayColor", s.useTimeOfDayColor); put("useUsageSignal", s.useUsageSignal)
        put("autoRefresh", s.autoRefresh); put("autoRefreshTrigger", s.autoRefreshTrigger.name)
        put("animationScale", s.animationScale.toDouble())
        put("showStatusBar", s.showStatusBar); put("showSearchOnHome", s.showSearchOnHome)
    }

    private suspend fun applySettings(j: JSONObject) {
        settingsRepository.update {
            homeColumns = j.optInt("homeColumns", 5)
            homeRows = j.optInt("homeRows", 5)
            dockSlots = j.optInt("dockSlots", 5)
            iconSizeDp = j.optInt("iconSizeDp", 56)
            showLabels = j.optBoolean("showLabels", true)
            labelSizeSp = j.optInt("labelSizeSp", 11)
            pageIndicator = enumOrdinal<PageIndicatorStyle>(j.optString("pageIndicator"))
            folderStyle = enumOrdinal<FolderStyle>(j.optString("folderStyle"))
            themeMode = enumOrdinal<ThemeMode>(j.optString("themeMode"))
            useDynamicColor = j.optBoolean("useDynamicColor", true)
            activeIconPack = j.optString("activeIconPack", "")
            swipeUp = enumOrdinal<GestureAction>(j.optString("swipeUp"))
            swipeDown = enumOrdinal<GestureAction>(j.optString("swipeDown"))
            doubleTap = enumOrdinal<GestureAction>(j.optString("doubleTap"))
            twoFingerSwipeUp = enumOrdinal<GestureAction>(j.optString("twoFingerSwipeUp"))
            gestureCustomApp = j.optString("gestureCustomApp", "")
            useTimeOfDayColor = j.optBoolean("useTimeOfDayColor", true)
            useUsageSignal = j.optBoolean("useUsageSignal", false)
            autoRefresh = j.optBoolean("autoRefresh", false)
            autoRefreshTrigger = enumOrdinal<RefreshTrigger>(j.optString("autoRefreshTrigger"))
            animationScale = j.optDouble("animationScale", 1.0).toFloat()
            showStatusBar = j.optBoolean("showStatusBar", true)
            showSearchOnHome = j.optBoolean("showSearchOnHome", false)
        }
    }

    private inline fun <reified E : Enum<E>> enumOrdinal(name: String): Int =
        runCatching { enumValueOf<E>(name).ordinal }.getOrDefault(0)

    // ── table (de)serialization ─────────────────────────────────────────────
    private fun homeItemsJson(items: List<HomeItemEntity>) = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("page", it.page)
                put("gridX", it.gridX); put("gridY", it.gridY)
                put("type", it.type); put("payload", it.payload)
            })
        }
    }

    private fun parseHomeItems(arr: JSONArray?): List<HomeItemEntity> = arr.map {
        HomeItemEntity(
            id = it.getLong("id"), page = it.getInt("page"),
            gridX = it.getInt("gridX"), gridY = it.getInt("gridY"),
            type = it.getString("type"), payload = it.getString("payload"),
        )
    }

    private fun foldersJson(items: List<FolderEntity>) = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("name", it.name)
                put("items", it.items); put("customIconKey", it.customIconKey ?: "")
            })
        }
    }

    private fun parseFolders(arr: JSONArray?): List<FolderEntity> = arr.map {
        FolderEntity(
            id = it.getLong("id"), name = it.getString("name"),
            items = it.getString("items"),
            customIconKey = it.optString("customIconKey").ifEmpty { null },
        )
    }

    private fun overridesJson(items: List<AppOverrideEntity>) = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("componentName", it.componentName); put("userSerial", it.userSerial)
                put("isHidden", it.isHidden)
                put("customLabel", it.customLabel ?: "")
                put("customIconKey", it.customIconKey ?: "")
            })
        }
    }

    private fun parseOverrides(arr: JSONArray?): List<AppOverrideEntity> = arr.map {
        AppOverrideEntity(
            componentName = it.getString("componentName"),
            userSerial = it.getLong("userSerial"),
            isHidden = it.optBoolean("isHidden"),
            customLabel = it.optString("customLabel").ifEmpty { null },
            customIconKey = it.optString("customIconKey").ifEmpty { null },
        )
    }

    private fun presetsJson(items: List<WallpaperPresetEntity>) = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("name", it.name ?: "")
                put("seed", it.seed); put("style", it.style)
                put("lockedPalette", it.lockedPalette ?: "")
                put("isLive", it.isLive); put("isFavorite", it.isFavorite)
                put("createdAt", it.createdAt)
            })
        }
    }

    private fun parsePresets(arr: JSONArray?): List<WallpaperPresetEntity> = arr.map {
        WallpaperPresetEntity(
            id = it.getLong("id"), name = it.optString("name").ifEmpty { null },
            seed = it.getLong("seed"), style = it.getString("style"),
            lockedPalette = it.optString("lockedPalette").ifEmpty { null },
            isLive = it.optBoolean("isLive"), isFavorite = it.optBoolean("isFavorite"),
            createdAt = it.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    private inline fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { transform(getJSONObject(it)) }
    }
}
