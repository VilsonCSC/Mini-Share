package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import com.example.ui.components.AppIcons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.model.TransferDirection
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.ui.components.AppTopBar
import com.example.ui.components.KeepScreenOnEffect
import com.example.ui.components.getFileIcon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TransferScreen(
    files: List<FileItem>,
    stats: TransferStats,
    direction: TransferDirection,
    isActive: Boolean,
    isComplete: Boolean,
    onCancelFile: (String) -> Unit,
    onCancelAll: () -> Unit,
    onDone: () -> Unit
) {
    // Keep screen on during file transfer as requested
    KeepScreenOnEffect()

    val primaryAccent = if (direction == TransferDirection.SEND) NeonCyan else ElectricViolet
    val animatedProgress by animateFloatAsState(
        targetValue = stats.overallProgress,
        label = "OverallProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("transfer_screen")
    ) {
        AppTopBar(
            title = if (isComplete) "COMPLETE" else if (direction == TransferDirection.SEND) "SENDING" else "RECEIVING",
            onBackClick = if (isComplete) onDone else onCancelAll
        )

        // Top Progress & Stats Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row: Direction pill and Progress percentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryAccent.copy(alpha = 0.15f))
                            .border(1.dp, primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isComplete) "FINISHED" else if (direction == TransferDirection.SEND) "SENDING" else "RECEIVING",
                            color = primaryAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "${(stats.overallProgress * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("overall_progress_bar"),
                    color = primaryAccent,
                    trackColor = SurfaceCardBorder,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Stats row: Time (left time), Size (file size), Speed (transfer speed) below progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time (left time)
                    StatColumn(
                        label = "Time",
                        value = if (isComplete) "00:00" else stats.formattedRemainingTime,
                        modifier = Modifier.weight(1f)
                    )

                    // Size (file size)
                    StatColumn(
                        label = "Size",
                        value = stats.formattedSizeProgress,
                        modifier = Modifier.weight(1.3f)
                    )

                    // Speed (transfer speed)
                    StatColumn(
                        label = "Speed",
                        value = if (isComplete) "0 B/s" else stats.formattedSpeed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FILES (${files.size})",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // List of all transferring files below progress bar
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                TransferFileItem(
                    file = file,
                    primaryColor = primaryAccent,
                    onCancelClick = { onCancelFile(file.id) }
                )
            }
        }

        // Bottom control button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            if (isComplete) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("done_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "DONE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Button(
                    onClick = onCancelAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("cancel_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceCard,
                        contentColor = RoseRed
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseRed.copy(alpha = 0.5f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = null,
                            tint = RoseRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CANCEL ALL",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TransferFileItem(
    file: FileItem,
    primaryColor: Color,
    onCancelClick: () -> Unit
) {
    val fileIcon = getFileIcon(file.mimeType, file.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("file_item_${file.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when (file.status) {
                        TransferStatus.COMPLETED -> EmeraldGreen.copy(alpha = 0.15f)
                        TransferStatus.CANCELLED -> RoseRed.copy(alpha = 0.15f)
                        TransferStatus.FAILED -> RoseRed.copy(alpha = 0.15f)
                        else -> primaryColor.copy(alpha = 0.15f)
                    }
                )
                .border(
                    1.dp,
                    when (file.status) {
                        TransferStatus.COMPLETED -> EmeraldGreen.copy(alpha = 0.4f)
                        TransferStatus.CANCELLED -> RoseRed.copy(alpha = 0.4f)
                        TransferStatus.FAILED -> RoseRed.copy(alpha = 0.4f)
                        else -> primaryColor.copy(alpha = 0.4f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (file.status) {
                    TransferStatus.COMPLETED -> AppIcons.Check
                    TransferStatus.CANCELLED -> AppIcons.Cancel
                    else -> fileIcon
                },
                contentDescription = null,
                tint = when (file.status) {
                    TransferStatus.COMPLETED -> EmeraldGreen
                    TransferStatus.CANCELLED -> RoseRed
                    TransferStatus.FAILED -> RoseRed
                    else -> primaryColor
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and progress info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (file.status) {
                        TransferStatus.COMPLETED -> "${file.formattedSize} • COMPLETED"
                        TransferStatus.CANCELLED -> "CANCELLED"
                        TransferStatus.FAILED -> "FAILED"
                        TransferStatus.TRANSFERRING -> "${file.formattedTransferred} / ${file.formattedSize}"
                        TransferStatus.PENDING -> "${file.formattedSize} • PENDING"
                    },
                    color = when (file.status) {
                        TransferStatus.COMPLETED -> EmeraldGreen
                        TransferStatus.CANCELLED -> RoseRed
                        TransferStatus.FAILED -> RoseRed
                        TransferStatus.TRANSFERRING -> primaryColor
                        TransferStatus.PENDING -> TextMuted
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                if (file.status == TransferStatus.TRANSFERRING) {
                    Text(
                        text = "${(file.progress * 100).toInt()}%",
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Cancel button: hide after file is transferred
        if (file.status == TransferStatus.TRANSFERRING || file.status == TransferStatus.PENDING) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onCancelClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceCardBorder.copy(alpha = 0.4f))
                    .testTag("cancel_file_button_${file.id}")
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Cancel File",
                    tint = RoseRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
