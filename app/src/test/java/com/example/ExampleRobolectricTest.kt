package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun readAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RoboXR AI", appName)
    }

    @Test
    fun fullEndToEndCuj_tabsDemoSafetyAndXrealConsoleWorkWithoutCrash() {
        composeRule.waitForIdle()
        // Frame 1 verification: XREAL status strip, DEMO trigger, and preset chips visible immediately
        composeRule.onNodeWithTag("xreal_sdk_status_strip").assertExists()
        composeRule.onNodeWithTag("start_live_demo_button").assertExists()
        composeRule.onNodeWithTag("preset_clamp_collision_test").assertExists()

        // Trigger Clamp Keep-Out violation and verify Auto-Remediation button appears and succeeds
        composeRule.onNodeWithTag("preset_clamp_collision_test").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_remediate_button").assertExists().performClick()
        composeRule.waitForIdle()

        // Open and close XREAL Diagnostics Console Dialog
        composeRule.onNodeWithTag("xreal_sdk_status_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("xreal_recenter_button").assertExists().performClick()
        composeRule.onNodeWithTag("close_xreal_dialog_button").performClick()
        composeRule.waitForIdle()

        // Step through ALL 6 Live Interview Demo steps without blocking or crashing
        composeRule.onNodeWithTag("start_live_demo_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guided_demo_banner").assertExists()
        composeRule.onNodeWithTag("demo_inline_xreal_hud").assertExists()

        repeat(5) {
            composeRule.onNodeWithTag("demo_next_button").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("demo_stop_button").performClick()
        composeRule.waitForIdle()

        // Navigate all 4 tabs cleanly
        composeRule.onNodeWithTag("nav_tab_jam_recovery").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_tab_digital_twin").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_tab_audit_log").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("verify_chain_button").assertExists().performClick()
        composeRule.waitForIdle()
    }
}
