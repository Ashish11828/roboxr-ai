package com.example.roboxr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.roboxr.data.AuditDatabase
import com.example.roboxr.data.AuditRepository
import com.example.roboxr.model.AuditRecord
import com.example.roboxr.model.ExportFormat
import com.example.roboxr.model.JamScenario
import com.example.roboxr.model.PlannerMode
import com.example.roboxr.model.RoboXRPresets
import com.example.roboxr.model.SpatialAnchor
import com.example.roboxr.model.TaskPlan
import com.example.roboxr.model.TeachInPreset
import com.example.roboxr.model.ValidationResult
import com.example.roboxr.model.Waypoint
import com.example.roboxr.planner.PlannerOutcome
import com.example.roboxr.planner.RoboXRPlanner
import com.example.roboxr.safety.AuditHasher
import com.example.roboxr.safety.RobotCodeGenerator
import com.example.roboxr.safety.SafetyGuardrails
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StudioTab {
    TEACH_IN,
    JAM_RECOVERY,
    DIGITAL_TWIN,
    AUDIT_LOG
}

sealed interface PlanningStatus {
    data object Idle : PlanningStatus
    data object Planning : PlanningStatus
    data class Error(val message: String, val canFallbackOffline: Boolean = true) : PlanningStatus
}

data class RoboXRUiState(
    val activeTab: StudioTab = StudioTab.TEACH_IN,
    val operatorId: String = "OP-ALCLAN-01",
    val plannerMode: PlannerMode = PlannerMode.GEMINI_AI_LOGIC,
    val exportFormat: ExportFormat = ExportFormat.URSCRIPT,
    val selectedPresetId: String = RoboXRPresets.teachInPresets.first().id,
    val instruction: String = RoboXRPresets.teachInPresets.first().instruction,
    val pickAnchor: SpatialAnchor = RoboXRPresets.teachInPresets.first().pick,
    val placeAnchor: SpatialAnchor = RoboXRPresets.teachInPresets.first().place,
    val currentPlan: TaskPlan,
    val validation: ValidationResult,
    val planningStatus: PlanningStatus = PlanningStatus.Idle,
    val simProgress: Float = 0f,
    val isSimPlaying: Boolean = false,
    val selectedWaypointIndex: Int = 0,
    val selectedJam: JamScenario = RoboXRPresets.jamScenarios.first(),
    val customJamNote: String = "",
    val jamTimerRunning: Boolean = false,
    val jamElapsedSec: Double = 0.0,
    val jamCompletedSec: Double? = null,
    val checkedHriSteps: Set<Int> = emptySet(),
    val showExportDialog: Boolean = false,
    val exportedCode: String = "",
    val lastCommitMessage: String? = null,
    val integrityStatusMessage: String? = null,
    val demoActive: Boolean = false,
    val demoStepIndex: Int = 0,
    val demoAutoPlay: Boolean = true,
    val demoOpenXrealConsole: Boolean = false,
    val demoTitle: String = "",
    val demoTalkingPoint: String = ""
)

class RoboXRViewModel(application: Application) : AndroidViewModel(application) {

    private val planner = RoboXRPlanner()
    private val auditRepository = AuditRepository(
        AuditDatabase.getInstance(application).auditRecordDao()
    )

    val auditRecords: StateFlow<List<AuditRecord>> = auditRepository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val initialPreset = RoboXRPresets.teachInPresets.first()
    private val initialPlan = planner.planOfflineDeterministic(
        instruction = initialPreset.instruction,
        pickAnchor = initialPreset.pick,
        placeAnchor = initialPreset.place,
        isJamRecovery = false
    )
    private val initialValidation = SafetyGuardrails.validate(initialPlan.waypoints)

    private val _uiState = MutableStateFlow(
        RoboXRUiState(
            currentPlan = initialPlan,
            validation = initialValidation
        )
    )
    val uiState: StateFlow<RoboXRUiState> = _uiState.asStateFlow()

    private var simJob: Job? = null
    private var jamTimerJob: Job? = null
    private var demoJob: Job? = null

    companion object {
        const val DEMO_TOTAL_STEPS = 6
    }

    fun selectTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab, lastCommitMessage = null) }
    }

    fun updateOperatorId(id: String) {
        _uiState.update { it.copy(operatorId = id.take(24)) }
    }

    fun selectPlannerMode(mode: PlannerMode) {
        _uiState.update { it.copy(plannerMode = mode, planningStatus = PlanningStatus.Idle) }
    }

    fun selectExportFormat(format: ExportFormat) {
        _uiState.update { state ->
            val updatedCode = if (state.showExportDialog && state.validation.passed) {
                RobotCodeGenerator.generate(
                    plan = state.currentPlan,
                    validation = state.validation,
                    format = format,
                    operatorId = state.operatorId.ifBlank { "OP-ALCLAN-01" }
                )
            } else {
                state.exportedCode
            }
            state.copy(exportFormat = format, exportedCode = updatedCode)
        }
    }

    fun updateInstruction(text: String) {
        _uiState.update { it.copy(instruction = text, selectedPresetId = "custom") }
    }

    fun updatePickAnchor(x: Double? = null, y: Double? = null, z: Double? = null) {
        _uiState.update { state ->
            val current = state.pickAnchor
            state.copy(
                pickAnchor = SpatialAnchor(
                    xMm = x ?: current.xMm,
                    yMm = y ?: current.yMm,
                    zMm = z ?: current.zMm
                ),
                selectedPresetId = "custom"
            )
        }
    }

    fun updatePlaceAnchor(x: Double? = null, y: Double? = null, z: Double? = null) {
        _uiState.update { state ->
            val current = state.placeAnchor
            state.copy(
                placeAnchor = SpatialAnchor(
                    xMm = x ?: current.xMm,
                    yMm = y ?: current.yMm,
                    zMm = z ?: current.zMm
                ),
                selectedPresetId = "custom"
            )
        }
    }

    fun applyTeachInPreset(preset: TeachInPreset) {
        stopSimulation()
        val offlinePreview = planner.planOfflineDeterministic(
            instruction = preset.instruction,
            pickAnchor = preset.pick,
            placeAnchor = preset.place,
            isJamRecovery = false
        )
        val validation = SafetyGuardrails.validate(offlinePreview.waypoints)
        _uiState.update {
            it.copy(
                selectedPresetId = preset.id,
                instruction = preset.instruction,
                pickAnchor = preset.pick,
                placeAnchor = preset.place,
                currentPlan = offlinePreview,
                validation = validation,
                planningStatus = PlanningStatus.Idle,
                simProgress = 0f,
                selectedWaypointIndex = 0,
                checkedHriSteps = emptySet(),
                lastCommitMessage = null
            )
        }
    }

    fun synthesizeTeachInPlan(forceOffline: Boolean = false) {
        val state = _uiState.value
        val mode = if (forceOffline) PlannerMode.OFFLINE_EDGE else state.plannerMode
        stopSimulation()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    plannerMode = mode,
                    planningStatus = PlanningStatus.Planning,
                    lastCommitMessage = null
                )
            }
            when (
                val outcome = planner.planTask(
                    instruction = state.instruction.ifBlank { "Standard CNC billet pick and place" },
                    pickAnchor = state.pickAnchor,
                    placeAnchor = state.placeAnchor,
                    mode = mode,
                    isJamRecovery = false
                )
            ) {
                is PlannerOutcome.Success -> {
                    val validation = SafetyGuardrails.validate(outcome.plan.waypoints)
                    _uiState.update {
                        it.copy(
                            currentPlan = outcome.plan,
                            validation = validation,
                            planningStatus = PlanningStatus.Idle,
                            simProgress = 0f,
                            selectedWaypointIndex = 0,
                            checkedHriSteps = emptySet()
                        )
                    }
                    if (!validation.passed) {
                        logBlockedAudit(outcome.plan, validation)
                    }
                }

                is PlannerOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            planningStatus = PlanningStatus.Error(
                                message = outcome.message,
                                canFallbackOffline = outcome.canFallbackOffline
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectJamScenario(scenario: JamScenario) {
        _uiState.update {
            it.copy(
                selectedJam = scenario,
                checkedHriSteps = emptySet(),
                jamCompletedSec = null
            )
        }
    }

    fun updateCustomJamNote(note: String) {
        _uiState.update { it.copy(customJamNote = note) }
    }

    fun triggerJamRecoveryPlan(forceOffline: Boolean = false) {
        val state = _uiState.value
        val scenario = state.selectedJam
        val mode = if (forceOffline) PlannerMode.OFFLINE_EDGE else state.plannerMode
        val combinedInstruction = if (state.customJamNote.isBlank()) {
            "${scenario.code} ${scenario.title}: ${scenario.instruction}"
        } else {
            "${scenario.code} ${scenario.title}: ${scenario.instruction} Operator note: ${state.customJamNote}"
        }

        startJamTimer()
        stopSimulation()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    plannerMode = mode,
                    planningStatus = PlanningStatus.Planning,
                    pickAnchor = scenario.jamAnchor,
                    placeAnchor = scenario.safeDropAnchor,
                    checkedHriSteps = emptySet(),
                    jamCompletedSec = null,
                    lastCommitMessage = null
                )
            }
            when (
                val outcome = planner.planTask(
                    instruction = combinedInstruction,
                    pickAnchor = scenario.jamAnchor,
                    placeAnchor = scenario.safeDropAnchor,
                    mode = mode,
                    isJamRecovery = true
                )
            ) {
                is PlannerOutcome.Success -> {
                    val validation = SafetyGuardrails.validate(outcome.plan.waypoints)
                    _uiState.update {
                        it.copy(
                            currentPlan = outcome.plan,
                            validation = validation,
                            planningStatus = PlanningStatus.Idle,
                            simProgress = 0f,
                            selectedWaypointIndex = 0
                        )
                    }
                    if (!validation.passed) {
                        logBlockedAudit(outcome.plan, validation)
                    }
                }

                is PlannerOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            planningStatus = PlanningStatus.Error(
                                message = outcome.message,
                                canFallbackOffline = outcome.canFallbackOffline
                            )
                        )
                    }
                }
            }
        }
    }

    fun toggleHriChecklistStep(index: Int) {
        _uiState.update { state ->
            val updated = if (index in state.checkedHriSteps) {
                state.checkedHriSteps - index
            } else {
                state.checkedHriSteps + index
            }
            state.copy(checkedHriSteps = updated)
        }
    }

    fun autoRemediatePlan() {
        val state = _uiState.value
        val safeWaypoints = SafetyGuardrails.autoRemediateWaypoints(state.currentPlan.waypoints)
        val validation = SafetyGuardrails.validate(safeWaypoints)
        val cycleSec = planner.computeKinematicCycleTimeSec(safeWaypoints)
        val updatedPlan = state.currentPlan.copy(
            waypoints = safeWaypoints,
            cycleTimeSec = cycleSec,
            engine = "${state.currentPlan.engine} + ISO-10218 Solver"
        )
        _uiState.update {
            it.copy(
                currentPlan = updatedPlan,
                validation = validation,
                selectedWaypointIndex = 0,
                simProgress = 0f,
                lastCommitMessage = if (validation.passed) {
                    "Auto-remediated in ${String.format(Locale.US, "%.2f", validation.latencyMs)} ms — all ISO 10218 gates PASS."
                } else {
                    "Remediation applied; review remaining guardrail warnings."
                }
            )
        }
    }

    fun selectWaypoint(index: Int) {
        val count = _uiState.value.currentPlan.waypoints.size
        if (index in 0 until count) {
            val progress = if (count > 1) index.toFloat() / (count - 1).toFloat() else 0f
            _uiState.update { it.copy(selectedWaypointIndex = index, simProgress = progress) }
        }
    }

    fun updateWaypoint(index: Int, transform: (Waypoint) -> Waypoint) {
        val state = _uiState.value
        val list = state.currentPlan.waypoints.toMutableList()
        if (index !in list.indices) return
        list[index] = transform(list[index])
        val validation = SafetyGuardrails.validate(list)
        val cycleSec = planner.computeKinematicCycleTimeSec(list)
        _uiState.update {
            it.copy(
                currentPlan = state.currentPlan.copy(
                    waypoints = list,
                    cycleTimeSec = cycleSec
                ),
                validation = validation
            )
        }
    }

    fun toggleSimulation() {
        if (_uiState.value.isSimPlaying) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    fun scrubSimulation(progress: Float) {
        stopSimulation()
        val clamped = progress.coerceIn(0f, 1f)
        val count = _uiState.value.currentPlan.waypoints.size
        val nearestIdx = if (count > 1) {
            (clamped * (count - 1)).toInt().coerceIn(0, count - 1)
        } else 0
        _uiState.update { it.copy(simProgress = clamped, selectedWaypointIndex = nearestIdx) }
    }

    fun resetSimulation() {
        stopSimulation()
        _uiState.update { it.copy(simProgress = 0f, selectedWaypointIndex = 0) }
    }

    private fun startSimulation() {
        simJob?.cancel()
        _uiState.update { it.copy(isSimPlaying = true) }
        simJob = viewModelScope.launch {
            while (true) {
                delay(32L)
                val current = _uiState.value
                val next = current.simProgress + 0.008f
                val wrapped = if (next >= 1f) 0f else next
                val count = current.currentPlan.waypoints.size
                val idx = if (count > 1) (wrapped * (count - 1)).toInt().coerceIn(0, count - 1) else 0
                _uiState.update { it.copy(simProgress = wrapped, selectedWaypointIndex = idx) }
            }
        }
    }

    private fun stopSimulation() {
        simJob?.cancel()
        simJob = null
        _uiState.update { it.copy(isSimPlaying = false) }
    }

    private fun startJamTimer() {
        jamTimerJob?.cancel()
        _uiState.update { it.copy(jamTimerRunning = true, jamElapsedSec = 0.0, jamCompletedSec = null) }
        jamTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                delay(100L)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                _uiState.update { it.copy(jamElapsedSec = elapsed) }
            }
        }
    }

    private fun stopJamTimer(markCompleted: Boolean) {
        jamTimerJob?.cancel()
        jamTimerJob = null
        _uiState.update { state ->
            state.copy(
                jamTimerRunning = false,
                jamCompletedSec = if (markCompleted) state.jamElapsedSec else state.jamCompletedSec
            )
        }
    }

    /**
     * Human-in-the-Loop Operator Sign-Off Gate.
     * Refuses code generation if ISO 10218 guardrails failed.
     */
    fun approveAndGenerateCode(
        isJamSignOff: Boolean = false,
        openExportDialog: Boolean = true
    ) {
        val state = _uiState.value
        if (!state.validation.passed) {
            viewModelScope.launch {
                logBlockedAudit(state.currentPlan, state.validation)
            }
            _uiState.update {
                it.copy(
                    lastCommitMessage = "LOCKOUT: Resolve ISO 10218 violations before sign-off."
                )
            }
            return
        }

        if (isJamSignOff && state.jamTimerRunning) {
            stopJamTimer(markCompleted = true)
        }

        val operator = state.operatorId.ifBlank { "OP-ALCLAN-01" }
        val code = RobotCodeGenerator.generate(
            plan = state.currentPlan,
            validation = state.validation,
            format = state.exportFormat,
            operatorId = operator
        )
        val timestamp = isoTimestampNow()
        val status = if (isJamSignOff) "JAM_RECOVERED" else "APPROVED"
        val hash = AuditHasher.computeSha256(
            timestamp = timestamp,
            operatorId = operator,
            taskName = state.currentPlan.taskName,
            status = status,
            programCode = code
        )

        val record = AuditRecord(
            timestamp = timestamp,
            operatorId = operator,
            taskName = state.currentPlan.taskName,
            status = status,
            sha256 = hash,
            engine = state.currentPlan.engine,
            waypointCount = state.currentPlan.waypoints.size,
            programCode = code
        )

        viewModelScope.launch {
            auditRepository.recordAudit(record)
        }

        _uiState.update {
            it.copy(
                showExportDialog = openExportDialog,
                exportedCode = code,
                lastCommitMessage = "Signed by $operator • SHA-256 ${hash.take(12)}… committed locally."
            )
        }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false) }
    }

    fun verifyAuditChainIntegrity() {
        val records = auditRecords.value
        if (records.isEmpty()) {
            _uiState.update {
                it.copy(integrityStatusMessage = "Ledger empty — sign off a verified plan to record SHA-256 digests.")
            }
            return
        }
        var validCount = 0
        for (rec in records) {
            val recomputed = AuditHasher.computeSha256(
                timestamp = rec.timestamp,
                operatorId = rec.operatorId,
                taskName = rec.taskName,
                status = rec.status,
                programCode = rec.programCode
            )
            if (recomputed == rec.sha256) validCount++
        }
        val msg = if (validCount == records.size) {
            "SHA-256 INTEGRITY VERIFIED: $validCount/${records.size} local records untampered."
        } else {
            "ALERT: ${records.size - validCount} record(s) failed SHA-256 verification."
        }
        _uiState.update { it.copy(integrityStatusMessage = msg) }
    }

    fun clearAuditHistory() {
        viewModelScope.launch {
            auditRepository.clearAll()
            _uiState.update { it.copy(integrityStatusMessage = "Local audit ledger cleared.") }
        }
    }

    private suspend fun logBlockedAudit(plan: TaskPlan, validation: ValidationResult) {
        val operator = _uiState.value.operatorId.ifBlank { "OP-ALCLAN-01" }
        val timestamp = isoTimestampNow()
        val summary = "BLOCKED_BY_ISO_10218_GUARDRAILS:\n" + validation.violations.joinToString("\n")
        val hash = AuditHasher.computeSha256(
            timestamp = timestamp,
            operatorId = operator,
            taskName = plan.taskName,
            status = "BLOCKED",
            programCode = summary
        )
        auditRepository.recordAudit(
            AuditRecord(
                timestamp = timestamp,
                operatorId = operator,
                taskName = plan.taskName,
                status = "BLOCKED",
                sha256 = hash,
                engine = plan.engine,
                waypointCount = plan.waypoints.size,
                programCode = summary
            )
        )
    }

    private fun isoTimestampNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    fun startGuidedDemo() {
        if (_uiState.value.demoActive) {
            stopGuidedDemo()
            return
        }
        _uiState.update { it.copy(demoActive = true, demoAutoPlay = true) }
        executeDemoStep(0)
        scheduleDemoAutoAdvance()
    }

    fun nextDemoStep() {
        val next = (_uiState.value.demoStepIndex + 1) % DEMO_TOTAL_STEPS
        executeDemoStep(next)
        if (_uiState.value.demoAutoPlay) scheduleDemoAutoAdvance()
    }

    fun prevDemoStep() {
        val prev = (_uiState.value.demoStepIndex - 1).coerceAtLeast(0)
        executeDemoStep(prev)
        if (_uiState.value.demoAutoPlay) scheduleDemoAutoAdvance()
    }

    fun toggleDemoAutoPlay() {
        val enable = !_uiState.value.demoAutoPlay
        _uiState.update { it.copy(demoAutoPlay = enable) }
        if (enable) {
            scheduleDemoAutoAdvance()
        } else {
            demoJob?.cancel()
            demoJob = null
        }
    }

    fun stopGuidedDemo() {
        demoJob?.cancel()
        demoJob = null
        stopSimulation()
        _uiState.update {
            it.copy(
                demoActive = false,
                demoOpenXrealConsole = false,
                demoTitle = "",
                demoTalkingPoint = ""
            )
        }
    }

    fun clearDemoConsoleTrigger() {
        _uiState.update { it.copy(demoOpenXrealConsole = false) }
    }

    private fun scheduleDemoAutoAdvance() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            while (_uiState.value.demoActive && _uiState.value.demoAutoPlay) {
                delay(6_000L)
                if (!_uiState.value.demoActive || !_uiState.value.demoAutoPlay) break
                val next = _uiState.value.demoStepIndex + 1
                if (next < DEMO_TOTAL_STEPS) {
                    executeDemoStep(next)
                } else {
                    _uiState.update { it.copy(demoAutoPlay = false) }
                    break
                }
            }
        }
    }

    private fun executeDemoStep(step: Int) {
        when (step) {
            0 -> {
                val nominal = RoboXRPresets.teachInPresets.first()
                applyTeachInPreset(nominal)
                _uiState.update {
                    it.copy(
                        activeTab = StudioTab.TEACH_IN,
                        showExportDialog = false,
                        demoStepIndex = 0,
                        demoOpenXrealConsole = true,
                        demoTitle = "XREAL Optical Lens & XR SDK",
                        demoTalkingPoint = "Pure #000000 additive HUD projects onto XREAL optics via USB-C DP Alt Mode & Jetpack XR Subspace with live 3DoF reticle aiming."
                    )
                }
            }

            1 -> {
                val clampTest = RoboXRPresets.teachInPresets[1] // Clamp Keep-Out Test
                applyTeachInPreset(clampTest)
                _uiState.update {
                    it.copy(
                        activeTab = StudioTab.TEACH_IN,
                        showExportDialog = false,
                        demoStepIndex = 1,
                        demoOpenXrealConsole = false,
                        demoTitle = "ISO 10218 Safety Lockout (<1ms)",
                        demoTalkingPoint = "Deterministic Digital Twin detects CNC fixture clamp collision in <1 ms, turns path crimson, and hard-locks code export."
                    )
                }
            }

            2 -> {
                autoRemediatePlan()
                startSimulation()
                _uiState.update {
                    it.copy(
                        activeTab = StudioTab.TEACH_IN,
                        showExportDialog = false,
                        demoStepIndex = 2,
                        demoOpenXrealConsole = false,
                        demoTitle = "Auto-Remediation to PASS",
                        demoTalkingPoint = "One-tap kinematic solver elevates transit above Z=160mm clamp ceiling, caps TCP speed ≤250 mm/s, and passes all 4 ISO gates."
                    )
                }
            }

            3 -> {
                val jam = RoboXRPresets.jamScenarios.first()
                selectJamScenario(jam)
                triggerJamRecoveryPlan(forceOffline = true)
                _uiState.update {
                    it.copy(
                        activeTab = StudioTab.JAM_RECOVERY,
                        showExportDialog = false,
                        demoStepIndex = 3,
                        demoOpenXrealConsole = false,
                        checkedHriSteps = setOf(0, 1),
                        demoTitle = "30-Second CNC Jam Recovery",
                        demoTalkingPoint = "Live 30s SLA timer guides operator through ALM-412 chuck jaw misalignment extraction with verified collaborative speed limits."
                    )
                }
            }

            4 -> {
                startSimulation()
                _uiState.update {
                    it.copy(
                        activeTab = StudioTab.DIGITAL_TWIN,
                        showExportDialog = false,
                        demoStepIndex = 4,
                        demoOpenXrealConsole = false,
                        demoTitle = "Digital Twin Live CAD",
                        demoTalkingPoint = "Dual XY workcell and XZ elevation plots stream real-time X/Y/Z/R/V telemetry; tapping any waypoint allows live safety tuning."
                    )
                }
            }

            5 -> {
                stopSimulation()
                if (!_uiState.value.validation.passed) {
                    autoRemediatePlan()
                }
                approveAndGenerateCode(isJamSignOff = true, openExportDialog = false)
                _uiState.update {
                    it.copy(
                        activeTab = StudioTab.AUDIT_LOG,
                        showExportDialog = false,
                        demoStepIndex = 5,
                        demoOpenXrealConsole = false,
                        demoTitle = "SHA-256 Tamper-Evident Audit",
                        demoTalkingPoint = "Human sign-off generates verified URScript/KRL/G-Code and commits a SHA-256 digest to local Room SQLite with zero backend server."
                    )
                }
                viewModelScope.launch {
                    delay(300L)
                    verifyAuditChainIntegrity()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
        jamTimerJob?.cancel()
        demoJob?.cancel()
    }
}
