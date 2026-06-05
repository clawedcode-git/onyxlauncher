package com.onyxlauncher.domain.model

import android.content.ComponentName

data class WidgetBinding(
    val appWidgetId: Int,
    val provider: ComponentName,
    val minWidth: Int,
    val minHeight: Int,
)
