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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.roboxr.model.JamScenario
import com.example.roboxr.model.RoboXRPresets
import com.example.roboxr.ui.PlanningStatus
import com.example.roboxr.ui.RoboXRUiState
import com.example.roboxr.ui.components.DigitalTwinCanvasCard
import com.example.roboxr.ui.components.SafetyGuardrailsCard
import java.util.Locale

@Composable
fun JamRecoveryScreen(
    state: RoboXRUiState,
    onSelectJam: (JamScenario) -> Unit,
    onCustomNoteChange: (String) -> Unit,
    onTriggerRecovery: (Boolean) -> Unit,
    onToggleChecklistStep: (Int) -> Unit,
    onAutoRemediate: () -> Unit,
    onToggleSim: () -> Unit,
    onResetSim: () -> Unit,
    onScrubSim: (Float) -> Unit,
    onCommitJamSignOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val slaTargetSec = 30.0
    val slaProgress = (state.jamElapsedSec / slaTargetSec).toFloat().coerceIn(0f, 1f)
    val withinSla = (state.jamCompletedSec ?: state.jamElapsedSec) <= slaTargetSec

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 30-Second Jam Recovery SLA HUD Banner
        item {
            val accent = when {
                state.jamCompletedSec != null && withinSla -> colorScheme.tertiary
                state.jamTimerRunning && withinSla -> colorScheme.secondary
                !withinSla -> colorScheme.error
                else -> colorScheme.primary
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surface,
                border = BorderStroke(1.2.dp, accent.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "30-SECOND CNC JAM RECOVERY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Rapid shop-floor anomaly recovery with ISO 10218 lockout gate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.background,
                            border = BorderStroke(1.dp, accent)
                        ) {
                            val displaySec = state.jamCompletedSec ?: state.jamElapsedSec
                            Text(
                                text = String.format(Locale.US, "%04.1fs / 30s", displaySec),
                                style = MaterialTheme.typography.titleMedium,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { slaProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = accent,
                        trackColor = colorScheme.outlineVariant
                    )

                    if (state.jamCompletedSec != null) {
                        Text(
                            text = if (withinSla) {
                                "SLA MET: CNC jam cleared & SHA-256 signed in ${String.format(Locale.US, "%.1f", state.jamCompletedSec)} seconds."
                            } else {
                                "Recovery completed in ${String.format(Locale.US, "%.1f", state.jamCompletedSec)} seconds."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                }
            }
        }

        // 2. Brownfield CNC Alarm Selector Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SELECT ACTIVE CNC CELL ANOMALY",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.secondary
                )
                RoboXRPresets.jamScenarios.forEach { scenario ->
                    val selected = state.selectedJam.id == scenario.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectJam(scenario) }
                            .testTag("jam_scenario_${scenario.id}"),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) colorScheme.secondaryContainer.copy(alpha = 0.55f) else colorScheme.surface,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) colorScheme.secondary else colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = if (selected) colorScheme.secondary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = scenario.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = scenario.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = scenario.symptom,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Optional Operator Shop-Floor Observation + Trigger Recovery Button
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.customJamNote,
                        onValueChange = onCustomNoteChange,
                        label = { Text("Optional XREAL Visual Observation (e.g. coolant mist, burrs)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("jam_custom_note_input")
                    )

                    Button(
                        onClick = { onTriggerRecovery(false) },
                        enabled = state.planningStatus !is PlanningStatus.Planning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("run_jam_recovery_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondary,
                            contentColor = colorScheme.onSecondary
                        )
                    ) {
                        if (state.planningStatus is PlanningStatus.Planning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Computing Safe Extraction...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_start_jam_recovery),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (state.planningStatus is PlanningStatus.Error) {
                        OutlinedButton(
                            onClick = { onTriggerRecovery(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("jam_offline_fallback_button"),
                            border = BorderStroke(1.dp, colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Build,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Instant Offline Edge Extraction")
                        }
                    }
                }
            }
        }

        // 4. Deterministic Safety Gate
        item {
            SafetyGuardrailsCard(
                validation = state.validation,
                onAutoRemediate = onAutoRemediate
            )
        }

        // 5. Interactive HRI Operator Checklist (XREAL Glasses HUD Steps)
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "INTERACTIVE HRI RECOVERY CHECKLIST",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary
                    )
                    state.currentPlan.hriGuidance.forEachIndexed { idx, step ->
                        val checked = idx in state.checkedHriSteps
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklistStep(idx) }
                                .padding(vertical = 6.dp)
                                .testTag("hri_step_check_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                contentDescription = if (checked) "Completed step" else "Unchecked step",
                                tint = if (checked) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (checked) colorScheme.tertiary else colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onCommitJamSignOff,
                        enabled = state.validation.passed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("commit_jam_signoff_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.tertiary,
                            contentColor = colorScheme.onTertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Approve Recovery & Log SHA-256",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 6. Digital Twin Extraction Preview
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
                topDownHeight = 200.dp,
                showElevationProfile = true
            )
        }
    }
}
