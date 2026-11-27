package com.example.roboxr.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.roboxr.model.ValidationResult
import com.example.roboxr.model.Waypoint
import com.example.roboxr.safety.SafetyGuardrails
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun DigitalTwinCanvasCard(
    waypoints: List<Waypoint>,
    validation: ValidationResult,
    simProgress: Float,
    isPlaying: Boolean,
    selectedWaypointIndex: Int,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier,
    topDownHeight: Dp = 230.dp,
    showElevationProfile: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val interpolated = interpolateTcp(waypoints, simProgress)
    val radiusMm = SafetyGuardrails.radialDistance(interpolated.xMm, interpolated.yMm, interpolated.zMm)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (validation.passed) colorScheme.outline else colorScheme.error.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header + Live Telemetry Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DIGITAL TWIN • XY WORKCELL & XZ ELEVATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary
                    )
                    Text(
                        text = interpolated.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (interpolated.gripperClosed) colorScheme.secondaryContainer else colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (interpolated.gripperClosed) "GRIP CLOSED" else "GRIP OPEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (interpolated.gripperClosed) colorScheme.secondary else colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Top-Down XY Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topDownHeight)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(colorScheme.surfaceVariant, colorScheme.background)
                        ),
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // World coordinates mapped into DrawScope: X in [-60..860], Y in [-460..460]
                    val minWorldX = -80.0
                    val maxWorldX = 880.0
                    val minWorldY = -460.0
                    val maxWorldY = 460.0

                    val scaleX = size.width / (maxWorldX - minWorldX).toFloat()
                    val scaleY = size.height / (maxWorldY - minWorldY).toFloat()
                    val scale = minOf(scaleX, scaleY)

                    val offsetX = (size.width - (maxWorldX - minWorldX).toFloat() * scale) / 2f
                    val offsetY = (size.height - (maxWorldY - minWorldY).toFloat() * scale) / 2f

                    fun toCanvas(xMm: Double, yMm: Double): Offset {
                        val cx = offsetX + ((xMm - minWorldX).toFloat() * scale)
                        val cy = offsetY + ((maxWorldY - yMm).toFloat() * scale)
                        return Offset(cx, cy)
                    }

                    // 1. Tactical CAD Grid every 100mm
                    val gridColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
                    var gx = 0.0
                    while (gx <= 800.0) {
                        val top = toCanvas(gx, maxWorldY)
                        val bottom = toCanvas(gx, minWorldY)
                        drawLine(gridColor, top, bottom, strokeWidth = 1f)
                        gx += 100.0
                    }
                    var gy = -400.0
                    while (gy <= 400.0) {
                        val left = toCanvas(minWorldX, gy)
                        val right = toCanvas(maxWorldX, gy)
                        drawLine(gridColor, left, right, strokeWidth = 1f)
                        gy += 100.0
                    }

                    val baseCenter = toCanvas(0.0, 0.0)

                    // 2. 820mm Cobot Spherical Reach Envelope Arc
                    val reachRadiusPx = (SafetyGuardrails.MAX_RADIAL_REACH_MM.toFloat() * scale)
                    val reachColor = if (validation.reachabilityOk) {
                        colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        colorScheme.error.copy(alpha = 0.85f)
                    }
                    drawCircle(
                        color = reachColor,
                        radius = reachRadiusPx,
                        center = baseCenter,
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )

                    // 3. CNC Fixture Clamp Keep-Out Box (X: 310..395, Y: 65..145)
                    val clampTopLeft = toCanvas(SafetyGuardrails.CLAMP_MIN_X, SafetyGuardrails.CLAMP_MAX_Y)
                    val clampBottomRight = toCanvas(SafetyGuardrails.CLAMP_MAX_X, SafetyGuardrails.CLAMP_MIN_Y)
                    val clampSize = Size(
                        width = clampBottomRight.x - clampTopLeft.x,
                        height = clampBottomRight.y - clampTopLeft.y
                    )
                    val clampAccent = if (validation.collisionFree) colorScheme.secondary else colorScheme.error
                    drawRect(
                        color = clampAccent.copy(alpha = 0.22f),
                        topLeft = clampTopLeft,
                        size = clampSize
                    )
                    drawRect(
                        color = clampAccent,
                        topLeft = clampTopLeft,
                        size = clampSize,
                        style = Stroke(width = 2.5f)
                    )
                    // Diagonal hazard hatching inside clamp box
                    drawLine(
                        color = clampAccent.copy(alpha = 0.5f),
                        start = clampTopLeft,
                        end = clampBottomRight,
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = clampAccent.copy(alpha = 0.5f),
                        start = Offset(clampBottomRight.x, clampTopLeft.y),
                        end = Offset(clampTopLeft.x, clampBottomRight.y),
                        strokeWidth = 1.5f
                    )

                    // 4. Trajectory Segments (colored cyan if safe, crimson if violating)
                    for (i in 0 until waypoints.size - 1) {
                        val a = waypoints[i]
                        val b = waypoints[i + 1]
                        val segBad = SafetyGuardrails.segmentIntersectsClampBox(a, b) ||
                            SafetyGuardrails.isPointInClampBox(a.xMm, a.yMm, a.zMm) ||
                            SafetyGuardrails.isPointInClampBox(b.xMm, b.yMm, b.zMm) ||
                            SafetyGuardrails.radialDistance(b.xMm, b.yMm, b.zMm) > SafetyGuardrails.MAX_RADIAL_REACH_MM ||
                            b.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM ||
                            b.speedMmS > SafetyGuardrails.MAX_COLLAB_VELOCITY_MM_S

                        drawLine(
                            color = if (segBad) colorScheme.error else colorScheme.primary,
                            start = toCanvas(a.xMm, a.yMm),
                            end = toCanvas(b.xMm, b.yMm),
                            strokeWidth = if (segBad) 4.5f else 3f,
                            cap = StrokeCap.Round
                        )
                    }

                    // 5. Cobot Base Pedestal at (0, 0)
                    drawCircle(
                        color = colorScheme.surfaceVariant,
                        radius = 16f,
                        center = baseCenter
                    )
                    drawCircle(
                        color = colorScheme.primary,
                        radius = 16f,
                        center = baseCenter,
                        style = Stroke(width = 3f)
                    )

                    // 6. Articulated 2-Link Cobot Arm Preview to Live TCP
                    val tcpPos = toCanvas(interpolated.xMm, interpolated.yMm)
                    val dx = tcpPos.x - baseCenter.x
                    val dy = tcpPos.y - baseCenter.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val mid = Offset(
                        x = baseCenter.x + dx * 0.52f - (dy / dist) * (dist * 0.16f),
                        y = baseCenter.y + dy * 0.52f + (dx / dist) * (dist * 0.16f)
                    )
                    drawLine(
                        color = colorScheme.onSurface.copy(alpha = 0.45f),
                        start = baseCenter,
                        end = mid,
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = colorScheme.onSurface.copy(alpha = 0.72f),
                        start = mid,
                        end = tcpPos,
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = colorScheme.primary, radius = 5f, center = mid)

                    // 7. Waypoint Nodes
                    waypoints.forEachIndexed { idx, wp ->
                        val p = toCanvas(wp.xMm, wp.yMm)
                        val wpViolated = wp.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM ||
                            SafetyGuardrails.radialDistance(wp.xMm, wp.yMm, wp.zMm) > SafetyGuardrails.MAX_RADIAL_REACH_MM ||
                            SafetyGuardrails.isPointInClampBox(wp.xMm, wp.yMm, wp.zMm) ||
                            wp.speedMmS > SafetyGuardrails.MAX_COLLAB_VELOCITY_MM_S

                        val nodeColor = when {
                            wpViolated -> colorScheme.error
                            idx == selectedWaypointIndex -> colorScheme.tertiary
                            wp.gripperClosed -> colorScheme.secondary
                            else -> colorScheme.primary
                        }
                        drawCircle(
                            color = nodeColor,
                            radius = if (idx == selectedWaypointIndex) 8.5f else 5.5f,
                            center = p
                        )
                    }

                    // 8. Live TCP Optical Reticle
                    val reticleColor = if (validation.passed) colorScheme.tertiary else colorScheme.error
                    drawCircle(
                        color = reticleColor.copy(alpha = 0.25f),
                        radius = 18f,
                        center = tcpPos
                    )
                    drawCircle(
                        color = reticleColor,
                        radius = 12f,
                        center = tcpPos,
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f,
                        center = tcpPos
                    )
                }

                // Corner CAD legend pills
                Text(
                    text = "R≤820mm",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
                Text(
                    text = "CLAMP X[310..395] Y[65..145]",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (validation.collisionFree) colorScheme.secondary else colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }

            // Optional XZ Side Elevation Profile (Z-floor 35mm + Clamp Z<=160mm)
            if (showElevationProfile) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .background(colorScheme.background, MaterialTheme.shapes.small)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        val maxX = 850.0
                        val maxZ = 340.0

                        fun toXZ(xMm: Double, zMm: Double): Offset {
                            val px = ((xMm.coerceIn(0.0, maxX) / maxX).toFloat() * size.width)
                            val pz = size.height - ((zMm.coerceIn(0.0, maxZ) / maxZ).toFloat() * size.height)
                            return Offset(px, pz)
                        }

                        // Z=35mm Worktable Floor Hard Limit Line
                        val floorY = toXZ(0.0, SafetyGuardrails.MIN_Z_FLOOR_MM).y
                        drawLine(
                            color = if (validation.zFloorClear) colorScheme.tertiary.copy(alpha = 0.65f) else colorScheme.error,
                            start = Offset(0f, floorY),
                            end = Offset(size.width, floorY),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )

                        // Fixture Clamp Elevation Block (X: 310..395, Z: 0..160)
                        val cTopLeft = toXZ(SafetyGuardrails.CLAMP_MIN_X, SafetyGuardrails.CLAMP_MAX_Z)
                        val cBotRight = toXZ(SafetyGuardrails.CLAMP_MAX_X, 0.0)
                        val cColor = if (validation.collisionFree) colorScheme.secondary else colorScheme.error
                        drawRect(
                            color = cColor.copy(alpha = 0.25f),
                            topLeft = cTopLeft,
                            size = Size(cBotRight.x - cTopLeft.x, cBotRight.y - cTopLeft.y)
                        )
                        drawRect(
                            color = cColor,
                            topLeft = cTopLeft,
                            size = Size(cBotRight.x - cTopLeft.x, cBotRight.y - cTopLeft.y),
                            style = Stroke(width = 2f)
                        )

                        // Elevation Path
                        if (waypoints.size >= 2) {
                            val path = Path()
                            val first = toXZ(waypoints.first().xMm, waypoints.first().zMm)
                            path.moveTo(first.x, first.y)
                            waypoints.drop(1).forEach { wp ->
                                val pt = toXZ(wp.xMm, wp.zMm)
                                path.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                path = path,
                                color = colorScheme.primary,
                                style = Stroke(width = 2.5f)
                            )
                        }

                        // Live TCP dot on elevation profile
                        val liveXZ = toXZ(interpolated.xMm, interpolated.zMm)
                        drawCircle(
                            color = if (interpolated.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM) colorScheme.error else colorScheme.tertiary,
                            radius = 5.5f,
                            center = liveXZ
                        )
                    }
                    Text(
                        text = "XZ PROFILE • Z-FLOOR 35mm • CLAMP Z≤160mm",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Numeric CAD Readout Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryMetric(label = "X", value = String.format(Locale.US, "%.0f mm", interpolated.xMm))
                TelemetryMetric(label = "Y", value = String.format(Locale.US, "%.0f mm", interpolated.yMm))
                TelemetryMetric(
                    label = "Z",
                    value = String.format(Locale.US, "%.0f mm", interpolated.zMm),
                    alert = interpolated.zMm < SafetyGuardrails.MIN_Z_FLOOR_MM
                )
                TelemetryMetric(
                    label = "R",
                    value = String.format(Locale.US, "%.0f mm", radiusMm),
                    alert = radiusMm > SafetyGuardrails.MAX_RADIAL_REACH_MM
                )
                TelemetryMetric(
                    label = "TCP V",
                    value = String.format(Locale.US, "%.0f mm/s", interpolated.speedMmS),
                    alert = interpolated.speedMmS > SafetyGuardrails.MAX_COLLAB_VELOCITY_MM_S
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Simulation Scrubber Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sim_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_simulation),
                        tint = colorScheme.primary
                    )
                }
                Slider(
                    value = simProgress,
                    onValueChange = onScrub,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sim_progress_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary,
                        inactiveTrackColor = colorScheme.outlineVariant
                    )
                )
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sim_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = stringResource(R.string.cd_reset_simulation),
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    alert: Boolean = false
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun interpolateTcp(waypoints: List<Waypoint>, progress: Float): Waypoint {
    if (waypoints.isEmpty()) {
        return Waypoint(0, "IDLE", "No Waypoints", 0.0, 0.0, 100.0, false, 0.0)
    }
    if (waypoints.size == 1) return waypoints.first()

    val scaled = progress.coerceIn(0f, 1f) * (waypoints.size - 1)
    val idx = scaled.toInt().coerceIn(0, waypoints.size - 2)
    val frac = (scaled - idx).toDouble()
    val a = waypoints[idx]
    val b = waypoints[idx + 1]

    return Waypoint(
        stepId = b.stepId,
        stage = b.stage,
        label = "Step ${a.stepId}→${b.stepId}: ${b.label}",
        xMm = a.xMm + (b.xMm - a.xMm) * frac,
        yMm = a.yMm + (b.yMm - a.yMm) * frac,
        zMm = a.zMm + (b.zMm - a.zMm) * frac,
        gripperClosed = if (frac < 0.5) a.gripperClosed else b.gripperClosed,
        speedMmS = b.speedMmS
    )
}
