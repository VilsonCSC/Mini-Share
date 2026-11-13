package com.example.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    init {
        channel = manager?.initialize(context, context.mainLooper) {
            Log.d("WifiDirectManager", "Channel disconnected")
        }
        registerReceiver()
    }

    fun ensureRegistered() {
        if (receiver == null) {
            registerReceiver()
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestPeers()
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (networkInfo?.isConnected == true) {
                            _isConnected.value = true
                            requestConnectionInfo()
                        } else {
                            _isConnected.value = false
                            _connectionInfo.value = null
                        }
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                        _thisDevice.value = device
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = channel ?: return
        _isDiscovering.value = true
        manager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = true
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                onFailure?.invoke(reason)
            }
        })
    }

    fun stopDiscovery() {
        val ch = channel ?: return
        manager?.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = false
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun requestPeers() {
        val ch = channel ?: return
        manager?.requestPeers(ch) { peersList: WifiP2pDeviceList? ->
            val list = peersList?.deviceList?.toList() ?: emptyList()
            _peers.value = list
        }
    }

    @SuppressLint("MissingPermission")
    fun createGroup(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = channel ?: return
        manager?.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                requestConnectionInfo()
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                // If group already exists, request connection info
                requestConnectionInfo()
                onFailure?.invoke(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: WifiP2pDevice,
        groupOwnerIntent: Int = 0,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            this.groupOwnerIntent = groupOwnerIntent
        }

        manager?.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                onFailure?.invoke(reason)
            }
        })
    }

    fun requestConnectionInfo(onReady: ((WifiP2pInfo) -> Unit)? = null) {
        val ch = channel ?: return
        manager?.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
            if (info != null && info.groupFormed) {
                _connectionInfo.value = info
                _isConnected.value = true
                onReady?.invoke(info)
            }
        }
    }

    fun disconnect() {
        val ch = channel ?: return
        manager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isConnected.value = false
                _connectionInfo.value = null
            }

            override fun onFailure(reason: Int) {
                _isConnected.value = false
                _connectionInfo.value = null
            }
        })
    }

    fun unregister() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        receiver = null
    }
}
