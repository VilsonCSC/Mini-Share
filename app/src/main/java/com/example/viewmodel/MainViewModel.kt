package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppScreen
import com.example.model.FileItem
import com.example.model.TransferDirection
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.p2p.WifiDirectManager
import com.example.transfer.FileTransferEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val wifiDirectManager = WifiDirectManager(application)
    private val transferEngine = FileTransferEngine(application)

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()

    private val _transferFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val transferFiles: StateFlow<List<FileItem>> = _transferFiles.asStateFlow()

    private val _transferStats = MutableStateFlow(TransferStats())
    val transferStats: StateFlow<TransferStats> = _transferStats.asStateFlow()

    private val _transferDirection = MutableStateFlow(TransferDirection.SEND)
    val transferDirection: StateFlow<TransferDirection> = _transferDirection.asStateFlow()

    private val _isTransferActive = MutableStateFlow(false)
    val isTransferActive: StateFlow<Boolean> = _isTransferActive.asStateFlow()

    private val _isTransferComplete = MutableStateFlow(false)
    val isTransferComplete: StateFlow<Boolean> = _isTransferComplete.asStateFlow()

    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError.asStateFlow()

    private val _isReceiverWaiting = MutableStateFlow(false)
    val isReceiverWaiting: StateFlow<Boolean> = _isReceiverWaiting.asStateFlow()

    private var transferJob: Job? = null

    val peers = wifiDirectManager.peers
    val isDiscovering = wifiDirectManager.isDiscovering
    val isWifiEnabled = wifiDirectManager.isWifiP2pEnabled
    val thisDevice = wifiDirectManager.thisDevice
    val isConnected = wifiDirectManager.isConnected

    private val prefs = application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val _userName = MutableStateFlow(
        prefs.getString("user_name", null) ?: (android.os.Build.MODEL ?: "User")
    )
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun updateUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            _userName.value = trimmed
            prefs.edit().putString("user_name", trimmed).apply()
        }
    }

    init {
        // Observe connection state
        viewModelScope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                if (info != null && info.groupFormed) {
                    if (_currentScreen.value == AppScreen.RADAR && _selectedFiles.value.isNotEmpty()) {
                        val host = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                        startSendingFiles(host)
                    }
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun onFilesPicked(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val items = uris.mapNotNull { uri ->
            extractFileItem(context, uri)
        }

        if (items.isNotEmpty()) {
            _selectedFiles.value = items
            _transferFiles.value = items
            _transferDirection.value = TransferDirection.SEND
            _currentScreen.value = AppScreen.RADAR
            startRadarScan()
        }
    }

    private fun extractFileItem(context: Context, uri: Uri): FileItem? {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        val mime = context.contentResolver.getType(uri) ?: "*/*"

        if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val file = java.io.File(path)
                if (file.name.isNotBlank()) name = file.name
                if (file.exists()) size = file.length()
            }
        } else {
            try {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex) ?: name
                        }
                        if (sizeIndex != -1) {
                            size = it.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error query URI", e)
            }
        }

        // If size is still 0, try AssetFileDescriptor then stream
        if (size <= 0) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) size = afd.length
                }
            } catch (_: Exception) {}
        }
        if (size <= 0) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    size = stream.available().toLong()
                }
            } catch (_: Exception) {}
        }

        return FileItem(
            id = UUID.randomUUID().toString(),
            name = name,
            sizeBytes = size,
            uriString = uri.toString(),
            mimeType = mime,
            status = TransferStatus.PENDING
        )
    }

    fun startRadarScan() {
        wifiDirectManager.discoverPeers(
            onSuccess = {
                Log.d("MainViewModel", "Discovery started")
            },
            onFailure = {
                Log.e("MainViewModel", "Discovery failed: $it")
            }
        )
    }

    fun stopRadarScan() {
        wifiDirectManager.stopDiscovery()
    }

    fun onReceiveClicked() {
        _transferDirection.value = TransferDirection.RECEIVE
        _currentScreen.value = AppScreen.RECEIVE
        _isReceiverWaiting.value = true

        // Create group so receiver has known Group Owner IP (192.168.49.1)
        wifiDirectManager.createGroup(
            onSuccess = {
                startListeningForSender()
            },
            onFailure = {
                // If group creation failed, start discovery and listen anyway
                wifiDirectManager.discoverPeers()
                startListeningForSender()
            }
        )
    }

    fun connectAndSend(device: WifiP2pDevice) {
        wifiDirectManager.stopDiscovery()
        wifiDirectManager.connectToDevice(
            device = device,
            groupOwnerIntent = 0,
            onSuccess = {
                Log.d("MainViewModel", "Connection initiated with ${device.deviceName}")
            },
            onFailure = {
                _transferError.value = "Failed to connect to ${device.deviceName}"
            }
        )
    }

    private fun startSendingFiles(hostAddress: String) {
        if (_isTransferActive.value) return
        _isTransferActive.value = true
        _isTransferComplete.value = false
        _transferError.value = null
        _currentScreen.value = AppScreen.TRANSFER

        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            transferEngine.sendFiles(
                hostAddress = hostAddress,
                files = _selectedFiles.value,
                onProgress = { updatedFiles, stats ->
                    _transferFiles.value = updatedFiles
                    _transferStats.value = stats
                },
                onComplete = { success, error ->
                    _isTransferActive.value = false
                    _isTransferComplete.value = true
                    _transferError.value = error
                }
            )
        }
    }

    private fun startListeningForSender() {
        transferEngine.stopTransfer()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            _isTransferActive.value = false
            _isTransferComplete.value = false
            _transferError.value = null

            transferEngine.receiveFiles(
                onProgress = { updatedFiles, stats ->
                    _isTransferActive.value = true
                    if (_currentScreen.value != AppScreen.TRANSFER) {
                        _currentScreen.value = AppScreen.TRANSFER
                    }
                    _transferFiles.value = updatedFiles
                    _transferStats.value = stats
                },
                onComplete = { success, error ->
                    _isTransferActive.value = false
                    _isTransferComplete.value = success
                    if (!success && error != null && !error.contains("closed", ignoreCase = true)) {
                        _transferError.value = error
                    }
                }
            )
        }
    }

    fun cancelTransferringFile(fileId: String) {
        transferEngine.cancelFile(fileId)
        _transferFiles.update { list ->
            list.map { item ->
                if (item.id == fileId) {
                    item.copy(status = TransferStatus.CANCELLED)
                } else item
            }
        }
    }

    fun cancelAllTransfers() {
        transferEngine.stopTransfer()
        transferJob?.cancel()
        _isTransferActive.value = false
        _transferFiles.update { list ->
            list.map {
                if (it.status == TransferStatus.TRANSFERRING || it.status == TransferStatus.PENDING) {
                    it.copy(status = TransferStatus.CANCELLED)
                } else it
            }
        }
    }

    fun onAppForegrounded() {
        wifiDirectManager.ensureRegistered()
    }

    fun onAppBackgrounded() {
        resetToHome()
        wifiDirectManager.unregister()
    }

    fun resetToHome() {
        cancelAllTransfers()
        wifiDirectManager.stopDiscovery()
        wifiDirectManager.disconnect()
        _selectedFiles.value = emptyList()
        _transferFiles.value = emptyList()
        _transferStats.value = TransferStats()
        _isTransferComplete.value = false
        _isReceiverWaiting.value = false
        _transferError.value = null
        _currentScreen.value = AppScreen.HOME
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllTransfers()
        wifiDirectManager.unregister()
    }
}
