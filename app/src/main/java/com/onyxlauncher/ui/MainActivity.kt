package com.onyxlauncher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.onyxlauncher.onyxApp
import com.onyxlauncher.ui.drawer.DrawerScreen
import com.onyxlauncher.ui.drawer.DrawerViewModel
import com.onyxlauncher.ui.home.HomeScreen
import com.onyxlauncher.ui.home.HomeViewModel
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.onyxlauncher.domain.model.Settings
import com.onyxlauncher.ui.theme.OnyxTheme

class MainActivity : ComponentActivity() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var drawerViewModel: DrawerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application.onyxApp
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                homeItemDao = app.database.homeItemDao(),
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

        setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = Settings())

            OnyxTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor,
            ) {
                LauncherRoot(
                    homeViewModel = homeViewModel,
                    drawerViewModel = drawerViewModel,
                )
            }
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
