package com.onyxlauncher.data.`package`

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.UserHandle
import com.onyxlauncher.data.db.dao.AppOverrideDao
import com.onyxlauncher.domain.model.App
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PackageMonitor(
    private val context: Context,
    private val appOverrideDao: AppOverrideDao,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)!!
    private val userManager = context.getSystemService(android.os.UserManager::class.java)!!

    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    val apps: StateFlow<List<App>> = combine(
        _refreshTrigger,
        appOverrideDao.observeAll(),
    ) { _, overrides ->
        val overrideMap = overrides.associateBy { it.componentName }
        loadAllApps(overrideMap)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = triggerRefresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = triggerRefresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = triggerRefresh()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = triggerRefresh()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = triggerRefresh()
    }

    init {
        launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private fun triggerRefresh() {
        scope.launch { _refreshTrigger.emit(Unit) }
    }

    private fun loadAllApps(
        overrideMap: Map<String, com.onyxlauncher.data.db.entity.AppOverrideEntity>,
    ): List<App> {
        val profiles = userManager.userProfiles
        return profiles.flatMap { userHandle ->
            val serial = userManager.getSerialNumberForUser(userHandle)
            launcherApps.getActivityList(null, userHandle).map { info ->
                val key = "${info.componentName.flattenToString()}"
                val override = overrideMap[key]
                App(
                    packageName = info.componentName.packageName,
                    activityName = info.componentName.className,
                    label = info.label.toString(),
                    userSerial = serial,
                    isHidden = override?.isHidden ?: false,
                    customLabel = override?.customLabel,
                    customIconKey = override?.customIconKey,
                )
            }
        }.sortedBy { (it.customLabel ?: it.label).lowercase() }
    }

    fun unregister() {
        launcherApps.unregisterCallback(callback)
    }
}
