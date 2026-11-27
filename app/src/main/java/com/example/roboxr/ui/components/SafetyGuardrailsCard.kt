package com.example.roboxr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.roboxr.model.ValidationResult
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafetyGuardrailsCard(
    validation: ValidationResult,
    onAutoRemediate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (validation.passed) colorScheme.tertiary else colorScheme.error
    val containerBg = if (validation.passed) colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    else colorScheme.errorContainer.copy(alpha = 0.55f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerBg,
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.75f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (validation.passed) Icons.Filled.VerifiedUser else Icons.Filled.GppBad,
                        contentDescription = if (validation.passed) "ISO 10218 safety passed" else "ISO 10218 safety lockout",
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (validation.passed) {
                            stringResource(R.string.safety_badge_pass)
                        } else {
                            stringResource(R.string.safety_badge_blocked)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colorScheme.background.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Text(
                        text = String.format(Locale.US, "GATE %.3f ms", validation.latencyMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Deterministic ISO 10218 / Kinematic Gate Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GateStatusChip(label = "Z ≥ 35mm", passed = validation.zFloorClear)
                GateStatusChip(label = "R ≤ 820mm", passed = validation.reachabilityOk)
                GateStatusChip(label = "CLAMP CLEAR", passed = validation.collisionFree)
                GateStatusChip(label = "V ≤ 250mm/s", passed = validation.velocitySafe)
            }

            if (validation.violations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    validation.violations.take(4).forEach { msg ->
                        Text(
                            text = "• $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface
                        )
                    }
                    if (validation.violations.size > 4) {
                        Text(
                            text = "+ ${validation.violations.size - 4} additional violation(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onAutoRemediate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_remediate_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.secondary,
                        contentColor = colorScheme.onSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_auto_remediate),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.zero_direct_control_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GateStatusChip(
    label: String,
    passed: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val tint = if (passed) colorScheme.tertiary else colorScheme.error
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colorScheme.background.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.GppBad,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}
