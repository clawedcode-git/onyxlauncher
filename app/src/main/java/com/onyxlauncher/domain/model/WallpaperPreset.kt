package com.onyxlauncher.domain.model

data class WallpaperPreset(
    val id: Long = 0,
    val name: String? = null,
    val seed: Long,
    val style: WallpaperStyle,
    val lockedPalette: List<Int>? = null,
    val isLive: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class WallpaperStyle { GRADIENT_MESH, FLOW_FIELD, GEOMETRIC_FACETS }
