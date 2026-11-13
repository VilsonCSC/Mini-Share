package com.example.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun KeepScreenOnEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceCardBorder, CircleShape)
                            .clickable { onBackClick() }
                            .testTag("top_bar_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "‹",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (actions != null) {
                actions()
            }
        }
    }
}

@Composable
fun RadarAnimation(
    modifier: Modifier = Modifier,
    radarColor: Color = NeonCyan
) {
    val transition = rememberInfiniteTransition(label = "RadarTransition")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f - 12.dp.toPx()

        // Background concentric circles
        val rings = 4
        for (i in 1..rings) {
            val r = maxRadius * (i.toFloat() / rings)
            drawCircle(
                color = radarColor.copy(alpha = 0.12f),
                radius = r,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Crosshairs
        drawLine(
            color = radarColor.copy(alpha = 0.15f),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = radarColor.copy(alpha = 0.15f),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.dp.toPx()
        )

        // Pulsing wave
        drawCircle(
            color = radarColor.copy(alpha = (1f - pulseScale) * 0.25f),
            radius = maxRadius * pulseScale,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Rotating radar beam line
        val rad = Math.toRadians(sweepAngle.toDouble())
        val endX = center.x + maxRadius * cos(rad).toFloat()
        val endY = center.y + maxRadius * sin(rad).toFloat()

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(radarColor.copy(alpha = 0.9f), radarColor.copy(alpha = 0.0f)),
                start = center,
                end = Offset(endX, endY)
            ),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Center dot
        drawCircle(
            color = radarColor,
            radius = 5.dp.toPx(),
            center = center
        )
    }
}

fun getFileIcon(mimeType: String, fileName: String): ImageVector {
    val lowerName = fileName.lowercase()
    return when {
        mimeType.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> AppIcons.Image
        mimeType.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") -> AppIcons.Movie
        mimeType.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".flac") -> AppIcons.MusicNote
        lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") -> AppIcons.FolderZip
        mimeType.contains("pdf") || lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".txt") -> AppIcons.Description
        else -> AppIcons.InsertDriveFile
    }
}
