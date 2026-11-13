package com.example.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.model.formatBytes
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.RadarAnimation

@Composable
fun RadarScreen(
    peers: List<WifiP2pDevice>,
    selectedFiles: List<FileItem>,
    isScanning: Boolean,
    onBackClick: () -> Unit,
    onRescanClick: () -> Unit,
    onDeviceClick: (WifiP2pDevice) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val totalSizeBytes = remember(selectedFiles) {
        selectedFiles.sumOf { it.sizeBytes }
    }
    val totalFormattedSize = remember(totalSizeBytes) {
        formatBytes(totalSizeBytes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("radar_screen")
    ) {
        AppTopBar(
            title = "SEARCHING",
            onBackClick = onBackClick
        )

        // Selected files count & total size pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${selectedFiles.size} FILES • $totalFormattedSize",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onRescanClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline, CircleShape)
                    .testTag("rescan_button")
            ) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = "Rescan",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Searching visual area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            RadarAnimation(
                modifier = Modifier.fillMaxSize(),
                radarColor = colors.primary
            )
        }

        // Discovered Devices List
        Text(
            text = "SEARCHING FOR DEVICES (${peers.size})",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (peers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "SEARCHING..." else "NO DEVICES FOUND",
                            color = colors.outlineVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                itemsIndexed(peers) { index, device ->
                    DeviceItemCard(
                        device = device,
                        testTag = "connect_device_button_$index",
                        onConnectClick = { onDeviceClick(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceItemCard(
    device: WifiP2pDevice,
    testTag: String,
    onConnectClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
            .clickable { onConnectClick() }
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.15f))
                    .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Devices,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = if (device.deviceName.isNullOrBlank()) "Android Device" else device.deviceName,
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.deviceAddress,
                    color = colors.outlineVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary)
                .clickable { onConnectClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "CONNECT",
                color = colors.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
