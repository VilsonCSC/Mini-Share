package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.theme.AppTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

private fun getRequiredPermissions(): Array<String> {
    val perms = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
    perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    return perms.toTypedArray()
}

private fun hasAllPermissions(context: Context): Boolean {
    val perms = getRequiredPermissions()
    return perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isWifiEnabled by viewModel.isWifiEnabled.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val transferFiles by viewModel.transferFiles.collectAsStateWithLifecycle()
    val transferStats by viewModel.transferStats.collectAsStateWithLifecycle()
    val transferDirection by viewModel.transferDirection.collectAsStateWithLifecycle()
    val isTransferActive by viewModel.isTransferActive.collectAsStateWithLifecycle()
    val isTransferComplete by viewModel.isTransferComplete.collectAsStateWithLifecycle()
    val transferError by viewModel.transferError.collectAsStateWithLifecycle()
    val thisDevice by viewModel.thisDevice.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesPicked(context, uris)
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            pendingAction = null
            scope.launch {
                snackbarHostState.showSnackbar("Permissions are required for file sharing")
            }
        }
    }

    val requestActionWithPermissions: (() -> Unit) -> Unit = { action ->
        if (hasAllPermissions(context)) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(getRequiredPermissions())
        }
    }

    // Show error toast/snackbar if transfer encounters an error
    LaunchedEffect(transferError) {
        transferError?.let { err ->
            if (err.isNotBlank() && !err.contains("closed", ignoreCase = true)) {
                snackbarHostState.showSnackbar(err)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                AppScreen.HOME -> {
                    HomeScreen(
                        userName = userName,
                        onUserNameChange = { viewModel.updateUserName(it) },
                        onSendClick = {
                            requestActionWithPermissions {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        },
                        onReceiveClick = {
                            requestActionWithPermissions {
                                viewModel.onReceiveClicked()
                            }
                        }
                    )
                }

                AppScreen.RADAR -> {
                    BackHandler {
                        viewModel.resetToHome()
                    }
                    RadarScreen(
                        peers = peers,
                        selectedFiles = selectedFiles,
                        isScanning = isScanning,
                        onBackClick = { viewModel.resetToHome() },
                        onRescanClick = { viewModel.startRadarScan() },
                        onDeviceClick = { device -> viewModel.connectAndSend(device) }
                    )
                }

                AppScreen.RECEIVE -> {
                    BackHandler {
                        viewModel.resetToHome()
                    }
                    ReceiveScreen(
                        userName = userName,
                        thisDevice = thisDevice,
                        onBackClick = { viewModel.resetToHome() }
                    )
                }

                AppScreen.TRANSFER -> {
                    BackHandler {
                        if (isTransferComplete) {
                            viewModel.resetToHome()
                        } else {
                            viewModel.cancelAllTransfers()
                        }
                    }
                    TransferScreen(
                        files = transferFiles,
                        stats = transferStats,
                        direction = transferDirection,
                        isActive = isTransferActive,
                        isComplete = isTransferComplete,
                        onCancelFile = { fileId -> viewModel.cancelTransferringFile(fileId) },
                        onCancelAll = { viewModel.resetToHome() },
                        onDone = { viewModel.resetToHome() }
                    )
                }
            }
        }
    }
}
