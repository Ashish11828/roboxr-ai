package com.example.roboxr.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.roboxr.model.PlannerMode
import com.example.roboxr.model.RoboXRPresets
import com.example.roboxr.model.SpatialAnchor
import com.example.roboxr.model.TeachInPreset
import com.example.roboxr.ui.PlanningStatus
import com.example.roboxr.ui.RoboXRUiState
import com.example.roboxr.ui.components.DigitalTwinCanvasCard
import com.example.roboxr.ui.components.SafetyGuardrailsCard
import java.util.Locale

@Composable
fun TeachInScreen(
    state: RoboXRUiState,
    onSelectPlannerMode: (PlannerMode) -> Unit,
    onApplyPreset: (TeachInPreset) -> Unit,
    onInstructionChange: (String) -> Unit,
    onPickChange: (Double?, Double?, Double?) -> Unit,
    onPlaceChange: (Double?, Double?, Double?) -> Unit,
    onSynthesizePlan: (Boolean) -> Unit,
    onAutoRemediate: () -> Unit,
    onToggleSim: () -> Unit,
    onResetSim: () -> Unit,
    onScrubSim: (Float) -> Unit,
    onApproveExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Compact XREAL Optical HUD Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cnc_cell_hero_1795227716731),
                    contentDescription = stringResource(R.string.cd_xr_hud_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colorScheme.background.copy(alpha = 0.25f),
                                    colorScheme.background.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.primaryContainer.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "XREAL XR • 0-SERVER",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.background.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, colorScheme.outline)
                        ) {
                            Text(
                                text = "CYCLE ${String.format(Locale.US, "%.1fs", state.currentPlan.cycleTimeSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.studio_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "${stringResource(R.string.studio_subtitle)} • ${state.currentPlan.engine}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Quick Cell Scenarios & ISO 10218 Safety Stress Tests (above the fold!)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RoboXRPresets.teachInPresets, key = { it.id }) { preset ->
                    val isSelected = state.selectedPresetId == preset.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onApplyPreset(preset) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("preset_${preset.id}"),
                        leadingIcon = if (preset.expectViolation) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            {
                                Icon(
                                    imageVector = Icons.Filled.PrecisionManufacturing,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (preset.expectViolation) {
                                colorScheme.secondaryContainer
                            } else {
                                colorScheme.primaryContainer
                            },
                            selectedLabelColor = if (preset.expectViolation) {
                                colorScheme.secondary
                            } else {
                                colorScheme.primary
                            }
                        )
                    )
                }
            }
        }

        // 3. Deterministic ISO 10218 Digital Twin Safety Guardrails (< 1 ms) — immediately visible!
        item {
            SafetyGuardrailsCard(
                validation = state.validation,
                onAutoRemediate = onAutoRemediate
            )
        }

        // 4. Live Digital Twin Workcell Visualizer — immediately visible!
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
                topDownHeight = 205.dp,
                showElevationProfile = true
            )
        }

        // 5. Planner Engine Toggle (Firebase AI Logic vs 100% Offline Edge)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlannerMode.entries.forEach { mode ->
                    val selected = state.plannerMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPlannerMode(mode) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("planner_mode_${mode.name.lowercase()}"),
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == PlannerMode.GEMINI_AI_LOGIC) {
                                    Icons.Filled.AutoAwesome
                                } else {
                                    Icons.Filled.Memory
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.primary,
                            selectedLeadingIconColor = colorScheme.primary
                        )
                    )
                }
            }
        }

        // 6. Spatial Anchor Inputs (Pick XYZ / Place XYZ) & Operator Instruction
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SPATIAL ANCHORS (MILLIMETERS)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary
                    )

                    AnchorRowEditor(
                        title = "PICK ANCHOR",
                        anchor = state.pickAnchor,
                        tagPrefix = "pick",
                        onUpdate = onPickChange
                    )

                    AnchorRowEditor(
                        title = "PLACE ANCHOR",
                        anchor = state.placeAnchor,
                        tagPrefix = "place",
                        onUpdate = onPlaceChange
                    )

                    OutlinedTextField(
                        value = state.instruction,
                        onValueChange = onInstructionChange,
                        label = { Text("Operator Teach-In Instruction") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teach_in_instruction_input"),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { onSynthesizePlan(false) },
                        enabled = state.planningStatus !is PlanningStatus.Planning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("synthesize_plan_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        if (state.planningStatus is PlanningStatus.Planning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Synthesizing & Auditing...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Icon(
                                imageVector = if (state.plannerMode == PlannerMode.GEMINI_AI_LOGIC) {
                                    Icons.Filled.AutoAwesome
                                } else {
                                    Icons.Filled.Memory
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_generate_plan),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    AnimatedVisibility(visible = state.planningStatus is PlanningStatus.Error) {
                        val err = state.planningStatus as? PlanningStatus.Error
                        if (err != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = colorScheme.errorContainer.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, colorScheme.error)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = err.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface
                                    )
                                    if (err.canFallbackOffline) {
                                        OutlinedButton(
                                            onClick = { onSynthesizePlan(true) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("fallback_offline_button"),
                                            border = BorderStroke(1.dp, colorScheme.secondary)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.btn_switch_offline),
                                                color = colorScheme.secondary,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. XREAL Glasses HRI Operator Guidance + Human-in-the-Loop Sign-Off
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "XREAL HUD HRI GUIDANCE",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary
                        )
                        Text(
                            text = "PLAN ${state.currentPlan.planLatencyMs} ms • ${state.currentPlan.waypoints.size} PTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    state.currentPlan.hriGuidance.forEachIndexed { idx, step ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "0${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.secondary
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    if (!state.lastCommitMessage.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.lastCommitMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onApproveExport,
                        enabled = state.validation.passed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("approve_export_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.tertiary,
                            contentColor = colorScheme.onTertiary,
                            disabledContainerColor = colorScheme.surfaceVariant,
                            disabledContentColor = colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.validation.passed) {
                                stringResource(R.string.btn_approve_export)
                            } else {
                                "Code Export Locked (Resolve Violations)"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchorRowEditor(
    title: String,
    anchor: SpatialAnchor,
    tagPrefix: String,
    onUpdate: (Double?, Double?, Double?) -> Unit
) {
    var xText by remember(anchor.xMm) { mutableStateOf(formatCoord(anchor.xMm)) }
    var yText by remember(anchor.yMm) { mutableStateOf(formatCoord(anchor.yMm)) }
    var zText by remember(anchor.zMm) { mutableStateOf(formatCoord(anchor.zMm)) }

    LaunchedEffect(anchor) {
        xText = formatCoord(anchor.xMm)
        yText = formatCoord(anchor.yMm)
        zText = formatCoord(anchor.zMm)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoordField(
                label = "X",
                value = xText,
                onValueChange = {
                    xText = it
                    it.toDoubleOrNull()?.let { v -> onUpdate(v, null, null) }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_x_input")
            )
            CoordField(
                label = "Y",
                value = yText,
                onValueChange = {
                    yText = it
                    it.toDoubleOrNull()?.let { v -> onUpdate(null, v, null) }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_y_input")
            )
            CoordField(
                label = "Z",
                value = zText,
                onValueChange = {
                    zText = it
                    it.toDoubleOrNull()?.let { v -> onUpdate(null, null, v) }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_z_input")
            )
        }
    }
}

@Composable
private fun CoordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}

private fun formatCoord(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
