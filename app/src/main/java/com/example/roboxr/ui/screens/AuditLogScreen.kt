package com.example.roboxr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.roboxr.model.AuditRecord
import com.example.roboxr.model.ExportFormat
import com.example.roboxr.ui.RoboXRUiState

@Composable
fun AuditLogScreen(
    state: RoboXRUiState,
    records: List<AuditRecord>,
    onOperatorIdChange: (String) -> Unit,
    onVerifyChain: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var expandedRecordId by remember { mutableStateOf<Long?>(null) }

    val approvedCount = records.count { it.status == "APPROVED" || it.status == "JAM_RECOVERED" }
    val blockedCount = records.count { it.status == "BLOCKED" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Operator Identity & Local Standalone Ledger Controls
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SHA-256 TAMPER-EVIDENT AUDIT TRAIL",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "100% On-Device Room SQLite • Zero External Server",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    OutlinedTextField(
                        value = state.operatorId,
                        onValueChange = onOperatorIdChange,
                        label = { Text("Active Operator Sign-Off ID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("operator_id_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LedgerStatTile(
                            label = "SIGNED",
                            value = approvedCount.toString(),
                            accent = colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        LedgerStatTile(
                            label = "BLOCKED",
                            value = blockedCount.toString(),
                            accent = colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        LedgerStatTile(
                            label = "TOTAL",
                            value = records.size.toString(),
                            accent = colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onVerifyChain,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("verify_chain_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_verify_hashes),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (records.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearHistory,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("clear_audit_button"),
                                border = BorderStroke(1.dp, colorScheme.outline)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear local audit records",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!state.integrityStatusMessage.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = state.integrityStatusMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Empty State or Tamper-Evident Audit Records
        if (records.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Audit Records Committed Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Approve a verified CNC Teach-In or 30s Jam Recovery plan to write a SHA-256 signed record to local storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(records, key = { it.id }) { rec ->
                val isBlocked = rec.status == "BLOCKED"
                val accent = if (isBlocked) colorScheme.error else colorScheme.tertiary
                val expanded = expandedRecordId == rec.id

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedRecordId = if (expanded) null else rec.id }
                        .testTag("audit_record_${rec.id}"),
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rec.taskName,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = rec.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "${rec.timestamp}  •  ${rec.operatorId}  •  ${rec.waypointCount} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SHA-256: ${rec.sha256.take(24)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary
                            )
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(rec.sha256)) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy SHA-256 digest",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (expanded && rec.programCode.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = colorScheme.background,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = rec.programCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerStatTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExportProgramDialog(
    state: RoboXRUiState,
    onSelectFormat: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var copied by remember(state.exportedCode) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surface,
            border = BorderStroke(1.2.dp, colorScheme.tertiary)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VERIFIED ROBOT CODE EXPORT",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.tertiary
                        )
                        Text(
                            text = "Signed by ${state.operatorId} • Logged to local SHA-256 ledger",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_export_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_dialog),
                            tint = colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = state.exportFormat == fmt,
                            onClick = { onSelectFormat(fmt) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("export_format_${fmt.name.lowercase()}"),
                            label = {
                                Text(
                                    text = fmt.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.background,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                ) {
                    Text(
                        text = state.exportedCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }

                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.exportedCode))
                        copied = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("copy_robot_code_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_code),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (copied) "Copied ${state.exportFormat.extension} Program" else "Copy ${state.exportFormat.extension} to Clipboard",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
