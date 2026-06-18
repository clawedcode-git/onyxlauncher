package com.onyxlauncher.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile covering launcher cold start and the
 * drawer-open/scroll hot path. The androidx.baselineprofile plugin wires the
 * produced profile back into :app so it ships in the APK and is installed at
 * first run by ProfileInstaller.
 *
 * Run: ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait()
        waitForLauncher()
        openDrawerAndScroll()
    }
}
