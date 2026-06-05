package com.onyxlauncher.ui.home

import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.HomeItem
import com.onyxlauncher.ui.component.AppIcon
import com.onyxlauncher.ui.component.PageIndicator

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { state.pageCount })

    Box(modifier = modifier.fillMaxSize()) {
        // ── Home pages ──────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            HomePageGrid(
                items = state.pages[page] ?: emptyList(),
                apps = state.apps,
                columns = state.settings.homeColumns,
                rows = state.settings.homeRows,
                iconSizeDp = state.settings.iconSizeDp,
                showLabels = state.settings.showLabels,
                labelSizeSp = state.settings.labelSizeSp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Page indicator ───────────────────────────────────────
        PageIndicator(
            pageCount = state.pageCount,
            currentPage = pagerState.currentPage,
            style = state.settings.pageIndicator,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )

        // ── Dock ─────────────────────────────────────────────────
        Dock(
            items = state.dock,
            apps = state.apps,
            slots = state.settings.dockSlots,
            iconSizeDp = state.settings.iconSizeDp,
            showLabels = state.settings.showLabels,
            labelSizeSp = state.settings.labelSizeSp,
            onOpenDrawer = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),  // lift above gesture nav bar
        )
    }
}

@Composable
private fun HomePageGrid(
    items: List<HomeItem>,
    apps: List<App>,
    columns: Int,
    rows: Int,
    iconSizeDp: Int,
    showLabels: Boolean,
    labelSizeSp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Build a lookup map for placed items
    val placed = remember(items) {
        items.associateBy { it.gridX to it.gridY }
    }

    BoxWithConstraints(modifier = modifier.padding(horizontal = 8.dp)) {
        val cellW = maxWidth / columns
        val cellH = (maxHeight - 8.dp) / rows

        placed.forEach { (pos, item) ->
            val (x, y) = pos
            if (item is HomeItem.Shortcut) {
                val app = apps.find { it.componentName == item.componentName }
                if (app != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = cellW * x, y = cellH * y)
                            .size(cellW, cellH),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            app = app,
                            iconSize = iconSizeDp.dp,
                            showLabel = showLabels,
                            labelSizeSp = labelSizeSp,
                            modifier = Modifier.clickable {
                                val launcherApps = context.getSystemService(LauncherApps::class.java)!!
                                val userManager = context.getSystemService(UserManager::class.java)!!
                                val userHandle = userManager.getUserForSerialNumber(app.userSerial)
                                    ?: return@clickable
                                launcherApps.startMainActivity(app.componentName, userHandle, null, null)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(
    items: List<HomeItem>,
    apps: List<App>,
    slots: Int,
    iconSizeDp: Int,
    showLabels: Boolean,
    labelSizeSp: Int,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onOpenDrawer),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dockItems = items.sortedBy { it.gridX }.take(slots)

            dockItems.forEach { item ->
                if (item is HomeItem.Shortcut) {
                    val app = apps.find { it.componentName == item.componentName }
                    if (app != null) {
                        AppIcon(
                            app = app,
                            iconSize = (iconSizeDp - 4).dp,
                            showLabel = false,
                            modifier = Modifier.clickable {
                                val launcherApps = context.getSystemService(LauncherApps::class.java)!!
                                val userManager = context.getSystemService(UserManager::class.java)!!
                                val userHandle = userManager.getUserForSerialNumber(app.userSerial)
                                    ?: return@clickable
                                launcherApps.startMainActivity(app.componentName, userHandle, null, null)
                            },
                        )
                    }
                }
            }

            // Fill empty slots with spacers so dock dimensions are stable
            repeat(slots - dockItems.size) {
                Spacer(Modifier.size((iconSizeDp - 4).dp))
            }
        }
    }
}
