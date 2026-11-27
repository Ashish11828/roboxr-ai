package com.example.roboxr.xr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roboxr.ui.RoboXRUiState
import java.util.Locale

/**
 * Compact persistent XREAL SDK & Android XR hardware telemetry bar.
 */
@Composable
fun XrealHudStatusStrip(
    hardwareState: XrealHardwareState,
    isDemoRunning: Boolean,
    onOpenDiagnostics: () -> Unit,
    onStartDemo: () -> Unit,
    onToggleSpaceMode: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val connected = hardwareState.isUsbGlassesConnected ||
        hardwareState.externalDisplayConnected ||
        hardwareState.isSpatialUiEnabled

    val statusColor = when {
        hardwareState.isSpatialUiEnabled -> colorScheme.tertiary
        connected -> colorScheme.primary
        else -> colorScheme.secondary
    }

    val statusLabel = when {
        hardwareState.isSpatialUiEnabled -> "ANDROID XR FULL SPACE"
        hardwareState.isUsbGlassesConnected -> hardwareState.glassesModel.uppercase(Locale.US)
        hardwareState.externalDisplayConnected -> "XREAL OPTICAL DISPLAY ACTIVE"
        else -> "XREAL SDK READY"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpenDiagnostics)
            .testTag("xreal_sdk_status_strip"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "XREAL Smart Glasses SDK status",
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "3DoF Y:%.0f° P:%.0f° • Tap for XREAL Console",
                            hardwareState.yawDeg, hardwareState.pitchDeg
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDemoRunning) colorScheme.tertiaryContainer else colorScheme.primaryContainer,
                    border = BorderStroke(
                        1.dp,
                        if (isDemoRunning) colorScheme.tertiary else colorScheme.primary
                    ),
                    modifier = Modifier
                        .clickable(onClick = onStartDemo)
                        .padding(vertical = 4.dp)
                        .testTag("start_live_demo_button")
                ) {
                    Text(
                        text = if (isDemoRunning) "LIVE" else "DEMO",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDemoRunning) colorScheme.tertiary else colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                if (onToggleSpaceMode != null) {
                    IconButton(
                        onClick = onToggleSpaceMode,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_xr_space_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = "Toggle Android XR Full Space or Home Space",
                            tint = colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full XREAL SDK & Android XR Hardware Diagnostics + Additive Optical Lens HUD Mirror Dialog.
 */
@Composable
fun XrealDiagnosticsDialog(
    uiState: RoboXRUiState,
    hardwareState: XrealHardwareState,
    onRescanHardware: () -> Unit,
    onRecenter3DoF: () -> Unit,
    onGazeLockPick: () -> Unit,
    onGazeLockPlace: () -> Unit,
    onToggleSpaceMode: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surface,
            border = BorderStroke(1.2.dp, colorScheme.primary)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "XREAL & ANDROID XR SDK CONSOLE",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.primary
                        )
                        Text(
                            text = "Jetpack XR SDK + USB-C HID (0x3318) + Optical Presentation",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_xreal_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close XREAL SDK console",
                            tint = colorScheme.onSurface
                        )
                    }
                }

                // Live Additive-Black (#000000 transparent) XREAL Optical Lens HUD Mirror
                Text(
                    text = "LIVE XREAL OPTICAL LENS OUTPUT (#000000 = SEE-THROUGH)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.secondary
                )
                XrealAdditiveOpticalHud(
                    uiState = uiState,
                    hardwareState = hardwareState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(MaterialTheme.shapes.medium)
                )

                // Hardware & Jetpack XR Capability Diagnostics
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.background,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagRow("Device / Runtime", hardwareState.glassesModel)
                        DiagRow(
                            "USB-C HID (0x3318)",
                            if (hardwareState.isUsbGlassesConnected) {
                                "CONNECTED (${hardwareState.usbVendorIdHex}:${hardwareState.usbProductIdHex}, ${hardwareState.usbInterfaceCount} ifaces)"
                            } else {
                                "Not Detected (Plug XREAL via USB-C)"
                            }
                        )
                        DiagRow(
                            "DP Alt Mode Optics",
                            if (hardwareState.externalDisplayConnected) {
                                "${hardwareState.externalDisplayName} • ${hardwareState.externalDisplayResolution} @ ${hardwareState.externalDisplayRefreshHz?.toInt() ?: 60}Hz"
                            } else {
                                "No External Optical Display"
                            }
                        )
                        DiagRow(
                            "Jetpack XR Spatial UI",
                            if (hardwareState.isSpatialUiEnabled) "ACTIVE (Full Space Subspace)" else "2D Host / Home Space"
                        )
                        DiagRow("XR Display Blend Mode", hardwareState.displayBlendMode)
                        DiagRow(
                            "3DoF Orientation IMU",
                            if (hardwareState.isImuActive) {
                                String.format(
                                    Locale.US,
                                    "Y:%.1f° P:%.1f° R:%.1f° (%s)",
                                    hardwareState.yawDeg, hardwareState.pitchDeg, hardwareState.rollDeg, hardwareState.imuSource
                                )
                            } else {
                                hardwareState.imuSource
                            }
                        )
                    }
                }

                // 3DoF Spatial Gaze Anchor Lock Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onGazeLockPick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("xreal_gaze_lock_pick"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text("Gaze-Lock Pick", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = onGazeLockPlace,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("xreal_gaze_lock_place"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondary,
                            contentColor = colorScheme.onSecondary
                        )
                    ) {
                        Text("Gaze-Lock Place", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Recenter 3DoF & Hardware Rescan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRecenter3DoF,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("xreal_recenter_button"),
                        border = BorderStroke(1.dp, colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recenter 3DoF", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onRescanHardware,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("xreal_rescan_usb_button"),
                        border = BorderStroke(1.dp, colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Usb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rescan USB-C", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (onToggleSpaceMode != null) {
                    OutlinedButton(
                        onClick = onToggleSpaceMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("xreal_switch_space_button"),
                        border = BorderStroke(1.dp, colorScheme.tertiary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hardwareState.isSpatialUiEnabled) {
                                "Switch Android XR to Home Space"
                            } else {
                                "Launch Android XR Full Space Panels"
                            },
                            color = colorScheme.tertiary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(0.58f)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
