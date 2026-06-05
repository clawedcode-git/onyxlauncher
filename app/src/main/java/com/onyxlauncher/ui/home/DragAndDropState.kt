package com.onyxlauncher.ui.home

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.HomeItem

// ─────────────────────────────────────────────────────────────────────────────
// Drop target discriminated union
// ─────────────────────────────────────────────────────────────────────────────
sealed interface DropTarget {
    /** Drop onto an empty grid cell */
    data class GridCell(val page: Int, val col: Int, val row: Int) : DropTarget

    /** Drop onto an existing item → fold into a folder */
    data class MergeWithItem(val target: HomeItem) : DropTarget

    /** Drop onto the dock */
    data class DockSlot(val slot: Int) : DropTarget

    /** Drop onto trash / off-screen → remove */
    object Trash : DropTarget
}

// ─────────────────────────────────────────────────────────────────────────────
// Drag-and-drop state — one instance per LauncherRoot lifetime
// ─────────────────────────────────────────────────────────────────────────────
@Stable
class DragAndDropState {
    // ── active drag ──────────────────────────────────────────────────────────
    var isDragging by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<HomeItem?>(null)
        private set
    var draggedApp by mutableStateOf<App?>(null)
        private set

    /** Absolute screen X of the drag ghost centre */
    var dragX by mutableFloatStateOf(0f)
        private set
    var dragY by mutableFloatStateOf(0f)
        private set

    /** px size of the ghost icon */
    var iconSizePx by mutableFloatStateOf(0f)
        private set

    // ── accumulated displacement (to detect "no-move" context-menu trigger) ──
    private var _totalDx = 0f
    private var _totalDy = 0f
    val movedSignificantly get() = (_totalDx * _totalDx + _totalDy * _totalDy) > (20f * 20f)

    // ── cell / dock hit registries (filled by onGloballyPositioned) ──────────
    // key = "page:col:row"
    val cellRects = mutableMapOf<String, Rect>()
    val dockRects = mutableMapOf<Int, Rect>()   // slot index → Rect
    var trashRect: Rect? = null

    // ─────────────────────────────────────────────────────────────────────────
    fun startDrag(item: HomeItem, app: App?, absX: Float, absY: Float, iconPx: Float) {
        draggedItem = item
        draggedApp = app
        dragX = absX
        dragY = absY
        iconSizePx = iconPx
        _totalDx = 0f
        _totalDy = 0f
        isDragging = true
    }

    fun updatePosition(dx: Float, dy: Float) {
        dragX += dx
        dragY += dy
        _totalDx += dx
        _totalDy += dy
    }

    fun endDrag() {
        isDragging = false
        draggedItem = null
        draggedApp = null
        _totalDx = 0f
        _totalDy = 0f
    }

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Find what the finger is hovering over at the given absolute screen coords.
     * Checks trash first, then dock, then grid cells.
     */
    fun findDropTarget(
        x: Float,
        y: Float,
        occupiedCells: Set<String>,       // "page:col:row" of non-empty cells
        draggedItemId: Long,
    ): DropTarget? {
        val point = Offset(x, y)

        // 1. Trash
        trashRect?.let { if (it.contains(point)) return DropTarget.Trash }

        // 2. Dock slots
        dockRects.entries
            .sortedBy { it.key }
            .firstOrNull { it.value.contains(point) }
            ?.let { (slot, _) -> return DropTarget.DockSlot(slot) }

        // 3. Grid cells — prefer occupied (merge) over empty
        val hitCells = cellRects.entries.filter { it.value.contains(point) }

        // Try merge first: occupied cell that belongs to a different item
        hitCells.firstOrNull { (key, _) -> key in occupiedCells }?.let { (key, _) ->
            // We'll let the caller look up the item; return the key-parsed target
            val (page, col, row) = key.split(":").map { it.toInt() }
            // Return GridCell; caller will check if it's occupied → merge
            return DropTarget.GridCell(page, col, row)
        }

        // Empty cell
        hitCells.firstOrNull()?.let { (key, _) ->
            val (page, col, row) = key.split(":").map { it.toInt() }
            return DropTarget.GridCell(page, col, row)
        }

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    fun registerCell(page: Int, col: Int, row: Int, rect: Rect) {
        cellRects["$page:$col:$row"] = rect
    }

    fun registerDockSlot(slot: Int, rect: Rect) {
        dockRects[slot] = rect
    }
}

val LocalDragAndDrop = staticCompositionLocalOf { DragAndDropState() }
