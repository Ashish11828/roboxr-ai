package com.example.roboxr.safety

import com.example.roboxr.model.ExportFormat
import com.example.roboxr.model.TaskPlan
import com.example.roboxr.model.ValidationResult
import com.example.roboxr.model.Waypoint
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic Digital Twin Safety Guardrails (ISO 10218-1 / ISO/TS 15066).
 * Executes pure mathematical kinematic checks in < 1 ms with zero network or LLM calls.
 * Enforces Zero Direct LLM-to-Motor Control: no robot program can be generated unless passed == true.
 */
object SafetyGuardrails {
    const val MIN_Z_FLOOR_MM = 35.0
    const val MAX_RADIAL_REACH_MM = 820.0
    const val MAX_COLLAB_VELOCITY_MM_S = 250.0

    // CNC Fixture Clamp Keep-Out Volume (AABB)
    const val CLAMP_MIN_X = 310.0
    const val CLAMP_MAX_X = 395.0
    const val CLAMP_MIN_Y = 65.0
    const val CLAMP_MAX_Y = 145.0
    const val CLAMP_MAX_Z = 160.0
    const val SAFE_TRANSIT_Z_MM = 220.0

    fun validate(waypoints: List<Waypoint>): ValidationResult {
        val startNs = System.nanoTime()
        val violations = mutableListOf<String>()

        if (waypoints.isEmpty()) {
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0
            return ValidationResult(
                passed = false,
                latencyMs = latencyMs,
                zFloorClear = false,
                reachabilityOk = false,
                collisionFree = false,
                velocitySafe = false,
                violations = listOf("Plan contains zero waypoints; motion execution blocked.")
            )
        }

        var zFloorClear = true
        var reachabilityOk = true
        var collisionFree = true
        var velocitySafe = true

        for (wp in waypoints) {
            // 1. Worktable Z-floor minimum clearance: zMm >= 35.0 mm
            if (wp.zMm < MIN_Z_FLOOR_MM) {
                zFloorClear = false
                violations.add(
                    String.format(
                        Locale.US,
                        "Step %d (%s): Z-floor clearance violation (Z=%.1f mm < 35.0 mm min)",
                        wp.stepId, wp.label, wp.zMm
                    )
                )
            }

            // 2. Radial cobot reach envelope: sqrt(x^2 + y^2 + z^2) <= 820.0 mm
            val radius = radialDistance(wp.xMm, wp.yMm, wp.zMm)
            if (radius > MAX_RADIAL_REACH_MM) {
                reachabilityOk = false
                violations.add(
                    String.format(
                        Locale.US,
                        "Step %d (%s): Exceeds cobot reach envelope (R=%.1f mm > 820.0 mm max)",
                        wp.stepId, wp.label, radius
                    )
                )
            }

            // 3. CNC fixture clamp keep-out box: x in 310..395, y in 65..145, z <= 160
            if (isPointInClampBox(wp.xMm, wp.yMm, wp.zMm)) {
                collisionFree = false
                violations.add(
                    String.format(
                        Locale.US,
                        "Step %d (%s): Waypoint inside CNC fixture clamp keep-out box (%.0f, %.0f, %.0f)",
                        wp.stepId, wp.label, wp.xMm, wp.yMm, wp.zMm
                    )
                )
            }

            // 4. Collaborative TCP velocity cap: speedMmS <= 250.0 mm/s (and > 0)
            if (wp.speedMmS > MAX_COLLAB_VELOCITY_MM_S || wp.speedMmS <= 0.0) {
                velocitySafe = false
                violations.add(
                    String.format(
                        Locale.US,
                        "Step %d (%s): TCP velocity outside ISO/TS 15066 cap (%.1f mm/s; allowed 1..250 mm/s)",
                        wp.stepId, wp.label, wp.speedMmS
                    )
                )
            }
        }

        // 5. Continuous segment swept-path intersection against CNC fixture clamp keep-out box
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            if (!isPointInClampBox(a.xMm, a.yMm, a.zMm) &&
                !isPointInClampBox(b.xMm, b.yMm, b.zMm) &&
                segmentIntersectsClampBox(a, b)
            ) {
                collisionFree = false
                violations.add(
                    String.format(
                        Locale.US,
                        "Transit Step %d→%d: Linear swept path intersects CNC fixture clamp keep-out volume",
                        a.stepId, b.stepId
                    )
                )
            }
        }

        val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0
        val passed = zFloorClear && reachabilityOk && collisionFree && velocitySafe

        return ValidationResult(
            passed = passed,
            latencyMs = latencyMs,
            zFloorClear = zFloorClear,
            reachabilityOk = reachabilityOk,
            collisionFree = collisionFree,
            velocitySafe = velocitySafe,
            violations = violations
        )
    }

    fun radialDistance(x: Double, y: Double, z: Double): Double =
        sqrt(x * x + y * y + z * z)

    fun isPointInClampBox(x: Double, y: Double, z: Double): Boolean {
        return x in CLAMP_MIN_X..CLAMP_MAX_X &&
            y in CLAMP_MIN_Y..CLAMP_MAX_Y &&
            z <= CLAMP_MAX_Z
    }

    /**
     * Exact 3D Slab Method ray/segment vs AABB intersection test.
     * Box: X [310.0, 395.0], Y [65.0, 145.0], Z [-1000.0, 160.0]
     */
    fun segmentIntersectsClampBox(a: Waypoint, b: Waypoint): Boolean {
        var tMin = 0.0
        var tMax = 1.0

        val axes = listOf(
            Triple(a.xMm, b.xMm - a.xMm, CLAMP_MIN_X to CLAMP_MAX_X),
            Triple(a.yMm, b.yMm - a.yMm, CLAMP_MIN_Y to CLAMP_MAX_Y),
            Triple(a.zMm, b.zMm - a.zMm, -1000.0 to CLAMP_MAX_Z)
        )

        for ((origin, delta, bounds) in axes) {
            val (boxMin, boxMax) = bounds
            if (abs(delta) < 1e-9) {
                if (origin < boxMin || origin > boxMax) return false
            } else {
                var t1 = (boxMin - origin) / delta
                var t2 = (boxMax - origin) / delta
                if (t1 > t2) {
                    val tmp = t1
                    t1 = t2
                    t2 = tmp
                }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                if (tMin > tMax) return false
            }
        }
        return true
    }

    /**
     * Deterministic Safety Remediation Solver:
     * Clamps Z >= 42mm, clamps TCP velocity <= 220 mm/s, projects out-of-reach points inside 800mm sphere,
     * lifts any waypoint inside or crossing the CNC fixture clamp above Z = 220mm safe transit ceiling.
     */
    fun autoRemediateWaypoints(waypoints: List<Waypoint>): List<Waypoint> {
        val remediated = mutableListOf<Waypoint>()
        var nextStepId = 1

        val clampedPoints = waypoints.map { wp ->
            var x = wp.xMm
            var y = wp.yMm
            var z = max(wp.zMm, MIN_Z_FLOOR_MM + 7.0) // 42mm safe floor margin
            val speed = wp.speedMmS.coerceIn(40.0, 220.0)

            // If point sits inside clamp keep-out box, elevate safely above clamp Z ceiling
            if (isPointInClampBox(x, y, z)) {
                z = SAFE_TRANSIT_Z_MM
            }

            // Ensure radial reach <= 800mm (with 20mm safety margin under 820mm hard cap)
            val r = radialDistance(x, y, z)
            if (r > 800.0) {
                val scale = 800.0 / r
                x *= scale
                y *= scale
                z = max(z * scale, MIN_Z_FLOOR_MM + 7.0)
                // Re-check clamp after radial projection
                if (isPointInClampBox(x, y, z)) {
                    x = CLAMP_MIN_X - 25.0
                }
            }

            wp.copy(xMm = round1(x), yMm = round1(y), zMm = round1(z), speedMmS = round1(speed))
        }

        for (i in clampedPoints.indices) {
            val current = clampedPoints[i]
            if (remediated.isNotEmpty()) {
                val prev = remediated.last()
                if (segmentIntersectsClampBox(prev, current)) {
                    // Insert two high-clearance bridge waypoints over the CNC fixture clamp
                    val liftZ = computeSafeBridgeZ(prev.xMm, prev.yMm, current.xMm, current.yMm)
                    remediated.add(
                        Waypoint(
                            stepId = nextStepId++,
                            stage = "SAFETY_BYPASS",
                            label = "Safe Lift Over Clamp",
                            xMm = prev.xMm,
                            yMm = prev.yMm,
                            zMm = liftZ,
                            gripperClosed = prev.gripperClosed,
                            speedMmS = min(prev.speedMmS, 180.0)
                        )
                    )
                    remediated.add(
                        Waypoint(
                            stepId = nextStepId++,
                            stage = "SAFETY_BYPASS",
                            label = "Safe Bridge Clear",
                            xMm = current.xMm,
                            yMm = current.yMm,
                            zMm = liftZ,
                            gripperClosed = prev.gripperClosed,
                            speedMmS = min(current.speedMmS, 180.0)
                        )
                    )
                }
            }
            remediated.add(current.copy(stepId = nextStepId++))
        }

        return remediated
    }

    private fun computeSafeBridgeZ(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val maxXY = max(sqrt(x1 * x1 + y1 * y1), sqrt(x2 * x2 + y2 * y2))
        val maxAllowedZ = sqrt(max(0.0, 800.0 * 800.0 - maxXY * maxXY))
        return round1(min(SAFE_TRANSIT_Z_MM, max(CLAMP_MAX_Z + 20.0, maxAllowedZ)))
    }

    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0
}

/**
 * Generates deterministic shop-floor robot controller scripts strictly gated by SafetyGuardrails.
 */
object RobotCodeGenerator {
    fun generate(
        plan: TaskPlan,
        validation: ValidationResult,
        format: ExportFormat,
        operatorId: String
    ): String {
        require(validation.passed) {
            "SAFETY LOCKOUT: Cannot generate robot motion code while ISO 10218 guardrails fail."
        }

        val sb = StringBuilder()
        when (format) {
            ExportFormat.URSCRIPT -> {
                sb.appendLine("# RoboXR AI Studio v2.0-Pro (Team Alclan) - Verified URScript")
                sb.appendLine("# Task: ${plan.taskName} | Engine: ${plan.engine}")
                sb.appendLine("# Operator Sign-Off: $operatorId | ISO 10218 Gate: PASS (${String.format(Locale.US, "%.2f", validation.latencyMs)} ms)")
                sb.appendLine("def roboxr_verified_program():")
                sb.appendLine("  set_tcp(p[0.0, 0.0, 0.145, 0.0, 0.0, 0.0])")
                sb.appendLine("  set_payload(1.85)")
                for (wp in plan.waypoints) {
                    val xM = wp.xMm / 1000.0
                    val yM = wp.yMm / 1000.0
                    val zM = wp.zMm / 1000.0
                    val vMs = wp.speedMmS / 1000.0
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            "  # Step %d [%s]: %s",
                            wp.stepId, wp.stage, wp.label
                        )
                    )
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            "  movel(p[%.4f, %.4f, %.4f, 3.1416, 0.0, 0.0], a=0.6, v=%.3f)",
                            xM, yM, zM, vMs
                        )
                    )
                    sb.appendLine(
                        if (wp.gripperClosed) "  set_standard_digital_out(0, True)  # Gripper CLOSE"
                        else "  set_standard_digital_out(0, False) # Gripper OPEN"
                    )
                }
                sb.appendLine("end")
                sb.appendLine("roboxr_verified_program()")
            }

            ExportFormat.KUKA_KRL -> {
                sb.appendLine("; RoboXR AI Studio v2.0-Pro (Team Alclan) - Verified KUKA KRL")
                sb.appendLine("; Task: ${plan.taskName} | Operator: $operatorId")
                sb.appendLine("DEF ROBOXR_CELL_PROG()")
                sb.appendLine("  BAS(#INITMOV, 0)")
                for (wp in plan.waypoints) {
                    val vMs = wp.speedMmS / 1000.0
                    sb.appendLine("; Step ${wp.stepId} [${wp.stage}]: ${wp.label}")
                    sb.appendLine(String.format(Locale.US, "  \$VEL.CP = %.3f", vMs))
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            "  LIN {X %.1f, Y %.1f, Z %.1f, A 0.0, B 90.0, C 0.0}",
                            wp.xMm, wp.yMm, wp.zMm
                        )
                    )
                    sb.appendLine(if (wp.gripperClosed) "  \$OUT[1] = TRUE" else "  \$OUT[1] = FALSE")
                }
                sb.appendLine("END")
            }

            ExportFormat.ISO_GCODE -> {
                sb.appendLine("(ROBOXR AI STUDIO V2.0-PRO - TEAM ALCLAN - VERIFIED ISO G-CODE)")
                sb.appendLine("(TASK: ${plan.taskName.uppercase(Locale.US)} | OP: $operatorId)")
                sb.appendLine("G21 G90 G17 (MM, ABSOLUTE, XY PLANE)")
                for (wp in plan.waypoints) {
                    val feedMmMin = wp.speedMmS * 60.0
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            "N%02d G01 X%.1f Y%.1f Z%.1f F%.0f (%s)",
                            wp.stepId * 10, wp.xMm, wp.yMm, wp.zMm, feedMmMin, wp.label.uppercase(Locale.US)
                        )
                    )
                    sb.appendLine(if (wp.gripperClosed) "M10 (CLAMP/GRASP ON)" else "M11 (CLAMP/GRASP OFF)")
                }
                sb.appendLine("M30 (PROGRAM END)")
            }
        }
        return sb.toString()
    }
}

object AuditHasher {
    fun computeSha256(
        timestamp: String,
        operatorId: String,
        taskName: String,
        status: String,
        programCode: String
    ): String {
        val canonical = "$timestamp|$operatorId|$taskName|$status|$programCode"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
