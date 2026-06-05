package com.onyxlauncher.domain.model

import android.content.ComponentName

data class IconPack(
    val packageName: String,
    val label: String,
    val version: Int,
)

data class IconPackEntry(
    val componentName: ComponentName,
    val drawableName: String,
    val packPackage: String,
    val packVersion: Int,
)
