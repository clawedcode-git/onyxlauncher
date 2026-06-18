package com.onyxlauncher.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "com.onyxlauncher"

/**
 * Opens the app drawer (swipe up on the dock) and flings the app grid a few
 * times. Shared by the drawer-scroll benchmark and the baseline-profile
 * generator so both exercise the same hot path.
 */
fun MacrobenchmarkScope.openDrawerAndScroll() {
    // Swipe up from near the dock to open the drawer.
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(width / 2, (height * 0.85).toInt(), width / 2, (height * 0.3).toInt(), 10)
    device.waitForIdle()

    // Fling the drawer grid up and down to capture scroll frames.
    val grid = device.findObject(By.scrollable(true))
    if (grid != null) {
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }
        grid.fling(Direction.UP)
        device.waitForIdle()
    } else {
        // Fallback: raw swipes if the scrollable node isn't found.
        repeat(3) {
            device.swipe(width / 2, (height * 0.7).toInt(), width / 2, (height * 0.3).toInt(), 8)
            device.waitForIdle()
        }
    }
    device.pressBack()
    device.waitForIdle()
}

/** Wait for the launcher's first frame after a cold start. */
fun MacrobenchmarkScope.waitForLauncher() {
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
}
