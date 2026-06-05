package com.onyxlauncher.ui.widget

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onyxlauncher.data.widget.OnyxWidgetHost
import com.onyxlauncher.domain.model.HomeItem

/** CompositionLocal that provides the OnyxWidgetHost down the tree. */
val LocalWidgetHost = staticCompositionLocalOf<OnyxWidgetHost> {
    error("LocalWidgetHost not provided — wrap your composable with CompositionLocalProvider(LocalWidgetHost provides host)")
}

/**
 * Renders a live AppWidgetHostView inside Compose.
 *
 * @param appWidgetId   The allocated widget ID.
 * @param widgetHost    The OnyxWidgetHost instance (or use LocalWidgetHost).
 * @param widgetInfo    AppWidgetProviderInfo for this widget; may be null if the
 *                      widget was just bound and the system hasn't reported it yet.
 * @param spanX         Number of grid columns this widget occupies.
 * @param spanY         Number of grid rows this widget occupies.
 * @param isEditMode    When true, show resize corner handles.
 * @param onResize      Called when the user drags a corner handle; new span dimensions.
 * @param modifier      Caller-supplied size/position modifier.
 */
@Composable
fun WidgetView(
    appWidgetId: Int,
    widgetHost: OnyxWidgetHost,
    widgetInfo: AppWidgetProviderInfo?,
    spanX: Int,
    spanY: Int,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    item: HomeItem.WidgetRef? = null,
    onResize: ((newSpanX: Int, newSpanY: Int) -> Unit)? = null,
) {
    Box(modifier = modifier) {
        // The actual widget view
        AndroidView(
            factory = { ctx ->
                widgetHost.createView(ctx, appWidgetId, widgetInfo)
            },
            update = { /* AppWidgetHostView updates itself via RemoteViews */ },
            modifier = Modifier.matchParentSize(),
        )

        // Resize handles — only in edit mode
        if (isEditMode && item != null && onResize != null) {
            val density = LocalDensity.current
            val minSpanX = widgetInfo?.minWidth?.let { w ->
                maxOf(1, with(density) { w.toDp().value.toInt() / 80 })
            } ?: 1
            val minSpanY = widgetInfo?.minHeight?.let { h ->
                maxOf(1, with(density) { h.toDp().value.toInt() / 80 })
            } ?: 1
            val maxSpanX = 5
            val maxSpanY = 5

            // Bottom-right corner handle
            ResizeHandle(
                alignment = Alignment.BottomEnd,
                spanX = spanX,
                spanY = spanY,
                minSpanX = minSpanX,
                minSpanY = minSpanY,
                maxSpanX = maxSpanX,
                maxSpanY = maxSpanY,
                onResize = onResize,
            )
            // Bottom-left corner handle
            ResizeHandle(
                alignment = Alignment.BottomStart,
                spanX = spanX,
                spanY = spanY,
                minSpanX = minSpanX,
                minSpanY = minSpanY,
                maxSpanX = maxSpanX,
                maxSpanY = maxSpanY,
                onResize = onResize,
            )
            // Top-right corner handle
            ResizeHandle(
                alignment = Alignment.TopEnd,
                spanX = spanX,
                spanY = spanY,
                minSpanX = minSpanX,
                minSpanY = minSpanY,
                maxSpanX = maxSpanX,
                maxSpanY = maxSpanY,
                onResize = onResize,
            )
            // Top-left corner handle
            ResizeHandle(
                alignment = Alignment.TopStart,
                spanX = spanX,
                spanY = spanY,
                minSpanX = minSpanX,
                minSpanY = minSpanY,
                maxSpanX = maxSpanX,
                maxSpanY = maxSpanY,
                onResize = onResize,
            )
        }
    }
}

/**
 * A small circular drag handle placed at a corner of the widget.
 * Dragging it adjusts the span clamped to the given min/max.
 * Must be called inside a [BoxScope] so [Modifier.align] is available.
 */
@Composable
private fun BoxScope.ResizeHandle(
    alignment: Alignment,
    spanX: Int,
    spanY: Int,
    minSpanX: Int,
    minSpanY: Int,
    maxSpanX: Int,
    maxSpanY: Int,
    onResize: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    // Track cumulative drag delta in px for this handle session
    var accDx by remember { mutableFloatStateOf(0f) }
    var accDy by remember { mutableFloatStateOf(0f) }

    // Cell size assumed ~80.dp for delta-to-span conversion
    val cellPx = with(density) { 80.dp.toPx() }

    Box(
        modifier = Modifier
            .size(12.dp)
            .background(Color.White.copy(alpha = 0.6f), CircleShape)
            .align(alignment)
            .pointerInput(spanX, spanY) {
                awaitEachGesture {
                    awaitFirstDown()
                    accDx = 0f
                    accDy = 0f
                    var currentSpanX = spanX
                    var currentSpanY = spanY
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val delta = change.positionChange()
                            accDx += delta.x
                            accDy += delta.y
                            val newSpanX = (spanX + (accDx / cellPx).toInt())
                                .coerceIn(minSpanX, maxSpanX)
                            val newSpanY = (spanY + (accDy / cellPx).toInt())
                                .coerceIn(minSpanY, maxSpanY)
                            currentSpanX = newSpanX
                            currentSpanY = newSpanY
                            change.consume()
                        } else {
                            onResize(currentSpanX, currentSpanY)
                            break
                        }
                    }
                }
            },
    )
}
