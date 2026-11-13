package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AppIcons {
    val Send: ImageVector by lazy {
        vectorIcon(
            name = "Send",
            pathData = "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z",
            autoMirror = true
        )
    }

    val FileDownload: ImageVector by lazy {
        vectorIcon(
            name = "FileDownload",
            pathData = "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"
        )
    }

    val Person: ImageVector by lazy {
        vectorIcon(
            name = "Person",
            pathData = "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
        )
    }

    val Edit: ImageVector by lazy {
        vectorIcon(
            name = "Edit",
            pathData = "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
        )
    }

    val Refresh: ImageVector by lazy {
        vectorIcon(
            name = "Refresh",
            pathData = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
        )
    }

    val Devices: ImageVector by lazy {
        vectorIcon(
            name = "Devices",
            pathData = "M4,6h18V4H4c-1.1,0 -2,0.9 -2,2v11H0v3h14v-3H4V6zM23,8h-6c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1V9c0,-0.55 -0.45,-1 -1,-1zM22,17h-4v-7h4v7z"
        )
    }

    val Close: ImageVector by lazy {
        vectorIcon(
            name = "Close",
            pathData = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
        )
    }

    val Check: ImageVector by lazy {
        vectorIcon(
            name = "Check",
            pathData = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"
        )
    }

    val Cancel: ImageVector by lazy {
        vectorIcon(
            name = "Cancel",
            pathData = "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM17,15.59L15.59,17 12,13.41 8.41,17 7,15.59 10.59,12 7,8.41 8.41,7 12,10.59 15.59,7 17,8.41 13.41,12 17,15.59z"
        )
    }

    val Image: ImageVector by lazy {
        vectorIcon(
            name = "Image",
            pathData = "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
        )
    }

    val Movie: ImageVector by lazy {
        vectorIcon(
            name = "Movie",
            pathData = "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z"
        )
    }

    val MusicNote: ImageVector by lazy {
        vectorIcon(
            name = "MusicNote",
            pathData = "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
        )
    }

    val FolderZip: ImageVector by lazy {
        vectorIcon(
            name = "FolderZip",
            pathData = "M20,6h-8l-2,-2H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM18,12h-2v2h2v2h-2v2h-2v-2h2v-2h-2v-2h2v-2h-2V8h2v2h2v2z"
        )
    }

    val Description: ImageVector by lazy {
        vectorIcon(
            name = "Description",
            pathData = "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"
        )
    }

    val InsertDriveFile: ImageVector by lazy {
        vectorIcon(
            name = "InsertDriveFile",
            pathData = "M6,2c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6H6zM13,9V3.5L18.5,9H13z"
        )
    }

    private fun vectorIcon(
        name: String,
        pathData: String,
        autoMirror: Boolean = false
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black)
        ).build()
    }
}
