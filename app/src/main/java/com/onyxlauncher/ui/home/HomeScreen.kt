package com.onyxlauncher.ui.home

import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.Folder
import com.onyxlauncher.domain.model.HomeItem
import com.onyxlauncher.ui.component.AppIcon
import com.onyxlauncher.ui.component.PageIndicator
import com.onyxlauncher.ui.folder.FolderIcon
import com.onyxlauncher.ui.folder.FolderSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen root
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val contextMenuState by viewModel.contextMenu.collectAsState()
    val folderSheetState by viewModel.folderSheet.collectAsState()
    val dragState = remember { DragAndDropState() }
    val pagerState = rememberPagerState(pageCount = { state.pageCount })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var renameTarget by remember { mutableStateOf<App?>(null) }

    CompositionLocalProvider(LocalDragAndDrop provides dragState) {
        Box(modifier = modifier.fillMaxSize()) {

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

    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth / columns
        val cellH = maxHeight / rows

        // Register all cells for drop-target detection
        repeat(columns) { col ->
            repeat(rows) { row ->
                val density = LocalDensity.current
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

    val pointerModifier = Modifier
        .onGloballyPositioned { coords ->
            globalPos = coords.boundsInRoot().topLeft
        }
        .pointerInput(item.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { localOffset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragState.startDrag(
                        item = item,
                        app = when (item) {
                            is HomeItem.Shortcut -> apps.find { it.componentName == item.componentName }
                            else -> null
                        },
                        absX = globalPos.x + localOffset.x,
                        absY = globalPos.y + localOffset.y,
                        iconPx = with(density) { iconSizeDp.dp.toPx() },
                    )
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragState.updatePosition(dragAmount.x, dragAmount.y)
                },
                onDragEnd = {
                    if (!dragState.movedSignificantly && item is HomeItem.Shortcut) {
                        val app = apps.find { it.componentName == item.componentName }
                        if (app != null) viewModel.showContextMenu(item, app, context)
                        dragState.endDrag()
                    } else {
                        viewModel.onDrop(dragState, item.page)
                        dragState.endDrag()
                    }
                },
                onDragCancel = { dragState.endDrag() },
            )
        }
        .pointerInput(item.id) {
            detectTapGestures(
                onTap = {
                    when (item) {
                        is HomeItem.Shortcut -> {
                            val app = apps.find { it.componentName == item.componentName } ?: return@detectTapGestures
                            val launcherApps = context.getSystemService(LauncherApps::class.java)!!
                            val userManager = context.getSystemService(UserManager::class.java)!!
                            val uh = userManager.getUserForSerialNumber(app.userSerial) ?: return@detectTapGestures
                            launcherApps.startMainActivity(app.componentName, uh, null, null)
                        }
                        is HomeItem.FolderRef -> viewModel.openFolder(item.folderId)
                        else -> {}
                    }
                },
            )
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
            // Widget host rendering deferred to Phase 3
            Box(
                modifier = pointerModifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Widget", color = Color.White.copy(alpha = 0.4f))
            }
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
