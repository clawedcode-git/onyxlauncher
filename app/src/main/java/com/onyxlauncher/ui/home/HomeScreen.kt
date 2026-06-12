package com.onyxlauncher.ui.home

import android.appwidget.AppWidgetManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.Folder
import com.onyxlauncher.domain.model.GestureAction
import com.onyxlauncher.domain.model.HomeItem
import com.onyxlauncher.onyxApp
import com.onyxlauncher.ui.component.AppIcon
import com.onyxlauncher.ui.component.PageIndicator
import com.onyxlauncher.ui.folder.FolderIcon
import com.onyxlauncher.ui.folder.FolderSheet
import com.onyxlauncher.ui.widget.LocalWidgetHost
import com.onyxlauncher.ui.widget.WidgetView
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen root
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDrawer: () -> Unit,
    onOpenWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val contextMenuState by viewModel.contextMenu.collectAsState()
    val folderSheetState by viewModel.folderSheet.collectAsState()
    val iconPackList by viewModel.iconPackPicker.collectAsState()
    val iconChooserApp by viewModel.iconChooser.collectAsState()
    val dragState = remember { DragAndDropState() }
    val pagerState = rememberPagerState(pageCount = { state.pageCount })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widgetHost = (context.applicationContext as android.app.Application).onyxApp.widgetHost

    var renameTarget by remember { mutableStateOf<App?>(null) }
    var showHomeOptions by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalDragAndDrop provides dragState,
        LocalWidgetHost provides widgetHost,
        LocalShowWidgetPicker provides { showHomeOptions = true },
    ) {
        // Gesture modifier on the root Box — processes events before any child
        // (parent pointerInput runs in Initial pass, before pager/icon handlers).
        val gestureModifier = homeScreenGestureModifier(
            settings = state.settings,
            context = context,
            onOpenDrawer = onOpenDrawer,
        )

        Box(modifier = modifier.fillMaxSize().then(gestureModifier)) {

            // ── Pages ─────────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !dragState.isDragging,
            ) { page ->
                HomePageGrid(
                    page = page,
                    items = state.pages[page] ?: emptyList(),
                    folders = state.folders,
                    apps = state.apps,
                    columns = state.settings.homeColumns,
                    rows = state.settings.homeRows,
                    iconSizeDp = state.settings.iconSizeDp,
                    showLabels = state.settings.showLabels,
                    labelSizeSp = state.settings.labelSizeSp,
                    dragState = dragState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .statusBarsPadding(),
                )
            }

            // ── Page auto-turn during drag ─────────────────────────────────
            if (dragState.isDragging) {
                LaunchedEffect(dragState.dragX) {
                    val screenW = context.resources.displayMetrics.widthPixels.toFloat()
                    val edgeZone = screenW * 0.12f
                    when {
                        dragState.dragX < edgeZone && pagerState.currentPage > 0 ->
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        dragState.dragX > screenW - edgeZone && pagerState.currentPage < state.pageCount - 1 ->
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }
            }

            // ── Page indicator ────────────────────────────────────────────
            PageIndicator(
                pageCount = state.pageCount,
                currentPage = pagerState.currentPage,
                style = state.settings.pageIndicator,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .navigationBarsPadding(),
            )

            // ── Dock ──────────────────────────────────────────────────────
            Dock(
                items = state.dock,
                apps = state.apps,
                folders = state.folders,
                slots = state.settings.dockSlots,
                iconSizeDp = state.settings.iconSizeDp,
                dragState = dragState,
                viewModel = viewModel,
                onOpenDrawer = onOpenDrawer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )

            // ── Drag overlay (highest z-index) ────────────────────────────
            if (dragState.isDragging) {
                DragOverlay(dragState = dragState, apps = state.apps)
                TrashZone(
                    dragState = dragState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                )
            }
        }
    }

    // ── Context menu sheet ────────────────────────────────────────────────
    contextMenuState?.let { menuState ->
        ContextMenuSheet(
            state = menuState,
            viewModel = viewModel,
            onStartRename = { renameTarget = menuState.app },
            onDismiss = viewModel::dismissContextMenu,
        )
    }

    // ── Folder sheet ──────────────────────────────────────────────────────
    folderSheetState?.let { sheetState ->
        FolderSheet(
            state = sheetState,
            viewModel = viewModel,
            onDismiss = viewModel::closeFolder,
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────
    renameTarget?.let { app ->
        RenameDialog(
            currentLabel = app.customLabel ?: app.label,
            onConfirm = { newLabel ->
                viewModel.renameApp(app, newLabel)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    // ── Home options sheet (long-press empty space) ───────────────────────
    if (showHomeOptions) {
        HomeOptionsSheet(
            onAddWidget = {
                showHomeOptions = false
                showWidgetPicker = true
            },
            onChangeIconPack = {
                showHomeOptions = false
                viewModel.openIconPackPicker()
            },
            onChangeWallpaper = {
                showHomeOptions = false
                onOpenWallpaper()
            },
            onDismiss = { showHomeOptions = false },
        )
    }

    // ── Widget picker sheet ───────────────────────────────────────────────
    if (showWidgetPicker) {
        com.onyxlauncher.ui.widget.WidgetPickerSheet(
            viewModel = viewModel,
            onDismiss = { showWidgetPicker = false },
        )
    }

    // ── Icon pack picker sheet ────────────────────────────────────────────
    iconPackList?.let { packs ->
        // Only show as the global picker when no per-app chooser is active.
        if (iconChooserApp == null) {
            com.onyxlauncher.ui.iconpack.IconPackPickerSheet(
                packs = packs,
                activePackage = state.settings.activeIconPack,
                onSelect = { viewModel.setActiveIconPack(it) },
                onDismiss = { viewModel.closeIconPackPicker() },
            )
        }
    }

    // ── Per-app icon chooser sheet ────────────────────────────────────────
    iconChooserApp?.let { app ->
        com.onyxlauncher.ui.iconpack.IconChooserSheet(
            app = app,
            packs = iconPackList ?: emptyList(),
            onSelectPack = { pkg -> viewModel.setAppIconOverride(app, pkg) },
            onReset = { viewModel.resetAppIcon(app) },
            onDismiss = { viewModel.closeIconChooser() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home options bottom sheet (shown on long-press of empty home space)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeOptionsSheet(
    onAddWidget: () -> Unit,
    onChangeIconPack: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1F),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            HomeOptionRow("Add widget", Icons.Default.Widgets, onAddWidget)
            HomeOptionRow("Change icon pack", Icons.Default.Palette, onChangeIconPack)
            HomeOptionRow("Wallpaper", Icons.Default.Wallpaper, onChangeWallpaper)
        }
    }
}

@Composable
private fun HomeOptionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home page grid
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomePageGrid(
    page: Int,
    items: List<HomeItem>,
    folders: Map<Long, Folder>,
    apps: List<App>,
    columns: Int,
    rows: Int,
    iconSizeDp: Int,
    showLabels: Boolean,
    labelSizeSp: Int,
    dragState: DragAndDropState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val placed = remember(items) { items.associateBy { it.gridX to it.gridY } }
    val showWidgetPicker = LocalShowWidgetPicker.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth / columns
        val cellH = maxHeight / rows
        val cellWPx = with(density) { cellW.toPx() }
        val cellHPx = with(density) { cellH.toPx() }

        // Long-press on empty grid cell → widget picker
        // Must live inside BoxWithConstraints so it can compute grid coords from px.
        val backgroundModifier = Modifier.pointerInput(placed, cellWPx, cellHPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Convert touch to grid cell using local (BoxWithConstraints-relative) coords
                val col = (down.position.x / cellWPx).toInt().coerceIn(0, columns - 1)
                val row = (down.position.y / cellHPx).toInt().coerceIn(0, rows - 1)
                val onOccupiedCell = placed.containsKey(col to row)
                if (!onOccupiedCell) {
                    val earlyExit = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: return@withTimeoutOrNull
                            if (c.changedToUp()) return@withTimeoutOrNull
                        }
                    }
                    if (earlyExit == null) {          // null = timeout = long-press confirmed
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showWidgetPicker()
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().then(backgroundModifier)) { /* gesture capture layer */ }

        // Register all cells for drop-target detection
        repeat(columns) { col ->
            repeat(rows) { row ->
                Box(
                    modifier = Modifier
                        .offset(x = cellW * col, y = cellH * row)
                        .size(cellW, cellH)
                        .onGloballyPositioned { coords ->
                            dragState.registerCell(page, col, row, coords.boundsInRoot())
                        },
                )
            }
        }

        // Render placed items
        placed.forEach { (pos, item) ->
            val (col, row) = pos
            val isDragged = dragState.draggedItem?.id == item.id
            Box(
                modifier = Modifier
                    .offset(x = cellW * col, y = cellH * row)
                    .size(cellW, cellH)
                    .alpha(if (isDragged) 0.3f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                HomeItemCell(
                    item = item,
                    folders = folders,
                    apps = apps,
                    iconSizeDp = iconSizeDp,
                    showLabels = showLabels,
                    labelSizeSp = labelSizeSp,
                    dragState = dragState,
                    viewModel = viewModel,
                )
            }
        }

        // Drop-target highlight
        if (dragState.isDragging) {
            val draggedId = dragState.draggedItem?.id
            placed.forEach { (_, item) ->
                if (item.id == draggedId) return@forEach
                // Subtle scale pulse on hovered cell
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual home item cell (shortcut, folder, widget)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeItemCell(
    item: HomeItem,
    folders: Map<Long, Folder>,
    apps: List<App>,
    iconSizeDp: Int,
    showLabels: Boolean,
    labelSizeSp: Int,
    dragState: DragAndDropState,
    viewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var globalPos by remember { mutableStateOf(Offset.Zero) }

    // ── Single unified gesture handler ───────────────────────────────────────
    // Three cases, one pointerInput block — no competing detectors:
    //   tap   (finger up before long-press timeout, < touch slop)  → launch
    //   long-press + no drag  (timeout, then up without movement)  → context menu
    //   long-press + drag     (timeout, then move > slop)          → drag-and-drop
    val pointerModifier = Modifier
        .onGloballyPositioned { coords ->
            globalPos = coords.boundsInRoot().topLeft
        }
        .pointerInput(item.id) {
            val longPressMs  = viewConfiguration.longPressTimeoutMillis
            val touchSlopPx  = viewConfiguration.touchSlop

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var fingerUp = false
                var movedTooFar = false

                // ── Phase 1: wait for long-press timeout ─────────────────
                // withTimeoutOrNull returns:
                //   null  = timeout fired  = long press  (finger still down)
                //   Unit  = block returned = tap or move cancelled the wait
                val earlyExit = withTimeoutOrNull(longPressMs) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull() ?: return@withTimeoutOrNull
                        if (c.changedToUp()) { fingerUp = true; return@withTimeoutOrNull }
                        val dx = c.position.x - down.position.x
                        val dy = c.position.y - down.position.y
                        if (abs(dx) + abs(dy) > touchSlopPx) {
                            movedTooFar = true; return@withTimeoutOrNull
                        }
                    }
                }

                when {
                    // ── Tap: lifted before long-press ────────────────────
                    fingerUp -> {
                        when (item) {
                            is HomeItem.Shortcut -> {
                                val app = apps.find { it.componentName == item.componentName }
                                if (app != null) {
                                    val la = context.getSystemService(android.content.pm.LauncherApps::class.java)!!
                                    val um = context.getSystemService(android.os.UserManager::class.java)!!
                                    val uh = um.getUserForSerialNumber(app.userSerial)
                                    if (uh != null) la.startMainActivity(app.componentName, uh, null, null)
                                }
                            }
                            is HomeItem.FolderRef -> viewModel.openFolder(item.folderId)
                            else -> {}
                        }
                    }

                    // ── Moved too far too quickly: let pager handle it ───
                    movedTooFar -> { /* no-op */ }

                    // ── Long-press confirmed (earlyExit == null) ─────────
                    earlyExit == null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragState.startDrag(
                            item = item,
                            app = when (item) {
                                is HomeItem.Shortcut -> apps.find { it.componentName == item.componentName }
                                else -> null
                            },
                            absX = globalPos.x + down.position.x,
                            absY = globalPos.y + down.position.y,
                            iconPx = with(density) { iconSizeDp.dp.toPx() },
                        )

                        // ── Phase 2: track drag until lift ───────────────
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull() ?: break
                            if (c.changedToUp()) {
                                if (dragState.movedSignificantly) {
                                    viewModel.onDrop(dragState, item.page)
                                } else {
                                    // Long-press without drag → context menu
                                    if (item is HomeItem.Shortcut) {
                                        val app = apps.find { it.componentName == item.componentName }
                                        if (app != null) viewModel.showContextMenu(item, app, context)
                                    }
                                }
                                dragState.endDrag()
                                break
                            }
                            val delta = c.positionChange()
                            dragState.updatePosition(delta.x, delta.y)
                            c.consume()
                        }
                    }
                }
            }
        }

    when (item) {
        is HomeItem.Shortcut -> {
            val app = apps.find { it.componentName == item.componentName }
            if (app != null) {
                AppIcon(
                    app = app,
                    iconSize = iconSizeDp.dp,
                    showLabel = showLabels,
                    labelSizeSp = labelSizeSp,
                    modifier = pointerModifier,
                )
            }
        }
        is HomeItem.FolderRef -> {
            val folder = folders[item.folderId]
            if (folder != null) {
                Column(
                    modifier = pointerModifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FolderIcon(folder = folder, apps = apps, size = iconSizeDp.dp)
                    if (showLabels) {
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.paddingFromBaseline(top = 4.dp),
                        )
                    }
                }
            }
        }
        is HomeItem.WidgetRef -> {
            val widgetHost = LocalWidgetHost.current
            val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
            val widgetInfo = remember(item.appWidgetId) {
                runCatching { appWidgetManager.getAppWidgetInfo(item.appWidgetId) }.getOrNull()
            }
            // Cell dimensions from parent BoxWithConstraints are not accessible here;
            // use a fixed per-span size approximation — the outer Box already sizes to cellW×cellH
            WidgetView(
                appWidgetId = item.appWidgetId,
                widgetHost = widgetHost,
                widgetInfo = widgetInfo,
                spanX = item.spanX,
                spanY = item.spanY,
                isEditMode = false,
                item = item,
                onResize = { newSpanX, newSpanY ->
                    viewModel.resizeWidget(item, newSpanX, newSpanY)
                },
                modifier = pointerModifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dock
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Dock(
    items: List<HomeItem>,
    apps: List<App>,
    folders: Map<Long, Folder>,
    slots: Int,
    iconSizeDp: Int,
    dragState: DragAndDropState,
    viewModel: HomeViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dockItems = items.sortedBy { it.gridX }

    Box(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(72.dp)
            .background(
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(enabled = !dragState.isDragging, onClick = onOpenDrawer),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(slots) { slot ->
                val item = dockItems.find { it.gridX == slot }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            dragState.registerDockSlot(slot, coords.boundsInRoot())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (item != null) {
                        HomeItemCell(
                            item = item,
                            folders = folders,
                            apps = apps,
                            iconSizeDp = iconSizeDp - 4,
                            showLabels = false,
                            labelSizeSp = 10,
                            dragState = dragState,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drag ghost overlay — renders the dragged icon floating at finger position
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BoxScope.DragOverlay(
    dragState: DragAndDropState,
    apps: List<App>,
) {
    val app = dragState.draggedApp ?: return
    val iconPx = dragState.iconSizePx
    val halfIcon = iconPx / 2

    val animScale by animateFloatAsState(
        targetValue = 1.12f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "dragScale",
    )

    Box(
        modifier = Modifier
            .zIndex(Float.MAX_VALUE)
            .fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        AppIcon(
            app = app,
            iconSize = with(LocalDensity.current) { iconPx.toDp() },
            showLabel = false,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (dragState.dragX - halfIcon).roundToInt(),
                        y = (dragState.dragY - halfIcon).roundToInt(),
                    )
                }
                .scale(animScale)
                .alpha(0.88f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trash zone — appears at top when dragging; drop here to remove
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TrashZone(
    dragState: DragAndDropState,
    modifier: Modifier = Modifier,
) {
    val trashHeight = 72.dp
    var isHovered by remember { mutableStateOf(false) }

    LaunchedEffect(dragState.dragY) {
        isHovered = dragState.trashRect?.contains(Offset(dragState.dragX, dragState.dragY)) == true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0.7f,
        label = "trashAlpha",
    )

    Box(
        modifier = modifier
            .height(trashHeight)
            .padding(horizontal = 60.dp, vertical = 8.dp)
            .background(
                color = if (isHovered) Color(0xFFFF4444).copy(alpha = 0.9f)
                        else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
            )
            .alpha(alpha)
            .onGloballyPositioned { coords ->
                dragState.trashRect = coords.boundsInRoot()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Text("Remove", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}
