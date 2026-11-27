package com.example.roboxr.model

data class Waypoint(
    val stepId: Int,
    val stage: String,
    val label: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val gripperClosed: Boolean,
    val speedMmS: Double
)

data class ValidationResult(
    val passed: Boolean,
    val latencyMs: Double,
    val zFloorClear: Boolean,
    val reachabilityOk: Boolean,
    val collisionFree: Boolean,
    val velocitySafe: Boolean,
    val violations: List<String>
)

data class TaskPlan(
    val taskName: String,
    val engine: String,
    val planLatencyMs: Long,
    val cycleTimeSec: Double,
    val waypoints: List<Waypoint>,
    val hriGuidance: List<String>
)

data class AuditRecord(
    val id: Long = 0L,
    val timestamp: String,
    val operatorId: String,
    val taskName: String,
    val status: String,
    val sha256: String,
    val engine: String = "",
    val waypointCount: Int = 0,
    val programCode: String = ""
)

data class SpatialAnchor(
    val xMm: Double,
    val yMm: Double,
    val zMm: Double
)

enum class PlannerMode(val displayName: String, val badge: String) {
    GEMINI_AI_LOGIC("Gemini AI Planner", "AI LOGIC"),
    OFFLINE_EDGE("Offline Kinematics", "100% EDGE")
}

enum class ExportFormat(val label: String, val extension: String) {
    URSCRIPT("URScript (UR5e/UR10e)", ".script"),
    KUKA_KRL("KUKA KRL (iiwa)", ".src"),
    ISO_GCODE("ISO G-Code / Haas", ".nc")
}

data class TeachInPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val instruction: String,
    val pick: SpatialAnchor,
    val place: SpatialAnchor,
    val expectViolation: Boolean = false
)

data class JamScenario(
    val id: String,
    val code: String,
    val title: String,
    val symptom: String,
    val instruction: String,
    val jamAnchor: SpatialAnchor,
    val safeDropAnchor: SpatialAnchor
)

object RoboXRPresets {
    val teachInPresets = listOf(
        TeachInPreset(
            id = "haas_vf2_load",
            title = "Haas VF-2 Chuck Load",
            subtitle = "Nominal billet pick & CNC vise load",
            instruction = "Pick 6061 aluminum billet from gravity infeed tray, lift clear over hydraulic fixture clamp, seat into CNC chuck vise, and retract to safe home.",
            pick = SpatialAnchor(xMm = 210.0, yMm = -180.0, zMm = 75.0),
            place = SpatialAnchor(xMm = 470.0, yMm = 195.0, zMm = 110.0),
            expectViolation = false
        ),
        TeachInPreset(
            id = "clamp_collision_test",
            title = "Clamp Keep-Out Test",
            subtitle = "Triggers CNC fixture clamp collision guard",
            instruction = "Direct low-clearance transit across CNC table clamp at Z=120mm from infeed to chuck without vertical lift.",
            pick = SpatialAnchor(xMm = 340.0, yMm = 95.0, zMm = 120.0),
            place = SpatialAnchor(xMm = 480.0, yMm = 135.0, zMm = 115.0),
            expectViolation = true
        ),
        TeachInPreset(
            id = "z_floor_test",
            title = "Z-Floor & Speed Stress",
            subtitle = "Tests Z < 35mm and > 250 mm/s velocity lockout",
            instruction = "High-speed skim pickup near worktable surface at Z=18mm and 340 mm/s TCP speed.",
            pick = SpatialAnchor(xMm = 220.0, yMm = -140.0, zMm = 18.0),
            place = SpatialAnchor(xMm = 440.0, yMm = 210.0, zMm = 90.0),
            expectViolation = true
        ),
        TeachInPreset(
            id = "reach_envelope_test",
            title = "820mm Reach Boundary",
            subtitle = "Tests radial spherical envelope violation",
            instruction = "Transfer finished rotor shaft to far inspection bin outside cobot spherical reach envelope.",
            pick = SpatialAnchor(xMm = 240.0, yMm = -120.0, zMm = 85.0),
            place = SpatialAnchor(xMm = 740.0, yMm = 420.0, zMm = 180.0),
            expectViolation = true
        )
    )

    val jamScenarios = listOf(
        JamScenario(
            id = "chuck_jaw_misalign",
            code = "ALM-412",
            title = "Chuck Jaw Misalignment",
            symptom = "Billet seated 4mm skewed against soft jaw #2; spindle interlock halted.",
            instruction = "Execute 30-second jam recovery: slow collaborative approach at 80 mm/s, grasp skewed billet at chuck, vertical extract clear of clamp zone, deposit in quarantine chute, and return home.",
            jamAnchor = SpatialAnchor(xMm = 465.0, yMm = 185.0, zMm = 118.0),
            safeDropAnchor = SpatialAnchor(xMm = 185.0, yMm = -230.0, zMm = 95.0)
        ),
        JamScenario(
            id = "chip_nest_obstruction",
            code = "ALM-209",
            title = "Chip Nest Obstruction",
            symptom = "Swarf bird-nest blocking vise proximity sensor; air-blast failed to clear.",
            instruction = "Execute 30-second swarf clear & part extraction: approach from Z=240mm overhead clearance, retrieve half-machined part cleanly above hydraulic clamp, move to inspection tray.",
            jamAnchor = SpatialAnchor(xMm = 445.0, yMm = 210.0, zMm = 105.0),
            safeDropAnchor = SpatialAnchor(xMm = 200.0, yMm = -190.0, zMm = 80.0)
        ),
        JamScenario(
            id = "spindle_stall_extract",
            code = "ALM-501",
            title = "ATC Spindle Stall Recovery",
            symptom = "Tool-changer stalled mid-cycle; safe cobot extraction required after door interlock.",
            instruction = "Execute 30-second safe stall extraction: verify spindle zero-RPM interlock, extract workpiece at 95 mm/s max TCP speed with high-arch clearance over fixture clamp to reject chute.",
            jamAnchor = SpatialAnchor(xMm = 485.0, yMm = 165.0, zMm = 125.0),
            safeDropAnchor = SpatialAnchor(xMm = 175.0, yMm = -215.0, zMm = 90.0)
        ),
        JamScenario(
            id = "part_drop_near_clamp",
            code = "ALM-318",
            title = "Dropped Part Near Clamp",
            symptom = "Vacuum slip dropped aluminum flange adjacent to hydraulic clamp block.",
            instruction = "Execute 30-second recovery near clamp keep-out boundary: strict vertical descent outside X[310..395] Y[65..145] clamp volume, grasp dropped flange, lift to Z=240mm, place in recovery bin.",
            jamAnchor = SpatialAnchor(xMm = 285.0, yMm = 105.0, zMm = 60.0),
            safeDropAnchor = SpatialAnchor(xMm = 190.0, yMm = -205.0, zMm = 85.0)
        )
    )
}
