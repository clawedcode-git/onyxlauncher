package com.onyxlauncher.domain.model

import android.content.ComponentName
import android.os.UserHandle

data class App(
    val packageName: String,
    val activityName: String,
    val label: String,
    val userSerial: Long,
    val isHidden: Boolean = false,
    val customLabel: String? = null,
    val customIconKey: String? = null,
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)

    val key: String get() = "${packageName}/${activityName}/${userSerial}"
}
