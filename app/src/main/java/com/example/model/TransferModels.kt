package com.example.model

import java.util.Locale

enum class TransferStatus {
    PENDING,
    TRANSFERRING,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class AppScreen {
    HOME,
    RADAR,
    RECEIVE,
    TRANSFER
}

enum class TransferDirection {
    SEND,
    RECEIVE
}

data class FileItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val uriString: String? = null,
    val mimeType: String = "*/*",
    val transferredBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.PENDING
) {
    val progress: Float
        get() = if (sizeBytes > 0) (transferredBytes.toFloat() / sizeBytes).coerceIn(0f, 1f) else 0f

    val formattedSize: String
        get() = formatBytes(sizeBytes)

    val formattedTransferred: String
        get() = formatBytes(transferredBytes)
}

data class TransferStats(
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val remainingSeconds: Long = 0L,
    val overallProgress: Float = 0f
) {
    val formattedSpeed: String
        get() = "${formatBytes(speedBytesPerSec)}/s"

    val formattedSizeProgress: String
        get() = "${formatBytes(transferredBytes)} / ${formatBytes(totalBytes)}"

    val formattedRemainingTime: String
        get() = formatSeconds(remainingSeconds)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun formatSeconds(seconds: Long): String {
    if (seconds <= 0) return "00:00"
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins >= 60) {
        val hrs = mins / 60
        val remMins = mins % 60
        String.format(Locale.US, "%02d:%02d:%02d", hrs, remMins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
