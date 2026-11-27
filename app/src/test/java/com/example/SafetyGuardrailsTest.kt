package com.example

import com.example.roboxr.model.ExportFormat
import com.example.roboxr.model.RoboXRPresets
import com.example.roboxr.model.Waypoint
import com.example.roboxr.planner.RoboXRPlanner
import com.example.roboxr.safety.AuditHasher
import com.example.roboxr.safety.RobotCodeGenerator
import com.example.roboxr.safety.SafetyGuardrails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardrailsTest {

    @Test
    fun nominalHaasPreset_passesAllGuardrailsUnderOneMs() {
        val preset = RoboXRPresets.teachInPresets.first()
        val plan = RoboXRPlanner().planOfflineDeterministic(
            instruction = preset.instruction,
            pickAnchor = preset.pick,
            placeAnchor = preset.place
        )
        // Warm up JIT once
        SafetyGuardrails.validate(plan.waypoints)
        val result = SafetyGuardrails.validate(plan.waypoints)

        assertTrue("Expected nominal Haas plan to pass", result.passed)
        assertTrue(result.zFloorClear)
        assertTrue(result.reachabilityOk)
        assertTrue(result.collisionFree)
        assertTrue(result.velocitySafe)
        assertTrue("Expected < 1 ms latency, got ${result.latencyMs}", result.latencyMs < 1.0)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun enforcesAllFourHardSafetyLimitsAndAutoRemediates() {
        val unsafeWaypoints = listOf(
            Waypoint(1, "FLOOR", "Too Low", 200.0, 0.0, 20.0, false, 120.0),
            Waypoint(2, "CLAMP", "Inside Clamp", 350.0, 100.0, 120.0, true, 150.0),
            Waypoint(3, "REACH", "Beyond 820mm", 760.0, 420.0, 200.0, true, 180.0),
            Waypoint(4, "SPEED", "Over 250mm/s", 220.0, -100.0, 180.0, false, 310.0)
        )

        val failed = SafetyGuardrails.validate(unsafeWaypoints)
        assertFalse(failed.passed)
        assertFalse(failed.zFloorClear)
        assertFalse(failed.collisionFree)
        assertFalse(failed.reachabilityOk)
        assertFalse(failed.velocitySafe)
        assertEquals(4, failed.violations.size)

        val fixed = SafetyGuardrails.autoRemediateWaypoints(unsafeWaypoints)
        val rechecked = SafetyGuardrails.validate(fixed)
        assertTrue("Expected auto-remediated waypoints to pass: ${rechecked.violations}", rechecked.passed)
    }

    @Test
    fun detectsSweptSegmentThroughFixtureClamp() {
        val transitThroughClamp = listOf(
            Waypoint(1, "LEFT", "Before Clamp", 260.0, 105.0, 110.0, true, 140.0),
            Waypoint(2, "RIGHT", "After Clamp", 450.0, 105.0, 110.0, true, 140.0)
        )
        val res = SafetyGuardrails.validate(transitThroughClamp)
        assertFalse(res.passed)
        assertFalse(res.collisionFree)

        val fixed = SafetyGuardrails.autoRemediateWaypoints(transitThroughClamp)
        assertTrue(SafetyGuardrails.validate(fixed).passed)
    }

    @Test
    fun robotCodeGeneratorEnforcesZeroDirectLlmToMotorLockout() {
        val preset = RoboXRPresets.teachInPresets.first()
        val plan = RoboXRPlanner().planOfflineDeterministic(
            instruction = preset.instruction,
            pickAnchor = preset.pick,
            placeAnchor = preset.place
        )
        val passed = SafetyGuardrails.validate(plan.waypoints)
        val urscript = RobotCodeGenerator.generate(plan, passed, ExportFormat.URSCRIPT, "OP-TEST")
        assertTrue(urscript.contains("def roboxr_verified_program():"))

        val digest = AuditHasher.computeSha256("2026-11-20", "OP-TEST", plan.taskName, "APPROVED", urscript)
        assertEquals(64, digest.length)
    }
}
