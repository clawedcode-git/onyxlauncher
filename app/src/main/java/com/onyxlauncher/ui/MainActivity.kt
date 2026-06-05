package com.onyxlauncher.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.onyxlauncher.onyxApp
import com.onyxlauncher.ui.drawer.DrawerScreen
import com.onyxlauncher.ui.drawer.DrawerViewModel
import com.onyxlauncher.ui.home.HomeScreen
import com.onyxlauncher.ui.home.HomeViewModel
import com.onyxlauncher.ui.theme.OnyxTheme
import com.onyxlauncher.domain.model.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var drawerViewModel: DrawerViewModel

    // ── Widget bind flow ────────────────────────────────────────────────────
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    /** Result from ACTION_APPWIDGET_BIND — proceed to configure or place. */
    private val bindWidgetLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val id = result.data
                    ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    ?: AppWidgetManager.INVALID_APPWIDGET_ID
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    pendingWidgetId = id
                    tryConfigureThenPlace(id)
                }
            } else {
                // Bind denied — release the id
                releaseAndResetPendingWidget()
            }
        }

    /** Result from widget configuration Activity. */
    private val configureWidgetLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingWidgetId
            if (result.resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                placeCurrentWidget(id)
            } else {
                releaseAndResetPendingWidget()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application.onyxApp
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                homeItemDao = app.database.homeItemDao(),
                folderDao = app.database.folderDao(),
                appOverrideDao = app.database.appOverrideDao(),
                packageMonitor = app.packageMonitor,
                settingsRepository = app.settingsRepository,
            ),
        )[HomeViewModel::class.java]

        drawerViewModel = ViewModelProvider(
            this,
            DrawerViewModel.Factory(
                packageMonitor = app.packageMonitor,
                settingsRepository = app.settingsRepository,
            ),
        )[DrawerViewModel::class.java]

        // Observe pending widget provider and drive the bind → configure → place flow
        lifecycleScope.launch {
            homeViewModel.pendingWidgetProvider.filterNotNull().collect { providerInfo ->
                // Reset immediately so re-selects trigger a new flow
                homeViewModel.pendingWidgetProvider.value = null

                val widgetManager = AppWidgetManager.getInstance(this@MainActivity)
                val widgetHost = app.widgetHost
                val id = widgetHost.allocateAppWidgetId()
                pendingWidgetId = id

                val bound = widgetManager.bindAppWidgetIdIfAllowed(id, providerInfo.provider)
                if (bound) {
                    tryConfigureThenPlace(id)
                } else {
                    // Request user permission to bind
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                    }
                    bindWidgetLauncher.launch(intent)
                }
            }
        }

        setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = Settings())
            OnyxTheme(themeMode = settings.themeMode, useDynamicColor = settings.useDynamicColor) {
                LauncherRoot(homeViewModel = homeViewModel, drawerViewModel = drawerViewModel)
            }
        }
    }

    private fun tryConfigureThenPlace(appWidgetId: Int) {
        val widgetManager = AppWidgetManager.getInstance(this)
        val providerInfo = widgetManager.getAppWidgetInfo(appWidgetId)
        val configureComponent = providerInfo?.configure
        if (configureComponent != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configureComponent
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            configureWidgetLauncher.launch(configIntent)
        } else {
            placeCurrentWidget(appWidgetId)
        }
    }

    private fun placeCurrentWidget(appWidgetId: Int) {
        // Place at page 0, col 0, row 0 with default 2x2 span
        homeViewModel.placeWidget(appWidgetId, page = 0, col = 0, row = 0, spanX = 2, spanY = 2)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun releaseAndResetPendingWidget() {
        val id = pendingWidgetId
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            application.onyxApp.widgetHost.deleteAppWidgetId(id)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }
}

@Composable
private fun LauncherRoot(
    homeViewModel: HomeViewModel,
    drawerViewModel: DrawerViewModel,
) {
    var drawerOpen by remember { mutableStateOf(false) }

    HomeScreen(
        viewModel = homeViewModel,
        onOpenDrawer = { drawerOpen = true },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    )

    DrawerScreen(
        viewModel = drawerViewModel,
        visible = drawerOpen,
        onDismiss = { drawerOpen = false },
        modifier = Modifier.fillMaxSize(),
    )
}
