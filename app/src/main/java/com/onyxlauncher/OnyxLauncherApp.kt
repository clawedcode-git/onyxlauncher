package com.onyxlauncher

import android.app.Application
import com.onyxlauncher.data.HomeSeeder
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.AppDatabase
import com.onyxlauncher.data.iconpack.IconPackRepository
import com.onyxlauncher.data.`package`.PackageMonitor
import com.onyxlauncher.data.wallpaper.WallpaperRepository
import com.onyxlauncher.data.widget.OnyxWidgetHost
import com.onyxlauncher.wallpaper.engine.WallpaperGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    val iconPackRepository by lazy { IconPackRepository(this) }

    val wallpaperRepository by lazy {
        WallpaperRepository(this, database.wallpaperPresetDao())
    }

    val wallpaperGenerator by lazy { WallpaperGenerator() }

    val widgetHost by lazy { OnyxWidgetHost(this) }

    override fun onCreate() {
        super.onCreate()
        widgetHost.startListening()

        // First-run setup: populate dock + home page and apply a generated wallpaper.
        appScope.launch {
            HomeSeeder(
                context = this@OnyxLauncherApp,
                homeItemDao = database.homeItemDao(),
                settingsRepository = settingsRepository,
                packageMonitor = packageMonitor,
                wallpaperRepository = wallpaperRepository,
                generator = wallpaperGenerator,
            ).seedIfFirstRun()
        }
    }
}

val Application.onyxApp get() = this as OnyxLauncherApp
