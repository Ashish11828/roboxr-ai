package com.example.roboxr.xr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import androidx.xr.runtime.XrDevice
import androidx.xr.scenecore.scene
import com.example.roboxr.ui.RoboXRUiState
import com.example.roboxr.ui.components.DigitalTwinCanvasCard
import com.example.roboxr.ui.components.SafetyGuardrailsCard

/**
 * Official Jetpack XR SDK (`androidx.xr.compose` + `androidx.xr.scenecore` + `androidx.xr.runtime`)
 * Spatial Container targeting Android XR Wired Glasses (XREAL Project Aura) and XR Headsets.
 *
 * - Automatically detects `LocalSpatialCapabilities.current.isSpatialUiEnabled`.
 * - In Android XR Full Space: renders dual floating 3D `SpatialPanel`s inside a `Subspace` `SpatialRow`
 *   (Main Teach-In/Jam Console + Dedicated Digital Twin & ISO 10218 Telemetry Panel).
 * - On standard 2D Android phones / XREAL Beam Pro host: renders the responsive flat controller UI
 *   while tethered XREAL glasses receive the dedicated additive-black `XrealOpticalPresentation`.
 */
@Composable
fun XrealSpatialHost(
    hardwareManager: XrealHardwareManager,
    uiState: RoboXRUiState,
    onAutoRemediate: () -> Unit,
    onToggleSim: () -> Unit,
    onResetSim: () -> Unit,
    onScrubSim: (Float) -> Unit,
    mainContent: @Composable (onToggleSpaceMode: (() -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    val spatialCaps = LocalSpatialCapabilities.current
    val session = LocalSession.current

    val isSpatialUi = spatialCaps.isSpatialUiEnabled
    val isContent3d = spatialCaps.isContent3dEnabled
    val isPassthrough = spatialCaps.isPassthroughControlEnabled

    LaunchedEffect(isSpatialUi, isContent3d, isPassthrough, session) {
        val blendMode = runCatching {
            XrDevice.getCurrentDevice(context).getPreferredDisplayBlendMode().toString()
        }.getOrDefault(if (session != null) "ADDITIVE_OR_ALPHA" else "STANDARD_2D")

        hardwareManager.updateJetpackXrCapabilities(
            isSpatialUiEnabled = isSpatialUi,
            isContent3dEnabled = isContent3d,
            isPassthroughControlEnabled = isPassthrough,
            displayBlendMode = blendMode,
            isXrSessionAvailable = session != null
        )
    }

    val toggleSpaceCallback: (() -> Unit)? = if (session != null) {
        {
            runCatching {
                if (isSpatialUi) {
                    session.scene.requestHomeSpace()
                } else {
                    session.scene.requestFullSpace()
                }
            }
        }
    } else {
        null
    }

    if (isSpatialUi) {
        // Android XR / XREAL Project Aura Multi-Panel 3D Spatial Layout
        Subspace {
            SpatialRow {
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(860.dp)
                        .height(700.dp)
                ) {
                    mainContent(toggleSpaceCallback)
                }

                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(500.dp)
                        .height(700.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "XREAL SPATIAL TELEMETRY WING",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SafetyGuardrailsCard(
                                validation = uiState.validation,
                                onAutoRemediate = onAutoRemediate
                            )
                            DigitalTwinCanvasCard(
                                waypoints = uiState.currentPlan.waypoints,
                                validation = uiState.validation,
                                simProgress = uiState.simProgress,
                                isPlaying = uiState.isSimPlaying,
                                selectedWaypointIndex = uiState.selectedWaypointIndex,
                                onTogglePlay = onToggleSim,
                                onReset = onResetSim,
                                onScrub = onScrubSim,
                                topDownHeight = 250.dp,
                                showElevationProfile = true
                            )
                        }
                    }
                }
            }
        }
    } else {
        mainContent(toggleSpaceCallback)
    }
}
