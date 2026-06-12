package com.onyxlauncher.data.db

import com.onyxlauncher.data.db.entity.WallpaperPresetEntity
import com.onyxlauncher.domain.model.WallpaperPreset
import com.onyxlauncher.domain.model.WallpaperStyle

object WallpaperPresetMapper {

    fun WallpaperPresetEntity.toDomain(): WallpaperPreset = WallpaperPreset(
        id = id,
        name = name,
        seed = seed,
        style = runCatching { WallpaperStyle.valueOf(style) }.getOrDefault(WallpaperStyle.GRADIENT_MESH),
        lockedPalette = lockedPalette?.let { decodeColors(it) },
        isLive = isLive,
        isFavorite = isFavorite,
        createdAt = createdAt,
    )

    fun WallpaperPreset.toEntity(): WallpaperPresetEntity = WallpaperPresetEntity(
        id = id,
        name = name,
        seed = seed,
        style = style.name,
        lockedPalette = lockedPalette?.let { encodeColors(it) },
        isLive = isLive,
        isFavorite = isFavorite,
        createdAt = createdAt,
    )

    // Compact CSV of signed ints — no org.json dependency, trivially testable.
    private fun encodeColors(colors: List<Int>): String = colors.joinToString(",")

    private fun decodeColors(s: String): List<Int>? =
        s.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.isNotEmpty() }
}
