package com.example.roboxr.xr

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.roboxr.safety.SafetyGuardrails
import com.example.roboxr.ui.RoboXRUiState
import com.example.ui.theme.RoboXRTheme
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Secondary Optical Display Presentation for tethered USB-C XREAL Smart Glasses
 * (Air, Air 2, Air 2 Pro, Air 2 Ultra, One) operating via DisplayPort Alternate Mode.
 *
 * Uses pure #000000 black background which renders 100% optically transparent on XREAL
 * birdbath/waveguide additive micro-OLED optics so shop-floor operators keep unobstructed
 * line-of-sight on the physical CNC workcell.
 */
class XrealOpticalPresentation(
    private val hostActivity: ComponentActivity,
    display: Display,
    private val uiStateFlow: StateFlow<RoboXRUiState>,
    private val hardwareStateFlow: StateFlow<XrealHardwareState>
) : Presentation(hostActivity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(hostActivity)
            setViewTreeSavedStateRegistryOwner(hostActivity)
            setContent {
                val uiState by uiStateFlow.collectAsStateWithLifecycle()
                val hwState by hardwareStateFlow.collectAsStateWithLifecycle()
                RoboXRTheme {
                    XrealAdditiveOpticalHud(
                        uiState = uiState,
                        hardwareState = hwState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        setContentView(composeView)
    }
}

/**
 * High-contrast Additive-Black (#000000 transparent) XREAL Optical Lens HUD.
 * Used both on the physical external XREAL display via Presentation AND inside the
 * in-app Optical Lens Mirror preview.
 */
@Composable
fun XrealAdditiveOpticalHud(
    uiState: RoboXRUiState,
    hardwareState: XrealHardwareState,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val safetyColor = if (uiState.validation.passed) colorScheme.tertiary else colorScheme.error
    val waypoints = uiState.currentPlan.waypoints
    val activeWp = waypoints.getOrNull(uiState.selectedWaypointIndex) ?: waypoints.firstOrNull()

    Box(
        modifier = modifier
            .background(Color.Black) // Optically transparent on XREAL additive birdbath/waveguide lenses
            .padding(14.dp)
    ) {
        // Wireframe Spatial Trajectory + 3DoF Head-Tracked Optical Reticle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Parallax offset driven by real 3DoF IMU orientation
            val reticleX = (cx + hardwareState.yawDeg * 3.2f).coerceIn(48f, size.width - 48f)
            val reticleY = (cy + hardwareState.pitchDeg * -3.2f).coerceIn(48f, size.height - 48f)

            // Corner optical framing brackets
            val bLen = 26f
            val bracketColor = colorScheme.primary.copy(alpha = 0.7f)
            drawLine(bracketColor, Offset(0f, 0f), Offset(bLen, 0f), strokeWidth = 3f)
            drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bLen), strokeWidth = 3f)
            drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width - bLen, 0f), strokeWidth = 3f)
            drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, bLen), strokeWidth = 3f)
            drawLine(bracketColor, Offset(0f, size.height), Offset(bLen, size.height), strokeWidth = 3f)
            drawLine(bracketColor, Offset(0f, size.height), Offset(0f, size.height - bLen), strokeWidth = 3f)
            drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width - bLen, size.height), strokeWidth = 3f)
            drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width, size.height - bLen), strokeWidth = 3f)

            // Mini spatial wireframe projection of CNC clamp & trajectory
            val scaleX = (size.width * 0.65f) / 860f
            val scaleY = (size.height * 0.45f) / 760f
            val scale = minOf(scaleX, scaleY)
            val originX = cx - (380f * scale)
            val originY = cy + 12f

            fun mapPt(xMm: Double, yMm: Double): Offset =
                Offset(originX + (xMm.toFloat() * scale), originY - (yMm.toFloat() * scale))

            // Clamp keep-out wireframe
            val cTL = mapPt(SafetyGuardrails.CLAMP_MIN_X, SafetyGuardrails.CLAMP_MAX_Y)
            val cBR = mapPt(SafetyGuardrails.CLAMP_MAX_X, SafetyGuardrails.CLAMP_MIN_Y)
            drawRect(
                color = if (uiState.validation.collisionFree) colorScheme.secondary else colorScheme.error,
                topLeft = cTL,
                size = Size(cBR.x - cTL.x, cBR.y - cTL.y),
                style = Stroke(width = 2.5f)
            )

            // Waypoint spline
            for (i in 0 until waypoints.size - 1) {
                val a = waypoints[i]
                val b = waypoints[i + 1]
                val bad = SafetyGuardrails.segmentIntersectsClampBox(a, b) ||
                    b.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM ||
                    b.speedMmS > SafetyGuardrails.MAX_COLLAB_VELOCITY_MM_S
                drawLine(
                    color = if (bad) colorScheme.error else colorScheme.primary,
                    start = mapPt(a.xMm, a.yMm),
                    end = mapPt(b.xMm, b.yMm),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // 3DoF Optical Targeting Reticle
            drawCircle(
                color = safetyColor,
                radius = 22f,
                center = Offset(reticleX, reticleY),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )
            )
            drawLine(safetyColor, Offset(reticleX - 30f, reticleY), Offset(reticleX - 10f, reticleY), 2f)
            drawLine(safetyColor, Offset(reticleX + 10f, reticleY), Offset(reticleX + 30f, reticleY), 2f)
            drawLine(safetyColor, Offset(reticleX, reticleY - 30f), Offset(reticleX, reticleY - 10f), 2f)
            drawLine(safetyColor, Offset(reticleX, reticleY + 10f), Offset(reticleX, reticleY + 30f), 2f)
        }

        // Top HUD Strip (ISO Gate + 3DoF Pose)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black,
                border = BorderStroke(1.2.dp, safetyColor)
            ) {
                Text(
                    text = if (uiState.validation.passed) {
                        "ISO 10218 PASS • ${String.format(Locale.US, "%.2fms", uiState.validation.latencyMs)}"
                    } else {
                        "SAFETY LOCKOUT • ${uiState.validation.violations.size} FAULT(S)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = safetyColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, colorScheme.primary)
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "3DoF Y:%.0f° P:%.0f° R:%.0f°",
                        hardwareState.yawDeg, hardwareState.pitchDeg, hardwareState.rollDeg
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Bottom HUD Strip (Active Step + HRI Directive)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (activeWp != null) {
                Text(
                    text = String.format(
                        Locale.US,
                        "STEP %d [%s]  X:%.0f  Y:%.0f  Z:%.0f mm  V:%.0f mm/s",
                        activeWp.stepId, activeWp.stage, activeWp.xMm, activeWp.yMm, activeWp.zMm, activeWp.speedMmS
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.secondary
                )
            }
            val directive = uiState.currentPlan.hriGuidance.firstOrNull().orEmpty()
            if (directive.isNotBlank()) {
                Text(
                    text = "HUD ▸ $directive",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onBackground,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
