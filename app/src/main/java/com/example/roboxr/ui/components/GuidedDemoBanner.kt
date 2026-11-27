package com.example.roboxr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun GuidedDemoBanner(
    stepIndex: Int,
    totalSteps: Int,
    title: String,
    talkingPoint: String,
    autoPlay: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val progress = ((stepIndex + 1).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("guided_demo_banner"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.primaryContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.2.dp, colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PresentToAll,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colorScheme.background
                    ) {
                        Text(
                            text = "DEMO ${stepIndex + 1}/$totalSteps",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPrev,
                        enabled = stepIndex > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("demo_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous demo step",
                            tint = if (stepIndex > 0) colorScheme.onSurface else colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleAutoPlay,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("demo_autoplay_button")
                    ) {
                        Icon(
                            imageVector = if (autoPlay) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (autoPlay) "Pause auto advance" else "Resume auto advance",
                            tint = colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("demo_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next demo step",
                            tint = colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("demo_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit interview demo",
                            tint = colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = talkingPoint,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = colorScheme.primary,
                trackColor = colorScheme.background
            )
        }
    }
}
