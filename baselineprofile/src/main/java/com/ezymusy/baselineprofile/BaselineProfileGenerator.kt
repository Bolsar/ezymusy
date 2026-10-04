package com.ezymusy.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start to a rendered Library. Run: ./gradlew :app:generateReleaseBaselineProfile */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = "com.ezymusy.app") {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Shuffle all")), 5_000)
    }
}
