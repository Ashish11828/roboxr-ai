package com.example.roboxr.planner

import android.util.Log
import com.example.roboxr.model.PlannerMode
import com.example.roboxr.model.SpatialAnchor
import com.example.roboxr.model.TaskPlan
import com.example.roboxr.model.Waypoint
import com.example.roboxr.safety.SafetyGuardrails
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "RoboXRPlanner"

sealed interface PlannerOutcome {
    data class Success(val plan: TaskPlan) : PlannerOutcome
    data class Error(val message: String, val canFallbackOffline: Boolean = true) : PlannerOutcome
}

/**
 * Multimodal & Offline CNC Machine-Tending Task Planner.
 * - Cloud/Hybrid mode uses Firebase AI Logic (`gemini-3.8-flash`) with strict `Schema.obj(...)` structured JSON output.
 * - Offline Edge mode executes a 100% deterministic local kinematics synthesizer with zero connectivity required.
 */
class RoboXRPlanner {

    private val taskPlanSchema = Schema.obj(
        properties = mapOf(
            "task_name" to Schema.string(description = "Concise CNC tending or jam recovery task title"),
            "cycle_time_sec" to Schema.double(description = "Estimated total cycle duration in seconds"),
            "hri_guidance" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 concise XREAL Smart Glasses HUD guidance directives for the shop-floor operator"
            ),
            "waypoints" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "step_id" to Schema.integer(description = "1-based sequential step number"),
                        "stage" to Schema.string(description = "Motion phase such as HOME, APPROACH, PICK, TRANSIT, PLACE, RETRACT"),
                        "label" to Schema.string(description = "Short descriptive motion label"),
                        "x_mm" to Schema.double(description = "X coordinate in millimeters"),
                        "y_mm" to Schema.double(description = "Y coordinate in millimeters"),
                        "z_mm" to Schema.double(description = "Z elevation in millimeters"),
                        "gripper_closed" to Schema.boolean(description = "True when pneumatic/electric gripper is closed"),
                        "speed_mm_s" to Schema.double(description = "Collaborative TCP speed in mm/s")
                    )
                ),
                description = "Ordered 6 to 9 3D waypoints from home through pick/extract, safe transit, place, and retract"
            )
        )
    )

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = taskPlanSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun planTask(
        instruction: String,
        pickAnchor: SpatialAnchor,
        placeAnchor: SpatialAnchor,
        mode: PlannerMode,
        isJamRecovery: Boolean = false
    ): PlannerOutcome {
        return when (mode) {
            PlannerMode.OFFLINE_EDGE -> {
                PlannerOutcome.Success(
                    planOfflineDeterministic(
                        instruction = instruction,
                        pickAnchor = pickAnchor,
                        placeAnchor = placeAnchor,
                        isJamRecovery = isJamRecovery
                    )
                )
            }

            PlannerMode.GEMINI_AI_LOGIC -> {
                planWithGeminiAiLogic(
                    instruction = instruction,
                    pickAnchor = pickAnchor,
                    placeAnchor = placeAnchor,
                    isJamRecovery = isJamRecovery
                )
            }
        }
    }

    private suspend fun planWithGeminiAiLogic(
        instruction: String,
        pickAnchor: SpatialAnchor,
        placeAnchor: SpatialAnchor,
        isJamRecovery: Boolean
    ): PlannerOutcome {
        val startMs = System.currentTimeMillis()
        val prompt = buildString {
            appendLine("You are RoboXR AI Studio's industrial robotics planner for brownfield CNC machine-tending and 30-second jam recovery on XREAL Smart Glasses.")
            appendLine("Operator Instruction: $instruction")
            appendLine("Mode: ${if (isJamRecovery) "30-SECOND CNC JAM RECOVERY" else "CNC TEACH-IN MACHINE TENDING"}")
            appendLine(
                String.format(
                    Locale.US,
                    "Pick/Target Anchor (mm): X=%.1f, Y=%.1f, Z=%.1f",
                    pickAnchor.xMm, pickAnchor.yMm, pickAnchor.zMm
                )
            )
            appendLine(
                String.format(
                    Locale.US,
                    "Place/Drop Anchor (mm): X=%.1f, Y=%.1f, Z=%.1f",
                    placeAnchor.xMm, placeAnchor.yMm, placeAnchor.zMm
                )
            )
            appendLine("Cell Constraints (informational - downstream ISO 10218 digital twin enforces hard limits):")
            appendLine("- Worktable Z-floor minimum clearance: Z >= 35.0 mm")
            appendLine("- Cobot spherical reach envelope: sqrt(X^2 + Y^2 + Z^2) <= 820.0 mm")
            appendLine("- CNC Fixture Clamp Keep-Out Box: X in [310..395], Y in [65..145], Z <= 160 mm (route high at Z >= 220 mm unless the operator explicitly requests a violation stress test)")
            appendLine("- Collaborative TCP velocity cap: speed_mm_s <= 250.0 mm/s (slower 75-120 mm/s for jam recovery or grasping)")
            appendLine("Honor exact Pick and Place anchor coordinates in the PICK and PLACE waypoints so the Digital Twin Safety Guardrails can audit them faithfully.")
            appendLine("Generate 7 or 8 structured waypoints and 4 actionable XREAL HUD HRI operator guidance steps.")
        }

        return try {
            val response = withTimeout(60_000L) {
                generativeModel.generateContent(prompt)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return PlannerOutcome.Error("Gemini returned an empty response. Try again or switch to Offline Kinematics.")
            }

            val planLatencyMs = System.currentTimeMillis() - startMs
            val parsed = parseStructuredPlan(
                jsonString = rawJson,
                engineName = "Gemini 3.8 Flash (Firebase AI Logic)",
                planLatencyMs = planLatencyMs
            )
            PlannerOutcome.Success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini task planning timed out", e)
            PlannerOutcome.Error("AI planning timed out after 60s. Retry or run 100% Offline Kinematics.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI Logic inference failed", e)
            PlannerOutcome.Error(
                e.localizedMessage
                    ?: "Firebase AI Logic request failed. Check network or switch to 100% Offline Kinematics."
            )
        }
    }

    private fun parseStructuredPlan(
        jsonString: String,
        engineName: String,
        planLatencyMs: Long
    ): TaskPlan {
        val root = JSONObject(jsonString)
        val taskName = root.optString("task_name", "CNC Machine-Tending Cycle")
        val cycleTimeSec = root.optDouble("cycle_time_sec", 18.5)

        val hriArray = root.optJSONArray("hri_guidance")
        val hriGuidance = mutableListOf<String>()
        if (hriArray != null) {
            for (i in 0 until hriArray.length()) {
                val item = hriArray.optString(i)
                if (item.isNotBlank()) hriGuidance.add(item)
            }
        }
        if (hriGuidance.isEmpty()) {
            hriGuidance.add("Verify CNC door interlock and spindle zero-RPM status in XR HUD.")
        }

        val wpArray = root.getJSONArray("waypoints")
        val waypoints = mutableListOf<Waypoint>()
        for (i in 0 until wpArray.length()) {
            val obj = wpArray.getJSONObject(i)
            waypoints.add(
                Waypoint(
                    stepId = obj.optInt("step_id", i + 1),
                    stage = obj.optString("stage", "MOVE").uppercase(Locale.US),
                    label = obj.optString("label", "Waypoint ${i + 1}"),
                    xMm = obj.getDouble("x_mm"),
                    yMm = obj.getDouble("y_mm"),
                    zMm = obj.getDouble("z_mm"),
                    gripperClosed = obj.optBoolean("gripper_closed", false),
                    speedMmS = obj.optDouble("speed_mm_s", 140.0)
                )
            )
        }

        return TaskPlan(
            taskName = taskName,
            engine = engineName,
            planLatencyMs = planLatencyMs,
            cycleTimeSec = cycleTimeSec,
            waypoints = waypoints,
            hriGuidance = hriGuidance
        )
    }

    /**
     * 100% Standalone Offline Deterministic Kinematics Synthesizer.
     * Preserves exact operator spatial anchors (and requested stress-test velocities/clearances)
     * so SafetyGuardrails always perform authentic physical enforcement.
     */
    fun planOfflineDeterministic(
        instruction: String,
        pickAnchor: SpatialAnchor,
        placeAnchor: SpatialAnchor,
        isJamRecovery: Boolean = false
    ): TaskPlan {
        val startNs = System.nanoTime()
        val lower = instruction.lowercase(Locale.US)

        // Detect if operator explicitly asked for a stress-test velocity or low-clearance transit
        val explicitSpeedMatch = Regex("""(\d{2,3})\s*mm/s""").find(lower)
        val requestedSpeed = explicitSpeedMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val forceLowTransit = lower.contains("without vertical lift") || lower.contains("low-clearance")

        val transitSpeed = requestedSpeed ?: if (isJamRecovery) 135.0 else 210.0
        val approachSpeed = if (requestedSpeed != null && requestedSpeed > 250.0) requestedSpeed else if (isJamRecovery) 80.0 else 115.0
        val graspSpeed = if (isJamRecovery) 65.0 else 85.0

        val safeOverheadZ = if (forceLowTransit) {
            max(pickAnchor.zMm, placeAnchor.zMm)
        } else {
            max(SafetyGuardrails.SAFE_TRANSIT_Z_MM, max(pickAnchor.zMm, placeAnchor.zMm) + 85.0).coerceAtMost(320.0)
        }

        val homeZ = 280.0
        val waypoints = listOf(
            Waypoint(
                stepId = 1,
                stage = "HOME",
                label = "Cell Safe Home",
                xMm = 180.0,
                yMm = -80.0,
                zMm = homeZ,
                gripperClosed = false,
                speedMmS = min(transitSpeed, 200.0)
            ),
            Waypoint(
                stepId = 2,
                stage = "PRE_PICK",
                label = if (isJamRecovery) "Hover Above Jam" else "Approach Pick Anchor",
                xMm = pickAnchor.xMm,
                yMm = pickAnchor.yMm,
                zMm = if (forceLowTransit) pickAnchor.zMm else max(pickAnchor.zMm + 95.0, safeOverheadZ),
                gripperClosed = false,
                speedMmS = transitSpeed
            ),
            Waypoint(
                stepId = 3,
                stage = "PICK",
                label = if (isJamRecovery) "Engage Jammed Part" else "Grasp CNC Billet",
                xMm = pickAnchor.xMm,
                yMm = pickAnchor.yMm,
                zMm = pickAnchor.zMm,
                gripperClosed = true,
                speedMmS = approachSpeed
            ),
            Waypoint(
                stepId = 4,
                stage = "LIFT",
                label = if (forceLowTransit) "Low Skim Lift" else "Vertical Clear Lift",
                xMm = pickAnchor.xMm,
                yMm = pickAnchor.yMm,
                zMm = if (forceLowTransit) pickAnchor.zMm else safeOverheadZ,
                gripperClosed = true,
                speedMmS = approachSpeed
            ),
            Waypoint(
                stepId = 5,
                stage = "TRANSIT",
                label = if (isJamRecovery) "Clear Fixture Transit" else "Overhead Clamp Transit",
                xMm = placeAnchor.xMm,
                yMm = placeAnchor.yMm,
                zMm = if (forceLowTransit) placeAnchor.zMm else safeOverheadZ,
                gripperClosed = true,
                speedMmS = transitSpeed
            ),
            Waypoint(
                stepId = 6,
                stage = "PLACE",
                label = if (isJamRecovery) "Release in Quarantine" else "Seat Into CNC Chuck",
                xMm = placeAnchor.xMm,
                yMm = placeAnchor.yMm,
                zMm = placeAnchor.zMm,
                gripperClosed = false,
                speedMmS = graspSpeed
            ),
            Waypoint(
                stepId = 7,
                stage = "RETRACT",
                label = "Safe Vertical Retract",
                xMm = placeAnchor.xMm,
                yMm = placeAnchor.yMm,
                zMm = max(placeAnchor.zMm + 110.0, 230.0),
                gripperClosed = false,
                speedMmS = min(transitSpeed, 210.0)
            ),
            Waypoint(
                stepId = 8,
                stage = "HOME",
                label = "Return Ready Position",
                xMm = 180.0,
                yMm = -80.0,
                zMm = homeZ,
                gripperClosed = false,
                speedMmS = min(transitSpeed, 200.0)
            )
        )

        val cycleTime = computeKinematicCycleTimeSec(waypoints)
        val elapsedMs = max(1L, (System.nanoTime() - startNs) / 1_000_000L)

        val title = when {
            isJamRecovery -> "30s CNC Jam Extraction Cycle"
            lower.contains("haas") || lower.contains("chuck") -> "Haas VF-2 Billet Load Cycle"
            lower.contains("clamp") -> "CNC Clamp Clearance Audit"
            lower.contains("rotor") -> "Rotor Transfer Reach Check"
            else -> "CNC Machine-Tending Teach-In"
        }

        val guidance = if (isJamRecovery) {
            listOf(
                "Confirm CNC spindle is at 0 RPM and safety door interlock is latched on XR HUD.",
                "Inspect Digital Twin wireframe overlay to verify cobot clears hydraulic clamp block (X 310-395 mm).",
                "Hold enabling deadman switch while collaborative extraction runs at <= 135 mm/s.",
                "Verify quarantined workpiece clears chuck jaws before resuming automatic CNC cycle."
            )
        } else {
            listOf(
                "Align XREAL optical reticle with Pick & Place spatial anchors on CNC worktable.",
                "Verify all four ISO 10218 Digital Twin safety gates show green PASS before export.",
                "Keep hands clear of vise jaw actuation zone during Step 6 billet seating.",
                "Sign off with Operator ID to commit SHA-256 tamper-evident audit digest."
            )
        }

        return TaskPlan(
            taskName = title,
            engine = "Offline Edge Kinematics (0-Server)",
            planLatencyMs = elapsedMs,
            cycleTimeSec = cycleTime,
            waypoints = waypoints,
            hriGuidance = guidance
        )
    }

    fun computeKinematicCycleTimeSec(waypoints: List<Waypoint>): Double {
        if (waypoints.size < 2) return 0.0
        var totalSec = 0.0
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val dx = b.xMm - a.xMm
            val dy = b.yMm - a.yMm
            val dz = b.zMm - a.zMm
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            val speed = max(b.speedMmS, 20.0)
            totalSec += dist / speed
            if (a.gripperClosed != b.gripperClosed) {
                totalSec += 0.65 // Pneumatic gripper actuation dwell
            }
        }
        return kotlin.math.round(totalSec * 10.0) / 10.0
    }
}
