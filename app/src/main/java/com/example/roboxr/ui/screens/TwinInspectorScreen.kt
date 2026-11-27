package com.example.roboxr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.roboxr.model.Waypoint
import com.example.roboxr.safety.SafetyGuardrails
import com.example.roboxr.ui.RoboXRUiState
import com.example.roboxr.ui.components.DigitalTwinCanvasCard
import com.example.roboxr.ui.components.SafetyGuardrailsCard
import java.util.Locale

@Composable
fun TwinInspectorScreen(
    state: RoboXRUiState,
    onSelectWaypoint: (Int) -> Unit,
    onUpdateWaypoint: (Int, (Waypoint) -> Waypoint) -> Unit,
    onAutoRemediate: () -> Unit,
    onToggleSim: () -> Unit,
    onResetSim: () -> Unit,
    onScrubSim: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DigitalTwinCanvasCard(
                waypoints = state.currentPlan.waypoints,
                validation = state.validation,
                simProgress = state.simProgress,
                isPlaying = state.isSimPlaying,
                selectedWaypointIndex = state.selectedWaypointIndex,
                onTogglePlay = onToggleSim,
                onReset = onResetSim,
                onScrub = onScrubSim,
                topDownHeight = 250.dp,
                showElevationProfile = true
            )
        }

        item {
            SafetyGuardrailsCard(
                validation = state.validation,
                onAutoRemediate = onAutoRemediate
            )
        }

        item {
            Text(
                text = "WAYPOINT KINEMATICS INSPECTOR (TAP TO TUNE LIVE)",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.primary
            )
        }

        itemsIndexed(
            items = state.currentPlan.waypoints,
            key = { index, wp -> "${wp.stepId}_$index" }
        ) { index, wp ->
            val selected = index == state.selectedWaypointIndex
            val rMm = SafetyGuardrails.radialDistance(wp.xMm, wp.yMm, wp.zMm)
            val wpUnsafe = wp.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM ||
                rMm > SafetyGuardrails.MAX_RADIAL_REACH_MM ||
                SafetyGuardrails.isPointInClampBox(wp.xMm, wp.yMm, wp.zMm) ||
                wp.speedMmS > SafetyGuardrails.MAX_COLLAB_VELOCITY_MM_S

            val borderColor = when {
                wpUnsafe -> colorScheme.error
                selected -> colorScheme.primary
                else -> colorScheme.outlineVariant
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWaypoint(index) }
                    .testTag("waypoint_card_${wp.stepId}"),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) colorScheme.surfaceVariant else colorScheme.surface,
                border = BorderStroke(if (selected || wpUnsafe) 1.5.dp else 1.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (wpUnsafe) colorScheme.errorContainer else colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "S${wp.stepId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (wpUnsafe) colorScheme.error else colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = wp.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "X:%.0f  Y:%.0f  Z:%.0f mm  |  V:%.0f mm/s  |  R:%.0f mm",
                                        wp.xMm, wp.yMm, wp.zMm, wp.speedMmS, rMm
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (wpUnsafe) colorScheme.error else colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = if (wp.gripperClosed) "CLOSED" else "OPEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wp.gripperClosed) colorScheme.secondary else colorScheme.primary
                        )
                    }

                    if (selected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        WaypointAxisSlider(
                            label = "X Position",
                            value = wp.xMm.toFloat(),
                            range = 0f..820f,
                            unit = "mm",
                            onValueChange = { v -> onUpdateWaypoint(index) { it.copy(xMm = v.toDouble()) } },
                            testTag = "wp_slider_x"
                        )
                        WaypointAxisSlider(
                            label = "Y Position",
                            value = wp.yMm.toFloat(),
                            range = -400f..450f,
                            unit = "mm",
                            onValueChange = { v -> onUpdateWaypoint(index) { it.copy(yMm = v.toDouble()) } },
                            testTag = "wp_slider_y"
                        )
                        WaypointAxisSlider(
                            label = "Z Elevation (Floor ≥ 35 mm)",
                            value = wp.zMm.toFloat(),
                            range = 10f..340f,
                            unit = "mm",
                            alert = wp.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM,
                            onValueChange = { v -> onUpdateWaypoint(index) { it.copy(zMm = v.toDouble()) } },
                            testTag = "wp_slider_z"
                        )
                        WaypointAxisSlider(
                            label = "TCP Velocity (Cap ≤ 250 mm/s)",
                            value = wp.speedMmS.toFloat(),
                            range = 20f..350f,
                            unit = "mm/s",
                            alert = wp.speedMmS > SafetyGuardrails.MAX_COLLAB_VELOCITY_MM_S,
                            onValueChange = { v -> onUpdateWaypoint(index) { it.copy(speedMmS = v.toDouble()) } },
                            testTag = "wp_slider_speed"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pneumatic Gripper Closed",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface
                            )
                            Switch(
                                checked = wp.gripperClosed,
                                onCheckedChange = { closed ->
                                    onUpdateWaypoint(index) { it.copy(gripperClosed = closed) }
                                },
                                modifier = Modifier.testTag("wp_gripper_switch")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaypointAxisSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
    testTag: String,
    alert: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (alert) colorScheme.error else colorScheme.primary
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (alert) colorScheme.error else colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.0f %s", value, unit),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent
            )
        )
    }
}
