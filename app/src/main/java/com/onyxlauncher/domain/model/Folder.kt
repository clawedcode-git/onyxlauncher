package com.onyxlauncher.domain.model

import android.content.ComponentName

data class Folder(
    val id: Long = 0,
    val name: String,
    val items: List<ComponentName>,
    val customIconKey: String? = null,
)
