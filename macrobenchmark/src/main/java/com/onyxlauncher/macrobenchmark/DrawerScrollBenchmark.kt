package com.onyxlauncher.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while opening and flinging the app drawer — the launcher's most
 * scroll-heavy surface. P50/P90/P99 frame durations surface jank.
 *
 * Run: ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DrawerScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun drawerScrollBaselineProfile() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 8,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startActivityAndWait()
            waitForLauncher()
        },
    ) {
        openDrawerAndScroll()
    }
}
