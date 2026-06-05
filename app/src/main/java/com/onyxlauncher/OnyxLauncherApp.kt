package com.onyxlauncher

import android.app.Application
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.AppDatabase
import com.onyxlauncher.data.`package`.PackageMonitor
import com.onyxlauncher.data.widget.OnyxWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OnyxLauncherApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getInstance(this) }

    val packageMonitor by lazy {
        PackageMonitor(
            context = this,
            appOverrideDao = database.appOverrideDao(),
            scope = appScope,
        )
    }

    val settingsRepository by lazy { SettingsRepository(this) }

    val widgetHost by lazy { OnyxWidgetHost(this) }

    override fun onCreate() {
        super.onCreate()
        widgetHost.startListening()
    }
}

val Application.onyxApp get() = this as OnyxLauncherApp
