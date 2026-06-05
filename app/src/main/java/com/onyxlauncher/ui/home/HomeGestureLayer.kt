package com.onyxlauncher.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.onyxlauncher.domain.model.GestureAction
import com.onyxlauncher.domain.model.Settings
import kotlin.math.abs

/**
 * Returns a Modifier that detects home-screen gestures.
 *
 * Attaching this to the ROOT Box in HomeScreen is critical: a parent's pointerInput
 * runs in the Initial pass — before HorizontalPager and icon pointerInput handlers —
 * so it sees every touch event regardless of whether children later consume it.
 *
 * Gestures detected:
 *   swipe up/down  |deltaY| > 80 dp,  |dy/dx| > 1.5
 *   double-tap     two taps < 350 ms apart, both < 20 px movement
 *   two-finger up  both pointers move up > 60 dp
 */
@Composable
fun homeScreenGestureModifier(
    settings: Settings,
    context: Context,
    onOpenDrawer: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val swipePx        = with(density) { 80.dp.toPx() }
    val twoFingerPx    = with(density) { 60.dp.toPx() }
    val tapMovePx      = 20f
    val doubleTapMs    = 350L

    fun fire(action: GestureAction) {
        if (action == GestureAction.CUSTOM_APP) {
            settings.gestureCustomApp
                ?.let { runCatching { android.content.ComponentName.unflattenFromString(it) }.getOrNull() }
                ?.let { cn ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent().apply {
                                component = cn
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
        } else {
            performGestureAction(action, context, onOpenDrawer)
        }
    }

    return Modifier.pointerInput(settings.swipeUp, settings.swipeDown,
                                  settings.doubleTap, settings.twoFingerSwipeUp) {
        // State persisted across gesture resets
        var lastTapMs  = 0L
        var lastTapPos = Offset.Zero

        while (true) {
            // ── Wait for first touch-down on Initial pass ─────────────────
            // Initial pass = parent-first, so we beat any child pointerInput.
            var startPos   = Offset.Zero
            var startMs    = 0L
            var gotDown    = false
            var fingerUp   = false
            var secondStartY  = Float.NaN
            var secondCurrentY = Float.NaN
            var maxPointers   = 0
            var currentPos    = Offset.Zero

            awaitPointerEventScope {
                // Spin until we find a down event
                while (!gotDown) {
                    val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val down = ev.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (down != null) {
                        startPos   = down.position
                        currentPos = startPos
                        startMs    = System.currentTimeMillis()
                        maxPointers = ev.changes.count { it.pressed }
                        gotDown = true
                    }
                }

                // ── Track the gesture until all fingers lift ───────────────
                while (true) {
                    val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val pressed = ev.changes.filter { it.pressed }
                    maxPointers = maxOf(maxPointers, pressed.size)

                    // Track first pointer position
                    ev.changes.firstOrNull()?.let { currentPos = it.position }

                    // Track second pointer for two-finger detection
                    if (ev.changes.size >= 2) {
                        val p2y = ev.changes[1].position.y
                        if (secondStartY.isNaN()) secondStartY = p2y
                        secondCurrentY = p2y
                    }

                    // Two-finger swipe up — fire in real-time
                    if (maxPointers >= 2 && !secondStartY.isNaN()) {
                        val dy1 = currentPos.y - startPos.y
                        val dy2 = secondCurrentY - secondStartY
                        if (dy1 < -twoFingerPx && dy2 < -twoFingerPx) {
                            fire(settings.twoFingerSwipeUp)
                            // drain remaining events
                            while (ev.changes.any { it.pressed }) {
                                awaitPointerEvent(pass = PointerEventPass.Initial)
                            }
                            break
                        }
                    }

                    if (pressed.isEmpty()) {
                        fingerUp = true
                        break
                    }
                }
            }

            if (!fingerUp) continue   // two-finger fired; restart

            // ── Classify gesture on finger-up ─────────────────────────────
            val totalDx  = currentPos.x - startPos.x
            val totalDy  = currentPos.y - startPos.y
            val durationMs = System.currentTimeMillis() - startMs

            val isTap = durationMs < 400 &&
                        abs(totalDx) < tapMovePx &&
                        abs(totalDy) < tapMovePx &&
                        maxPointers == 1

            val isSwipe = abs(totalDy) > swipePx &&
                          abs(totalDy) / maxOf(abs(totalDx), 1f) > 1.5f &&
                          maxPointers == 1

            when {
                isTap -> {
                    val now  = System.currentTimeMillis()
                    val dist = (currentPos - lastTapPos).getDistance()
                    if (now - lastTapMs < doubleTapMs && dist < tapMovePx * 4) {
                        fire(settings.doubleTap)
                        lastTapMs = 0L
                    } else {
                        lastTapMs  = now
                        lastTapPos = currentPos
                    }
                }
                isSwipe && totalDy < 0 -> fire(settings.swipeUp)
                isSwipe && totalDy > 0 -> fire(settings.swipeDown)
            }
        }
    }
}
